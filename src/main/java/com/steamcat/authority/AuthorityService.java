package com.steamcat.authority;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.netflix.eureka.EnableEurekaClient;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * @ClassName AuthorityService
 * @Description TODO
 * @Author Administrator
 * @Data 下午 11:52
 * @Version 1.0
 **/
@MapperScan("com.steamcat.authority.dao")
@SpringBootApplication
@EnableEurekaClient
public class AuthorityService {
    public static void main(String[] args) {
        SpringApplication.run(AuthorityService.class);
    }

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
