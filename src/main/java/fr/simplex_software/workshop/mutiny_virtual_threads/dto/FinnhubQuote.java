package fr.simplex_software.workshop.mutiny_virtual_threads.dto;

import jakarta.json.bind.annotation.JsonbProperty;

import java.math.BigDecimal;
import java.util.Objects;

public record FinnhubQuote(@JsonbProperty("c") BigDecimal current,
                           @JsonbProperty("pc") BigDecimal previousClose)
{
  public FinnhubQuote
  {
    current = Objects.requireNonNullElse(current, BigDecimal.ZERO);
    previousClose = Objects.requireNonNullElse(previousClose, BigDecimal.ZERO);
  }
}
