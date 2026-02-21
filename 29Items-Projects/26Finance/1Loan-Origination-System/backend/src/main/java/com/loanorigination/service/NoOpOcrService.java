package com.loanorigination.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Stub OCR implementation that always returns an empty string.
 * Replace with a real implementation (Apache Tika, Tesseract, AWS Textract) when ready.
 */
@Service
@Slf4j
public class NoOpOcrService implements OcrService {

    @Override
    public String extractText(byte[] fileContent, String mimeType) {
        log.debug("NoOpOcrService.extractText called for mimeType={}; returning empty string", mimeType);
        return "";
    }
}
