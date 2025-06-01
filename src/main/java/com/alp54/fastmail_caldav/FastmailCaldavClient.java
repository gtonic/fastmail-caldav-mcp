package com.alp54.fastmail_caldav;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
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
                return getCalendarEvents(null);
        }

        @Tool(name = "getCalendarEventsByDate", description = "Queries all events of the calendar, filtered by date (YYYY-mm-dd)")
        public List<String> getCalendarEvents(String date)
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
                        String reportXml = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                                        "<c:calendar-query xmlns:c=\"urn:ietf:params:xml:ns:caldav\">\n" +
                                        "  <d:prop xmlns:d=\"DAV:\">\n" +
                                        "    <d:getetag />\n" +
                                        "    <c:calendar-data />\n" +
                                        "  </d:prop>\n" +
                                        "  <c:filter>\n" +
                                        "    <c:comp-filter name=\"VCALENDAR\">" + timeRangeXml + "</c:comp-filter>\n" +
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
}
