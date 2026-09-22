package com.staydesk.mockterminal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class MockTerminalServer extends WebSocketServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long RESPONSE_DELAY_MS = 1500;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private final Map<String, Long> saleAmountsByReferenceNo = new ConcurrentHashMap<>();
    private final AtomicLong batchSaleAmount = new AtomicLong();
    private final AtomicLong batchSaleCount = new AtomicLong();
    private final AtomicLong batchRefundAmount = new AtomicLong();
    private final AtomicLong batchRefundCount = new AtomicLong();
    private final AtomicLong batchVoidAmount = new AtomicLong();
    private final AtomicLong batchVoidCount = new AtomicLong();

    public MockTerminalServer(int port) {
        super(new InetSocketAddress(port));
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        log("Bridge agent connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        log("Bridge agent disconnected: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        log("Received: " + message);

        JsonNode root;
        try {
            root = MAPPER.readTree(message);
        } catch (Exception e) {
            log("Could not parse incoming message, ignoring: " + e.getMessage());
            return;
        }

        JsonNode request = root.get("request");
        if (request == null) {
            log("Message has no \"request\" field, ignoring");
            return;
        }

        String flowId = request.path("flow_id").asText(null);
        JsonNode resource = request.get("resource");

        if (flowId == null || resource == null) {
            log("Request missing flow_id or resource, ignoring");
            return;
        }

        sendResponseStarted(conn, flowId);
        scheduler.schedule(() -> sendEvent(conn, flowId, resource), RESPONSE_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        log("Error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        log("Server started");
    }

    private void sendResponseStarted(WebSocket conn, String flowId) {
        Map<String, Object> payload = Map.of("response",
                Map.of("flow_id", flowId, "resource", Map.of("status", "started")));
        send(conn, payload);
    }

    private void sendEvent(WebSocket conn, String flowId, JsonNode resource) {
        if (!conn.isOpen()) {
            log("Connection closed before event could be sent for flow " + flowId);
            return;
        }

        String type = resource.path("type").asText("");
        long amountCents = resource.path("amount").asLong(-1);
        boolean isSaleOrRefund = type.equals("sale") || type.equals("refund");

        if (isSaleOrRefund && amountCents == 999) {
            log("Amount $9.99 magic value - deliberately not sending an event for flow " + flowId);
            return;
        }

        if (isSaleOrRefund && amountCents == 1) {
            log("Amount $0.01 magic value - sending a malformed event for flow " + flowId);
            conn.send("{\"event\": {\"flow_id\": \"" + flowId + "\", \"resource\": { this is not valid json");
            return;
        }

        Map<String, Object> result = switch (type) {
            case "sale" -> saleResult(amountCents);
            case "refund" -> refundResult(amountCents);
            case "void" -> voidResult(resource);
            case "settlement" -> settlementResult();
            default -> {
                log("Unhandled transaction type \"" + type + "\", approving generically");
                yield saleResult(amountCents);
            }
        };

        Map<String, Object> eventResource = new LinkedHashMap<>();
        eventResource.put("status", "completed");
        eventResource.put("results", List.of(result));

        Map<String, Object> payload = Map.of("event", Map.of("flow_id", flowId, "resource", eventResource));
        send(conn, payload);
    }

    private Map<String, Object> saleResult(long amountCents) {
        boolean declined = amountCents == 200; // $2.00 -> declined
        String referenceNo = String.valueOf(System.currentTimeMillis() % 1_000_000);

        Map<String, Object> result = baseCardResult(declined, "sale", referenceNo, amountCents);

        if (!declined) {
            saleAmountsByReferenceNo.put(referenceNo, amountCents);
            batchSaleAmount.addAndGet(amountCents);
            batchSaleCount.incrementAndGet();
            log("Batch now: " + batchSummary());
        }

        return result;
    }

    private Map<String, Object> refundResult(long amountCents) {
        boolean declined = amountCents == 200; // $2.00 -> declined
        String referenceNo = String.valueOf(System.currentTimeMillis() % 1_000_000);

        Map<String, Object> result = baseCardResult(declined, "refund", referenceNo, amountCents);

        if (!declined) {
            batchRefundAmount.addAndGet(amountCents);
            batchRefundCount.incrementAndGet();
            log("Batch now: " + batchSummary());
        }

        return result;
    }

    private Map<String, Object> voidResult(JsonNode resource) {
        String referenceNo = resource.path("reference_no").asText("");
        Long originalAmount = referenceNo.isBlank() ? null : saleAmountsByReferenceNo.remove(referenceNo);
        boolean notFound = originalAmount == null;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", notFound ? "transaction_record_not_found" : "approved");
        result.put("type", "void");
        result.put("reference_no", referenceNo.isBlank() ? UUID.randomUUID().toString() : referenceNo);
        result.put("host_response_text", notFound ? "RECORD NOT FOUND" : "APPROVED");

        if (!notFound) {
            batchVoidAmount.addAndGet(originalAmount);
            batchVoidCount.incrementAndGet();
            batchSaleAmount.addAndGet(-originalAmount);
            batchSaleCount.decrementAndGet();
            log("Batch now: " + batchSummary());
        }

        return result;
    }

    private Map<String, Object> settlementResult() {
        Map<String, Object> terminalTotal = new LinkedHashMap<>();
        terminalTotal.put("batch_sale_amount", String.valueOf(batchSaleAmount.get()));
        terminalTotal.put("batch_sale_count", String.valueOf(batchSaleCount.get()));
        terminalTotal.put("batch_refund_amount", String.valueOf(batchRefundAmount.get()));
        terminalTotal.put("batch_refund_count", String.valueOf(batchRefundCount.get()));
        terminalTotal.put("batch_void_amount", String.valueOf(batchVoidAmount.get()));
        terminalTotal.put("batch_void_count", String.valueOf(batchVoidCount.get()));

        long totalAmount = batchSaleAmount.get() + batchRefundAmount.get();
        long totalCount = batchSaleCount.get() + batchRefundCount.get();
        terminalTotal.put("batch_total_amount", String.valueOf(totalAmount));
        terminalTotal.put("batch_total_count", String.valueOf(totalCount));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "approved");
        result.put("type", "settlement");
        result.put("terminal_id", "MOCK001");
        result.put("merchant_id", "MOCKMERCHANT");
        result.put("terminal_total", List.of(terminalTotal));

        log("Settlement closed batch: " + batchSummary());
        resetBatch();

        return result;
    }

    private Map<String, Object> baseCardResult(boolean declined, String type, String referenceNo, long amountCents) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", declined ? "decline_by_host_or_card" : "approved");
        result.put("type", type);
        result.put("reference_no", referenceNo);
        result.put("authorization_no", declined ? null : "OK" + (100000 + (int) (Math.random() * 900000)));
        result.put("host_response_text", declined ? "DECLINED" : "APPROVED");
        result.put("transaction_amount", String.valueOf(amountCents));
        result.put("total_amount", String.valueOf(amountCents));

        if (!declined) {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("account_no", "************4242");
            card.put("entry_mode", Map.of("code", "5", "text", "tap"));
            card.put("type", Map.of("code", "01", "text", "visa"));
            result.put("card", card);
        }

        return result;
    }

    private void resetBatch() {
        saleAmountsByReferenceNo.clear();
        batchSaleAmount.set(0);
        batchSaleCount.set(0);
        batchRefundAmount.set(0);
        batchRefundCount.set(0);
        batchVoidAmount.set(0);
        batchVoidCount.set(0);
    }

    private String batchSummary() {
        return "sales=" + batchSaleCount.get() + " ($" + batchSaleAmount.get() / 100.0 + "), "
               + "refunds=" + batchRefundCount.get() + " ($" + batchRefundAmount.get() / 100.0 + "), "
               + "voids=" + batchVoidCount.get() + " ($" + batchVoidAmount.get() / 100.0 + ")";
    }

    private void send(WebSocket conn, Object payload) {
        try {
            String json = MAPPER.writeValueAsString(payload);
            conn.send(json);
            log("Sent: " + json);
        } catch (Exception e) {
            log("Failed to send message: " + e.getMessage());
        }
    }

    private void log(String message) {
        System.out.println("[mock-terminal] " + message);
    }
}
