package fr.simplex_software.workshop.mutiny_virtual_threads.api;

import com.github.tomakehurst.wiremock.*;
import com.github.tomakehurst.wiremock.core.*;
import io.quarkus.test.common.*;

import java.util.*;

public class WireMockFinnhub implements QuarkusTestResourceLifecycleManager
{
  static final String TOKEN = "secret-test-token";
  static final WireMockServer SERVER = new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @Override
  public Map<String, String> start()
  {
    SERVER.start();
    return Map.of("quarkus.rest-client.finnhub.url", SERVER.baseUrl(),
      "quarkus.rest-client.finnhub.read-timeout", "1000",
      "finnhub.token", TOKEN);
  }

  @Override
  public void stop()
  {
    SERVER.stop();
  }
}
