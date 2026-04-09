package com.elsworne.stubline.spike.health;

import java.net.URI;

/** One endpoint's observed status and elapsed time. */
public record CheckResult(URI url, String status, long latencyMillis) {}
