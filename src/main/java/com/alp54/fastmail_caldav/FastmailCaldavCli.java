package com.alp54.fastmail_caldav;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class FastmailCaldavCli {

    private final FastmailCaldavClient client;

    @Autowired
    public FastmailCaldavCli(FastmailCaldavClient client) {
        this.client = client;
    }

    public void run(String[] args) throws Exception {
        if (args.length < 1) {
            printUsage();
            return;
        }
        String command = args[0];

        switch (command) {
            case "getcalendarevents":
                String date = null;
                if (args.length >= 2) {
                    date = args[1];
                }
                List<String> events = client.getCalendarEvents(date);
                System.out.println("Calendar events:");
                for (String event : events) {
                    System.out.println(event);
                }
                break;
            default:
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  getcalendarevents");
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                "com.alp54.fastmail_caldav")) {
            FastmailCaldavCli cli = context.getBean(FastmailCaldavCli.class);
            cli.run(args);
        }
    }
}
