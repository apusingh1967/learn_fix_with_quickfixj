/* Licensed under Apache-2.0 */
package com.codingmonster.clientgateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.field.MsgType;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;

public class AcceptorApplication extends MessageCracker implements Application {

  private Logger LOG = LoggerFactory.getLogger(this.getClass());
  private SessionID sessionID;

  @Override
  public void onCreate(SessionID sessionId) {
    this.sessionID = sessionId;
  }

  @Override
  public void onLogon(SessionID sessionId) {
    System.out.println("Logged in: " + sessionId);
  }

  @Override
  public void onLogout(SessionID sessionId) {
    System.out.println("Logged out: " + sessionId);
  }

  @Override
  public void toAdmin(Message message, SessionID sessionId) {
    try {
      if (MsgType.LOGON.equals(message.getHeader().getString(MsgType.FIELD))) {
        System.out.println("Sending logon: " + message);
      }
    } catch (FieldNotFound e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound {
    System.out.println("ADMIN: " + message);
    if (MsgType.LOGON.equals(message.getHeader().getString(MsgType.FIELD))) {
      System.out.println("Logon details: " + message);
    } else {
      System.out.println("Other message: " + message);
    }
  }

  @Override
  public void toApp(Message message, SessionID sessionId) {
    System.out.println("to app");
  }

  @Override
  public void fromApp(Message message, SessionID sessionId) throws FieldNotFound {
    try {
      LOG.info("*******************");
      LOG.info(String.valueOf(message));
      crack(message, sessionId);
    } catch (UnsupportedMessageType | IncorrectTagValue e) {
      throw new RuntimeException(e);
    }
  }

  @Handler
  public void onExecutionReport(ExecutionReport report, SessionID sessionId) throws FieldNotFound {
    System.out.println("Received execution report: " + report);
  }

  @Handler
  public void onNewOrderSingle(NewOrderSingle order, SessionID sessionId) throws FieldNotFound {
    System.out.println("Received new order: " + order);
  }

  @Handler
  public void onAnyMessage(Message message, SessionID sessionId) {
    System.out.println("Unhandled message: " + message);
  }
}
