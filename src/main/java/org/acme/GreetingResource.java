package org.acme;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import java.time.Duration;
import java.util.Map;

@Path("/hello")
public class GreetingResource {

  private static final Logger LOG = Logger.getLogger(GreetingResource.class);

  /**
   * Synchronous endpoint - returns Response directly
   */
  @GET
  @Path("/sync")
  @Produces(MediaType.APPLICATION_JSON)
  public Response helloSync() {
    // Log to check MDC values
    LOG.infof("SYNC endpoint - traceId: %s, spanId: %s",
        MDC.get("traceId"),
        MDC.get("spanId"));

    Map<String, Object> response = Map.of(
        "message", "Hello from Sync");

    return Response.ok(response).build();
  }

  /**
   * Asynchronous endpoint - returns Uni<Response>
   */
  @GET
  @Path("/async")
  @Produces(MediaType.APPLICATION_JSON)
  public Uni<Response> helloAsync() {
    // Log to check MDC values before async operation
    LOG.infof("ASYNC endpoint (before) - traceId: %s, spanId: %s",
        MDC.get("traceId"),
        MDC.get("spanId"));

    return Uni.createFrom().item(() -> {
      // Log inside async operation
      LOG.infof("ASYNC endpoint (inside) - traceId: %s, spanId: %s",
          MDC.get("traceId"),
          MDC.get("spanId"));

      Map<String, Object> response = Map.of(
          "message", "Hello from Async");

      return Response.ok(response).build();
    })
        .onItem().delayIt().by(Duration.ofMillis(50)); // Small delay to simulate async work
  }
}