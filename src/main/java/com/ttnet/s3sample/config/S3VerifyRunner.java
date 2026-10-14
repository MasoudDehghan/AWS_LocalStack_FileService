package com.ttnet.s3sample.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;

@Configuration
public class S3VerifyRunner {

    @Bean
    CommandLineRunner verifyS3(S3Client s3Client) {
        return args -> {
            ListBucketsResponse response = s3Client.listBuckets();
            System.out.println("Successfully connected to LocalStack S3! Available buckets:");
            response.buckets().forEach(b -> System.out.println(" -> " + b.name()));
        };
    }
}