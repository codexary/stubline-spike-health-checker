package com.elsworne.stubline.spike.health;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Runs a network-free health-check demonstration against local endpoints. */
public final class DemoMain {
  private DemoMain() {}

  public static void main(String[] args) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      server.setExecutor(workers);
      server.createContext(
          "/ok",
          exchange -> {
            try {
              Thread.sleep(100);
              exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              exchange.close();
            }
          });
      server.createContext(
          "/fail",
          exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
          });
      server.createContext(
          "/hang",
          exchange -> {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            exchange.close();
          });
      server.start();
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      long start = System.nanoTime();
      System.out.println("URL\tSTATUS\tLATENCY_MS");
      for (var result :
          Checkers.virtualThreads()
              .check(
                  List.of(
                      URI.create(base + "/ok"),
                      URI.create(base + "/fail"),
                      URI.create(base + "/hang")),
                  Duration.ofMillis(250),
                  Duration.ofMillis(400),
                  3))
        System.out.printf("%s\t%s\t%d%n", result.url(), result.status(), result.latencyMillis());
      System.out.println("TOTAL_MS\t" + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
      var slow =
          java.util.stream.IntStream.range(0, 50).mapToObj(i -> URI.create(base + "/ok")).toList();
      for (var entry :
          java.util.Map.of(
                  "fixed",
                  Checkers.fixedPool(),
                  "virtual",
                  Checkers.virtualThreads(),
                  "async",
                  Checkers.async())
              .entrySet()) {
        long compared = System.nanoTime();
        var results =
            entry.getValue().check(slow, Duration.ofSeconds(2), Duration.ofSeconds(10), 10);
        System.out.printf(
            "%s endpoints=%d totalMs=%d%n",
            entry.getKey(),
            results.size(),
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - compared));
      }
      server.stop(0);
    } finally {
      server.stop(0);
    }
  }
}
