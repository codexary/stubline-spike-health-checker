package com.elsworne.stubline.spike.health;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CheckersTest {
  @Test
  void failedEndpointReportsHttpStatus() throws Exception {
    withServer(
        (base, peak) -> {
          for (var checker : checkers())
            assertEquals(
                "500",
                checker
                    .check(
                        List.of(URI.create(base + "/fail")),
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        2)
                    .getFirst()
                    .status());
        });
  }

  @Test
  void hungEndpointDoesNotHoldRunPastDeadline() throws Exception {
    withServer(
        (base, peak) -> {
          for (var checker : checkers()) {
            long started = System.nanoTime();
            var result =
                checker.check(
                    List.of(URI.create(base + "/hang")),
                    Duration.ofSeconds(2),
                    Duration.ofMillis(300),
                    2);
            assertEquals("TIMEOUT", result.getFirst().status());
            assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 900);
          }
        });
  }

  @Test
  void concurrentChecksRespectBound() throws Exception {
    withServer(
        (base, peak) -> {
          var urls = new ArrayList<URI>();
          for (int i = 0; i < 8; i++) urls.add(URI.create(base + "/slow"));
          for (var checker :
              new HealthChecker[] {
                Checkers.fixedPool(), Checkers.virtualThreads(), Checkers.async()
              }) {
            peak.set(0);
            assertEquals(
                8, checker.check(urls, Duration.ofSeconds(2), Duration.ofSeconds(4), 2).size());
            assertTrue(peak.get() <= 2);
          }
        });
  }

  @Test
  void concurrentChecksFinishFasterThanSequential() throws Exception {
    withServer(
        (base, peak) -> {
          var urls = new ArrayList<URI>();
          for (int i = 0; i < 8; i++) urls.add(URI.create(base + "/slow"));
          long started = System.nanoTime();
          Checkers.sequential().check(urls, Duration.ofSeconds(2), Duration.ofSeconds(4), 2);
          long sequentialMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
          for (var checker :
              new HealthChecker[] {
                Checkers.fixedPool(), Checkers.virtualThreads(), Checkers.async()
              }) {
            started = System.nanoTime();
            checker.check(urls, Duration.ofSeconds(2), Duration.ofSeconds(4), 2);
            assertTrue(
                Duration.ofNanos(System.nanoTime() - started).toMillis() < sequentialMs - 200);
          }
        });
  }

  private static HealthChecker[] checkers() {
    return new HealthChecker[] {
      Checkers.sequential(), Checkers.fixedPool(), Checkers.virtualThreads(), Checkers.async()
    };
  }

  private interface Scenario {
    void run(String base, AtomicInteger peak) throws Exception;
  }

  private static void withServer(Scenario scenario) throws Exception {
    var peak = new AtomicInteger();
    var active = new AtomicInteger();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
      server.setExecutor(workers);
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
      scenario.run("http://127.0.0.1:" + server.getAddress().getPort(), peak);
    } finally {
      server.stop(0);
    }
  }
}
