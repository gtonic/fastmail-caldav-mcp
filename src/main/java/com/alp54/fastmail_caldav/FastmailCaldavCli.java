package com.alp54.fastmail_caldav;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
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
                String title = null;
                String description = null;

                for (int i = 1; i < args.length; i++) {
                    switch (args[i]) {
                        case "--date":
                            if (i + 1 < args.length) {
                                date = args[++i];
                            } else {
                                System.out.println("Error: Missing value for --date");
                                printUsage();
                                return;
                            }
                            break;
                        case "--title":
                            if (i + 1 < args.length) {
                                title = args[++i];
                            } else {
                                System.out.println("Error: Missing value for --title");
                                printUsage();
                                return;
                            }
                            break;
                        case "--description":
                            if (i + 1 < args.length) {
                                description = args[++i];
                            } else {
                                System.out.println("Error: Missing value for --description");
                                printUsage();
                                return;
                            }
                            break;
                        default:
                            // If it's not a recognized option, treat it as the date parameter
                            date = args[i];
                            break;
                    }
                }

                List<String> events = client.getCalendarEvents(date, title, description);
                System.out.println("Calendar events:");
                for (String event : events) {
                    System.out.println(event);
                }
                break;
            case "createcalendarevent":
                if (args.length < 4) {
                    System.out.println("Error: Missing required arguments for createcalendarevent");
                    printUsage();
                    return;
                }
                String summary = args[1];
                String dateArg = args[2];
                String timeArg = args[3];

                // Parse the date and time
                LocalDate localDate = LocalDate.parse(dateArg, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                LocalDateTime localDateTime = localDate.atTime(0, 0);

                if (timeArg != null && !timeArg.isEmpty()) {
                    localDateTime = localDate.atTime(
                            Integer.parseInt(timeArg.substring(0, 2)),
                            Integer.parseInt(timeArg.substring(3, 5)));
                }

                ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
                String startDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        .withZone(ZoneId.of("UTC"))
                        .format(zonedDateTime);

                // Add 1 hour to the end time
                zonedDateTime = zonedDateTime.plusHours(1);
                String endDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        .withZone(ZoneId.of("UTC"))
                        .format(zonedDateTime);

                try {
                    String eventUrl = client.createCalendarEvent(summary, startDate, endDate);
                    System.out.println("Event created successfully: " + eventUrl);
                } catch (Exception e) {
                    System.err.println("Failed to create event: " + e.getMessage());
                }
                break;
            case "updatecalendarevent":
                if (args.length < 5) {
                    System.out.println("Error: Missing required arguments for updatecalendarevent");
                    printUsage();
                    return;
                }
                String eventUrl = args[1];
                String newSummary = args[2];
                String newDateArg = args[3];
                String newTimeArg = args[4];

                // Parse the date and time
                LocalDate newLocalDate = LocalDate.parse(newDateArg, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                LocalDateTime newLocalDateTime = newLocalDate.atTime(0, 0);

                if (newTimeArg != null && !newTimeArg.isEmpty()) {
                    newLocalDateTime = newLocalDate.atTime(
                            Integer.parseInt(newTimeArg.substring(0, 2)),
                            Integer.parseInt(newTimeArg.substring(3, 5)));
                }

                ZonedDateTime newZonedDateTime = newLocalDateTime.atZone(ZoneId.systemDefault());
                String newStartDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        .withZone(ZoneId.of("UTC"))
                        .format(newZonedDateTime);

                // Add 1 hour to the end time
                newZonedDateTime = newZonedDateTime.plusHours(1);
                String newEndDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        .withZone(ZoneId.of("UTC"))
                        .format(newZonedDateTime);

                try {
                    boolean success = client.updateCalendarEvent(eventUrl, newSummary, newStartDate, newEndDate);
                    if (success) {
                        System.out.println("Event updated successfully");
                    } else {
                        System.out.println("Failed to update event");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to update event: " + e.getMessage());
                }
                break;
            default:
                printUsage();
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  getcalendarevents [--date <date>] [--title <title>] [--description <description>]");
        System.out.println("  createcalendarevent <summary> <date> <time>");
        System.out.println("  updatecalendarevent <eventUrl> <summary> <date> <time>");
    }

    public static void main(String[] args) throws Exception {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                "com.alp54.fastmail_caldav")) {
            FastmailCaldavCli cli = context.getBean(FastmailCaldavCli.class);
            cli.run(args);
        }
    }
}
