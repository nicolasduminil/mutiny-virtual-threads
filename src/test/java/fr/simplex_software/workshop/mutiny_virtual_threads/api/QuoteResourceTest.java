package fr.simplex_software.workshop.mutiny_virtual_threads.api;

import fr.simplex_software.workshop.mutiny_virtual_threads.dto.*;
import com.github.tomakehurst.wiremock.client.*;
import com.github.tomakehurst.wiremock.http.*;
import io.quarkus.test.common.*;
import io.quarkus.test.junit.*;
import io.quarkus.test.junit.virtual.*;
import io.restassured.response.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.*;
import org.junit.jupiter.params.provider.*;

import java.util.stream.*;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.*;

@QuarkusTest
@VirtualThreadUnit
@WithTestResource(WireMockFinnhub.class)
class QuoteResourceTest
{
  private static final String KNOWN_QUOTE = """
    {"c":261.74,"d":2.03,"dp":0.78,"pc":259.71,"t":1582641000}""";

  @BeforeEach
  void resetFinnhub()
  {
    WireMockFinnhub.SERVER.resetAll();
  }

  @ParameterizedTest
  @MethodSource("models")
  void quote_knownSymbol_returnsQuote(String model)
  {
    finnhubAnswers(okJson(KNOWN_QUOTE));

    Response response = fetch("/api/v1/quote/{model}/AAPL", model);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.jsonPath().getString("symbol")).isEqualTo("AAPL");
    assertThat(response.jsonPath().getDouble("price")).isEqualTo(261.74);
    assertThat(response.jsonPath().getDouble("previousClose")).isEqualTo(259.71);
    WireMockFinnhub.SERVER.verify(getRequestedFor(urlPathEqualTo("/quote"))
      .withQueryParam("symbol", equalTo("AAPL"))
      .withQueryParam("token", equalTo(WireMockFinnhub.TOKEN)));
  }

  @Test
  @ShouldNotPin
  void quote_virtualEndpoint_runsOnVirtualThreadWithoutPinning()
  {
    finnhubAnswers(okJson(KNOWN_QUOTE));

    assertThat(fetch("/api/v1/quote/virtual/AAPL").header(ThreadKindFilter.HEADER)).isEqualTo("virtual");
  }

  @Test
  void quote_reactiveEndpoint_runsOnEventLoop()
  {
    finnhubAnswers(okJson(KNOWN_QUOTE));

    assertThat(fetch("/api/v1/quote/reactive/AAPL").header(ThreadKindFilter.HEADER)).isEqualTo("event-loop");
  }

  @ParameterizedTest
  @CsvSource({"offload-worker,worker", "offload-virtual,virtual"})
  void quote_offloadEndpoint_callsUpstreamOnChosenExecutor(String model, String expectedThread)
  {
    finnhubAnswers(okJson(KNOWN_QUOTE));

    assertThat(fetch("/api/v1/quote/{model}/AAPL", model).statusCode()).isEqualTo(200);

    WireMockFinnhub.SERVER.verify(getRequestedFor(urlPathEqualTo("/quote"))
      .withHeader(CallerThreadFilter.HEADER, equalTo(expectedThread)));
  }

  @Test
  @ShouldNotPin
  void quote_virtualOffload_doesNotPin()
  {
    finnhubAnswers(okJson(KNOWN_QUOTE));

    assertThat(fetch("/api/v1/quote/offload-virtual/AAPL").statusCode()).isEqualTo(200);
  }

  @Test
  @ShouldNotPin
  void quote_awaitEndpoint_runsOnVirtualThreadWithoutPinning()
  {
    finnhubAnswers(okJson(KNOWN_QUOTE));

    assertThat(fetch("/api/v1/quote/await/AAPL").header(ThreadKindFilter.HEADER)).isEqualTo("virtual");
  }

  @ParameterizedTest
  @MethodSource("models")
  void quote_invalidSymbol_returns400WithoutUpstreamCall(String model)
  {
    Response response = fetch("/api/v1/quote/{model}/not-valid", model);

    assertProblem(response, 400);
    assertThat(response.jsonPath().getMap("errors")).containsOnlyKeys("symbol");
    WireMockFinnhub.SERVER.verify(0, anyRequestedFor(anyUrl()));
  }

  @ParameterizedTest
  @MethodSource("models")
  void quote_zeroPrice_returns404(String model)
  {
    finnhubAnswers(okJson("""
      {"c":0,"d":null,"dp":null,"h":0,"l":0,"o":0,"pc":0,"t":0}"""));

    assertProblem(fetch("/api/v1/quote/{model}/UNKNOWN", model), 404);
  }

  @ParameterizedTest
  @MethodSource("modelsWithUpstreamStatuses")
  void quote_upstreamError_returns502(String model, int upstreamStatus)
  {
    finnhubAnswers(aResponse().withStatus(upstreamStatus));

    assertProblem(fetch("/api/v1/quote/{model}/AAPL", model), 502);
  }

  @ParameterizedTest
  @MethodSource("models")
  void quote_upstreamTooSlow_returns504(String model)
  {
    finnhubAnswers(okJson(KNOWN_QUOTE).withFixedDelay(3000));

    assertProblem(fetch("/api/v1/quote/{model}/AAPL", model), 504);
  }

  @ParameterizedTest
  @MethodSource("modelsWithFaults")
  void quote_upstreamConnectionDropped_returns502(String model, Fault fault)
  {
    finnhubAnswers(aResponse().withFault(fault));

    assertProblem(fetch("/api/v1/quote/{model}/AAPL", model), 502);
  }

  @Test
  void quote_unknownModel_returns404AsProblemDetails()
  {
    assertProblem(fetch("/api/v1/quote/unknown/AAPL"), 404);
  }

  static Stream<String> models()
  {
    return Stream.of("reactive", "virtual", "offload-worker", "offload-virtual", "await");
  }

  static Stream<Arguments> modelsWithUpstreamStatuses()
  {
    return models().flatMap(model -> Stream.of(401, 429, 500).map(status -> Arguments.of(model, status)));
  }

  static Stream<Arguments> modelsWithFaults()
  {
    return models().flatMap(model -> Stream.of(Fault.CONNECTION_RESET_BY_PEER, Fault.EMPTY_RESPONSE,
      Fault.MALFORMED_RESPONSE_CHUNK).map(fault -> Arguments.of(model, fault)));
  }

  private static void finnhubAnswers(ResponseDefinitionBuilder answer)
  {
    WireMockFinnhub.SERVER.stubFor(get(urlPathEqualTo("/quote")).willReturn(answer));
  }

  private static Response fetch(String path, Object... arguments)
  {
    return given().get(path, arguments);
  }

  private static void assertProblem(Response response, int status)
  {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.contentType()).startsWith(ProblemDetail.MEDIA_TYPE);
    assertThat(response.jsonPath().getInt("status")).isEqualTo(status);
    assertThat(response.jsonPath().getString("title")).isNotBlank();
    assertThat(response.asString()).doesNotContain(WireMockFinnhub.TOKEN);
  }
}
