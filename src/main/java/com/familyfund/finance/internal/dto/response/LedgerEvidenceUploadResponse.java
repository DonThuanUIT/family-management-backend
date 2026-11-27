package com.familyfund.finance.internal.dto.response;

/**
 * Response returned after uploading a supporting evidence document (PDF/JPG/PNG)
 * for a ledger adjustment proposal (US-15).
 */
public record LedgerEvidenceUploadResponse(
        String evidenceUrl,
        String fileName,
        String originalFilename,
        String mimeType,
        long sizeBytes,
        String sha256Checksum
) {}
