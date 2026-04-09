package com.elsworne.stubline.spike.health;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CheckersTest {
  @Test
  void checksStatusTimeoutDeadlineAndConcurrencyBound() throws Exception {
    var active = new AtomicInteger();
    var peak = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      server.setExecutor(executor);
      server.createContext(
          "/slow",
          exchange -> {
            int now = active.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
              Thread.sleep(150);
              exchange.sendResponseHeaders(200, -1);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } finally {
              active.decrementAndGet();
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
              Thread.sleep(2000);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            exchange.close();
          });
      server.start();
      var base = "http://127.0.0.1:" + server.getAddress().getPort();
      var urls = new ArrayList<URI>();
      for (int i = 0; i < 6; i++) urls.add(URI.create(base + "/slow"));
      long sequentialStart = System.nanoTime();
      var sequential =
          Checkers.sequential().check(urls, Duration.ofSeconds(2), Duration.ofSeconds(4), 2);
      long sequentialMs = Duration.ofNanos(System.nanoTime() - sequentialStart).toMillis();
      assertEquals(6, sequential.size());
      assertTrue(sequentialMs >= 800);
      for (var checker :
          new HealthChecker[] {Checkers.fixedPool(), Checkers.virtualThreads(), Checkers.async()}) {
        peak.set(0);
        long started = System.nanoTime();
        var results = checker.check(urls, Duration.ofSeconds(1), Duration.ofSeconds(3), 2);
        long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
        assertEquals(6, results.size());
        assertTrue(elapsed < sequentialMs - 200, "concurrent check should be faster");
        assertTrue(peak.get() <= 2, "bound exceeded: " + peak.get());
      }
      for (var checker :
          new HealthChecker[] {
            Checkers.sequential(), Checkers.fixedPool(), Checkers.virtualThreads(), Checkers.async()
          }) {
        long started = System.nanoTime();
        var mixed =
            checker.check(
                java.util.List.of(URI.create(base + "/fail"), URI.create(base + "/hang")),
                Duration.ofSeconds(2),
                Duration.ofMillis(500),
                2);
        assertTrue(mixed.stream().anyMatch(r -> r.status().equals("500")));
        assertTrue(mixed.stream().anyMatch(r -> r.status().equals("TIMEOUT")));
        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 800);
      }
      server.stop(0);
    } finally {
      server.stop(0);
    }
  }
}
