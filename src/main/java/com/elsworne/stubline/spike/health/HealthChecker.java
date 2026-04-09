package com.elsworne.stubline.spike.health;

import java.net.URI;
import java.time.Duration;
import java.util.List;

/** Checks endpoints within individual and overall time budgets. */
public interface HealthChecker {
  List<CheckResult> check(List<URI> urls, Duration perRequest, Duration deadline, int maxInFlight);
}
