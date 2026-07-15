package com.staydesk.service;

import com.staydesk.model.dto.SupabaseAuthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class SupabaseAdminClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SupabaseAdminClient.class);

    private final RestClient restClient = RestClient.create();

    @Value("${supabase.url}")
    private String projectUrl;
    @Value("${supabase.service.role.key}")
    private String roleKey;

    public UUID createUser(String email, String pin, String authRole) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", pin);
        body.put("email_confirm", true);
        body.put("app_metadata", Map.of("role", authRole));

        SupabaseUserResponse response = restClient.post()
                                                  .uri(projectUrl + "/auth/v1/admin/users")
                                                  .header("Authorization", "Bearer " + roleKey)
                                                  .header("apiKey", roleKey)
                                                  .contentType(MediaType.APPLICATION_JSON)
                                                  .body(body)
                                                  .retrieve()
                                                  .body(SupabaseUserResponse.class);


        if (response == null || response.id() == null) {
            throw new IllegalStateException("Supabase user could not be created, no ID returned");
        }

        return UUID.fromString(response.id());
    }

    public void updateUserRole(UUID supabaseId, String authRole) {
        restClient.put()
                  .uri(projectUrl + "/auth/v1/admin/users/" + supabaseId)
                  .header("Authorization", "Bearer " + roleKey)
                  .header("apiKey", roleKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(Map.of("app_metadata", Map.of("role", authRole)))
                  .retrieve()
                  .toBodilessEntity();
    }

    public void resetPin(UUID supabaseId, String newPin) {
        restClient.put()
                  .uri(projectUrl + "/auth/v1/admin/users/" + supabaseId)
                  .header("Authorization", "Bearer " + roleKey)
                  .header("apiKey", roleKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .body(Map.of("password", newPin))
                  .retrieve()
                  .toBodilessEntity();
    }

    public SupabaseAuthResponse signIn(String email, String pin) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", pin);

        SupabaseAuthResponse response = restClient.post()
                                               .uri(projectUrl + "/auth/v1/token?grant_type=password")
                                               .header("Authorization", "Bearer " + roleKey)
                                               .header("apiKey", roleKey)
                                               .contentType(MediaType.APPLICATION_JSON)
                                               .body(body)
                                               .retrieve()
                                               .body(SupabaseAuthResponse.class);
        LOGGER.info("Supabase signIn response: {}", response);
        return response;
    }

    private record SupabaseUserResponse(String id) {
    }
}
