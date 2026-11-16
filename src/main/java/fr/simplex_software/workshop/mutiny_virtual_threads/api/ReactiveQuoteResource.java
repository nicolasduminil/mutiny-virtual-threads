package fr.simplex_software.workshop.mutiny_virtual_threads.api;

import fr.simplex_software.workshop.mutiny_virtual_threads.dto.*;
import fr.simplex_software.workshop.mutiny_virtual_threads.service.*;
import io.smallrye.mutiny.*;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;

@Path("/api/v1/quote/reactive")
public class ReactiveQuoteResource
{
  private final QuoteService service;

  public ReactiveQuoteResource(QuoteService service)
  {
    this.service = service;
  }

  @GET
  @Path("/{symbol}")
  public Uni<Quote> quote(@PathParam("symbol") @Pattern(regexp = QuoteSymbol.PATTERN) String symbol)
  {
    return service.fetchReactive(symbol);
  }
}
