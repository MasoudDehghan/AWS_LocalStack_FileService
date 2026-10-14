package com.ttnet.s3sample;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class S3SampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(S3SampleApplication.class, args);
    }

}
