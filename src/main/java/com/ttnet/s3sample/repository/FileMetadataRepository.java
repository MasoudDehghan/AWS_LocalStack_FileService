package com.ttnet.s3sample.repository;

import com.ttnet.s3sample.model.FileMetadata;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;

@Repository
public class FileMetadataRepository {
    private final DynamoDbTable<FileMetadata> table;
    public FileMetadataRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table(
                "file-metadata",
                TableSchema.fromBean(FileMetadata.class)
        );
    }

    public void save(FileMetadata fileMetadata) {
        table.putItem(fileMetadata);
    }

    public Optional<FileMetadata> findById(String fileId) {
        return Optional.ofNullable(
                table.getItem(Key.builder()
                        .partitionValue(fileId)
                        .build())
        );
    }

    public List<FileMetadata> findAll() {
        List<FileMetadata> items = new ArrayList<>();
        table.scan().items().forEach(items::add);
        return items;
    }

    public void deleteById(String fileId) {
        table.deleteItem(Key.builder().partitionValue(fileId).build());
    }
}
