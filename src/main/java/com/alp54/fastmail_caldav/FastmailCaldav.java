package com.alp54.fastmail_caldav;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

@Component
public class FastmailCaldav {

    public record EchoRequest(String message) {
    }

    public record EchoResponse(String echoedMessage) {
    }

    @Tool(name = "echo_tool", description = "Echoes back the provided message.")
    public EchoResponse echo(EchoRequest request) {
        if (request == null || request.message() == null) {
            return new EchoResponse("Echo: No message provided.");
        }
        return new EchoResponse("Echo: " + request.message());
    }

}
