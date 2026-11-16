# mutiny-virtual-threads

This project compares two concurrency models of Java on one operation. It exposes the retrieval of a stock quote from the [Finnhub](https://finnhub.io) API through five HTTP endpoints, implemented with Mutiny, with virtual threads and with their combinations. The project tests them against one contract and measures them under load. It originates from the project [`reactive-messaging`](https://github.com/nicolasduminil/reactive-messaging), whose fourth stage it formerly was.

| Endpoint | Finnhub client | Thread serving the request | Thread waiting for Finnhub |
|---|---|---|---|
| `GET /api/v1/quote/reactive/{symbol}` | reactive | event loop | none |
| `GET /api/v1/quote/virtual/{symbol}` | blocking | virtual thread | the same virtual thread |
| `GET /api/v1/quote/offload-worker/{symbol}` | blocking | event loop | worker pool thread |
| `GET /api/v1/quote/offload-virtual/{symbol}` | blocking | event loop | virtual thread |
| `GET /api/v1/quote/await/{symbol}` | reactive | virtual thread | the same virtual thread |

## Running the application and the tests

The project requires JDK 21 and Maven 3.9 or later. The tests require nothing more, since they replace Finnhub with a WireMock server started within the test process. A call to the real service requires a free API key, obtained from the [Finnhub](https://finnhub.io) site, and the load comparison requires the HTTP benchmarking tool `wrk`.

The following commands clone the repository and execute the complete build, which compiles the code and runs the 59 tests described in the section [Testing](#testing).

```shell
git clone https://github.com/nicolasduminil/mutiny-virtual-threads.git
cd mutiny-virtual-threads
mvn verify
```

A single test class runs with the option `-Dtest`, for instance `mvn test -Dtest=QuoteResourceTest`. The application reads the API key from the environment variable `FINNHUB_TOKEN`, through the property `finnhub.token`. Once packaged, it starts on port 8080 and answers on the five endpoints, which share the prefix `/api/v1/quote/` and differ in the path segment listed in the table above.

```shell
mvn package -DskipTests
FINNHUB_TOKEN=<your-key> java -jar target/quarkus-app/quarkus-run.jar &
curl http://localhost:8080/api/v1/quote/reactive/AAPL
curl http://localhost:8080/api/v1/quote/virtual/AAPL
```

Each endpoint returns the same JSON document, for instance `{"previousClose":259.71,"price":261.74,"symbol":"AAPL"}`. In the absence of a valid key, Finnhub rejects the call and every endpoint answers with the status 502 defined by the error contract.

The load comparison executes the packaged application against a local stub, so it consumes no request of the Finnhub quota. The script `load/compare.sh` starts the stub and the application, measures each endpoint with `wrk`, and prints a summary grouped by number of connections. Its environment variables adjust the run, namely `DELAY_MS` for the latency of the stub, 200 ms by default, `DURATION` for the length of each measurement, 20 seconds by default, `CONNECTIONS` for the list of connection counts, and `MODELS` for the list of endpoints.

```shell
mvn package -DskipTests
load/compare.sh
DELAY_MS=50 DURATION=30s load/compare.sh
```

## The concurrency models

`QuoteService` holds all the implementations side by side.

```java
public Uni<Quote> fetchReactive(String symbol)
{
  return reactiveClient.quote(symbol, token())
      .map(quote -> toQuote(symbol, quote));
}

public Quote fetchBlocking(String symbol)
{
  return toQuote(symbol, blockingClient.quote(symbol, token()));
}

private Uni<Quote> offload(String symbol, Executor executor)
{
  return Uni.createFrom().item(() -> fetchBlocking(symbol))
      .runSubscriptionOn(executor);
}
```

| | Mutiny | `@RunOnVirtualThread` |
|---|---|---|
| Thread serving the request | an event loop, which must remain unblocked | one virtual thread per request |
| Code style | a composed pipeline, where each step is a callback | sequential statements with ordinary control flow |
| Main risk | a blocking call stalls every request on that event loop | a `synchronized` block around I/O pins the carrier thread on JDK 21 |

Failures are RFC 9457 Problem Details (`application/problem+json`), identical on all the endpoints.

| Situation | Status |
|---|---|
| Invalid symbol | 400, with `errors` naming the parameter |
| Zero price from Finnhub, treated as an unknown symbol | 404 |
| Finnhub answers with an error, or the connection fails | 502 |
| Finnhub exceeds the read timeout of 5 seconds | 504 |
| Unexpected failure | 500 |

## Load comparison

`load/compare.sh` measures the endpoints with `wrk` against a local stub with a fixed latency, because the rate limit of Finnhub excludes load tests against the real service. It raises the connection pool of the REST client, because the default pool hides the difference between the models.

One run with a latency of 200 ms and no errors, on a single 8 core machine that also hosted `wrk` and the stub. These figures are specific to that machine, and each cell gives the requests per second and the 99th percentile of latency.

| Connections | Ceiling | `reactive` | `virtual` | `offload-worker` | `offload-virtual` | `await` |
|---|---|---|---|---|---|---|
| 100 | 500 | 494, 210 ms | 494, 209 ms | 494, 218 ms | 494, 204 ms | 494, 205 ms |
| 500 | 2,500 | 2,472, 213 ms | 2,471, 213 ms | 984, 520 ms | 2,472, 212 ms | 2,472, 209 ms |
| 1,000 | 5,000 | 4,939, 217 ms | 4,920, 227 ms | 972, 1.27 s | 4,941, 212 ms | 4,939, 212 ms |
| 2,000 | 10,000 | 9,601, 281 ms | 9,792, 241 ms | 948, 2.79 s | 9,814, 229 ms | 9,749, 260 ms |

A cold JVM distorts these figures. In this run, `reactive` ran first and measured 9,601 requests per second at 2,000 connections. With the order reversed, it ran last and measured 9,810. The default sequence therefore ramps up the connections, and a single high `CONNECTIONS` value must discard its first result.

The worker pool holds 200 threads on this machine, the greater of 200 and 8 per processor, so at 200 ms it caps near 1,000 requests per second. The other endpoints reach the ceiling at every tested load, which shows no difference of throughput between them at this scale. The limit of the worker pool also protects the dependency, because it bounds the concurrent calls, so a rate limited dependency requires an explicit limit when virtual threads replace the pool.

## Code layout

The base package `fr.simplex_software.workshop.mutiny_virtual_threads` divides the code into layers, following the conventions of the Quarkus plugin. The package of the JAX-RS resources bears the name `api`, which distinguishes it from the `src/main/resources` directory holding the configuration.

| Package | Content |
|---|---|
| `api` | The JAX-RS resources exposing the five endpoints, and the pattern constraining a quote symbol |
| `service` | `QuoteService`, which implements the operation with each concurrency model |
| `client` | The reactive and blocking REST clients of the Finnhub API |
| `config` | The type safe mapping of the Finnhub configuration |
| `dto` | The records exchanged with the callers and with Finnhub, including the RFC 9457 problem detail |
| `exception` | The domain exception and the exception mappers producing problem details |

The tests reside in the package of the code they verify, together with the WireMock resource and the filters observing the threads. The load test stub `LatencyStub` remains in the base package.

## Testing

| Test | Type | Covers |
|---|---|---|
| `FinnhubQuoteTest` | plain JUnit | JSON-B mapping of the Finnhub response, and absent fields defaulting to zero |
| `QuoteResourceTest` | `@QuarkusTest` with WireMock | All five endpoints honour one contract: success, invalid symbol, unknown symbol, upstream errors, timeouts and dropped connections. It also verifies the thread serving each model and making each upstream call, the absence of pinning and the absence of the token from error responses |

## Versioning

Group `fr.simplex-software.workshop`, semantic versioning, currently `1.0.2-SNAPSHOT`. The project is a single module, since it has one deployable and nothing to share.
