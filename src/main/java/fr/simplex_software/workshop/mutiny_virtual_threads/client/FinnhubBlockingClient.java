package fr.simplex_software.workshop.mutiny_virtual_threads.client;

import fr.simplex_software.workshop.mutiny_virtual_threads.dto.*;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.rest.client.inject.*;

@RegisterRestClient(configKey = "finnhub")
public interface FinnhubBlockingClient
{
  @GET
  @Path("/quote")
  FinnhubQuote quote(@QueryParam("symbol") String symbol, @QueryParam("token") String token);
}
