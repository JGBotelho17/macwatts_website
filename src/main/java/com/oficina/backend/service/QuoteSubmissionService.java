package com.oficina.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oficina.backend.model.QuoteEmailRequest;
import com.oficina.backend.model.QuoteSubmission;
import com.oficina.backend.model.QuoteSubmissionStatus;
import com.oficina.backend.repository.QuoteSubmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuoteSubmissionService {

    private final QuoteSubmissionRepository repository;
    private final ObjectMapper objectMapper;

    public QuoteSubmissionService(QuoteSubmissionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public QuoteSubmission createProcessing(QuoteEmailRequest request) {
        QuoteSubmission submission = new QuoteSubmission();
        submission.setClientName(safe(request.getClientName()));
        submission.setClientEmail(safe(request.getClientEmail()));
        submission.setClientPhone(safe(request.getClientPhone()));
        submission.setClientNif(safe(request.getClientNif()));
        submission.setStatus(QuoteSubmissionStatus.PROCESSING);
        submission.setStatusMessage("Pedido recebido e em processamento.");
        submission.setPayloadJson(serializePayload(request));
        return repository.save(submission);
    }

    @Transactional
    public QuoteSubmission markSuccess(
        Long id,
        boolean emailSent,
        OdooCrmService.SyncResult odooResult,
        String message
    ) {
        QuoteSubmission submission = repository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Submissão não encontrada: " + id));

        submission.setStatus(QuoteSubmissionStatus.SUCCESS);
        submission.setEmailSent(emailSent);
        submission.setOdooConfigured(odooResult.configured());
        submission.setOdooSuccess(odooResult.success());
        submission.setStatusMessage(safe(message));
        return repository.save(submission);
    }

    @Transactional
    public QuoteSubmission markFailed(Long id, String message) {
        QuoteSubmission submission = repository.findById(id)
            .orElseThrow(() -> new IllegalStateException("Submissão não encontrada: " + id));

        submission.setStatus(QuoteSubmissionStatus.FAILED);
        submission.setStatusMessage(safe(message));
        return repository.save(submission);
    }

    private String serializePayload(QuoteEmailRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            return "{\"error\":\"Falha ao serializar payload\"}";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
