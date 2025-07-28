/* Licensed under Apache-2.0 */
package com.codingmonster.broker;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.Reject;

public class AcceptorApplication extends MessageCracker implements Application {
  private Logger LOG = LoggerFactory.getLogger(this.getClass());
  private SessionID sessionID;

  // controls
  private final Map<String, Control> controls;

  public AcceptorApplication(Map<String, Control> controls) {
    this.controls = controls;
  }

  @Override
  public void onCreate(SessionID sessionId) {
    this.sessionID = sessionId;
  }

  @Override
  public void onLogon(SessionID sessionId) {
    LOG.info("Logged in: " + sessionId);
  }

  @Override
  public void onLogout(SessionID sessionId) {
    LOG.info("Logged out: " + sessionId);
  }

  @Override
  public void toAdmin(Message message, SessionID sessionId) {
    try {
      if (MsgType.LOGON.equals(message.getHeader().getString(MsgType.FIELD))) {
        LOG.info("Sending logon message");
      }
    } catch (FieldNotFound e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound {
    LOG.info("ADMIN: " + message);
    if (MsgType.LOGON.equals(message.getHeader().getString(MsgType.FIELD))) {
      LOG.info("Successful Logon");
    } else {
      LOG.info("Other message: " + message.getHeader().getString(MsgType.FIELD));
    }
  }

  @Override
  public void toApp(Message message, SessionID sessionId) {
    LOG.info("to app");
  }

  @Override
  public void fromApp(Message message, SessionID sessionId) throws FieldNotFound {
    try {
      LOG.info("Message Received");

      // using crack appears more convenient than:
      //   if (MsgType.NEW_ORDER_SINGLE.equals(message.getHeader().getString(MsgType.FIELD))) {
      // crack will check header message type and call appropriate method with @Handler
      crack(message, sessionId);
    } catch (UnsupportedMessageType | IncorrectTagValue e) {
      throw new RuntimeException(e);
    }
  }

  @Handler
  public void onExecutionReport(ExecutionReport report, SessionID sessionId) {
    LOG.info("Received execution report");
  }

  @Handler
  public void onNewOrderSingle(NewOrderSingle order, SessionID sessionId) {
    try {
      // 1. Validate the order (example: price check)
      String reason;
      if (!(reason = validate(order)).isEmpty()) {
        sendRejection(order, sessionId, reason, OrdRejReason.INCORRECT_QUANTITY);
      } else {
        sendSuccessNewOrderSingle(order, sessionId);
      }
    } catch (FieldNotFound e) {
      throw new RuntimeException(e);
    }
  }

  private String validate(NewOrderSingle order) {
    var reason = new StringBuilder();
    try {
      if (!controls.containsKey(order.getSymbol().getValue())) {
        reason.append("Symbol: ").append(order.getSymbol()).append(" not allowed to trade");
      } else {
        var control = controls.get(order.getSymbol().getValue());
        boolean qtyInvalid = false;
        if (order.getOrderQty().getValue() >= control.maxQty) {
          reason
              .append("Max qty: ")
              .append(order.getOrderQty().getValue())
              .append(" exceeded configured: ")
              .append(control.maxQty);
          qtyInvalid = true;
        }
        if (order.getPrice().getValue() >= control.maxPrice) {
          if (qtyInvalid) reason.append(" | ");
          reason
              .append("Max Price: ")
              .append(order.getPrice().getValue())
              .append(" exceeded configured: ")
              .append(control.maxPrice);
        }
      }
    } catch (FieldNotFound e) {
      throw new RuntimeException(e);
    }
    return reason.toString();
  }

  private void sendSuccessNewOrderSingle(NewOrderSingle order, SessionID sessionId) {
    try {
      // 1. Process the order (e.g., save to database)
      // N = New
      // P = Partially Filled
      // F = Filled
      // C = Canceled
      // R = Replaced
      // X = Rejected
      // seems nice way to calculate orderId combining senderCompId and clOrdId, keeps track of the original order id, and the sender
      String orderId =  order.getHeader().getString(SenderCompID.FIELD) + "-" + order.getClOrdID();

      // 2. Create ExecutionReport
      ExecutionReport executionReport =
          new ExecutionReport(
              new OrderID(orderId), // Tag 37 (Server's OrderID)
              new ExecID("EXEC-" + System.currentTimeMillis()), // Tag 17
              new ExecType(
                  ExecType.NEW), // Tag 150 (New order), this indicates what happened e.g. FILL
              new OrdStatus(
                  OrdStatus.NEW), // Tag 39 (Order status), this indicates what is status now, e.g.
              // FILLED or PARTIALLY_FILLED
              order.getSide(), // Tag 54 (Buy/Sell)
              new LeavesQty(order.getOrderQty().getValue()), // Tag 151 (Remaining quantity)
              new CumQty(0), // Tag 14 (Filled quantity = 0 for new orders)
              new AvgPx(150.40) // Weighted average price
              );

      // 3. Copy fields from NewOrderSingle
      //             order.getOrderQty(),           // Tag 38 (Original quantity)
      executionReport.set(order.getClOrdID()); // Tag 11 (Client Order ID)
      executionReport.set(order.getSymbol()); // Tag 55 (Symbol)
      executionReport.set(order.getOrderQty()); // Tag 38 (Quantity)
      if (order.isSetPrice()) {
        executionReport.set(order.getPrice()); // Tag 44 (Price for limit orders)
      }

      // 4. Send the ExecutionReport
      try {
        Session.sendToTarget(executionReport, sessionId);
      } catch (SessionNotFound e) {
        throw new RuntimeException(e);
      }
    } catch (FieldNotFound e) {
      throw new RuntimeException(e);
    }
  }

  private void sendRejection(
      NewOrderSingle order, SessionID sessionId, String reasonText, int ordRejReason)
      throws FieldNotFound {
    try {
      // Create ExecutionReport for rejection
      ExecutionReport rejection =
          new ExecutionReport(
              new OrderID("REJ-" + System.currentTimeMillis()), // Server-generated ID
              new ExecID("EXEC-" + UUID.randomUUID().toString().substring(0, 8)),
              new ExecType(ExecType.REJECTED), // Tag 150=8
              new OrdStatus(OrdStatus.REJECTED), // Tag 39=8
              order.getSide(), // Tag 54 (Buy/Sell)
              new LeavesQty(order.getOrderQty().getValue()), // Tag 151 (Remaining quantity)
              new CumQty(0), // Tag 14 (Filled quantity = 0 for new orders)
              new AvgPx(150.40) // Weighted average price
              );

      // Copy fields from NewOrderSingle
      rejection.set(order.getClOrdID()); // Tag 11 (Client's Order ID)
      rejection.set(order.getSymbol()); // Tag 55 (Symbol)
      rejection.set(order.getOrderQty()); // Tag 38 (Quantity)
      if (order.isSetPrice()) {
        rejection.set(order.getPrice()); // Tag 44 (Price)
      }

      // Set rejection details
      rejection.setString(Text.FIELD, reasonText); // Tag 58
      rejection.setInt(OrdRejReason.FIELD, ordRejReason); // Tag 103

      // Send the rejection
      try {
        Session.sendToTarget(rejection, sessionId);
      } catch (SessionNotFound e) {
        throw new RuntimeException(e);
      }
    } catch (FieldNotFound e) {
      throw new RuntimeException(e);
    }
  }

  @Handler
  public void onAnyMessage(Message message, SessionID sessionId) {
    LOG.info("Unhandled message");
  }

  // only for protocol errors, not business logic errors
  private void rejectOrder(Message message, String reason) {
    Reject reject = new Reject();
    reject.setString(Text.FIELD, reason);
    try {
      reject.setField(new RefSeqNum(message.getHeader().getInt(MsgSeqNum.FIELD)));
      Session.sendToTarget(reject, sessionID);
    } catch (FieldNotFound | SessionNotFound e) {
      throw new RuntimeException(e);
    }
  }

  public static class Control {
    public double maxPrice;
    public int maxQty;

    public Control(double maxPrice, int maxQty) {
      this.maxPrice = maxPrice;
      this.maxQty = maxQty;
    }
  }
}
