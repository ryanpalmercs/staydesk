package com.staydesk.payment.elavon.dto;

public record CpiMessageEnvelope(String responseChannelType, CpiResponseChannel responseChannel, String messageId,
                                 String messageType, CpiTransaction message) {

    private static final String CONTENT_TYPE = "application/vnd.elavon.transaction-b.v1+json";

    public static CpiMessageEnvelope synchronous(String messageId, CpiTransaction transaction) {
        return new CpiMessageEnvelope("SYNCHRONOUS", new CpiResponseChannel("DEVICEMESSAGE"),
                messageId, CONTENT_TYPE, transaction);
    }
}