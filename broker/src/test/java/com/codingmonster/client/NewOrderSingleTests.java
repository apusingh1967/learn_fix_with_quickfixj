/* Licensed under Apache-2.0 */
package com.codingmonster.client;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.field.*;
import quickfix.fix44.NewOrderSingle;

import java.time.LocalDateTime;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NewOrderSingleTests {

  private Logger LOG = LoggerFactory.getLogger(this.getClass());
  private InitiatorApplication initiatorApp;

  @BeforeAll
  public void before() throws Exception {
    initiatorApp = new InitiatorApplication();
  }

  @AfterAll
  public void afterAll() {
    initiatorApp.stop();
  }

  @Test
  public void new_order_single() throws InterruptedException {
    for (int i = 0; i < 3; i++) {
      NewOrderSingle newOrder =
          new NewOrderSingle(
              new ClOrdID("ORDER123" + i), // Unique client order ID
              new Side(Side.BUY), // Side: BUY/SELL
              new TransactTime(LocalDateTime.now()), // Transaction timestamp
              new OrdType(OrdType.LIMIT) // Order type: LIMIT / MARKET etc.
              );

      // Instrument
      newOrder.set(new Symbol("AAPL"));

      // Order details
      newOrder.set(new OrderQty(100)); // Quantity
      newOrder.set(new Price(99.50)); // Limit price

      // Optional fields
      newOrder.set(new TimeInForce(TimeInForce.DAY)); // Time in force (e.g. DAY, IOC)
      newOrder.set(
          new HandlInst(HandlInst.AUTOMATED_EXECUTION_ORDER_PUBLIC_BROKER_INTERVENTION_OK));

      initiatorApp.sendSbeMessage(newOrder);
      LOG.info("sent sbe message: " + i);

      Thread.sleep(1000);
    }
  }
}
