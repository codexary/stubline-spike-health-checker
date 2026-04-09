package com.elsworne.stubline.spike.health;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Runs a health check from a file containing one URL per line. */
public final class HealthMain {
  private HealthMain() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 1)
      throw new IllegalArgumentException(
          "Usage: HealthMain urls.txt [sequential|fixed|virtual|async] [bound] [perRequestMs] [deadlineMs]");
    var urls =
        Files.readAllLines(Path.of(args[0])).stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(URI::create)
            .toList();
    String strategy = args.length > 1 ? args[1] : "virtual";
    int bound = args.length > 2 ? Integer.parseInt(args[2]) : 8;
    Duration perRequest = Duration.ofMillis(args.length > 3 ? Long.parseLong(args[3]) : 1000);
    Duration deadline = Duration.ofMillis(args.length > 4 ? Long.parseLong(args[4]) : 3000);
    HealthChecker checker =
        switch (strategy) {
          case "sequential" -> Checkers.sequential();
          case "fixed" -> Checkers.fixedPool();
          case "virtual" -> Checkers.virtualThreads();
          case "async" -> Checkers.async();
          default -> throw new IllegalArgumentException("Unknown strategy: " + strategy);
        };
    long start = System.nanoTime();
    System.out.println("URL\tSTATUS\tLATENCY_MS");
    checker
        .check(urls, perRequest, deadline, bound)
        .forEach(r -> System.out.printf("%s\t%s\t%d%n", r.url(), r.status(), r.latencyMillis()));
    System.out.printf(
        "TOTAL_MS\t%d%n",
        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
  }
}
