package fr.simplex_software.workshop.mutiny_virtual_threads.config;

import io.smallrye.config.*;

import java.util.*;

@ConfigMapping(prefix = "finnhub")
public interface FinnhubConfig
{
  Optional<String> token();
}
