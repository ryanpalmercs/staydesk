package com.staydesk.payment.elavon;

import com.staydesk.payment.elavon.dto.CpiDevice;
import com.staydesk.payment.elavon.dto.CpiIntegratorDeviceData;
import com.staydesk.payment.elavon.dto.CpiMessageEnvelope;
import com.staydesk.payment.elavon.dto.CpiTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Service
public class ElavonCpiClient {

    private static final String MESSAGE_CONTENT_TYPE = "application/vnd.elavon.transaction-b.v1+json";

    private final RestClient restClient = RestClient.create();
    private final ElavonCpiAuthService authService;

    @Value("${elavon.cpi.base-url}")
    private String baseUrl;

    public ElavonCpiClient(ElavonCpiAuthService authService) {
        this.authService = authService;
    }

    public CpiDevice pairDevice(String pairingCode, String friendlyName, String location) {
        return restClient.post()
                         .uri(baseUrl + "/devices")
                         .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                         .contentType(MediaType.APPLICATION_JSON)
                         .body(new CpiIntegratorDeviceData(pairingCode, friendlyName, location))
                         .retrieve()
                         .body(CpiDevice.class);
    }

    public void unpairDevice(String deviceId) {
        restClient.delete()
                  .uri(baseUrl + "/devices/" + deviceId)
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                  .retrieve()
                  .toBodilessEntity();
    }

    public CpiTransaction sendDeviceMessage(String deviceId, CpiTransaction transaction) {
        CpiMessageEnvelope envelope = CpiMessageEnvelope.synchronous(UUID.randomUUID().toString(), transaction);

        CpiMessageEnvelope response = restClient.post()
                                                .uri(baseUrl + "/devices/" + deviceId + "/message")
                                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                                                .contentType(MediaType.parseMediaType(MESSAGE_CONTENT_TYPE))
                                                .body(envelope)
                                                .retrieve()
                                                .body(CpiMessageEnvelope.class);

        if (response == null) {
            throw new IllegalStateException("Elavon CPI device message returned no response");
        }

        return response.message();
    }

    public CpiTransaction sendGatewayMessage(CpiTransaction transaction) {
        CpiMessageEnvelope envelope = CpiMessageEnvelope.synchronous(UUID.randomUUID().toString(), transaction);

        CpiMessageEnvelope response = restClient.post()
                                                .uri(baseUrl + "/gateways/message")
                                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + authService.getAccessToken())
                                                .contentType(MediaType.parseMediaType(MESSAGE_CONTENT_TYPE))
                                                .body(envelope)
                                                .retrieve()
                                                .body(CpiMessageEnvelope.class);

        if (response == null) {
            throw new IllegalStateException("Elavon CPI gateway message returned no response");
        }

        return response.message();
    }
}