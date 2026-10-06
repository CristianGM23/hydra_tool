# HYDRA scale benchmarks

The Update Site scale benchmark is deliberately excluded from the ordinary naming convention so that a normal test run does not pay its 50k-entry cost.
Run it explicitly in a release/performance environment with Java 24:

```cmd
mvnw.cmd -Dtest=UpdateSiteScaleBenchmark test
```

It exercises 1,000, 10,000 and 50,000 staged entries and prints elapsed time and materialized logical bytes. Release CI should retain the output as performance evidence rather than treating a single developer-machine timing as a universal budget.
