package fr.simplex_software.workshop.mutiny_virtual_threads.exception;

import fr.simplex_software.workshop.mutiny_virtual_threads.dto.*;
import jakarta.validation.*;
import jakarta.ws.rs.core.*;
import jakarta.ws.rs.ext.*;

import java.util.*;
import java.util.stream.*;

@Provider
public class ValidationProblemMapper implements ExceptionMapper<ConstraintViolationException>
{
  private final UriInfo uriInfo;

  public ValidationProblemMapper(UriInfo uriInfo)
  {
    this.uriInfo = uriInfo;
  }

  @Override
  public Response toResponse(ConstraintViolationException ex)
  {
    Map<String, String> errors = ex.getConstraintViolations().stream()
      .collect(Collectors.toMap(ValidationProblemMapper::parameterName, ConstraintViolation::getMessage,
        (first, second) -> first));
    return ProblemDetail.response(400, "Invalid Request", "The request parameters are invalid",
      uriInfo.getPath(), errors);
  }

  private static String parameterName(ConstraintViolation<?> violation)
  {
    String path = violation.getPropertyPath().toString();
    return path.substring(path.lastIndexOf('.') + 1);
  }
}
