/* Licensed under Apache-2.0 */
package com.codingmonster.client;

import java.io.InputStream;
import java.util.concurrent.SynchronousQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;
import quickfix.fix44.ExecutionReport;

public class InitiatorApplication extends MessageCracker implements Application {

  private final Logger LOG = LoggerFactory.getLogger(this.getClass());
  private final SynchronousQueue<ExecutionReport>queue;
  private SessionID sessionID;

  public InitiatorApplication(SynchronousQueue<ExecutionReport> queue) {
    this.queue = queue;
    try {
      InputStream input =
          InitiatorApplication.class.getClassLoader().getResourceAsStream("initiator.cfg");
      assert input != null;
      SessionSettings settings = new SessionSettings(input);

      MessageStoreFactory storeFactory =
          new MemoryStoreFactory(); // new FileStoreFactory(settings);
      LogFactory logFactory = new ScreenLogFactory(settings); // new FileLogFactory(settings);
      MessageFactory messageFactory = new DefaultMessageFactory();

      SocketInitiator initiator =
          new SocketInitiator(this, storeFactory, settings, logFactory, messageFactory);
      initiator.start();
    } catch (ConfigError e) {
      throw new RuntimeException(e);
    }
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
  public void toAdmin(Message message, SessionID sessionId) {}

  @Override
  public void fromAdmin(Message message, SessionID sessionId) {}

  @Override
  public void toApp(Message message, SessionID sessionId) {}

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
  public void onExecutionReport(ExecutionReport report, SessionID sessionId) {
    LOG.info("Received execution report: " + report);
    try {
      queue.put(report);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @Handler
  public void onAnyMessage(Message message, SessionID sessionId) {
    LOG.info("Unhandled message: " + message);
  }

  public void sendSbeMessage(Message message) {
    try {
      Session.sendToTarget(message, sessionID);
    } catch (SessionNotFound e) {
      throw new RuntimeException(e);
    }
  }

  public void stop() {
    // Gracefully terminate the session (sends Logout + waits for reply)
    Session.lookupSession(sessionID).logout();
  }
}
