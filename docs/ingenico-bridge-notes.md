# Ingenico Desk 3500 — Terminal Bridge Implementation Notes

## Verification status (2026-08-04)

This file was originally an unverified sketch from a Claude Desktop conversation
with no access to real Ingenico docs. Two real, official PDFs are now in hand
(gitignored, not committed — see `docs/.gitignore`):

- **iConnect WebSockets Specification - TSI Rev 1.27.1** (`DIV351683`, Ingenico,
  copyright 2022) — the actual wire-protocol spec.
- **Tetra Semi-Integrated Addendum** (`0030-08708-0124`, Ingenico, May 2020) —
  terminal-side setup/configuration spec.

**Confirmed correct** against the real spec: the endpoint (`ws://.../tsi/v1/payment`),
the `request`/`response`/`event`/`event_ack` message-pair flow, `flow_id`
correlation, and amounts represented in cents.

**Corrected**:
- The transaction identifier used for a later `void` or `pre_auth_completion`
  is **`reference_no`** (returned in the original transaction's response),
  **not `invoice_no`** as this file originally assumed. `FolioPayment`'s
  transaction-id column should store `reference_no`.
- **`pre_auth` and `pre_auth_completion` are real, separate transaction types**
  (spec §5.3.4/§5.3.5), distinct from `sale` — contrary to this file's original
  "incidentals as sale only" design. This means the existing
  `PaymentService.createIncidentalHold`/`capture()`/`captureHold`/`cancelHold`
  two-phase hold logic can likely be implemented against this API unchanged
  (`authorize()` → `pre_auth`, `capture()` → `pre_auth_completion`,
  `void_()` → `void`). **Still needs confirmation** — a previous Elavon rep
  claimed no pre-auth/hold capability exists, directly contradicting the spec.
  Resolve which is right before designing the incidentals flow around this.
- **`refund` (§5.3.8) is not a reference-based reversal** — its request
  params have no `reference_no` field, only `amount`/`tender_type`/etc.
  This suggests refunding likely requires the card to be re-presented at the
  terminal (or a `cash` tender_type payout), unlike `void` (which does use
  `reference_no` to look up the original transaction, but only works
  pre-settlement/same-batch). **Needs confirmation**: exact mechanics for
  refunding a guest who has already checked out and left, since the existing
  same-day-void-else-refund pattern in `PaymentService.refundPayment` may not
  translate directly if "refund" requires physical card presence.
- **Desk 3500 support**: neither PDF names the Desk 3500 (only Desk 5000 /
  Move 5000 appear in the addendum's device list and the spec's sample
  `terminal_info` response). User confirmed directly with Banccard/Elavon on
  a call (2026-08-03/04) that the Desk 3500 is supported — this is confirmed
  via that conversation, not via either written document.
- Local terminal↔ECR communication types per the addendum: Direct Ethernet,
  Direct USB, RS232 (Desk 5000). Ethernet is TCP/IP, terminal acts as
  **server**, ECR as **client**, **mono-server (one-to-one) only**, and
  **does not support SSL** (`ws://`, not `wss://`, on that leg — confirms the
  plan's assumption that only the Render↔bridge-agent leg needs its own
  security, not the bridge↔terminal leg).
- Built-in reconciliation tools exist that the original sketch didn't account
  for: `recall_last_transaction` and `recent_error` (`/tsi/v1/reports`) let
  the bridge ask the terminal "what actually happened last" after a
  reconnect — a better crash-recovery primitive than manual receipt checking.

**Still open before Phase 1 coding starts** (per the locked plan at
`~/.claude/plans/twinkly-dancing-quasar.md`): resolve the `pre_auth`
contradiction and the exact refund-after-checkout mechanics with
Ingenico/Elavon.

## Context

StayDesk backend runs on Render (cloud). The Ingenico Desk 3500 terminal is on the local motel network. They cannot communicate directly. A bridge agent running on the front desk Windows laptop relays commands between the two.

The terminal uses the **iConnect WebSockets TSI API** — a WebSocket-based semi-integrated POS API that runs on the terminal itself.

---

## Architecture

```
StayDesk UI (browser)
    ↕ HTTPS
Render backend (api.martinhousemotel.com)
    ↕ WebSocket (wss) — persistent, outbound from laptop
Bridge Agent (front desk Windows laptop)
    ↕ WebSocket (ws) — local network
Ingenico Desk 3500 (192.168.x.x)
    ↕ Elavon payment network
```

**Key point:** Render never knows the terminal's internal IP. The bridge agent holds that config locally. Render just sends/receives commands over the persistent WebSocket connection from the laptop.

---

## iConnect WebSockets API

**Endpoint on terminal:**
```
ws://[terminal-ip]/tsi/v1/payment
```

**Message format — all messages are JSON with a `flow_id`:**
```json
{
  "request": {
    "flow_id": "277554",
    "resource": {
      "type": "sale",
      "amount": 6417
    }
  }
}
```

Amount is always in **cents** (e.g. 6417 = $64.17).

**Transaction flow is async — three message pairs:**

1. Client sends `request` → terminal responds immediately with `"status": "started"`
2. Guest taps/dips/swipes card on terminal
3. Terminal sends `event` with result → client sends `event_ack`

**Supported transaction types:**
- `sale` — auth + capture in one step
- `refund` — post-settlement refund
- `void` — same-day pre-batch cancellation
- `force`, `card_balance`, `reprint_receipt`

**Sale request:**
```json
{
  "request": {
    "flow_id": "277554",
    "resource": {
      "type": "sale",
      "amount": 6417
    }
  }
}
```

**Sale event response (success):**
```json
{
  "event": {
    "flow_id": "277554",
    "resource": {
      "status": "completed",
      "results": [
        {
          "authorization_no": "191509",
          "status": "approved",
          "total_amount": "6417",
          "transaction_amount": "6417",
          "transaction_date": "26/07/14",
          "transaction_time": "04:21:22",
          "type": "sale",
          "card": {
            "account_no": "************2205",
            "entry_mode": { "code": "0", "text": "magnetic_strip" },
            "type": { "code": "02", "text": "mastercard" }
          },
          "terminal_id": "001",
          "merchant_id": "VISAMID0123",
          "reference_no": "3"
        }
      ]
    }
  }
}
```

**Refund request:**
```json
{
  "request": {
    "flow_id": "unique-id",
    "resource": {
      "type": "refund",
      "amount": 10000,
      "invoice_no": "original-transaction-id"
    }
  }
}
```

**Void request:**
```json
{
  "request": {
    "flow_id": "unique-id",
    "resource": {
      "type": "void",
      "invoice_no": "original-transaction-id"
    }
  }
}
```

---

## Incidentals Hold Logic

Incidentals are captured as a full **sale** at check-in (not an auth-only hold). They batch out that night. At checkout, release logic is:

```
If checkout same calendar day as capture → VOID (cleaner, no money moved)
  If void fails (already batched) → fall back to REFUND
Else → REFUND remainder

If incidentals fully consumed → no action
If partial incidentals used → REFUND remainder only
```

**Folio fields to store at incidentals capture:**
```
incidentals_transaction_id   — authorization_no or invoice_no from terminal
incidentals_hold_amount      — dollar amount captured
incidentals_capture_date     — LocalDate, for same-day void decision
incidentals_payment_provider — "ingenico" (routes refund back through correct provider)
```

---

## Render Backend — New Components

### Dependencies (`pom.xml`)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

### `TerminalBridgeEndpoint.java`
Accepts the persistent WebSocket connection from the bridge agent.

```java
@Component
@ServerEndpoint("/terminal-bridge")
public class TerminalBridgeEndpoint {

    private static Session bridgeSession;

    @OnOpen
    public void onOpen(Session session) {
        bridgeSession = session;
        System.out.println("Bridge agent connected");
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        // Result from terminal via bridge — route by flow_id
        TerminalResponseRouter.route(message);
    }

    @OnClose
    public void onClose(Session session) {
        bridgeSession = null;
        System.out.println("Bridge agent disconnected");
    }

    public static void sendToTerminal(String message) throws IOException {
        if (bridgeSession != null && bridgeSession.isOpen()) {
            bridgeSession.getBasicRemote().sendText(message);
        } else {
            throw new IllegalStateException("Terminal bridge not connected");
        }
    }
}
```

### `TerminalResponseRouter.java`
Correlates async `flow_id` responses.

```java
@Component
public class TerminalResponseRouter {

    private static final Map<String, CompletableFuture<String>>
        pendingRequests = new ConcurrentHashMap<>();

    public static CompletableFuture<String> register(String flowId) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(flowId, future);
        return future;
    }

    public static void route(String message) {
        String flowId = JsonPath.read(message, "$.event.flow_id");
        CompletableFuture<String> future = pendingRequests.remove(flowId);
        if (future != null) {
            future.complete(message);
        }
    }
}
```

### `IngenicoPaymentProvider.java`
Implements existing `PaymentProvider` interface.

```java
@Service("ingenico")
public class IngenicoPaymentProvider implements PaymentProvider {

    @Override
    public AuthResult authorize(BigDecimal amount, String token, String description) {
        String flowId = UUID.randomUUID().toString();
        CompletableFuture<String> future = TerminalResponseRouter.register(flowId);

        String request = """
            {"request": {
                "flow_id": "%s",
                "resource": {
                    "type": "sale",
                    "amount": %d
                }
            }}
            """.formatted(flowId, amount.multiply(BigDecimal.valueOf(100)).intValue());

        try {
            TerminalBridgeEndpoint.sendToTerminal(request);
            // 30s timeout — guest is standing at terminal
            String response = future.get(30, TimeUnit.SECONDS);
            return parseAuthResult(response);
        } catch (TimeoutException e) {
            return AuthResult.failure("Terminal timeout — no response from card reader");
        } catch (Exception e) {
            return AuthResult.failure(e.getMessage());
        }
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount) {
        String flowId = UUID.randomUUID().toString();
        CompletableFuture<String> future = TerminalResponseRouter.register(flowId);

        String request = """
            {"request": {
                "flow_id": "%s",
                "resource": {
                    "type": "refund",
                    "amount": %d,
                    "invoice_no": "%s"
                }
            }}
            """.formatted(
                flowId,
                amount.multiply(BigDecimal.valueOf(100)).intValue(),
                transactionId
            );

        try {
            TerminalBridgeEndpoint.sendToTerminal(request);
            String response = future.get(30, TimeUnit.SECONDS);
            return parseRefundResult(response);
        } catch (TimeoutException e) {
            return RefundResult.failure("Terminal timeout");
        } catch (Exception e) {
            return RefundResult.failure(e.getMessage());
        }
    }

    @Override
    public VoidResult void_(String transactionId) {
        String flowId = UUID.randomUUID().toString();
        CompletableFuture<String> future = TerminalResponseRouter.register(flowId);

        String request = """
            {"request": {
                "flow_id": "%s",
                "resource": {
                    "type": "void",
                    "invoice_no": "%s"
                }
            }}
            """.formatted(flowId, transactionId);

        try {
            TerminalBridgeEndpoint.sendToTerminal(request);
            String response = future.get(30, TimeUnit.SECONDS);
            return parseVoidResult(response);
        } catch (TimeoutException e) {
            return VoidResult.failure("Terminal timeout");
        } catch (Exception e) {
            return VoidResult.failure(e.getMessage());
        }
    }

    private AuthResult parseAuthResult(String json) {
        String status = JsonPath.read(json, "$.event.resource.results[0].status");
        String authNo = JsonPath.read(json, "$.event.resource.results[0].authorization_no");
        String totalAmount = JsonPath.read(json, "$.event.resource.results[0].total_amount");
        if ("approved".equals(status)) {
            return AuthResult.success(authNo, totalAmount);
        }
        return AuthResult.failure(status);
    }

    private RefundResult parseRefundResult(String json) {
        String status = JsonPath.read(json, "$.event.resource.results[0].status");
        String authNo = JsonPath.read(json, "$.event.resource.results[0].authorization_no");
        if ("approved".equals(status)) {
            return RefundResult.success(authNo);
        }
        return RefundResult.failure(status);
    }

    private VoidResult parseVoidResult(String json) {
        String status = JsonPath.read(json, "$.event.resource.results[0].status");
        if ("approved".equals(status)) {
            return VoidResult.success();
        }
        return VoidResult.failure(status);
    }
}
```

### `IncidentalsService.java`
Handles void vs refund decision at checkout.

```java
@Service
public class IncidentalsService {

    private final IngenicoPaymentProvider paymentProvider;
    private final FolioRepository folioRepository;

    public void releaseIncidentalsHold(Long folioId, BigDecimal amountUsed) {
        Folio folio = folioRepository.findById(folioId).orElseThrow();
        BigDecimal holdAmount = folio.getIncidentalsHoldAmount();
        BigDecimal remainder = holdAmount.subtract(amountUsed);

        if (remainder.compareTo(BigDecimal.ZERO) <= 0) {
            // Fully consumed — nothing to return
            return;
        }

        String transactionId = folio.getIncidentalsTransactionId();
        LocalDate captureDate = folio.getIncidentalsCaptureDate();
        LocalDate today = LocalDate.now();

        if (captureDate.equals(today)) {
            // Same day — try void first
            VoidResult voidResult = paymentProvider.void_(transactionId);
            if (!voidResult.success()) {
                // Already batched — fall back to refund
                paymentProvider.refund(transactionId, remainder);
            }
        } else {
            // Already batched — must refund
            paymentProvider.refund(transactionId, remainder);
        }
    }
}
```

---

## Bridge Agent — Separate Spring Boot Project

**Repo:** create `ryanpalmercs/staydesk-bridge` (or add as a module)

### Dependencies (`pom.xml`)
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>org.java-websocket</groupId>
    <artifactId>Java-WebSocket</artifactId>
    <version>1.5.4</version>
</dependency>
```

### `application.properties`
```properties
staydesk.render.url=wss://api.martinhousemotel.com/terminal-bridge
staydesk.terminal.ip=192.168.1.x
staydesk.terminal.port=80
staydesk.bridge.secret=your-shared-secret-here
```

### `TerminalBridgeClient.java`
Outbound WebSocket to Render.

```java
@Component
public class TerminalBridgeClient extends WebSocketClient {

    private final IngenicoTerminalClient terminalClient;

    public TerminalBridgeClient(
            @Value("${staydesk.render.url}") String renderUrl,
            IngenicoTerminalClient terminalClient) throws URISyntaxException {
        super(new URI(renderUrl));
        this.terminalClient = terminalClient;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Connected to Render backend");
    }

    @Override
    public void onMessage(String message) {
        // Command from Render — relay to terminal
        terminalClient.send(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Disconnected from Render: " + reason);
        scheduleReconnect();
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void sendToRender(String message) {
        if (isOpen()) {
            send(message);
        }
    }

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                reconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }
}
```

### `IngenicoTerminalClient.java`
Local WebSocket to terminal.

```java
@Component
public class IngenicoTerminalClient extends WebSocketClient {

    private final TerminalBridgeClient bridgeClient;

    public IngenicoTerminalClient(
            @Value("${staydesk.terminal.ip}") String terminalIp,
            @Value("${staydesk.terminal.port}") int terminalPort,
            TerminalBridgeClient bridgeClient) throws URISyntaxException {
        super(new URI("ws://" + terminalIp + ":" + terminalPort + "/tsi/v1/payment"));
        this.bridgeClient = bridgeClient;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        System.out.println("Connected to Ingenico terminal");
    }

    @Override
    public void onMessage(String message) {
        // Result from terminal — relay up to Render
        bridgeClient.sendToRender(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Terminal disconnected: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }
}
```

### `BridgeApplication.java`
```java
@SpringBootApplication
public class BridgeApplication {

    public static void main(String[] args) throws Exception {
        ConfigurableApplicationContext ctx =
            SpringApplication.run(BridgeApplication.class, args);

        TerminalBridgeClient bridgeClient = ctx.getBean(TerminalBridgeClient.class);
        IngenicoTerminalClient terminalClient = ctx.getBean(IngenicoTerminalClient.class);

        bridgeClient.connectBlocking();
        terminalClient.connectBlocking();
    }
}
```

### WinSW config (`staydesk-bridge.xml`)
Registers the bridge agent as a Windows service that starts on boot.

```xml
<service>
    <id>StayDeskBridge</id>
    <name>StayDesk Terminal Bridge</name>
    <description>Bridges StayDesk cloud backend to Ingenico Desk 3500</description>
    <executable>java</executable>
    <arguments>-jar staydesk-bridge.jar</arguments>
    <logmode>rotate</logmode>
    <onfailure action="restart" delay="10 sec"/>
    <onfailure action="restart" delay="30 sec"/>
    <onfailure action="restart" delay="60 sec"/>
</service>
```

---

## Versioning

- `1.0.0` — Phase 1 launch
- `1.0.1` — Post-launch bug fixes (in progress)
- `1.1.0` — Ingenico terminal bridge integration (this work)
- `2.0.0` — Phase 2 (remote check-in, QuickBooks, booking site, OTA)

---

## Key Notes for Implementation

- Render backend does NOT need the terminal's internal IP — that lives in bridge agent config only
- The bridge agent's WebSocket to Render is **outbound** — no firewall/NAT issues
- `flow_id` is a UUID generated per transaction — used to correlate async terminal responses
- 30-second timeout on terminal responses — guest is standing at the terminal waiting
- Incidentals are a **sale** (not auth-only) — they batch nightly — release at checkout is a **refund** (or same-day **void** with refund fallback)
- Bridge agent runs as a Windows service via WinSW — starts on boot, auto-restarts on failure
- Add heartbeat/ping-pong to the Render WebSocket endpoint to keep the persistent connection alive
