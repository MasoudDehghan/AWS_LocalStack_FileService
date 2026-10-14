package com.ttnet.s3sample.service;

import com.ttnet.s3sample.model.FileMetadata;
import com.ttnet.s3sample.repository.FileMetadataRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
public class FileCatalogService {

    private final S3Client s3Client;
    private final FileQueueService fileQueueService;
    private final FileMetadataRepository repository;

    private final String bucketName;

    public FileCatalogService(S3Client s3Client, FileQueueService fileQueueService,
                              FileMetadataRepository repository,
                              @Value("${app.aws.s3.bucket-name}") String bucketName) {
        this.s3Client = s3Client;
        this.repository = repository;
        this.fileQueueService = fileQueueService;
        this.bucketName = bucketName;
    }

    public FileMetadata uploadFile(MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID().toString();
        String originalFileName = file.getOriginalFilename();
        String safeFileName = originalFileName == null || originalFileName.isBlank()
                ? "file"
                : originalFileName.replace('\\', '/');
        safeFileName = safeFileName.substring(safeFileName.lastIndexOf('/') + 1)
                .replaceAll("[^\\p{L}\\p{N}._-]", "_");
        if (safeFileName.isBlank()) {
            safeFileName = "file";
        }
        String keyName = "uploads/" + fileId + "/" + safeFileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest,
                RequestBody.fromBytes(file.getBytes()));

        RequestBody.fromInputStream(
                file.getInputStream(),
                file.getSize()
        );

        FileMetadata metadata = new FileMetadata();
        metadata.setFileId(fileId);
        metadata.setFileName(file.getOriginalFilename());
        metadata.setS3Bucket(bucketName);
        metadata.setS3Key(keyName);
        metadata.setContentType(file.getContentType());
        metadata.setSize(file.getSize());
        metadata.setStatus("UPLOADED");
        metadata.setUploadedAt(Instant.now().toString());

        repository.save(metadata);

        fileQueueService.enqueue(bucketName, keyName);

        return metadata;
    }

    public void deleteFile(String key){
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }

    public byte[] downloadFile(String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
    }
}
