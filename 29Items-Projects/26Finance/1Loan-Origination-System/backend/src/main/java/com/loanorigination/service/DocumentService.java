package com.loanorigination.service;

import com.loanorigination.dto.DocumentDto;
import com.loanorigination.kafka.LoanEventProducer;
import com.loanorigination.model.LoanDocument;
import com.loanorigination.repository.LoanDocumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final LoanDocumentRepository documentRepository;
    private final OcrService ocrService;
    private final LoanEventProducer loanEventProducer;
    private final DocumentSearchService documentSearchService;

    @Value("${application.document.storage-root:./document-storage}")
    private String storageRoot;

    @Value("${application.document.max-file-size-mb:10}")
    private int maxFileSizeMb;

    @Value("${application.document.allowed-types:application/pdf,image/png,image/jpeg}")
    private String allowedTypesStr;

    private Set<String> allowedMimeTypes;
    private Path rootPath;

    @PostConstruct
    public void init() throws IOException {
        this.rootPath = Paths.get(storageRoot).toAbsolutePath().normalize();
        Files.createDirectories(rootPath);
        this.allowedMimeTypes = Set.of(allowedTypesStr.split(","));
        log.info("Document storage initialized at: {}", rootPath);
    }

    @Transactional
    public DocumentDto uploadDocument(Long applicationId, String documentType,
                                      MultipartFile file) {
        validateFile(file);

        String relativePath = applicationId + "/"
                + UUID.randomUUID().toString().substring(0, 8)
                + "_" + sanitizeFilename(file.getOriginalFilename());

        Path targetPath = rootPath.resolve(relativePath).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new BusinessRuleException("Invalid file path");
        }

        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessRuleException("Failed to store file: " + e.getMessage());
        }

        String ocrText = "";
        try {
            ocrText = ocrService.extractText(file.getBytes(), file.getContentType());
        } catch (Exception e) {
            log.warn("OCR extraction failed for {}: {}", file.getOriginalFilename(), e.getMessage());
        }

        LoanDocument doc = new LoanDocument();
        doc.setApplicationId(applicationId);
        doc.setDocumentType(documentType);
        doc.setDocumentName(file.getOriginalFilename());
        doc.setS3Key(relativePath);
        doc.setS3Bucket(storageRoot);
        doc.setFileSize(file.getSize());
        doc.setMimeType(file.getContentType());
        doc.setOcrText(ocrText);
        if (!ocrText.isEmpty()) {
            doc.markProcessed();
        }

        LoanDocument saved = documentRepository.save(doc);
        log.info("Uploaded document id={} for applicationId={}", saved.getId(), applicationId);

        loanEventProducer.publishDocumentUploaded(saved);

        try {
            documentSearchService.indexDocument(saved);
        } catch (Exception e) {
            log.warn("Failed to index document id={} in Elasticsearch: {}", saved.getId(), e.getMessage());
        }

        return mapToDto(saved);
    }

    public Resource downloadDocument(Long documentId) {
        LoanDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        Path filePath = rootPath.resolve(doc.getS3Key()).normalize();
        if (!Files.exists(filePath)) {
            throw new ResourceNotFoundException("File not found on disk: " + doc.getS3Key());
        }

        try {
            return new UrlResource(filePath.toUri());
        } catch (MalformedURLException e) {
            throw new ResourceNotFoundException("Could not read file: " + doc.getS3Key());
        }
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        LoanDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        Path filePath = rootPath.resolve(doc.getS3Key()).normalize();
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            log.warn("Failed to delete file from disk: {}", doc.getS3Key(), e);
        }

        try {
            documentSearchService.removeFromIndex(doc.getId());
        } catch (Exception e) {
            log.warn("Failed to remove document id={} from Elasticsearch index: {}", doc.getId(), e.getMessage());
        }

        documentRepository.delete(doc);
        log.info("Deleted document id={} file={}", documentId, doc.getS3Key());
    }

    public List<DocumentDto> listByApplicationId(Long applicationId) {
        return documentRepository.findByApplicationId(applicationId)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    public DocumentDto getDocument(Long documentId) {
        LoanDocument doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));
        return mapToDto(doc);
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessRuleException("File is empty");
        }
        long maxBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new BusinessRuleException("File size exceeds maximum of " + maxFileSizeMb + "MB");
        }
        if (!allowedMimeTypes.contains(file.getContentType())) {
            throw new BusinessRuleException(
                    "File type not allowed: " + file.getContentType()
                            + ". Allowed: " + allowedMimeTypes);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unnamed";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private DocumentDto mapToDto(LoanDocument doc) {
        return DocumentDto.builder()
                .id(doc.getId())
                .applicationId(doc.getApplicationId())
                .documentType(doc.getDocumentType())
                .documentName(doc.getDocumentName())
                .fileSize(doc.getFileSize())
                .mimeType(doc.getMimeType())
                .uploadedBy(doc.getUploadedBy())
                .uploadedAt(doc.getUploadedAt())
                .processed(doc.isProcessed())
                .downloadUrl("/api/documents/" + doc.getId() + "/download")
                .build();
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) { super(message); }
    }

    public static class BusinessRuleException extends RuntimeException {
        public BusinessRuleException(String message) { super(message); }
    }
}
