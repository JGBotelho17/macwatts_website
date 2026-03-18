package com.oficina.backend.repository;

import com.oficina.backend.model.QuoteSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteSubmissionRepository extends JpaRepository<QuoteSubmission, Long> {
}
