package com.alp54.fastmail_caldav;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.time.Instant; // Added for conversion
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.io.StringReader;
import java.text.ParseException; // For RRule parsing
import java.text.SimpleDateFormat; // For all-day date parsing

import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
// import net.fortuna.ical4j.model.Component; // Replaced by fully qualified name
import net.fortuna.ical4j.model.Date; // Explicit import for ical4j.model.Date
import net.fortuna.ical4j.model.DateTime; // Explicit import for ical4j.model.DateTime
import net.fortuna.ical4j.model.Period;
import net.fortuna.ical4j.model.PeriodList;
// import net.fortuna.ical4j.model.Property; // Will use specific property classes
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.jackrabbit.webdav.client.methods.HttpReport;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FastmailCaldavClient {

        @Value("${fastmail.caldav.url}")
        private String caldavUrl;

        @Value("${fastmail.calendar.path}")
        private String calendarPath;

        @Value("${fastmail-username}")
        private String username;

        @Value("${fastmail.app.password}")
        private String password;

        @Tool(name = "getCalendarEvents", description = "Queries all events of the calendar")
        public List<String> getCalendarEvents()
                        throws IOException, URISyntaxException, javax.xml.parsers.ParserConfigurationException,
                        org.xml.sax.SAXException, org.apache.jackrabbit.webdav.DavException {
                return getCalendarEvents(null, null, null);
        }

        @Tool(name = "getCalendarEventsByDate", description = "Queries all events of the calendar, filtered by date (YYYY-mm-dd)")
        public List<String> getCalendarEvents(String date)
                        throws IOException, URISyntaxException, javax.xml.parsers.ParserConfigurationException,
                        org.xml.sax.SAXException, org.apache.jackrabbit.webdav.DavException {
                return getCalendarEvents(date, null, null);
        }

        @Tool(name = "getCalendarEventsByTitleOrDescription", description = "Queries all events of the calendar, filtered by title or description")
        public List<String> getCalendarEvents(String date, String title, String description)
                        throws IOException, URISyntaxException, javax.xml.parsers.ParserConfigurationException,
                        org.xml.sax.SAXException, org.apache.jackrabbit.webdav.DavException {
                List<String> events = new ArrayList<>();

                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                                AuthScope.ANY,
                                new UsernamePasswordCredentials(username, password));

                try (CloseableHttpClient httpclient = HttpClients.custom()
                                .setDefaultCredentialsProvider(credsProvider)
                                .build()) {

                        URI uri = new URI(caldavUrl + calendarPath);
                        String timeRangeXml = "";
                        if (date != null && !date.isEmpty()) {
                                // Convert YYYY-mm-dd to YYYYMMDDT000000Z
                                String start = date.replaceAll("-", "") + "T000000Z";
                                // Calculate end as next day midnight
                                java.time.LocalDate localDate = java.time.LocalDate.parse(date);
                                java.time.LocalDate nextDay = localDate.plusDays(1);
                                String end = nextDay.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                                                + "T000000Z";
                                timeRangeXml = "<c:comp-filter name=\"VEVENT\"><c:time-range start=\"" + start
                                                + "\" end=\"" + end + "\" /></c:comp-filter>";
                        }

                        // Build the filter for title and/or description
                        String textMatchXml = "";
                        if (title != null || description != null) {
                                textMatchXml = "<c:comp-filter name=\"VEVENT\">";
                                if (title != null) {
                                        textMatchXml += "<c:prop-filter name=\"SUMMARY\">" +
                                                        "<c:text-match collation=\"i;ascii-numeric\">" + title
                                                        + "</c:text-match>" +
                                                        "</c:prop-filter>";
                                }
                                if (description != null) {
                                        textMatchXml += "<c:prop-filter name=\"DESCRIPTION\">" +
                                                        "<c:text-match collation=\"i;ascii-numeric\">" + description
                                                        + "</c:text-match>" +
                                                        "</c:prop-filter>";
                                }
                                textMatchXml += "</c:comp-filter>";
                        }

                        // Add filter for recurring events if freq=recurring is specified
                        String freqFilterXml = "";
                        if ("recurring".equalsIgnoreCase(description)) {
                                freqFilterXml = "<c:prop-filter name=\"RRULE\" />";
                        }

                        String reportXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                                        "<c:calendar-query xmlns:c=\"urn:ietf:params:xml:ns:caldav\">\n" +
                                        "  <d:prop xmlns:d=\"DAV:\">\n" +
                                        "    <d:getetag />\n" +
                                        "    <c:calendar-data />\n" +
                                        "  </d:prop>\n" +
                                        "  <c:filter>\n" +
                                        "    <c:comp-filter name=\"VCALENDAR\">" + timeRangeXml + textMatchXml
                                        + freqFilterXml + "</c:comp-filter>\n" +
                                        "  </c:filter>\n" +
                                        "</c:calendar-query>";
                        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory
                                        .newInstance();
                        factory.setNamespaceAware(true);
                        org.w3c.dom.Document doc = factory.newDocumentBuilder()
                                        .parse(new java.io.ByteArrayInputStream(
                                                        reportXml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        org.w3c.dom.Element reportElement = doc.getDocumentElement();

                        org.apache.jackrabbit.webdav.version.report.ReportInfo reportInfo = new org.apache.jackrabbit.webdav.version.report.ReportInfo(
                                        reportElement, 1);
                        HttpReport report = new HttpReport(uri.toString(), reportInfo);
                        report.addHeader("Depth", "infinity");
                        report.setHeader("Content-Type", "application/xml");
                        report.setEntity(new StringEntity(reportXml, "UTF-8"));
                        try (CloseableHttpResponse response = httpclient.execute(report)) {
                                String responseBody = EntityUtils.toString(response.getEntity());
                                LocalDate queryDate = null;
                                if (date != null && !date.isEmpty()) {
                                        try {
                                                queryDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
                                        } catch (Exception e) {
                                                System.err.println("Could not parse query date: " + date
                                                                + ", defaulting to no specific date for recurrence calculation.");
                                        }
                                }
                                // Parse the XML response and extract events as JSON
                                events.addAll(parseEventsToJson(responseBody, queryDate));
                        }
                }
                return events;
        }

        private List<String> parseEventsToJson(String xml, LocalDate queryDate)
                        throws javax.xml.parsers.ParserConfigurationException, org.xml.sax.SAXException, IOException {
                List<String> jsonEvents = new ArrayList<>();
                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory
                                .newInstance();
                factory.setNamespaceAware(true);
                javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
                org.w3c.dom.Document doc = builder.parse(new java.io.ByteArrayInputStream(
                                xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

                org.w3c.dom.NodeList responses = doc.getElementsByTagNameNS("DAV:", "response");
                for (int i = 0; i < responses.getLength(); i++) {
                        org.w3c.dom.Element response = (org.w3c.dom.Element) responses.item(i);
                        org.w3c.dom.NodeList calendarDataList = response
                                        .getElementsByTagNameNS("urn:ietf:params:xml:ns:caldav", "calendar-data");
                        if (calendarDataList.getLength() == 0) {
                                continue;
                        }
                        String calendarData = calendarDataList.item(0).getTextContent();
                        if (calendarData == null || calendarData.isEmpty()) {
                                continue;
                        }
                        // Parse iCalendar data from calendarData string
                        try {
                                List<String> eventJsons = parseICalendarToJson(calendarData, queryDate);
                                if (eventJsons != null && !eventJsons.isEmpty()) {
                                        jsonEvents.addAll(eventJsons);
                                }
                        } catch (IOException | ParserException e) {
                                System.err.println("Failed to parse iCalendar data: " + e.getMessage());
                                // Optionally add the raw data or a placeholder error to jsonEvents
                        }
                }
                return jsonEvents;
        }

        private String formatIcal4jDate(net.fortuna.ical4j.model.Date dateValue, boolean isAllDayHint) {
                if (dateValue == null)
                        return "";

                ZoneId outputZoneId = ZoneId.systemDefault();
                ZonedDateTime zdt;

                if (dateValue instanceof net.fortuna.ical4j.model.DateTime) {
                        // It's a DateTime with a specific time
                        // Convert ical4j DateTime to java.util.Date then to ZonedDateTime
                        java.util.Date utilDate = new java.util.Date(dateValue.getTime());
                        zdt = utilDate.toInstant().atZone(outputZoneId);
                        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(zdt);
                } else {
                        // It's an ical4j.model.Date, representing an all-day event.
                        // These are represented as yyyyMMdd internally.
                        // Convert to LocalDate then to ZonedDateTime at start of day.
                        try {
                                // SimpleDateFormat is not thread-safe, but used locally here.
                                // Ensure the format matches how ical4j.model.Date.toString() would represent it
                                // if it's just a date.
                                // Or, more robustly, convert its time (which is midnight UTC for Date objects)
                                // to an Instant.
                                LocalDate localDate = Instant.ofEpochMilli(dateValue.getTime()).atZone(ZoneId.of("UTC"))
                                                .toLocalDate();
                                return DateTimeFormatter.ISO_LOCAL_DATE.format(localDate);
                        } catch (Exception e) {
                                // Fallback or error logging if parsing date string fails
                                System.err.println("Error formatting all-day date: " + dateValue.toString() + " "
                                                + e.getMessage());
                                return dateValue.toString(); // Fallback to raw string
                        }
                }
        }

        private List<String> parseICalendarToJson(String icalendarString, LocalDate queryDate)
                        throws IOException, ParserException {
                CalendarBuilder builder = new CalendarBuilder();
                Calendar cal;
                // Always ensure we have a VCALENDAR wrapper, and only parse ONCE
                try {
                        if (icalendarString.trim().startsWith("BEGIN:VEVENT")) {
                                String fakeCalendar = "BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//Cline Helper//EN\n"
                                                + icalendarString.trim() + "\nEND:VCALENDAR";
                                cal = builder.build(new StringReader(fakeCalendar));
                        } else {
                                cal = builder.build(new StringReader(icalendarString));
                        }
                } catch (ParserException e) {
                        throw e;
                }

                // Only process VEVENT components, skip VTIMEZONE, STANDARD, DAYLIGHT, etc.
                List<String> eventJsons = new ArrayList<>();
                for (Object compObj : cal.getComponents(net.fortuna.ical4j.model.Component.VEVENT)) {
                        if (!(compObj instanceof VEvent))
                                continue;
                        VEvent event = (VEvent) compObj;

                        Uid uidPropFull = event.getUid();
                        String uid = (uidPropFull == null) ? "" : uidPropFull.getValue();

                        Summary summaryPropFull = event.getSummary();
                        String summary = (summaryPropFull == null) ? "" : summaryPropFull.getValue();

                        Description descriptionPropFull = event.getDescription();
                        String description = (descriptionPropFull == null) ? "" : descriptionPropFull.getValue();

                        Location locationPropFull = event.getLocation();
                        String location = (locationPropFull == null) ? "" : locationPropFull.getValue();

                        net.fortuna.ical4j.model.Property rawRruleProp = event
                                        .getProperty(net.fortuna.ical4j.model.Property.RRULE);
                        RRule rruleProp = (rawRruleProp instanceof RRule) ? (RRule) rawRruleProp : null;
                        String rruleString = rruleProp != null ? rruleProp.getValue() : "";

                        DtStart dtStartProperty = event.getStartDate(); // Returns DtStart property
                        DtEnd dtEndProperty = event.getEndDate(); // Returns DtEnd property

                        boolean isAllDayEvent = dtStartProperty != null
                                        && !(dtStartProperty.getDate() instanceof net.fortuna.ical4j.model.DateTime);

                        if (dtStartProperty != null && rruleProp != null && queryDate != null) {
                                net.fortuna.ical4j.model.Date initialDtStart = dtStartProperty.getDate();
                                Recur recur;
                                try {
                                        recur = new Recur(rruleProp.getValue());
                                } catch (java.text.ParseException e) {
                                        System.err.println("Failed to parse RRULE: " + rruleProp.getValue() + " - "
                                                        + e.getMessage());
                                        // Fallback to treating as non-recurring for date calculation
                                        String dtstartStr = formatIcal4jDate(initialDtStart, isAllDayEvent);
                                        String dtendStr = "";
                                        if (dtEndProperty != null) {
                                                dtendStr = formatIcal4jDate(dtEndProperty.getDate(),
                                                                isAllDayEvent || !(dtEndProperty
                                                                                .getDate() instanceof net.fortuna.ical4j.model.DateTime));
                                        }
                                        // Proceed to JSON formatting without recurrence calculation
                                        summary = summary.replace("\"", "\\\"");
                                        description = description.replace("\"", "\\\"");
                                        location = location.replace("\"", "\\\"");
                                        if (dtstartStr == null || dtstartStr.isEmpty()
                                                        || dtstartStr.equals("0001-12-30")) {
                                                continue;
                                        }
                                        eventJsons.add(String.format(
                                                        "{\"uid\":\"%s\",\"dtstart\":\"%s\",\"dtend\":\"%s\",\"summary\":\"%s\",\"description\":\"%s\",\"location\":\"%s\",\"rrule\":\"%s\"}",
                                                        uid, dtstartStr, dtendStr, summary, description, location,
                                                        rruleString));
                                        continue;
                                }

                                net.fortuna.ical4j.model.DateTime periodSearchStart = new net.fortuna.ical4j.model.DateTime(
                                                queryDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
                                                                .toEpochMilli());

                                net.fortuna.ical4j.model.DateTime periodSearchEnd = new net.fortuna.ical4j.model.DateTime(
                                                queryDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                                                                .toEpochMilli());

                                Period period = new Period(periodSearchStart, periodSearchEnd);
                                PeriodList occurrences = event.calculateRecurrenceSet(period);

                                if (!occurrences.isEmpty()) {
                                        for (Object occObj : occurrences) {
                                                if (!(occObj instanceof Period))
                                                        continue;
                                                Period occ = (Period) occObj;
                                                net.fortuna.ical4j.model.Date occurrenceStart = occ.getStart();
                                                String dtstartStrRec = formatIcal4jDate(occurrenceStart, isAllDayEvent);

                                                String dtendStrRec = "";
                                                if (dtEndProperty != null) {
                                                        long durationMillis = dtEndProperty.getDate().getTime()
                                                                        - initialDtStart.getTime();
                                                        net.fortuna.ical4j.model.Date occurrenceEnd = new net.fortuna.ical4j.model.DateTime(
                                                                        occurrenceStart.getTime() + durationMillis);
                                                        dtendStrRec = formatIcal4jDate(occurrenceEnd, isAllDayEvent
                                                                        || !(occurrenceEnd instanceof net.fortuna.ical4j.model.DateTime));
                                                } else if (isAllDayEvent) {
                                                        net.fortuna.ical4j.model.Date occurrencePeriodEnd = occ
                                                                        .getEnd();
                                                        java.util.Calendar calEnd = java.util.Calendar.getInstance();
                                                        calEnd.setTime(occurrencePeriodEnd);
                                                        if (isAllDayEvent)
                                                                calEnd.add(java.util.Calendar.DATE, -1);
                                                        dtendStrRec = formatIcal4jDate(
                                                                        new net.fortuna.ical4j.model.Date(
                                                                                        calEnd.getTime()),
                                                                        true);
                                                }

                                                // Ignore invalid events (e.g., dtstartStr is "0001-12-30" or empty)
                                                if (dtstartStrRec == null || dtstartStrRec.isEmpty()
                                                                || dtstartStrRec.equals("0001-12-30")) {
                                                        continue;
                                                }
                                                summary = summary.replace("\"", "\\\"");
                                                description = description.replace("\"", "\\\"");
                                                location = location.replace("\"", "\\\"");
                                                eventJsons.add(String.format(
                                                                "{\"uid\":\"%s\",\"dtstart\":\"%s\",\"dtend\":\"%s\",\"summary\":\"%s\",\"description\":\"%s\",\"location\":\"%s\",\"rrule\":\"%s\"}",
                                                                uid, dtstartStrRec, dtendStrRec, summary, description,
                                                                location, rruleString));
                                        }
                                } else {
                                        String dtstartStr = "";
                                        String dtendStr = "";
                                        if (dtStartProperty != null) {
                                                dtstartStr = formatIcal4jDate(dtStartProperty.getDate(), isAllDayEvent);
                                        }
                                        if (dtEndProperty != null) {
                                                boolean isAllDayEnd = !(dtEndProperty
                                                                .getDate() instanceof net.fortuna.ical4j.model.DateTime);
                                                dtendStr = formatIcal4jDate(dtEndProperty.getDate(), isAllDayEnd);
                                        }
                                        // Ignore invalid events (e.g., dtstartStr is "0001-12-30" or empty)
                                        if (dtstartStr == null || dtstartStr.isEmpty()
                                                        || dtstartStr.equals("0001-12-30")) {
                                                continue;
                                        }
                                        summary = summary.replace("\"", "\\\"");
                                        description = description.replace("\"", "\\\"");
                                        location = location.replace("\"", "\\\"");
                                        eventJsons.add(String.format(
                                                        "{\"uid\":\"%s\",\"dtstart\":\"%s\",\"dtend\":\"%s\",\"summary\":\"%s\",\"description\":\"%s\",\"location\":\"%s\",\"rrule\":\"%s\"}",
                                                        uid, dtstartStr, dtendStr, summary, description, location,
                                                        rruleString));
                                }
                        } else {
                                String dtstartStr = dtStartProperty != null
                                                ? formatIcal4jDate(dtStartProperty.getDate(), isAllDayEvent)
                                                : "";
                                String dtendStr = "";
                                if (dtEndProperty != null) {
                                        boolean isAllDayEnd = !(dtEndProperty
                                                        .getDate() instanceof net.fortuna.ical4j.model.DateTime);
                                        dtendStr = formatIcal4jDate(dtEndProperty.getDate(), isAllDayEnd);
                                }
                                summary = summary.replace("\"", "\\\"");
                                description = description.replace("\"", "\\\"");
                                location = location.replace("\"", "\\\"");
                                // Ignore invalid events (e.g., dtstartStr is "0001-12-30" or empty)
                                if (dtstartStr == null || dtstartStr.isEmpty() || dtstartStr.equals("0001-12-30")) {
                                        continue;
                                }
                                eventJsons.add(String.format(
                                                "{\"uid\":\"%s\",\"dtstart\":\"%s\",\"dtend\":\"%s\",\"summary\":\"%s\",\"description\":\"%s\",\"location\":\"%s\",\"rrule\":\"%s\"}",
                                                uid, dtstartStr, dtendStr, summary, description, location,
                                                rruleString));
                        }
                }
                return eventJsons;
        }

        // formatICalDateTime and formatICalDateTimeExtended are now removed / replaced
        // by formatIcal4jDate helper and ical4j direct usage.

        /**
         * Converts a local date and time to iCalendar format
         *
         * @param date The date in YYYY-mm-dd format
         * @param time The time in HH:mm format
         * @return The date and time in iCalendar format (YYYYMMDDTHHmmssZ)
         */
        private String toIcalFormat(String date, String time) {
                if (date == null || date.isEmpty()) {
                        return null;
                }

                LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                LocalDateTime localDateTime = localDate.atTime(0, 0);

                if (time != null && !time.isEmpty()) {
                        if (time.length() == 4) {
                                localDateTime = localDate.atTime(
                                                Integer.parseInt(time.substring(0, 2)),
                                                Integer.parseInt(time.substring(2, 4)));
                        } else {
                                throw new IllegalArgumentException("Time must be in HHmm format");
                        }
                }

                ZonedDateTime zonedDateTime = localDateTime.atZone(ZoneId.systemDefault());
                return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                                .withZone(ZoneId.of("UTC"))
                                .format(zonedDateTime);
        }

        /**
         * Creates a new calendar event
         *
         * @param summary   The summary of the event
         * @param date      The date of the event in YYYY-MM-DD format
         * @param startTime The start time of the event in HHmm format
         * @param endTime   The end time of the event in HHmm format
         * @return The location of the created event
         * @throws IOException
         * @throws URISyntaxException
         */
        @Tool(name = "createCalendarEvent", description = "Creates a new calendar event")
        public String createCalendarEvent(String summary, String date, String startTime, String endTime)
                        throws IOException, URISyntaxException {
                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                                AuthScope.ANY,
                                new UsernamePasswordCredentials(username, password));

                try (CloseableHttpClient httpclient = HttpClients.custom()
                                .setDefaultCredentialsProvider(credsProvider)
                                .build()) {

                        String startDateTime = toIcalFormat(date, startTime);
                        String endDateTime = toIcalFormat(date, endTime);

                        // Create iCalendar content
                        String icalContent = "BEGIN:VCALENDAR\n" +
                                        "VERSION:2.0\n" +
                                        "PRODID:-//Fastmail//Fastmail Caldav Client//EN\n" +
                                        "BEGIN:VEVENT\n" +
                                        "UID:" + java.util.UUID.randomUUID().toString() + "@fastmail.com\n" +
                                        "DTSTAMP:"
                                        + java.time.ZonedDateTime.now()
                                                        .format(java.time.format.DateTimeFormatter
                                                                        .ofPattern("yyyyMMdd'T'HHmmss'Z'"))
                                        + "\n" +
                                        "DTSTART:" + startDateTime + "\n" +
                                        "DTEND:" + endDateTime + "\n" +
                                        "SUMMARY:" + summary + "\n" +
                                        "END:VEVENT\n" +
                                        "END:VCALENDAR";

                        // Generate unique event URL
                        String eventUid = java.util.UUID.randomUUID().toString();
                        String eventPath = calendarPath;
                        if (!eventPath.endsWith("/")) {
                                eventPath += "/";
                        }
                        eventPath += eventUid + ".ics";

                        URI uri = new URI(caldavUrl + eventPath);
                        HttpPut put = new HttpPut(uri);
                        put.setHeader("Content-Type", "text/calendar; charset=utf-8");
                        put.setEntity(new StringEntity(icalContent, "UTF-8"));

                        try (CloseableHttpResponse response = httpclient.execute(put)) {
                                int statusCode = response.getStatusLine().getStatusCode();
                                if (statusCode >= 200 && statusCode < 300) {
                                        // Event created successfully
                                        return uri.toString();
                                } else {
                                        // Handle error
                                        String responseBody = EntityUtils.toString(response.getEntity());
                                        throw new IOException(
                                                        "Failed to create event: " + statusCode + " - " + responseBody);
                                }
                        }
                }
        }

        /**
         * Updates an existing calendar event
         *
         * @param eventUrl  The URL of the event to update
         * @param summary   The new summary of the event
         * @param date      The date of the event in YYYY-MM-DD format
         * @param startTime The new start time of the event in HHmm format
         * @param endTime   The new end time of the event in HHmm format
         * @return True if the event was updated successfully, false otherwise
         * @throws IOException
         * @throws URISyntaxException
         */
        @Tool(name = "updateCalendarEvent", description = "Updates an existing calendar event")
        public boolean updateCalendarEvent(String eventUrl, String summary, String date, String startTime,
                        String endTime)
                        throws IOException, URISyntaxException {
                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                                AuthScope.ANY,
                                new UsernamePasswordCredentials(username, password));

                try (CloseableHttpClient httpclient = HttpClients.custom()
                                .setDefaultCredentialsProvider(credsProvider)
                                .build()) {

                        String startDateTime = toIcalFormat(date, startTime);
                        String endDateTime = toIcalFormat(date, endTime);

                        // Create iCalendar content
                        String icalContent = "BEGIN:VCALENDAR\n" +
                                        "VERSION:2.0\n" +
                                        "PRODID:-//Fastmail//Fastmail Caldav Client//EN\n" +
                                        "BEGIN:VEVENT\n" +
                                        "DTSTAMP:"
                                        + java.time.ZonedDateTime.now()
                                                        .format(java.time.format.DateTimeFormatter
                                                                        .ofPattern("yyyyMMdd'T'HHmmss'Z'"))
                                        + "\n" +
                                        "DTSTART:" + startDateTime + "\n" +
                                        "DTEND:" + endDateTime + "\n" +
                                        "SUMMARY:" + summary + "\n" +
                                        "END:VEVENT\n" +
                                        "END:VCALENDAR";

                        // Update the event
                        HttpPut put = new HttpPut(eventUrl);
                        put.setHeader("Content-Type", "text/calendar; charset=utf-8");
                        put.setEntity(new StringEntity(icalContent, "UTF-8"));

                        try (CloseableHttpResponse response = httpclient.execute(put)) {
                                int statusCode = response.getStatusLine().getStatusCode();
                                return statusCode >= 200 && statusCode < 300;
                        }
                }
        }

        /**
         * Deletes a calendar event by UID.
         *
         * @param uid The UID of the event to delete
         * @return True if the event was deleted successfully, false otherwise
         * @throws IOException
         * @throws URISyntaxException
         */
        @Tool(name = "deleteCalendarEvent", description = "Deletes a calendar event by UID")
        public boolean deleteCalendarEvent(String uid) throws IOException, URISyntaxException {
                // Find the event URL by UID
                String eventUrl = findEventUrlByUid(uid);
                if (eventUrl == null) {
                        System.err.println("Event with UID " + uid + " not found.");
                        return false;
                }

                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                                AuthScope.ANY,
                                new UsernamePasswordCredentials(username, password));

                try (CloseableHttpClient httpclient = HttpClients.custom()
                                .setDefaultCredentialsProvider(credsProvider)
                                .build()) {

                        org.apache.http.client.methods.HttpDelete delete = new org.apache.http.client.methods.HttpDelete(
                                        eventUrl);
                        try (CloseableHttpResponse response = httpclient.execute(delete)) {
                                int statusCode = response.getStatusLine().getStatusCode();
                                return statusCode >= 200 && statusCode < 300;
                        }
                }
        }

        /**
         * Finds the event URL by UID.
         * 
         * @param uid The UID of the event
         * @return The full URL to the event, or null if not found
         */
        private String findEventUrlByUid(String uid) throws IOException {
                // This method assumes the event URL contains the UID (as is typical for
                // Fastmail/CalDAV)
                // You may need to adjust this logic if your server uses a different pattern.
                // We'll search for the event and extract its URL from the CalDAV REPORT
                // response.
                try {
                        List<String> eventUrls = new ArrayList<>();
                        CredentialsProvider credsProvider = new BasicCredentialsProvider();
                        credsProvider.setCredentials(
                                        AuthScope.ANY,
                                        new UsernamePasswordCredentials(username, password));
                        try (CloseableHttpClient httpclient = HttpClients.custom()
                                        .setDefaultCredentialsProvider(credsProvider)
                                        .build()) {

                                URI uri = new URI(caldavUrl + calendarPath);
                                String reportXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                                                "<c:calendar-query xmlns:c=\"urn:ietf:params:xml:ns:caldav\">\n" +
                                                "  <d:prop xmlns:d=\"DAV:\">\n" +
                                                "    <d:getetag />\n" +
                                                "    <c:calendar-data />\n" +
                                                "  </d:prop>\n" +
                                                "  <c:filter>\n" +
                                                "    <c:comp-filter name=\"VCALENDAR\">\n" +
                                                "      <c:comp-filter name=\"VEVENT\">\n" +
                                                "        <c:prop-filter name=\"UID\">\n" +
                                                "          <c:text-match collation=\"i;ascii-casemap\">" + uid
                                                + "</c:text-match>\n" +
                                                "        </c:prop-filter>\n" +
                                                "      </c:comp-filter>\n" +
                                                "    </c:comp-filter>\n" +
                                                "  </c:filter>\n" +
                                                "</c:calendar-query>";
                                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory
                                                .newInstance();
                                factory.setNamespaceAware(true);
                                org.w3c.dom.Document doc = factory.newDocumentBuilder()
                                                .parse(new java.io.ByteArrayInputStream(
                                                                reportXml.getBytes(
                                                                                java.nio.charset.StandardCharsets.UTF_8)));
                                org.w3c.dom.Element reportElement = doc.getDocumentElement();

                                org.apache.jackrabbit.webdav.version.report.ReportInfo reportInfo = new org.apache.jackrabbit.webdav.version.report.ReportInfo(
                                                reportElement, 1);
                                HttpReport report = new HttpReport(uri.toString(), reportInfo);
                                report.addHeader("Depth", "infinity");
                                report.setHeader("Content-Type", "application/xml");
                                report.setEntity(new StringEntity(reportXml, "UTF-8"));
                                try (CloseableHttpResponse response = httpclient.execute(report)) {
                                        String responseBody = EntityUtils.toString(response.getEntity());
                                        // Parse the XML response and extract the event URL
                                        javax.xml.parsers.DocumentBuilderFactory respFactory = javax.xml.parsers.DocumentBuilderFactory
                                                        .newInstance();
                                        respFactory.setNamespaceAware(true);
                                        org.w3c.dom.Document respDoc = respFactory.newDocumentBuilder()
                                                        .parse(new java.io.ByteArrayInputStream(
                                                                        responseBody.getBytes(
                                                                                        java.nio.charset.StandardCharsets.UTF_8)));
                                        org.w3c.dom.NodeList responses = respDoc.getElementsByTagNameNS("DAV:",
                                                        "response");
                                        for (int i = 0; i < responses.getLength(); i++) {
                                                org.w3c.dom.Element responseElem = (org.w3c.dom.Element) responses
                                                                .item(i);
                                                org.w3c.dom.NodeList hrefs = responseElem.getElementsByTagNameNS("DAV:",
                                                                "href");
                                                if (hrefs.getLength() > 0) {
                                                        String href = hrefs.item(0).getTextContent();
                                                        // Build the full URL if needed
                                                        if (!href.startsWith("http")) {
                                                                // Relative path, prepend server base
                                                                String base = caldavUrl.endsWith("/")
                                                                                ? caldavUrl.substring(0,
                                                                                                caldavUrl.length() - 1)
                                                                                : caldavUrl;
                                                                href = base + href;
                                                        }
                                                        eventUrls.add(href);
                                                }
                                        }
                                }
                        }
                        if (!eventUrls.isEmpty()) {
                                return eventUrls.get(0);
                        }
                } catch (Exception e) {
                        System.err.println("Error finding event URL by UID: " + e.getMessage());
                }
                return null;
        }
}
