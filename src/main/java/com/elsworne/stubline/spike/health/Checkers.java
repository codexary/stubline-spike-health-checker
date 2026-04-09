package com.elsworne.stubline.spike.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Factory for sequential, fixed-pool, virtual-thread and async checks. */
public final class Checkers {
  private Checkers() {}

  public static HealthChecker sequential() {
    return (urls, perRequest, deadline, bound) -> {
      long until = System.nanoTime() + deadline.toNanos();
      try (var http = HttpClient.newHttpClient()) {
        var results = new ArrayList<CheckResult>();
        for (URI url : urls) {
          if (System.nanoTime() >= until) results.add(new CheckResult(url, "TIMEOUT", 0));
          else
            results.add(
                send(http, url, min(perRequest, Duration.ofNanos(until - System.nanoTime()))));
        }
        return sorted(results);
      }
    };
  }

  public static HealthChecker fixedPool() {
    return (urls, perRequest, deadline, bound) ->
        run(urls, perRequest, deadline, Executors.newFixedThreadPool(bound), null);
  }

  public static HealthChecker virtualThreads() {
    return (urls, perRequest, deadline, bound) ->
        run(
            urls,
            perRequest,
            deadline,
            Executors.newVirtualThreadPerTaskExecutor(),
            new Semaphore(bound));
  }

  public static HealthChecker async() {
    return (urls, perRequest, deadline, bound) -> {
      try (var http = HttpClient.newHttpClient()) {
        var permits = new Semaphore(bound);
        long until = System.nanoTime() + deadline.toNanos();
        var futures = new ArrayList<CompletableFuture<CheckResult>>();
        for (URI url : urls) {
          futures.add(
              CompletableFuture.supplyAsync(
                  () -> {
                    try {
                      permits.acquire();
                      long start = System.nanoTime();
                      if (start >= until) {
                        permits.release();
                        return new CheckResult(url, "TIMEOUT", 0);
                      }
                      var request =
                          HttpRequest.newBuilder(url)
                              .timeout(min(perRequest, Duration.ofNanos(until - start)))
                              .build();
                      return http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                          .orTimeout(
                              Math.min(
                                  perRequest.toMillis(),
                                  Math.max(1, TimeUnit.NANOSECONDS.toMillis(until - start))),
                              TimeUnit.MILLISECONDS)
                          .handle(
                              (response, error) ->
                                  new CheckResult(
                                      url,
                                      error == null
                                          ? Integer.toString(response.statusCode())
                                          : classify(error),
                                      elapsed(start)))
                          .whenComplete((result, error) -> permits.release())
                          .join();
                    } catch (InterruptedException e) {
                      Thread.currentThread().interrupt();
                      return new CheckResult(url, "TIMEOUT", 0);
                    }
                  }));
        }
        return collect(urls, futures, until);
      }
    };
  }

  private static List<CheckResult> run(
      List<URI> urls,
      Duration perRequest,
      Duration deadline,
      ExecutorService workers,
      Semaphore permits) {
    long until = System.nanoTime() + deadline.toNanos();
    try (workers;
        var http = HttpClient.newHttpClient()) {
      var futures = new ArrayList<CompletableFuture<CheckResult>>();
      for (URI url : urls) {
        futures.add(
            CompletableFuture.supplyAsync(
                () -> {
                  boolean acquired = false;
                  try {
                    if (permits != null) {
                      permits.acquire();
                      acquired = true;
                    }
                    if (System.nanoTime() >= until) return new CheckResult(url, "TIMEOUT", 0);
                    return send(
                        http, url, min(perRequest, Duration.ofNanos(until - System.nanoTime())));
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new CheckResult(url, "TIMEOUT", 0);
                  } finally {
                    if (acquired) permits.release();
                  }
                },
                workers));
      }
      return collect(urls, futures, until);
    }
  }

  private static List<CheckResult> collect(
      List<URI> urls, List<CompletableFuture<CheckResult>> futures, long until) {
    var results = new ArrayList<CheckResult>();
    for (int i = 0; i < futures.size(); i++) {
      try {
        results.add(
            futures.get(i).get(Math.max(1, until - System.nanoTime()), TimeUnit.NANOSECONDS));
      } catch (Exception e) {
        futures.get(i).cancel(true);
        results.add(new CheckResult(urls.get(i), "TIMEOUT", 0));
      }
    }
    return sorted(results);
  }

  private static CheckResult send(HttpClient http, URI url, Duration timeout) {
    long start = System.nanoTime();
    try {
      var response =
          http.send(
              HttpRequest.newBuilder(url).timeout(timeout).build(),
              HttpResponse.BodyHandlers.discarding());
      return new CheckResult(url, Integer.toString(response.statusCode()), elapsed(start));
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      return new CheckResult(url, classify(e), elapsed(start));
    }
  }

  private static String classify(Throwable error) {
    Throwable cause = error;
    while (cause.getCause() != null) cause = cause.getCause();
    return cause instanceof TimeoutException || cause instanceof java.net.http.HttpTimeoutException
        ? "TIMEOUT"
        : "ERROR: " + cause.getClass().getSimpleName();
  }

  private static long elapsed(long start) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
  }

  private static Duration min(Duration a, Duration b) {
    return a.compareTo(b) < 0 ? a : b;
  }

  private static List<CheckResult> sorted(List<CheckResult> results) {
    return results.stream()
        .sorted(Comparator.comparingLong(CheckResult::latencyMillis).reversed())
        .toList();
  }
}
