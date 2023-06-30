package com.goodjob.batch;

import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableBatchProcessing
@EnableScheduling
@EnableAsync
@SpringBootApplication
public class GoodJobBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(GoodJobBatchApplication.class, args);
    }

}
