package fr.simplex_software.workshop.mutiny_virtual_threads.api;

import fr.simplex_software.workshop.mutiny_virtual_threads.dto.*;
import fr.simplex_software.workshop.mutiny_virtual_threads.service.*;
import io.smallrye.mutiny.*;
import jakarta.validation.constraints.*;
import jakarta.ws.rs.*;

@Path("/api/v1/quote")
public class OffloadQuoteResource
{
  private final QuoteService service;

  public OffloadQuoteResource(QuoteService service)
  {
    this.service = service;
  }

  @GET
  @Path("/offload-worker/{symbol}")
  public Uni<Quote> offloadToWorkerPool(@PathParam("symbol") @Pattern(regexp = QuoteSymbol.PATTERN) String symbol)
  {
    return service.offloadToWorkerPool(symbol);
  }

  @GET
  @Path("/offload-virtual/{symbol}")
  public Uni<Quote> offloadToVirtualThread(@PathParam("symbol") @Pattern(regexp = QuoteSymbol.PATTERN) String symbol)
  {
    return service.offloadToVirtualThread(symbol);
  }
}
