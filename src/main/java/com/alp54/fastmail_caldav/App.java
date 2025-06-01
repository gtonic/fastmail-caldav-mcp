package com.alp54.fastmail_caldav;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "spring.profiles.active", havingValue = "default")
    public ToolCallbackProvider alignitTools(FastmailCaldavClient client) {
        return MethodToolCallbackProvider.builder().toolObjects(client)
                .build();
    }

}
