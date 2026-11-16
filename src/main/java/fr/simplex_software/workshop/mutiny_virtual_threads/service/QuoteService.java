package fr.simplex_software.workshop.mutiny_virtual_threads.service;

import fr.simplex_software.workshop.mutiny_virtual_threads.client.*;
import fr.simplex_software.workshop.mutiny_virtual_threads.config.*;
import fr.simplex_software.workshop.mutiny_virtual_threads.dto.*;
import fr.simplex_software.workshop.mutiny_virtual_threads.exception.*;
import io.quarkus.virtual.threads.*;
import io.smallrye.mutiny.*;
import io.smallrye.mutiny.infrastructure.*;
import jakarta.enterprise.context.*;
import org.eclipse.microprofile.rest.client.inject.*;

import java.util.concurrent.*;

@ApplicationScoped
public class QuoteService
{
  private final FinnhubClient reactiveClient;
  private final FinnhubBlockingClient blockingClient;
  private final FinnhubConfig config;
  private final ExecutorService virtualThreads;

  public QuoteService(@RestClient FinnhubClient reactiveClient,
                      @RestClient FinnhubBlockingClient blockingClient,
                      FinnhubConfig config,
                      @VirtualThreads ExecutorService virtualThreads)
  {
    this.reactiveClient = reactiveClient;
    this.blockingClient = blockingClient;
    this.config = config;
    this.virtualThreads = virtualThreads;
  }

  public Uni<Quote> fetchReactive(String symbol)
  {
    return reactiveClient.quote(symbol, token()).map(quote -> toQuote(symbol, quote));
  }

  public Quote fetchBlocking(String symbol)
  {
    return toQuote(symbol, blockingClient.quote(symbol, token()));
  }

  public Uni<Quote> offloadToWorkerPool(String symbol)
  {
    return offload(symbol, Infrastructure.getDefaultWorkerPool());
  }

  public Uni<Quote> offloadToVirtualThread(String symbol)
  {
    return offload(symbol, virtualThreads);
  }

  public Quote awaitOnVirtualThread(String symbol)
  {
    return fetchReactive(symbol).await().indefinitely();
  }

  private Uni<Quote> offload(String symbol, Executor executor)
  {
    return Uni.createFrom().item(() -> fetchBlocking(symbol)).runSubscriptionOn(executor);
  }

  private String token()
  {
    return config.token().orElse("");
  }

  private static Quote toQuote(String symbol, FinnhubQuote quote)
  {
    if (quote.current().signum() == 0)
      throw new QuoteNotFoundException(symbol);
    return new Quote(symbol, quote.current(), quote.previousClose());
  }
}
