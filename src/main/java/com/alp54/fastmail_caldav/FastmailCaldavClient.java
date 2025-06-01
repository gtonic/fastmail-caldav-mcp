package com.alp54.fastmail_caldav;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
                                timeRangeXml = "<c:comp-filter name=\"VEVENT\"><c:time-range start=\"" + start
                                                + "\" /></c:comp-filter>";
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

                        String reportXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                                        "<c:calendar-query xmlns:c=\"urn:ietf:params:xml:ns:caldav\">\n" +
                                        "  <d:prop xmlns:d=\"DAV:\">\n" +
                                        "    <d:getetag />\n" +
                                        "    <c:calendar-data />\n" +
                                        "  </d:prop>\n" +
                                        "  <c:filter>\n" +
                                        "    <c:comp-filter name=\"VCALENDAR\">" + timeRangeXml + textMatchXml
                                        + "</c:comp-filter>\n" +
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
                        report.addHeader("Depth", "1");
                        report.setHeader("Content-Type", "application/xml");
                        report.setEntity(new StringEntity(reportXml, "UTF-8"));
                        try (CloseableHttpResponse response = httpclient.execute(report)) {
                                String responseBody = EntityUtils.toString(response.getEntity());
                                // For simplicity, just add the raw XML response to the list
                                events.add(responseBody);
                        }
                }
                return events;
        }

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
                        localDateTime = localDate.atTime(
                                        Integer.parseInt(time.substring(0, 2)),
                                        Integer.parseInt(time.substring(3, 5)));
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
         * @param startDate The start date of the event in YYYYMMDDTHHmmssZ format
         * @param endDate   The end date of the event in YYYYMMDDTHHmmssZ format
         * @return The location of the created event
         * @throws IOException
         * @throws URISyntaxException
         */
        @Tool(name = "createCalendarEvent", description = "Creates a new calendar event")
        public String createCalendarEvent(String summary, String startDate, String endDate)
                        throws IOException, URISyntaxException {
                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                                AuthScope.ANY,
                                new UsernamePasswordCredentials(username, password));

                try (CloseableHttpClient httpclient = HttpClients.custom()
                                .setDefaultCredentialsProvider(credsProvider)
                                .build()) {

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
                                        "DTSTART:" + startDate + "\n" +
                                        "DTEND:" + endDate + "\n" +
                                        "SUMMARY:" + summary + "\n" +
                                        "END:VEVENT\n" +
                                        "END:VCALENDAR";

                        // Create the event
                        URI uri = new URI(caldavUrl + calendarPath);
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
         * @param startDate The new start date of the event in YYYYMMDDTHHmmssZ format
         * @param endDate   The new end date of the event in YYYYMMDDTHHmmssZ format
         * @return True if the event was updated successfully, false otherwise
         * @throws IOException
         * @throws URISyntaxException
         */
        @Tool(name = "updateCalendarEvent", description = "Updates an existing calendar event")
        public boolean updateCalendarEvent(String eventUrl, String summary, String startDate, String endDate)
                        throws IOException, URISyntaxException {
                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(
                                AuthScope.ANY,
                                new UsernamePasswordCredentials(username, password));

                try (CloseableHttpClient httpclient = HttpClients.custom()
                                .setDefaultCredentialsProvider(credsProvider)
                                .build()) {

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
                                        "DTSTART:" + startDate + "\n" +
                                        "DTEND:" + endDate + "\n" +
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
}
