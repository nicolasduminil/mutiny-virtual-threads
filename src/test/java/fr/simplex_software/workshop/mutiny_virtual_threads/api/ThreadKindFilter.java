package fr.simplex_software.workshop.mutiny_virtual_threads.api;

import io.vertx.core.*;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.ext.*;

@Provider
public class ThreadKindFilter implements ContainerResponseFilter
{
  static final String HEADER = "X-Test-Thread-Kind";

  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response)
  {
    response.getHeaders().putSingle(HEADER, current());
  }

  static String current()
  {
    if (Thread.currentThread().isVirtual())
      return "virtual";
    return Context.isOnEventLoopThread() ? "event-loop" : "worker";
  }
}
