package com.ttnet.s3sample.service;

import com.ttnet.s3sample.model.FileMetadata;
import com.ttnet.s3sample.repository.FileMetadataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import org.mockito.ArgumentCaptor;

class FileCatalogServiceTest {

    @Test
    void uploadFileStoresObjectMetadataAndEnqueuesItsLocation() throws Exception {
        S3Client s3Client = mock(S3Client.class);
        FileQueueService queueService = mock(FileQueueService.class);
        FileMetadataRepository repository = mock(FileMetadataRepository.class);
        FileCatalogService service = new FileCatalogService(
                s3Client, queueService, repository, "document-hub");
        byte[] contents = "sample document".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "annual report.pdf", "application/pdf", contents);

        FileMetadata result = service.uploadFile(file);

        assertNotNull(result.getFileId());
        assertTrue(isUuid(result.getFileId()));
        assertEquals("annual report.pdf", result.getFileName());
        assertEquals("document-hub", result.getS3Bucket());
        assertTrue(result.getS3Key().startsWith("uploads/" + result.getFileId() + "/"));
        assertTrue(result.getS3Key().endsWith("/annual_report.pdf"));
        assertEquals("application/pdf", result.getContentType());
        assertEquals((long) contents.length, result.getSize());
        assertEquals("UPLOADED", result.getStatus());
        assertNotNull(result.getUploadedAt());

        ArgumentCaptor<PutObjectRequest> requestCaptor =
                ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));
        assertEquals("document-hub", requestCaptor.getValue().bucket());
        assertEquals(result.getS3Key(), requestCaptor.getValue().key());
        assertEquals("application/pdf", requestCaptor.getValue().contentType());

        ArgumentCaptor<FileMetadata> metadataCaptor =
                ArgumentCaptor.forClass(FileMetadata.class);
        verify(repository).save(metadataCaptor.capture());
        assertEquals(result.getFileId(), metadataCaptor.getValue().getFileId());
        verify(queueService).enqueue(eq("document-hub"), eq(result.getS3Key()));
    }

    private boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
