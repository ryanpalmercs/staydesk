package com.staydesk.payment.ingenico;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staydesk.bridge.TerminalBridgeSessionRegistry;
import com.staydesk.exception.TerminalBridgeException;
import com.staydesk.model.TerminalTransaction;
import com.staydesk.repository.TerminalTransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class IngenicoBridgeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngenicoBridgeClient.class);
    private static final int TRANSACTION_TIMEOUT_SECONDS = 45;

    private final TerminalBridgeSessionRegistry sessionRegistry;
    private final TerminalTransactionRepository transactionRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, CompletableFuture<JsonNode>> pendingFlows = new ConcurrentHashMap<>();

    public IngenicoBridgeClient(TerminalBridgeSessionRegistry sessionRegistry,
                                TerminalTransactionRepository transactionRepository, ObjectMapper objectMapper) {
        this.sessionRegistry = sessionRegistry;
        this.transactionRepository = transactionRepository;
        this.objectMapper = objectMapper;
    }

    public TsiTransactionResult sendTransaction(TerminalTransaction.Operation operation, Integer folioPaymentId,
                                                String type, BigDecimal amount, String referenceNumber) {
        String flowId = UUID.randomUUID().toString();

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("type", type);

        if (amount != null) {
            resource.put("amount", IngenicoAmountFormat.toCents(amount));
        }

        if (referenceNumber != null) {
            resource.put("reference_no", referenceNumber);
        }

        String requestJson;

        try {
            requestJson = objectMapper.writeValueAsString(Map.of("request", Map.of("flow_id", flowId, "resource", resource)));
        } catch (JsonProcessingException e) {
            throw new TerminalBridgeException("Failed to serialize TSI request", e);
        }

        TerminalTransaction saved = transactionRepository.save(new TerminalTransaction(0, flowId, folioPaymentId, operation,
                amount, TerminalTransaction.Status.PENDING, requestJson, null, LocalDateTime.now(), null));

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingFlows.put(flowId, future);

        try {
            sessionRegistry.send(requestJson);
        } catch (TerminalBridgeException e) {
            pendingFlows.remove(flowId);
            markComplete(saved, TerminalTransaction.Status.FAILED, e.getMessage());
        }

        JsonNode eventResource;

        try {
            eventResource = future.get(TRANSACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new TerminalBridgeException("Terminal did not respond within " + TRANSACTION_TIMEOUT_SECONDS
                                              + "s for flow " + flowId  + " - outcome unknown, will be reconciled");
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            throw new TerminalBridgeException("Interrupted waiting for terminal response", e);
        } finally {
            pendingFlows.remove(flowId);
        }

        TsiEventResource event;

        try {
            event = objectMapper.treeToValue(eventResource, TsiEventResource.class);
        } catch (JsonProcessingException e) {
            markComplete(saved, TerminalTransaction.Status.FAILED, "Could not parse terminal response");
            throw new TerminalBridgeException("Could not parse terminal response for flow " + flowId, e);
        }

        List<TsiTransactionResult> results = event.results();
        TsiTransactionResult result = (results == null || results.isEmpty()) ? null : results.getFirst();

        if (result == null) {
            markComplete(saved, TerminalTransaction.Status.FAILED, "Terminal event carried no results");
            throw new TerminalBridgeException("Terminal event for flow " + flowId + " carried no results");
        }

        boolean approved = "approved".equals(result.status()) || "completed".equals(result.status());
        markComplete(saved, approved ? TerminalTransaction.Status.COMPLETED : TerminalTransaction.Status.FAILED,
                eventResource.toString());
        sendEventAck(flowId);

        return result;
    }

    public void completeFlow(String flowId, JsonNode eventResource) {
        CompletableFuture<JsonNode> future = pendingFlows.get(flowId);

        if (future == null) {
            LOGGER.warn("Received terminal event for unknown or already-resolved flow_id {}", flowId);
            return;
        }

        future.complete(eventResource);
    }

    private void sendEventAck(String flowId) {
        try {
            String ack = objectMapper.writeValueAsString(
                    Map.of("event_ack", Map.of("flow_id", flowId, "resource", Map.of("status", "ok"))));
            sessionRegistry.send(ack);
        } catch (Exception e) {
            LOGGER.warn("Failed to send event_ack for flow {}", flowId, e);
        }
    }

    private void markComplete(TerminalTransaction row, TerminalTransaction.Status status, String responsePayload) {
        transactionRepository.save(new TerminalTransaction(row.id(), row.flowId(), row.folioPaymentId(),
                row.operation(), row.amount(), status, row.requestPayload(), responsePayload, row.createdAt(), null));
    }
}
