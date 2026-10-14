package com.ttnet.s3sample.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean

public class FileMetadata {
    private String fileId;
    private String fileName;
    private String s3Bucket;
    private String s3Key;
    private String contentType;
    private Long size;
    private String status;
    private String uploadedAt;

    @DynamoDbPartitionKey
    public String getFileId() {
        return fileId;
    }

    public void setFileId(String fileId) {
        this.fileId = fileId;
    }

    public String getFileName() { return fileName; }
    public void setFileName(String v) { fileName = v; }

    public String getS3Bucket() { return s3Bucket; }
    public void setS3Bucket(String v) { s3Bucket = v; }

    public String getS3Key() { return s3Key; }
    public void setS3Key(String v) { s3Key = v; }

    public String getContentType() { return contentType; }
    public void setContentType(String v) { contentType = v; }

    public Long getSize() { return size; }
    public void setSize(Long v) { size = v; }

    public String getStatus() { return status; }
    public void setStatus(String v) { status = v; }

    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String v) { uploadedAt = v; }
}
