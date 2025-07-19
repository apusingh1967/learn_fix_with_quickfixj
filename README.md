# TODO This is still in progress, do not use
# QuickFIX/J Java Examples: Create, Send, Receive, and Process FIX Messages

This repository demonstrates how to use [QuickFIX/J](https://github.com/quickfix-j/quickfixj) to build Java applications that communicate over the [FIX protocol](https://www.fixtrading.org/).

## 📦 Features

- ✅ Create `NewOrderSingle` (type `D`) and other standard FIX messages
- ✅ Set up **Initiator** (client) and **Acceptor** (server)
- ✅ Send/receive FIX messages over TCP/IP
- ✅ Use standard FIX fields and custom fields
- ✅ Load session settings from `.cfg` files
- ✅ Encode and decode raw SBE binary messages (optional)
- ✅ Full working sample with logs and session management

---

### FIX Architecture
The FIX (Financial Information eXchange) protocol is fundamentally peer-to-peer, not master-slave — but there is an asymmetry in roles:

#### FIX Is Peer-to-Peer (with Roles)
Two parties (e.g., a broker and a client, or an exchange and a trading firm) establish a FIX session over TCP.
Each side can send and receive messages independently during the session.
But there is a defined structure of messages and expectations, and often one side is the Initiator and the other the Acceptor.

#### Initiator vs Acceptor
Initiator: Actively starts the TCP connection and FIX session. Usually the client/trading firm.
Acceptor: Listens for incoming connections. Usually the broker or exchange.
Once connected, both sides can:
Send NewOrderSingle, OrderCancelRequest, etc.
Receive ExecutionReport, Reject, etc.
So message flow is bidirectional, even though connection setup is asymmetric.

#### Is it Like Master-Slave?
No, not in the traditional sense.
There is no strict controller or central authority like in a master-slave system.
Instead, both peers maintain session state, sequence numbers, and can initiate application-level messages.
However, because one side initiates the connection and certain message types are only valid in certain directions (e.g., client sends orders, broker sends fills), it can feel asymmetrical in practice.

#### Example: Buy-Side vs Sell-Side
Buy-Side (Initiator):
Starts connection to broker
Sends NewOrderSingle, OrderCancelRequest
Sell-Side (Acceptor):
Listens for connections
Sends ExecutionReport, OrderCancelReject

#### Session Management
FIX has a session layer:
Logon (MsgType=A)
Logout (MsgType=5)
Sequence reset, heartbeat, test request
Ensures both peers are in sync with sequence numbers and can recover after disconnects.

✅ Summary
FIX is peer-to-peer in message flow.
But has asymmetric connection roles (Initiator vs Acceptor).
Both sides send and receive messages — not a master-slave architecture.

It’s more like a stateful, ordered messaging protocol over TCP, with predefined roles.
---

## 📁 Project Structure

```text
learn_fix_with_quickfix
├── clientgateway
    ├── src/
    │   ├── main/
    │   │   └── java/
    │   │       ├── com.codingmonster.clientgateway.AcceptorApplication       # Acceptor logic
    │   │       ├── com.codingmonster.clientgateway.AcceptorRunner            # starter with main
    │   └── resources/
    │       └── acceptor.cfg         # Server-side FIX config
    │   ├── test/
    │   │   └── java/
    │   │       ├── com.codingmonster.clientgateway.InitiatorApplication       # Initiator logic
    │   │       ├── com.codingmonster.clientgateway.InitiatorRunner            # starter with main
    │   └── resources/
    │       └── initiator.cfg         # client-side FIX config    
    ├── build.gradle
├── settings.gradle
├── gradle.properties
├── README.md

## How it is organized

Single module called clientgateway, to keep things simple
src/main/java has the server side called acceptor in fix parlance
src/test/java has the client being simulated using JUnit
So you have to start server, and then run tests as explained below
Watch the two consoles, client side (tests) and server (acceptor) for log messages
I have added lots of messages and thread.sleep to give a good understanding of interaction

## How to Run

https://github.com/apusingh1967/learn_fix_with_quickfixj.git

cd learn_fix_with_quickfixj

# Run Acceptor - receiving fix messages, processing and sending execution report back
gradle :clientGateway:run

# Run Initiator - sending fix messages
gradle :clientgateway:test

Issues
unset GRADLE_OPTS

## Learning Resources
https://www.youtube.com/watch?v=l1X7pD9nU8A
https://javarevisited.blogspot.com/2011/04/fix-protocol-tutorial-for-beginners.html
