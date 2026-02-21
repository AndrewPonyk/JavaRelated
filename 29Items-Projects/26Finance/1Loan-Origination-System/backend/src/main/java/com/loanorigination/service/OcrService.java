package com.loanorigination.service;

/**
 * Contract for extracting text from document file content via OCR.
 * Implementations may delegate to Tesseract, AWS Textract, Apache Tika, etc.
 */
public interface OcrService {

    /**
     * Extracts text content from the given file bytes.
     *
     * @param fileContent the raw file bytes
     * @param mimeType    the MIME type of the file (e.g. "application/pdf", "image/png")
     * @return extracted text, or empty string if extraction is unavailable or fails
     */
    String extractText(byte[] fileContent, String mimeType);
}
