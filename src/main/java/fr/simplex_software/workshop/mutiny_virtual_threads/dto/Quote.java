package fr.simplex_software.workshop.mutiny_virtual_threads.dto;

import java.math.BigDecimal;

public record Quote(String symbol, BigDecimal price, BigDecimal previousClose)
{
}
