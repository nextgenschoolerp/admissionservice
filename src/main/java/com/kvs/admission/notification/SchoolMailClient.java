package com.kvs.admission.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.*;

/**
 * Sends the school's admissions email (offer notifications, entrance reminders) through Settings → Integrations: authservice sends it from the Admissions
 * mailbox when it is set up and switched on, else from the school's main email account, else not at all. The old server
 * SMTP settings (spring.mail, kvs.notification.email) are no longer used for email.
 * Called with the user's own token; without one (a background job) with the internal service key.
 */
@Component
@Slf4j
public class SchoolMailClient {

    /** status: SENT, FAILED or SKIPPED (not set up / no valid address); detail says why when not sent, and which mailbox when sent */
    public record Outcome(String status, String detail) { }

    /** One file sent with the email */
    public record Attachment(String filename, String contentType, byte[] content) { }

    private static final String PURPOSE = "ADMISSIONS";

    private final RestClient http;
    private final ObjectMapper objectMapper;
    private final String authUrl;
    private final String internalKey;

    public SchoolMailClient(ObjectMapper objectMapper,
                            @Value("${kvs.integrations.auth-url:http://localhost:8082/authservice}") String authUrl,
                            @Value("${kvs.internal.key:schoolerp-dev-internal-key}") String internalKey) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(60_000);
        this.http = RestClient.builder().requestFactory(f).build();
        this.objectMapper = objectMapper;
        this.authUrl = authUrl;
        this.internalKey = internalKey;
    }

    public Outcome send(String tenantId, String to, String subject, String body, List<Attachment> files, String reference) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("ref", "1");
        message.put("recipient", to);
        message.put("subject", subject);
        message.put("body", body);
        message.put("attachments", files == null ? List.of() : files.stream().map(a -> Map.of("filename", a.filename(),
                "contentType", a.contentType(), "contentBase64", Base64.getEncoder().encodeToString(a.content()))).toList());
        Map<String, Object> request = Map.of("channel", "EMAIL", "purpose", PURPOSE, "reference", reference, "messages", List.of(message));
        try {
            RestClient.RequestBodySpec call = http.post().uri(authUrl + "/api/v1/tenants/{t}/integrations/deliver", tenantId)
                    .contentType(MediaType.APPLICATION_JSON);
            String bearer = bearer();
            if (bearer != null) call = call.header(HttpHeaders.AUTHORIZATION, bearer);
            else call = call.header("X-Internal-Key", internalKey).header("X-Internal-Service", "admissionservice");
            JsonNode reply = objectMapper.readTree(call.body(request).retrieve().body(String.class)).path("data");
            JsonNode r = reply.path("results").path(0);
            String status = r.path("status").asText("FAILED");
            String detail = status.equals("SENT") ? "Sent from " + (reply.path("channel").asText().equals("EMAIL_ADMISSIONS") ? "the Admissions mailbox" : "the school email account")
                    : r.path("error").asText("No reason given");
            return new Outcome(status, detail);
        } catch (RestClientResponseException e) {
            log.warn("Admissions email {} refused by authservice: HTTP {}", reference, e.getStatusCode().value());
            return new Outcome("FAILED", e.getStatusCode().value() == 403 ? "You may not send admissions email" : "The messaging service refused the email (HTTP " + e.getStatusCode().value() + ")");
        } catch (Exception e) {
            log.warn("Admissions email {} not sent: {}", reference, e.getMessage());
            return new Outcome("FAILED", "The messaging service could not be reached");
        }
    }

    private static String bearer() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            String h = a.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            return h != null && h.startsWith("Bearer ") ? h : null;
        }
        return null;
    }
}
