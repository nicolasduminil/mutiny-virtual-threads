package fr.simplex_software.workshop.mutiny_virtual_threads.api;

import fr.simplex_software.workshop.mutiny_virtual_threads.dto.*;
import fr.simplex_software.workshop.mutiny_virtual_threads.service.*;
import io.smallrye.common.annotation.*;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;

@Path("/api/v1/quote/await")
public class AwaitQuoteResource
{
  private final QuoteService service;

  public AwaitQuoteResource(QuoteService service)
  {
    this.service = service;
  }

  @GET
  @Path("/{symbol}")
  @RunOnVirtualThread
  public Quote quote(@PathParam("symbol") @Pattern(regexp = QuoteSymbol.PATTERN) String symbol)
  {
    return service.awaitOnVirtualThread(symbol);
  }
}
