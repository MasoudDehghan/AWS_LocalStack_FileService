package com.ttnet.s3sample.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@ConditionalOnProperty(prefix = "app.aws.sqs.poller", name = "enabled", havingValue = "true")
public class FileProcessingWorker {
    private final SqsClient sqsClient;
    private final S3Client s3Client;
    private final String queueUrl;
    private static final Pattern BUCKET_FIELD = Pattern.compile("\\\"bucket\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern KEY_FIELD = Pattern.compile("\\\"key\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    public FileProcessingWorker(SqsClient sqsClient, S3Client s3Client,
                                @Value("${app.aws.sqs.queue-url}") String queueUrl) {
        this.sqsClient = sqsClient;
        this.s3Client = s3Client;
        this.queueUrl = queueUrl;
    }

    @Scheduled(fixedRate = 5000)
    public void pollQueue(){
        var response = sqsClient.receiveMessage(
                r->r.queueUrl(queueUrl).maxNumberOfMessages(5).waitTimeSeconds(2));
        for (var message : response.messages()) {
            try{
                process(message.body());
                sqsClient.deleteMessage(r->r
                        .queueUrl(queueUrl)
                        .receiptHandle(message.receiptHandle()));
            }
            catch (Exception e){
                System.out.println(e.getMessage());
            }
        }
    }

    private void process(String body) {
        String bucket = extractField(BUCKET_FIELD, body, "bucket");
        String key = extractField(KEY_FIELD, body, "key");
        HeadObjectResponse metadata = s3Client.headObject(request -> request
                .bucket(bucket)
                .key(key));

        String fileType = metadata.contentType() == null
                ? "unknown"
                : metadata.contentType();
        System.out.printf(
                "Processed S3 object: bucket=%s, key=%s, type=%s, size=%d bytes, "
                        + "lastModified=%s, eTag=%s%n",
                bucket,
                key,
                fileType,
                metadata.contentLength(),
                metadata.lastModified(),
                metadata.eTag());
    }

    private String extractField(Pattern pattern, String body, String fieldName) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new IllegalArgumentException("SQS message is missing '" + fieldName + "': " + body);
        }
        return matcher.group(1);
    }
}
