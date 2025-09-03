package org.acme;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class GreetingResourceAccessLogTest {

  private static final Path ACCESS_LOG_PATH = Paths.get("target/access-log.log");

  @BeforeEach
  public void clearAccessLog() throws Exception {
    // Clear the access log before each test
    if (Files.exists(ACCESS_LOG_PATH)) {
      Files.writeString(ACCESS_LOG_PATH, "");
    }
  }

  @Test
  public void testSyncEndpoint_ShouldHaveTraceIdAndSpanIdInAccessLog() throws Exception {
    System.out.println("\n=== Testing SYNC Endpoint - Should have traceId and spanId in access log ===");

    // Make request to sync endpoint
    Response response = given()
        .when()
        .get("/hello/sync")
        .then()
        .statusCode(200)
        .contentType("application/json")
        .body("message", equalTo("Hello from Sync"))
        .extract()
        .response();

    System.out.println("Sync Response: " + response.getBody().asString());

    // Wait for log to be written
    Thread.sleep(500);

    // Read and check access log
    String accessLogEntry = getLastAccessLogEntry();
    System.out.println("Access Log Entry for SYNC: " + accessLogEntry);

    // Verify the access log entry exists
    assertNotNull(accessLogEntry, "Access log entry should exist");
    assertTrue(accessLogEntry.contains("/hello/sync"), "Access log should contain the sync endpoint path");

    // Extract actual values from log
    String traceIdValue = extractValueFromLog(accessLogEntry, "traceId=");
    String spanIdValue = extractValueFromLog(accessLogEntry, "spanId=");

    System.out.println("TraceId in access log: " + traceIdValue);
    System.out.println("SpanId in access log: " + spanIdValue);

    // Check if MDC values are valid (not null, empty, or "-")
    boolean hasValidTraceId = isValidMDCValue(traceIdValue);
    boolean hasValidSpanId = isValidMDCValue(spanIdValue);

    // For SYNC endpoint, we expect traceId and spanId to be present
    assertTrue(hasValidTraceId, "SYNC endpoint should have valid traceId in access log");
    assertTrue(hasValidSpanId, "SYNC endpoint should have valid spanId in access log");

    System.out.println("✅ SYNC endpoint has valid MDC values in access log");
  }

  @Test
  public void testAsyncEndpoint_ShouldNotHaveTraceIdAndSpanIdInAccessLog() throws Exception {
    System.out.println("\n=== Testing ASYNC Endpoint - MDC Issue Demonstration ===");

    // Make request to async endpoint
    Response response = given()
        .when()
        .get("/hello/async")
        .then()
        .statusCode(200)
        .contentType("application/json")
        .body("message", equalTo("Hello from Async"))
        .extract()
        .response();

    System.out.println("Async Response: " + response.getBody().asString());

    // Wait for log to be written (longer for async)
    Thread.sleep(500);

    // Read and check access log
    String accessLogEntry = getLastAccessLogEntry();
    System.out.println("Access Log Entry for ASYNC: " + accessLogEntry);

    // Verify the access log entry exists
    assertNotNull(accessLogEntry, "Access log entry should exist");
    assertTrue(accessLogEntry.contains("/hello/async"), "Access log should contain the async endpoint path");

    // Extract actual values from log
    String traceIdValue = extractValueFromLog(accessLogEntry, "traceId=");
    String spanIdValue = extractValueFromLog(accessLogEntry, "spanId=");

    System.out.println("TraceId in access log: " + traceIdValue);
    System.out.println("SpanId in access log: " + spanIdValue);

    // Check if MDC values are valid
    boolean hasValidTraceId = isValidMDCValue(traceIdValue);
    boolean hasValidSpanId = isValidMDCValue(spanIdValue);

    // For ASYNC endpoint with Uni<Response>, MDC is lost
    // The access log shows "-" when MDC value is null/empty
    assertFalse(hasValidTraceId,
        "ASYNC endpoint issue confirmed: traceId is missing (shows as '-') in access log");
    assertFalse(hasValidSpanId,
        "ASYNC endpoint issue confirmed: spanId is missing (shows as '-') in access log");

    System.out.println("⚠️  ISSUE CONFIRMED: Async endpoint (Uni<Response>) loses MDC context in access log");
    System.out.println("   Access log shows 'traceId=-' and 'spanId=-' indicating null MDC values");
  }

  @Test
  public void testBothEndpointsComparison() throws Exception {
    System.out.println("\n=== Comparing SYNC vs ASYNC Endpoints MDC in Access Log ===");

    // Clear log first
    if (Files.exists(ACCESS_LOG_PATH)) {
      Files.writeString(ACCESS_LOG_PATH, "");
    }

    // Call sync endpoint
    given()
        .when()
        .get("/hello/sync")
        .then()
        .statusCode(200);

    Thread.sleep(200);

    // Call async endpoint
    given()
        .when()
        .get("/hello/async")
        .then()
        .statusCode(200);

    Thread.sleep(200);

    // Read all access log entries
    if (Files.exists(ACCESS_LOG_PATH)) {
      List<String> allLines = Files.readAllLines(ACCESS_LOG_PATH);

      System.out.println("\nAll Access Log Entries:");

      boolean syncHasValidMDC = false;
      boolean asyncHasValidMDC = false;

      for (int i = 0; i < allLines.size(); i++) {
        String line = allLines.get(i);
        System.out.println("Line " + (i + 1) + ": " + line);

        if (line.contains("/hello/sync")) {
          String syncTraceId = extractValueFromLog(line, "traceId=");
          String syncSpanId = extractValueFromLog(line, "spanId=");

          syncHasValidMDC = isValidMDCValue(syncTraceId) && isValidMDCValue(syncSpanId);

          if (syncHasValidMDC) {
            System.out.println("  → SYNC: ✅ Has valid traceId=" + syncTraceId + ", spanId=" + syncSpanId);
          } else {
            System.out.println("  → SYNC: ❌ Missing MDC (traceId=" + syncTraceId + ", spanId=" + syncSpanId + ")");
          }
        }

        if (line.contains("/hello/async")) {
          String asyncTraceId = extractValueFromLog(line, "traceId=");
          String asyncSpanId = extractValueFromLog(line, "spanId=");

          asyncHasValidMDC = isValidMDCValue(asyncTraceId) && isValidMDCValue(asyncSpanId);

          if (asyncHasValidMDC) {
            System.out.println("  → ASYNC: ✅ Has valid traceId=" + asyncTraceId + ", spanId=" + asyncSpanId);
          } else {
            System.out.println("  → ASYNC: ❌ Missing MDC (traceId=" + asyncTraceId + ", spanId=" + asyncSpanId + ")");
            System.out.println("            Note: '-' indicates null/empty MDC value in access log");
          }
        }
      }

      System.out.println("\n📊 Summary:");
      System.out
          .println("- SYNC endpoint:  " + (syncHasValidMDC ? "✅ MDC context preserved" : "❌ MDC context missing"));
      System.out.println(
          "- ASYNC endpoint: " + (asyncHasValidMDC ? "✅ MDC context preserved" : "❌ MDC context lost (shows as '-')"));

      if (syncHasValidMDC && !asyncHasValidMDC) {
        System.out.println("\n✅ Test successfully demonstrates the issue:");
        System.out.println("   Synchronous endpoints preserve MDC context in access logs");
        System.out.println("   Asynchronous endpoints (Uni<Response>) lose MDC context");
        System.out.println("   This is a known issue with reactive context propagation");
      }
    } else {
      System.out.println("❌ Access log file not found at: " + ACCESS_LOG_PATH);
    }
  }

  /**
   * Check if MDC value is valid (not null, empty, or "-")
   */
  private boolean isValidMDCValue(String value) {
    return value != null &&
        !value.isEmpty() &&
        !value.equals("-") &&
        !value.equals("null") &&
        !value.equals("") &&
        value.length() > 1; // A valid trace/span ID should be more than 1 character
  }

  private String getLastAccessLogEntry() throws Exception {
    if (!Files.exists(ACCESS_LOG_PATH)) {
      System.out.println("Access log file does not exist at: " + ACCESS_LOG_PATH);
      return null;
    }

    List<String> lines = Files.readAllLines(ACCESS_LOG_PATH);
    if (lines.isEmpty()) {
      System.out.println("Access log file is empty");
      return null;
    }

    // Get the last non-empty line
    for (int i = lines.size() - 1; i >= 0; i--) {
      String line = lines.get(i).trim();
      if (!line.isEmpty()) {
        return line;
      }
    }
    return null;
  }

  private String extractValueFromLog(String logLine, String key) {
    if (logLine == null || !logLine.contains(key)) {
      return null;
    }

    int startIndex = logLine.indexOf(key) + key.length();
    if (startIndex >= logLine.length()) {
      return "";
    }

    // Find the next space or end of line
    int endIndex = logLine.indexOf(" ", startIndex);
    if (endIndex == -1) {
      endIndex = logLine.length();
    }

    String value = logLine.substring(startIndex, endIndex).trim();

    // Handle case where value might have trailing characters
    if (value.endsWith(",") || value.endsWith(";")) {
      value = value.substring(0, value.length() - 1);
    }

    return value;
  }
}