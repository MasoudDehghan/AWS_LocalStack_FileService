package com.ttnet.s3sample.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S3FileMetadataHandler implements RequestHandler<SQSEvent, Void> {

    private static final Pattern BUCKET_FIELD =
            Pattern.compile("\\\"bucket\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern KEY_FIELD =
            Pattern.compile("\\\"key\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final S3Client S3_CLIENT = createS3Client();

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        for (SQSMessage message : event.getRecords()) {
            try {
                process(message.getBody(), context);
            } catch (RuntimeException exception) {
                context.getLogger().log("File processing failed: " + exception.getMessage());
                throw exception;
            }
        }
        return null;
    }

    private void process(String body, Context context) {
        String bucket = extractField(BUCKET_FIELD, body, "bucket");
        String key = extractField(KEY_FIELD, body, "key");
        HeadObjectResponse metadata = S3_CLIENT.headObject(request -> request
                .bucket(bucket)
                .key(key));

        context.getLogger().log(String.format(
                "Processed S3 object: bucket=%s, key=%s, type=%s, size=%d bytes, "
                        + "lastModified=%s, eTag=%s%n",
                bucket,
                key,
                metadata.contentType() == null ? "unknown" : metadata.contentType(),
                metadata.contentLength(),
                metadata.lastModified(),
                metadata.eTag()));
    }

    private String extractField(Pattern pattern, String body, String fieldName) {
        Matcher matcher = pattern.matcher(body);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "SQS message is missing '" + fieldName + "': " + body);
        }
        return matcher.group(1);
    }

    private static S3Client createS3Client() {
        String region = System.getenv().getOrDefault("AWS_REGION", "us-east-1");
        var builder = S3Client.builder().region(Region.of(region));

        // LocalStack injects AWS_ENDPOINT_URL into Lambda runtimes. In AWS, this
        // variable is absent and the SDK uses its normal AWS endpoint resolution.
        String endpoint = System.getenv("AWS_ENDPOINT_URL");
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}
