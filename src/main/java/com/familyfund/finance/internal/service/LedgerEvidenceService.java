package com.familyfund.finance.internal.service;

import com.familyfund.finance.internal.dto.response.LedgerEvidenceUploadResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Service for securely storing and serving private evidence documents attached to
 * common-fund ledger adjustment proposals (US-15).
 */
public interface LedgerEvidenceService {

    /**
     * Binary stream wrapper for authenticated private evidence downloads.
     */
    record EvidenceDownload(
            InputStream stream,
            String fileName,
            String mimeType
    ) {}

    /**
     * Validates, sanitizes, and stores a PDF/JPG/PNG evidence file in private clan storage.
     *
     * @param file                  uploaded multipart file (max 10 MiB)
     * @param originalTransactionId optional ledger transaction ID for EventTreasurer/HeadOfOrganizer scope checks
     * @return stored file metadata including the internal proxy URL
     */
    LedgerEvidenceUploadResponse uploadEvidence(MultipartFile file, String originalTransactionId);

    /**
     * Streams a previously stored evidence file after verifying tenant isolation.
     *
     * @param fileName stored UUID-based filename ({@code <uuid>.<ext>})
     * @return opened binary stream and MIME type
     */
    EvidenceDownload downloadEvidence(String fileName);
}
