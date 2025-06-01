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

### Environment Variables

Alternatively, you can export the following environment variables before running the application, which will override the properties file settings:

```sh
export FASTMAIL_CALDAV_URL=https://caldav.fastmail.com
export FASTMAIL_CALENDAR_PATH=/dav
export FASTMAIL_USERNAME=your-fastmail-username
export FASTMAIL_APP_PASSWORD=your-fastmail-app-password
```

This is useful for running the application in containerized or cloud environments.

### Running the Server (MCP)

To run the MCP server (web API) in server mode:

```sh
mvn spring-boot:run
```

or after packaging:

```sh
java -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar
```

This will start the server listening on the configured port (default 8099).

### Running the CLI

To run the CLI command line interface, activate the `cli` Spring profile and pass the command line arguments. For example:

```sh
java -Dspring.profiles.active=cli -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar get
```

The CLI supports the following commands and parameters:

- `get [date] [title] [description]`: Retrieve calendar events filtered by optional date, title, or description.
- `create <summary> <date:YYYY-MM-DD> <startTime:HHmm> <endTime:HHmm>`: Create a new calendar event.
- `update <eventUrl> <summary> <date:YYYY-MM-DD> <startTime:HHmm> <endTime:HHmm>`: Update an existing calendar event.
- `delete <uid>`: Delete a calendar event by UID.

### Examples

Run the CLI to get events with title "meeting":

```sh
java -Dspring.profiles.active=cli -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar get title=meeting
```

Create a new event:

```sh
java -Dspring.profiles.active=cli -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar create "Team Meeting" 2025-06-02 0900 1000
```

Update an event:

```sh
java -Dspring.profiles.active=cli -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar update "https://example.com/calendar/event1.ics" "Updated Meeting" 2025-06-02 0930 1030
```

Delete an event:

```sh
java -Dspring.profiles.active=cli -jar target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar delete <uid>
```

## Building

To build the project, run:

```sh
mvn clean package
```

This will generate a JAR file in the `target` directory.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Docker and Taskfile

### Docker

A Dockerfile is provided to containerize the application. It uses the Eclipse Temurin JRE 17 base image and copies the pre-built jar from the `target` directory. To build the Docker image, run:

```sh
docker build -t fastmail-caldav-mcp .
```

To run the Docker container:

```sh
docker run -p 8099:8080 fastmail-caldav-mcp
```

### Taskfile

A `Taskfile.yml` is included to simplify common tasks related to building and cleaning the project and Docker image. It requires the `task` CLI tool to be installed (https://taskfile.dev/#/installation).

The available tasks are:

- `task clean`: Cleans the Maven project by running `mvn clean`.
- `task build`: Builds the Maven project by running `mvn package -DskipTests`.
- `task docker-build`: Builds the Docker image using the Dockerfile.
- `task docker-clean`: Removes the Docker image named `fastmail-caldav-mcp`.

You can run these tasks using the `task` command. For example, to build the project and Docker image, run:

```sh
task build
task docker-build
```
