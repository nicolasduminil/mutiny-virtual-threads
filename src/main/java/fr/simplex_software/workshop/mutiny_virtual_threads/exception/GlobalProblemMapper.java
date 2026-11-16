package fr.simplex_software.workshop.mutiny_virtual_threads.exception;

import fr.simplex_software.workshop.mutiny_virtual_threads.dto.*;
import io.quarkus.logging.*;
import io.vertx.core.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;
import org.jboss.resteasy.reactive.*;

import java.io.*;
import java.util.concurrent.*;

@Provider
public class GlobalProblemMapper implements ExceptionMapper<Exception>
{
  private final UriInfo uriInfo;

  public GlobalProblemMapper(UriInfo uriInfo)
  {
    this.uriInfo = uriInfo;
  }

  @Override
  public Response toResponse(Exception ex)
  {
    return switch (ex)
    {
      case QuoteNotFoundException e ->
        problem(404, "Quote Not Found", "No quote exists for the requested symbol");
      case ClientWebApplicationException e -> upstreamFailure(e.getResponse().getStatus(), e);
      case WebApplicationException e ->
        problem(e.getResponse().getStatus(), e.getResponse().getStatusInfo().getReasonPhrase(),
          "The request could not be processed");
      case Exception e when causedBy(e, TimeoutException.class) ->
        problem(504, "Upstream Timeout", "The quote provider did not answer in time");
      // A connection failure takes any of these forms, depending on the client and on when it occurs.
      case Exception e when e instanceof ProcessingException || causedBy(e, IOException.class)
        || causedBy(e, VertxException.class) -> upstreamFailure(0, e);
      default ->
      {
        Log.error("Unexpected failure", ex);
        yield problem(500, "Internal Server Error", "An unexpected error occurred");
      }
    };
  }

  private Response upstreamFailure(int upstreamStatus, Exception failure)
  {
    Log.warnf("The quote provider failed with status %d", upstreamStatus);
    Log.debug("Cause of the quote provider failure", failure);
    return problem(502, "Upstream Failure", "The quote provider could not answer the request");
  }

  private Response problem(int status, String title, String detail)
  {
    return ProblemDetail.response(status, title, detail, uriInfo.getPath(), null);
  }

  private static boolean causedBy(Throwable failure, Class<? extends Throwable> type)
  {
    for (Throwable cause = failure; cause != null; cause = cause.getCause())
      if (type.isInstance(cause))
        return true;
    return false;
  }
}
