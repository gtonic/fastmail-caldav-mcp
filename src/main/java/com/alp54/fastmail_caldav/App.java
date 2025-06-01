package com.alp54.fastmail_caldav;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class App { // implements CommandLineRunner {
    /*
     * private final FastmailCaldavCli cli;
     * 
     * public App(FastmailCaldavCli cli) {
     * this.cli = cli;
     * }
     */

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    /*
     * @Override
     * public void run(String... args) throws Exception {
     * cli.run(args);
     * }
     */

    @Bean
    public ToolCallbackProvider alignitTools(FastmailCaldavClient client) {
        return MethodToolCallbackProvider.builder().toolObjects(client)
                .build();
    }
}
