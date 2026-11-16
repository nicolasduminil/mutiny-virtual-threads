package fr.simplex_software.workshop.mutiny_virtual_threads;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class LatencyStub
{
  public static void main(String[] args) throws Exception
  {
    System.setProperty("sun.net.httpserver.nodelay", "true");
    System.setProperty("sun.net.httpserver.maxIdleConnections", "100000");
    System.setProperty("sun.net.httpserver.idleInterval", "3600");
    long delayMillis = Long.parseLong(args[1]);
    byte[] body = "{\"c\":261.74,\"pc\":259.71}".getBytes(StandardCharsets.UTF_8);
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[0])), 8192);
    server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    server.createContext("/quote", exchange ->
    {
      try
      {
        Thread.sleep(delayMillis);
      }
      catch (InterruptedException e)
      {
        Thread.currentThread().interrupt();
      }
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var response = exchange.getResponseBody())
      {
        response.write(body);
      }
    });
    server.start();
  }
}
