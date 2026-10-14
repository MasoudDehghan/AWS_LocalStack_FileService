package com.ttnet.s3sample.controller;

import com.ttnet.s3sample.model.FileMetadata;
import com.ttnet.s3sample.repository.FileMetadataRepository;
import com.ttnet.s3sample.service.FileQueueService;
import com.ttnet.s3sample.service.FileCatalogService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileCatalogService catalogService;
    private final FileMetadataRepository repository;

    public FileController(FileCatalogService s3Service,
                          FileMetadataRepository repository)
    {
        this.catalogService = s3Service;
        this.repository = repository;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file) {
        try {
            FileMetadata metadata =  catalogService.uploadFile(file);
            return ResponseEntity.ok("File uploaded successfully to LocalStack S3 with key: " + metadata.getS3Key());
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("File upload failed: " + e.getMessage());
        }
    }

    @GetMapping
    public List<FileMetadata> listFiles() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getMetadata(
            @PathVariable String id) {

        return repository.findById(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadFile(@PathVariable String id) {
        Optional<FileMetadata> fileOptional = repository.findById(id);
        if (fileOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        FileMetadata metadata = fileOptional.get();
        byte[] fileContents = catalogService.downloadFile(metadata.getS3Key());
        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        if (metadata.getContentType() != null && !metadata.getContentType().isBlank()) {
            try {
                contentType = MediaType.parseMediaType(metadata.getContentType());
            } catch (IllegalArgumentException ignored) {
                // Keep the binary fallback when stored content type is malformed.
            }
        }
        String fileName = metadata.getFileName() == null || metadata.getFileName().isBlank()
                ? "download"
                : metadata.getFileName();
        ContentDisposition contentDisposition = ContentDisposition.builder("attachment")
                .filename(fileName, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(fileContents.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .body(fileContents);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMetadata(
            @PathVariable String id) {
        Optional<FileMetadata> fileOptional = repository.findById(id);
        if(fileOptional.isEmpty())
            return ResponseEntity.notFound().build();

        FileMetadata metadata = fileOptional.get();
        String s3Key = metadata.getS3Key();
        catalogService.deleteFile(s3Key);

        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
