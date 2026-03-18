package com.oficina.backend.controller;

import com.oficina.backend.model.QuoteEmailRequest;
import com.oficina.backend.model.QuoteSubmission;
import com.oficina.backend.service.OdooCrmService;
import com.oficina.backend.service.QuoteEmailService;
import com.oficina.backend.service.QuoteSubmissionService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quote")
public class QuoteController {

    private final QuoteEmailService quoteEmailService;
    private final OdooCrmService odooCrmService;
    private final QuoteSubmissionService quoteSubmissionService;

    public QuoteController(
        QuoteEmailService quoteEmailService,
        OdooCrmService odooCrmService,
        QuoteSubmissionService quoteSubmissionService
    ) {
        this.quoteEmailService = quoteEmailService;
        this.odooCrmService = odooCrmService;
        this.quoteSubmissionService = quoteSubmissionService;
    }

    @PostMapping("/email")
    public ResponseEntity<?> sendQuoteEmail(@RequestBody QuoteEmailRequest request) {
        if (isBlank(request.getClientName()) || isBlank(request.getClientEmail())) {
            return ResponseEntity.badRequest().body("Campos obrigatórios: clientName, clientEmail.");
        }
        if (isBlank(request.getClientNif())) {
            return ResponseEntity.badRequest().body("Campo obrigatório: clientNif.");
        }
        if (!request.getClientNif().trim().matches("\\d{9}")) {
            return ResponseEntity.badRequest().body("Campo inválido: clientNif deve ter 9 dígitos.");
        }

        QuoteSubmission submission = quoteSubmissionService.createProcessing(request);

        try {
            boolean sent = quoteEmailService.sendQuotePdf(request);
            OdooCrmService.SyncResult odooResult = odooCrmService.syncLeadAndContact(request);
            String statusMessage = sent
                ? "Pedido processado: email enviado e integração Odoo executada."
                : "Pedido processado: SMTP não configurado; integração Odoo executada.";
            quoteSubmissionService.markSuccess(submission.getId(), sent, odooResult, statusMessage);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("submissionId", submission.getId());
            response.put("emailSent", sent);
            response.put("odooConfigured", odooResult.configured());
            response.put("odooSuccess", odooResult.success());
            response.put("odooMessage", odooResult.message());
            response.put("message", statusMessage);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            quoteSubmissionService.markFailed(submission.getId(), ex.getMessage());
            return ResponseEntity.badRequest().body(ex.getMessage());
        } catch (IllegalStateException ex) {
            quoteSubmissionService.markFailed(submission.getId(), ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
        } catch (Exception ex) {
            quoteSubmissionService.markFailed(submission.getId(), rootMessage(ex));
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Falha ao processar pedido: " + rootMessage(ex));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() != null ? current.getMessage() : throwable.getMessage();
    }
}
