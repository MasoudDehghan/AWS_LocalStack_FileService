package com.ttnet.s3sample.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.services.sqs.SqsClient;

@Service
public class FileQueueService {
    private final SqsClient sqsClient;
    private final String queueUrl;

    public FileQueueService(SqsClient sqsClient,
                            @Value("${app.aws.sqs.queue-url}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    public void enqueue(String bucket, String key) {
        String message = """
                {
                 "bucket": "%s",
                 "key": "%s"
                }
                """.formatted(bucket, key);
        sqsClient.sendMessage(request->request
                .queueUrl(queueUrl)
                .messageBody(message));
    }
}
