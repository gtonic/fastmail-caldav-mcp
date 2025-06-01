package com.alp54.fastmail_caldav;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "spring.profiles.active", havingValue = "cli")
public class CalendarCommandLineRunner implements CommandLineRunner {

    private final FastmailCaldavClient caldavClient;

    @org.springframework.beans.factory.annotation.Value("${fastmail.caldav.url}")
    private String caldavUrl;

    @org.springframework.beans.factory.annotation.Value("${fastmail.calendar.path}")
    private String calendarPath;

    @org.springframework.beans.factory.annotation.Value("${fastmail-username}")
    private String username;

    @org.springframework.beans.factory.annotation.Value("${fastmail.app.password}")
    private String password;

    @Autowired
    public CalendarCommandLineRunner(FastmailCaldavClient caldavClient) {
        this.caldavClient = caldavClient;
    }

    @Override
    public void run(String... args) {
        System.out.print("Received arguments: ");
        for (int i = 0; i < args.length; i++) {
            System.out.print("[" + args[i] + "] ");
        }
        System.out.println();

        // Print loaded config for debugging
        System.out.println("Loaded config:");
        System.out.println("  fastmail.caldav.url = " + caldavUrl);
        System.out.println("  fastmail.calendar.path = " + calendarPath);
        System.out.println("  fastmail-username = " + username);
        System.out
                .println("  fastmail.app.password = " + (password != null && !password.isEmpty() ? "****" : "(empty)"));

        // Handle the case where all arguments are in args[0] as a comma-separated
        // string
        if (args.length == 1 && args[0].contains(",")) {
            args = args[0].split(",");
            System.out.print("Split arguments: ");
            for (int i = 0; i < args.length; i++) {
                System.out.print("[" + args[i] + "] ");
            }
            System.out.println();
        }

        if (args.length == 0) {
            System.out.println("Usage:");
            System.out.println("  get [date] [title] [description]");
            System.out.println("  create <summary> <startDate:YYYYMMDDTHHmmssZ> <endDate:YYYYMMDDTHHmmssZ>");
            System.out.println("  update <eventUrl> <summary> <startDate:YYYYMMDDTHHmmssZ> <endDate:YYYYMMDDTHHmmssZ>");
            return;
        }

        String command = args[0].toLowerCase();
        try {
            switch (command) {
                case "get":
                    handleGet(args);
                    break;
                case "create":
                    handleCreate(args);
                    break;
                case "update":
                    handleUpdate(args);
                    break;
                case "delete":
                    handleDelete(args);
                    break;
                default:
                    System.out.println("Unknown command: " + command);
            }
        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDelete(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Usage: delete <uid>");
            return;
        }
        String uid = args[1];
        boolean success = caldavClient.deleteCalendarEvent(uid);
        if (success) {
            System.out.println("Event deleted successfully.");
        } else {
            System.out.println("Failed to delete event.");
        }
    }

    private void handleGet(String[] args) throws Exception {
        List<String> events;

        String date = null;
        String title = null;
        String description = null;
        String freq = null;

        // Parse named parameters like freq=recurring, desc=foo, date=YYYY-MM-DD,
        // title=Meeting
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("freq=")) {
                freq = arg.substring(5);
            } else if (arg.startsWith("desc=")) {
                description = arg.substring(5);
            } else if (arg.startsWith("date=")) {
                date = arg.substring(5);
            } else if (arg.startsWith("title=")) {
                title = arg.substring(6);
            } else if (date == null) {
                date = arg;
            } else if (title == null) {
                title = arg;
            } else if (description == null) {
                description = arg;
            }
        }

        if (freq != null) {
            // For now, freq filter will be handled in client after fetching events
            events = caldavClient.getCalendarEvents(date, title, description);
            // Filter recurring events in Java after fetching
            // Temporarily disabled filtering of recurring events
            // events = events.stream()
            // .filter(eventJson -> eventJson.contains("\"rrule\"") ||
            // eventJson.contains("RRULE"))
            // .toList();
        } else {
            events = caldavClient.getCalendarEvents(date, title, description);
            // Exclude all recurring events when freq is not 'recurring' and date is
            // specified
            // Temporarily disabled exclusion of recurring events
            // if (date != null && !date.isEmpty()) {
            // events = events.stream()
            // .filter(eventJson -> !eventJson.toLowerCase().contains("rrule"))
            // .toList();
            // }
            // Exclude events with invalid date "0001-01-01 01:05"
            events = events.stream()
                    .filter(eventJson -> !eventJson.contains("\"dtstart\":\"0001-01-01 01:05\""))
                    .toList();
        }

        if (events.isEmpty()) {
            System.out.println("No events found.");
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();
        for (String eventJsonString : events) {
            try {
                JsonNode rootNode = objectMapper.readTree(eventJsonString);
                if (rootNode.isObject()) {
                    ObjectNode eventObject = (ObjectNode) rootNode;

                    if (eventObject.has("dtstart")) {
                        String dtstart = eventObject.get("dtstart").asText();
                        eventObject.put("dtstart", formatDateString(dtstart));
                    }

                    if (eventObject.has("dtend")) {
                        String dtend = eventObject.get("dtend").asText();
                        eventObject.put("dtend", formatDateString(dtend));
                    }
                    System.out.println(objectMapper.writeValueAsString(eventObject));
                } else {
                    System.out.println(eventJsonString); // Print as is if not a JSON object
                }
            } catch (Exception e) {
                System.err.println("Error processing event JSON: " + eventJsonString + " - " + e.getMessage());
                System.out.println(eventJsonString); // Print original on error
            }
        }
    }

    private String formatDateString(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return dateStr;
        }

        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ENGLISH);

        // Try parsing "yyyyMMdd'T'HHmmss"
        try {
            DateTimeFormatter inputFormatter1 = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss", Locale.ENGLISH);
            LocalDateTime dateTime = LocalDateTime.parse(dateStr, inputFormatter1);
            return dateTime.format(outputFormatter);
        } catch (DateTimeParseException e1) {
            // Ignore and try next format
        }

        // Try parsing "yyyy-MM-dd HH:mm" (already in desired or similar format)
        try {
            // This pattern handles cases where seconds might be present or not
            DateTimeFormatter inputFormatter2 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm[:ss]", Locale.ENGLISH);
            LocalDateTime dateTime = LocalDateTime.parse(dateStr, inputFormatter2);
            return dateTime.format(outputFormatter);
        } catch (DateTimeParseException e2) {
            // Ignore and try next format
        }

        // Try parsing "yyyy-MM-dd HH:mm:ss" explicitly if the above general one fails
        try {
            DateTimeFormatter inputFormatter3 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
            LocalDateTime dateTime = LocalDateTime.parse(dateStr, inputFormatter3);
            return dateTime.format(outputFormatter);
        } catch (DateTimeParseException e3) {
            // If all parsing attempts fail, return the original string
            // System.err.println("Could not parse date string: " + dateStr);
            return dateStr;
        }
    }

    private void handleCreate(String[] args) throws Exception {
        if (args.length != 5) {
            System.out.println("Usage: create <summary> <date:YYYY-MM-DD> <startTime:HHmm> <endTime:HHmm>");
            return;
        }
        String summary = args[1];
        String date = args[2];
        String startTime = args[3];
        String endTime = args[4];
        String eventUrl = caldavClient.createCalendarEvent(summary, date, startTime, endTime);
        System.out.println("Event created at: " + eventUrl);
    }

    private void handleUpdate(String[] args) throws Exception {
        if (args.length != 6) {
            System.out.println(
                    "Usage: update <eventUrl> <summary> <date:YYYY-MM-DD> <startTime:HHmm> <endTime:HHmm>");
            return;
        }
        String eventUrl = args[1];
        String summary = args[2];
        String date = args[3];
        String startTime = args[4];
        String endTime = args[5];
        boolean success = caldavClient.updateCalendarEvent(eventUrl, summary, date, startTime, endTime);
        if (success) {
            System.out.println("Event updated successfully.");
        } else {
            System.out.println("Failed to update event.");
        }
    }
}
