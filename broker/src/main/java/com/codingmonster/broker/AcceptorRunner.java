/* Licensed under Apache-2.0 */
package com.codingmonster.broker;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.*;

public class AcceptorRunner {

  private Logger LOG = LoggerFactory.getLogger(this.getClass());
  private CountDownLatch latch = new CountDownLatch(1);

  public static void main(String[] args) {
    new AcceptorRunner().run();
  }

  public void run() {
    InputStream input =
        AcceptorApplication.class.getClassLoader().getResourceAsStream("acceptor.cfg");
    assert input != null;

    try {
      SessionSettings settings = new SessionSettings(input);
      MessageStoreFactory storeFactory =
          new MemoryStoreFactory(); // new FileStoreFactory(settings);
      LogFactory logFactory = new ScreenLogFactory(settings); // new FileLogFactory(settings);
      MessageFactory messageFactory = new DefaultMessageFactory();

      var controls =
          Map.of(
              "AAPL", new AcceptorApplication.Control(320.3, 100_000),
              "MSFT", new AcceptorApplication.Control(350.4, 100_000),
              "GOOG", new AcceptorApplication.Control(220.8, 200_000));

      AcceptorApplication application = new AcceptorApplication(controls);
      Acceptor acceptor =
          new SocketAcceptor(application, storeFactory, settings, logFactory, messageFactory);
      acceptor.start();
      latch.await();
    } catch (ConfigError | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
