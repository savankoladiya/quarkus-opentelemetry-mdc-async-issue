# OpenTelemetry MDC Issue Reproducer

This project demonstrates an issue where OpenTelemetry's MDC (Mapped Diagnostic Context) values (`traceId` and `spanId`) are lost in access logs when using asynchronous endpoints with `Uni<Response>` in Quarkus.

## 🐛 Issue Description

When using Quarkus with OpenTelemetry:
- **Synchronous endpoints** (`Response`): MDC context (`traceId`, `spanId`) is correctly populated in access logs ✅
- **Asynchronous endpoints** (`Uni<Response>`): MDC context is lost and shows as `null` in access logs ❌

## 📋 Prerequisites

- Java 21
- Maven 3.8+
- Quarkus 3.20.2.SP1-redhat-00003

## 🏗️ Project Structure

```
ot-mdc-issue/
├── pom.xml                              # Maven configuration with Quarkus 3.26.1
├── src/main/java/org/acme/
│   └── GreetingResource.java           # REST endpoints (sync and async)
├── src/main/resources/
│   └── application.properties          # Quarkus and OpenTelemetry configuration
└── src/test/java/org/acme/
    └── GreetingResourceAccessLogTest.java  # Test cases to verify the issue
```

## 🔧 Configuration

### Important Note for Running Tests

To see access logs in file during test execution, ensure these properties are enabled in `application.properties`:

```properties
# Access log file configuration - REQUIRED for tests
quarkus.http.access-log.log-to-file=true
quarkus.http.access-log.base-file-name=access-log
quarkus.http.access-log.log-directory=target
```

The access logs will be written to: `target/access-log.log`

## 🚀 Running the Reproducer

### Option 1: Run Tests (Recommended)

```bash
# Clean and run all tests
./mvnw clean test

# Run specific test class
./mvnw test -Dtest=GreetingResourceAccessLogTest

# Run individual test methods
./mvnw test -Dtest=GreetingResourceAccessLogTest#testSyncEndpoint_ShouldHaveTraceIdAndSpanIdInAccessLog
./mvnw test -Dtest=GreetingResourceAccessLogTest#testAsyncEndpoint_ShouldNotHaveTraceIdAndSpanIdInAccessLog
```

### Option 2: Run in Dev Mode

```bash
# Start application in dev mode
./mvnw quarkus:dev

# In another terminal, test the endpoints:

# Test sync endpoint
curl http://localhost:8080/hello/sync

# Test async endpoint
curl http://localhost:8080/hello/async

# Check the access log file
cat target/access-log.log
```

## 📊 Test Cases

### 1. `testSyncEndpoint_ShouldHaveTraceIdAndSpanIdInAccessLog`
- **Endpoint**: `/hello/sync` (returns `Response`)
- **Expected**: Access log contains valid `traceId` and `spanId`
- **Actual**: ✅ Works correctly

### 2. `testAsyncEndpoint_ShouldNotHaveTraceIdAndSpanIdInAccessLog`
- **Endpoint**: `/hello/async` (returns `Uni<Response>`)
- **Expected**: Access log should contain valid `traceId` and `spanId`
- **Actual**: ❌ Shows `traceId=null spanId=null` (demonstrates the issue)

### 3. `testBothEndpointsComparison`
- Compares both endpoints side by side
- Shows the difference in MDC behavior

## 🔍 Expected vs Actual Behavior

### Console Output During Tests

#### Sync Endpoint (Working ✅):
```
14:11:10 INFO  [or.ac.GreetingResource] (executor-thread-1) traceId=abc123 spanId=def456 - SYNC endpoint - traceId: abc123, spanId: def456
```

#### Async Endpoint (Issue ❌):
```
14:11:10 INFO  [or.ac.GreetingResource] (vert.x-eventloop-thread-0) traceId= spanId= - ASYNC endpoint (inside) - traceId: null, spanId: null
```

### Access Log File (`target/access-log.log`)

#### Expected for Both Endpoints:
```
127.0.0.1 GET /hello/sync HTTP/1.1 200 5ms traceId=abc123 spanId=def456
127.0.0.1 GET /hello/async HTTP/1.1 200 52ms traceId=xyz789 spanId=uvw012
```

#### Actual:
```
127.0.0.1 GET /hello/sync HTTP/1.1 200 5ms traceId=abc123 spanId=def456     ✅
127.0.0.1 GET /hello/async HTTP/1.1 200 52ms traceId=null spanId=null       ❌
```

## 📝 Key Files

### GreetingResource.java
```java
@Path("/hello")
public class GreetingResource {
    // Synchronous - MDC works ✅
    @GET
    @Path("/sync")
    public Response helloSync() {
        LOG.infof("SYNC - traceId: %s, spanId: %s", 
            MDC.get("traceId"), MDC.get("spanId"));
        return Response.ok(Map.of("message", "Hello from Sync")).build();
    }

    // Asynchronous - MDC lost ❌
    @GET
    @Path("/async")
    public Uni<Response> helloAsync() {
        return Uni.createFrom().item(() -> {
            LOG.infof("ASYNC - traceId: %s, spanId: %s", 
                MDC.get("traceId"), MDC.get("spanId"));
            return Response.ok(Map.of("message", "Hello from Async")).build();
        }).onItem().delayIt().by(Duration.ofMillis(50));
    }
}
```

### application.properties
```properties
# OpenTelemetry
quarkus.otel.enabled=true
quarkus.otel.traces.enabled=true
quarkus.otel.traces.exporter=none
quarkus.otel.traces.sampler=always_on

# Access log pattern with MDC
quarkus.http.access-log.enabled=true
quarkus.http.access-log.pattern=%{REMOTE_HOST} %{REQUEST_LINE} %{RESPONSE_TIME}ms traceId=%{X,traceId} spanId=%{X,spanId}

# Enable file logging for access logs
quarkus.http.access-log.log-to-file=true
quarkus.http.access-log.base-file-name=access-log
quarkus.http.access-log.log-directory=target
```

## 🎯 Summary

This reproducer clearly demonstrates that:

1. **Synchronous endpoints** preserve MDC context in access logs
2. **Asynchronous endpoints** using `Uni<Response>` lose MDC context
3. The issue affects access log MDC values specifically when using reactive programming with Mutiny
