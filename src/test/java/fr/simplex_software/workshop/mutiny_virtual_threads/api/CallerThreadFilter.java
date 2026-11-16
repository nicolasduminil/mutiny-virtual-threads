package fr.simplex_software.workshop.mutiny_virtual_threads.api;

import jakarta.ws.rs.client.*;
import jakarta.ws.rs.ext.*;

@Provider
public class CallerThreadFilter implements ClientRequestFilter
{
  static final String HEADER = "X-Test-Caller-Thread";

  @Override
  public void filter(ClientRequestContext request)
  {
    request.getHeaders().putSingle(HEADER, ThreadKindFilter.current());
  }
}
