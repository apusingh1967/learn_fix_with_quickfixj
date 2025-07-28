/* Licensed under Apache-2.0 */
package com.codingmonster.client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.SynchronousQueue;

import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import quickfix.FieldNotFound;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;

// There is going to be only one instance of test class to avoid starting and connecting to acceptor (the broker) for each test case.
// Therefore @TestInstance is Per Class
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NewOrderSingleTests {

  private Logger LOG = LoggerFactory.getLogger(this.getClass());
  private InitiatorApplication initiatorApp;
  private SynchronousQueue<ExecutionReport>queue = new SynchronousQueue<>();

  @BeforeAll // for TestInstance PER_CLASS, this will execute only once and not per test case
  public void before() {
    initiatorApp = new InitiatorApplication(queue);
  }

  @AfterAll
  public void afterAll() {
    initiatorApp.stop();
  }

  @Test
  public void given_new_order_single_when_valid_then_success() throws InterruptedException {
    NewOrderSingle newOrder = getNewOrderSingle();

    newOrder.set(new Symbol("AAPL"));
    newOrder.set(new OrderQty(100)); // Quantity
    newOrder.set(new Price(99.50)); // Limit price

    initiatorApp.sendSbeMessage(newOrder);

    ExecutionReport report = queue.take(); // will wait till ER available
    LOG.info(report.toRawString());
    Assertions.assertNotNull(report);
    try {
      var status = report.getOrdStatus();
      Assertions.assertEquals(OrdStatus.NEW, status.getValue());
      var execType = report.getExecType();
      Assertions.assertEquals(ExecType.NEW, execType.getValue());
    } catch (FieldNotFound e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void given_new_order_single_when_invalid_symbol_then_reject() throws InterruptedException {
    NewOrderSingle newOrder = getNewOrderSingle();

    newOrder.set(new Symbol("BOZO"));
    newOrder.set(new OrderQty(100)); // Quantity
    newOrder.set(new Price(99.50)); // Limit price

    initiatorApp.sendSbeMessage(newOrder);

    ExecutionReport report = queue.take(); // will wait till ER available
    LOG.info(report.toRawString());
    Assertions.assertNotNull(report);
    try {
      var status = report.getOrdStatus();
      Assertions.assertEquals(OrdStatus.REJECTED, status.getValue());
      var execType = report.getExecType();
      Assertions.assertEquals(ExecType.REJECTED, execType.getValue());
    } catch (FieldNotFound e) {
      throw new RuntimeException(e);
    }
  }

  private static NewOrderSingle getNewOrderSingle() {
    // order type marker (e.g., N for new, R for replace, C for cancel)
    String orderId = String.format("%s-%s-%s", "TRADER1", "N", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")));
    NewOrderSingle newOrder =
        new NewOrderSingle(
            new ClOrdID(orderId), // Unique client order ID
            new Side(Side.BUY), // Side: BUY/SELL
            new TransactTime(LocalDateTime.now()), // Transaction timestamp
            new OrdType(OrdType.LIMIT) // Order type: LIMIT / MARKET etc.
            );

    // Optional fields
    newOrder.set(new TimeInForce(TimeInForce.DAY)); // Time in force (e.g. DAY, IOC)
    newOrder.set(new HandlInst(HandlInst.AUTOMATED_EXECUTION_ORDER_PUBLIC_BROKER_INTERVENTION_OK));
    return newOrder;
  }
}
