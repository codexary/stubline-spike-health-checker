English | [简体中文](README.zh-CN.md)

# Concurrent health checker

Before an on-sale, an on-call engineer can check many internal HTTP endpoints from a file containing one URL per line. The table is sorted slowest first and shows URL, HTTP status or error, latency in milliseconds, and total elapsed time. Each request and the complete run have time budgets.

Install Java 21. From the repository root:

```sh
./mvnw -q verify
./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.health.DemoMain
./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.health.HealthMain -Dexec.args='urls.txt virtual 8 1000 3000'
```

The local demo prints a 200, a 500 and a TIMEOUT, followed by `TOTAL_MS`; ports and timings vary. Create `urls.txt` with one endpoint per line for a real run. `sequential` is simple for a few endpoints; `fixed` caps platform threads; `virtual` scales blocking calls while a semaphore caps in-flight requests; `async` composes `sendAsync`, `orTimeout` and completion handlers without dedicating a waiting thread to each request. This spike informs on-call checks before sales and the timeout design in later ticketing milestones.

## License

This repository is licensed under the [MIT License](LICENSE).

## About this repository

This repository contains a focused Stubline experiment. The README documents its commands and observed results.

The code and documents in this repository were written with the help of AI tools; every recorded output comes from actually running them.
