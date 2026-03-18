package com.oficina.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.backend.model.QuoteEmailRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OdooCrmService {
    private static final Logger log = LoggerFactory.getLogger(OdooCrmService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final boolean enabled;
    private final String baseUrl;
    private final String database;
    private final String username;
    private final String password;
    private final String crmRecordType;
    private final Integer crmTeamId;
    private final Integer crmUserId;

    public OdooCrmService(
        @Value("${app.odoo.enabled:false}") boolean enabled,
        @Value("${app.odoo.base-url:}") String baseUrl,
        @Value("${app.odoo.db:}") String database,
        @Value("${app.odoo.username:}") String username,
        @Value("${app.odoo.password:}") String password,
        @Value("${app.odoo.crm.record-type:opportunity}") String crmRecordType,
        @Value("${app.odoo.crm.team-id:0}") Integer crmTeamId,
        @Value("${app.odoo.crm.user-id:0}") Integer crmUserId
    ) {
        this.enabled = enabled;
        this.baseUrl = trim(baseUrl);
        this.database = trim(database);
        this.username = trim(username);
        this.password = trim(password);
        this.crmRecordType = trim(crmRecordType);
        this.crmTeamId = crmTeamId;
        this.crmUserId = crmUserId;
    }

    public SyncResult syncLeadAndContact(QuoteEmailRequest request) throws Exception {
        if (!enabled) {
            return SyncResult.skipped("Integração Odoo desativada.");
        }

        validateConfig();

        Integer uid = authenticate();
        Integer partnerId = findOrCreatePartner(uid, request);
        Integer leadId = createLead(uid, partnerId, request);

        String message = "Odoo atualizado com sucesso (partner_id=" + partnerId + ", lead_id=" + leadId + ").";
        return SyncResult.success(message);
    }

    private void validateConfig() {
        if (isBlank(baseUrl) || isBlank(database) || isBlank(username) || isBlank(password)) {
            throw new IllegalStateException(
                "Configuração Odoo incompleta. Define APP_ODOO_BASE_URL, APP_ODOO_DB, APP_ODOO_USERNAME e APP_ODOO_PASSWORD."
            );
        }
    }

    private Integer authenticate() throws Exception {
        Object uid = call(
            "common",
            "authenticate",
            List.of(database, username, password, Map.of())
        );

        if (!(uid instanceof Number)) {
            throw new IllegalStateException("Falha na autenticação Odoo: UID inválido.");
        }

        return ((Number) uid).intValue();
    }

    private Integer findOrCreatePartner(Integer uid, QuoteEmailRequest request) throws Exception {
        List<Object> domain = buildPartnerSearchDomain(request);
        Object found = executeKw(uid, "res.partner", "search", List.of(domain), Map.of("limit", 1));
        Integer partnerId = extractFirstId(found);
        if (partnerId != null) {
            return partnerId;
        }

        Map<String, Object> vals = new LinkedHashMap<>();
        vals.put("name", safe(request.getClientName(), "Cliente Website"));
        putIfNotBlank(vals, "email", request.getClientEmail());
        putIfNotBlank(vals, "phone", request.getClientPhone());
        putIfNotBlank(vals, "vat", normalizeNif(request.getClientNif()));
        putIfNotBlank(vals, "comment", buildContactComment(request));

        Object created = executeKw(uid, "res.partner", "create", List.of(vals), Map.of());
        if (!(created instanceof Number)) {
            throw new IllegalStateException("Falha ao criar contacto no Odoo.");
        }
        return ((Number) created).intValue();
    }

    private Integer createLead(Integer uid, Integer partnerId, QuoteEmailRequest request) throws Exception {
        Map<String, Object> vals = new LinkedHashMap<>();
        vals.put("name", "Pedido Website - " + safe(request.getClientName(), "Sem nome"));
        vals.put("type", resolveCrmRecordType());
        vals.put("partner_id", partnerId);
        putIfNotBlank(vals, "contact_name", request.getClientName());
        putIfNotBlank(vals, "email_from", request.getClientEmail());
        putIfNotBlank(vals, "phone", request.getClientPhone());
        vals.put("description", buildLeadDescription(request));
        if (crmTeamId != null && crmTeamId > 0) {
            vals.put("team_id", crmTeamId);
        }
        if (crmUserId != null && crmUserId > 0) {
            vals.put("user_id", crmUserId);
        }

        Object created = executeKw(uid, "crm.lead", "create", List.of(vals), Map.of());
        if (!(created instanceof Number)) {
            throw new IllegalStateException("Falha ao criar lead no Odoo.");
        }
        return ((Number) created).intValue();
    }

    private List<Object> buildPartnerSearchDomain(QuoteEmailRequest request) {
        String email = trim(request.getClientEmail());
        String nif = normalizeNif(request.getClientNif());
        if (!isBlank(email) && !isBlank(nif)) {
            return List.of(
                "|",
                List.of("email", "=", email),
                List.of("vat", "=", nif)
            );
        }
        if (!isBlank(email)) {
            return List.of(List.of("email", "=", email));
        }
        if (!isBlank(nif)) {
            return List.of(List.of("vat", "=", nif));
        }
        return List.of();
    }

    private Integer extractFirstId(Object value) {
        if (!(value instanceof List<?> ids) || ids.isEmpty()) {
            return null;
        }
        Object first = ids.get(0);
        if (!(first instanceof Number)) {
            return null;
        }
        return ((Number) first).intValue();
    }

    private Object executeKw(Integer uid, String model, String method, List<Object> args, Map<String, Object> kwargs)
        throws Exception {
        return call(
            "object",
            "execute_kw",
            List.of(database, uid, password, model, method, args, kwargs)
        );
    }

    private Object call(String service, String method, List<Object> args) throws Exception {
        String rpcUrl = baseUrl.endsWith("/") ? baseUrl + "jsonrpc" : baseUrl + "/jsonrpc";
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jsonrpc", "2.0");
        payload.put("method", "call");
        payload.put("params", Map.of("service", service, "method", method, "args", args));
        payload.put("id", System.currentTimeMillis());

        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(rpcUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("Erro HTTP do Odoo (" + response.statusCode() + ").");
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode errorNode = root.path("error");
        if (!errorNode.isMissingNode() && !errorNode.isNull()) {
            String message = errorNode.path("message").asText("Erro na chamada ao Odoo.");
            String dataMessage = errorNode.path("data").path("message").asText("");
            String finalMessage = isBlank(dataMessage) ? message : message + " - " + dataMessage;
            throw new IllegalStateException(finalMessage);
        }

        JsonNode resultNode = root.path("result");
        if (resultNode.isMissingNode()) {
            throw new IllegalStateException("Resposta inválida do Odoo (sem result).");
        }

        return objectMapper.convertValue(resultNode, Object.class);
    }

    private String buildContactComment(QuoteEmailRequest request) {
        List<String> parts = new ArrayList<>();
        if (!isBlank(request.getAddressSummary())) {
            parts.add("Morada: " + request.getAddressSummary().trim());
        }
        if (request.getLatitude() != null && request.getLongitude() != null) {
            parts.add("Coordenadas: lat " + formatCoord(request.getLatitude()) + ", lon " + formatCoord(request.getLongitude()));
        }
        return String.join("\n", parts);
    }

    private String buildLeadDescription(QuoteEmailRequest request) {
        List<String> parts = new ArrayList<>();
        if (!isBlank(request.getAddressSummary())) {
            parts.add("Morada: " + request.getAddressSummary().trim());
        }
        if (request.getLatitude() != null && request.getLongitude() != null) {
            parts.add("Coordenadas: lat " + formatCoord(request.getLatitude()) + ", lon " + formatCoord(request.getLongitude()));
        }
        if (!isBlank(request.getQuestionnaireSummary())) {
            parts.add("Questionário:");
            parts.add(request.getQuestionnaireSummary().trim());
        }
        return String.join("\n", parts);
    }

    private String formatCoord(Double value) {
        return String.format(Locale.ROOT, "%.6f", value);
    }

    private String resolveCrmRecordType() {
        String normalized = crmRecordType.toLowerCase(Locale.ROOT);
        return "lead".equals(normalized) ? "lead" : "opportunity";
    }

    private void putIfNotBlank(Map<String, Object> vals, String key, String value) {
        if (!isBlank(value)) {
            vals.put(key, value.trim());
        }
    }

    private String normalizeNif(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        return digits.isBlank() ? null : digits;
    }

    private String safe(String value, String fallback) {
        if (isBlank(value)) {
            return fallback;
        }
        return value.trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record SyncResult(boolean configured, boolean success, String message) {
        public static SyncResult skipped(String message) {
            return new SyncResult(false, false, message);
        }

        public static SyncResult success(String message) {
            return new SyncResult(true, true, message);
        }
    }
}
