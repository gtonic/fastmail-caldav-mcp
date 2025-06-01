# fastmail-caldav-mcp

MCP for Fastmail Calendar

## Overview

This project provides a Java client for interacting with the Fastmail CalDAV API using the Model Context Protocol (MCP). It allows you to:

- Create new calendar events
- Update existing calendar events
- Search for calendar events by date, title, or description

## Model Context Protocol (MCP) Interface

The FastmailCaldavClient class exposes several methods as MCP tools:

- `getCalendarEvents`: Queries all events of the calendar
- `getCalendarEventsByDate`: Queries all events of the calendar, filtered by date (YYYY-mm-dd)
- `getCalendarEventsByTitleOrDescription`: Queries all events of the calendar, filtered by title or description
- `createCalendarEvent`: Creates a new calendar event
- `updateCalendarEvent`: Updates an existing calendar event

These methods are annotated with `@Tool` and can be used as part of an MCP server.

## Features

- **Create Calendar Events**: Create new calendar events with a summary, start date, and end date.
- **Update Calendar Events**: Update existing calendar events with a new summary, start date, and end date.
- **Search Calendar Events**: Search for calendar events by date, title, or description. Supports case-insensitive partial matches.

## Usage

### Prerequisites

- Java 17 or later
- Maven

### Configuration

Before using the client, you need to configure the following properties in `src/main/resources/application.properties`:

```
fastmail.caldav.url=https://caldav.fastmail.com
fastmail.calendar.path=/dav
fastmail-username=your-fastmail-username
fastmail.app.password=your-fastmail-app-password
```

### Commands (Cli-Mode)

#### Get Calendar Events

```sh
java -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar getcalendarevents [--date <date>] [--title <title>] [--description <description>]
```

- `--date <date>`: Filter events by date (YYYY-mm-dd)
- `--title <title>`: Filter events by title (case-insensitive partial match)
- `--description <description>`: Filter events by description (case-insensitive partial match)

#### Create Calendar Event

```sh
java -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar createcalendarevent "<summary>" "<date>" "<time>"
```

- `<summary>`: The summary of the event
- `<date>`: The date of the event (YYYY-mm-dd)
- `<time>`: The time of the event (HH:mm)

#### Update Calendar Event

```sh
java -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar updatecalendarevent "<eventUrl>" "<summary>" "<date>" "<time>"
```

- `<eventUrl>`: The URL of the event to update
- `<summary>`: The new summary of the event
- `<date>`: The new date of the event (YYYY-mm-dd)
- `<time>`: The new time of the event (HH:mm)

## Examples (Cli-Mode)

### Create a New Event

```sh
java -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar createcalendarevent "Team Meeting" "2025-06-02" "09:00"
```

### Update an Existing Event

```sh
java -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar updatecalendarevent "https://example.com/calendar/event1.ics" "Updated Meeting" "2025-06-02" "09:30"
```

### Search for Events

```sh
java -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar getcalendarevents --title "meeting" --description "project"
```

## Building

To build the project, run:

```sh
mvn clean package
```

This will generate a JAR file in the `target` directory.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.
