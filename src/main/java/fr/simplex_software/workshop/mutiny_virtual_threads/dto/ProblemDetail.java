package fr.simplex_software.workshop.mutiny_virtual_threads.dto;

import jakarta.ws.rs.core.*;

import java.util.*;

public record ProblemDetail(String type, String title, int status, String detail, String instance,
                            Map<String, String> errors)
{
  public static final String MEDIA_TYPE = "application/problem+json";

  public static Response response(int status, String title, String detail, String instance,
                                  Map<String, String> errors)
  {
    return Response.status(status)
      .type(MEDIA_TYPE)
      .entity(new ProblemDetail("about:blank", title, status, detail, instance, errors))
      .build();
  }
}
