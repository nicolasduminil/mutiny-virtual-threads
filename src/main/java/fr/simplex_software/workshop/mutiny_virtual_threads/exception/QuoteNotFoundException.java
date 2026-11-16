package fr.simplex_software.workshop.mutiny_virtual_threads.exception;

public class QuoteNotFoundException extends RuntimeException
{
  public QuoteNotFoundException(String symbol)
  {
    super("No quote exists for symbol " + symbol);
  }
}
