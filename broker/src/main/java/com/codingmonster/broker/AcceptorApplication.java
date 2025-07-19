/* Licensed under Apache-2.0 */
package com.codingmonster.broker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.Reject;

import java.util.UUID;

public class AcceptorApplication extends MessageCracker implements Application {

  private Logger LOG = LoggerFactory.getLogger(this.getClass());
  private SessionID sessionID;

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
      LOG.info("Other message");
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
  public void onExecutionReport(ExecutionReport report, SessionID sessionId) throws FieldNotFound {
    LOG.info("Received execution report");
  }

  @Handler
  public void onNewOrderSingle(NewOrderSingle order, SessionID sessionId)
          throws FieldNotFound {

    // 1. Process the order (e.g., save to database)
    String orderId = "SERVER-" + UUID.randomUUID().toString().substring(0, 8);

    // 2. Create ExecutionReport
    ExecutionReport executionReport = new ExecutionReport(
            new OrderID(orderId),             // Tag 37 (Server's OrderID)
            new ExecID("EXEC-" + System.currentTimeMillis()), // Tag 17
            new ExecType(ExecType.NEW),       // Tag 150 (New order)
            new OrdStatus(OrdStatus.NEW),     // Tag 39 (Order status)
            order.getSide(),                  // Tag 54 (Buy/Sell)
            new LeavesQty(order.getOrderQty().getValue()), // Tag 151 (Remaining quantity)
            new CumQty(0),              // Tag 14 (Filled quantity = 0 for new orders)
            new AvgPx(150.40)           // Weighted average price
    );

    // 3. Copy fields from NewOrderSingle
    //             order.getOrderQty(),           // Tag 38 (Original quantity)
    executionReport.set(order.getClOrdID());  // Tag 11 (Client Order ID)
    executionReport.set(order.getSymbol());   // Tag 55 (Symbol)
    if (order.isSetPrice()) {
      executionReport.set(order.getPrice()); // Tag 44 (Price for limit orders)
    }

    // 4. Send the ExecutionReport
    try {
      Session.sendToTarget(executionReport, sessionId);
    } catch (SessionNotFound e) {
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

}
