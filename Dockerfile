# Use a lightweight JRE image to run the app
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the already built jar from local context
COPY target/fastmail-caldav-mcp-1.0-SNAPSHOT.jar app.jar

# Expose port 8080 internally
EXPOSE 8080

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
