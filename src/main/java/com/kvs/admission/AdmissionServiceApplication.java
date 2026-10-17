package com.kvs.admission;

import com.kvs.core.config.web.WebConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = "com.kvs",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = WebConfig.class
        )
)
public class AdmissionServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdmissionServiceApplication.class, args);
    }
}
