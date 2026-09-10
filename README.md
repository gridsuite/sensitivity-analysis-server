# Sensitivity Analysis Server

[![Actions Status](https://github.com/gridsuite/sensitivity-analysis-server/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/gridsuite/sensitivity-analysis-server/actions)
[![Coverage Status](https://sonarcloud.io/api/project_badges/measure?project=org.gridsuite%3Asensitivity-analysis-server&metric=coverage)](https://sonarcloud.io/component_measures?id=org.gridsuite%3Asensitivity-analysis-server&metric=coverage)
[![MPL-2.0 License](https://img.shields.io/badge/license-MPL_2.0-blue.svg)](https://www.mozilla.org/en-US/MPL/2.0/)

## Description

The **sensitivity-analysis-server** is a microservice of the [GridSuite](https://github.com/gridsuite) platform dedicated to **power network sensitivity analysis computation**.

Sensitivity analysis computes, for a set of monitored quantities (branch flows, voltages, ...), how much they vary per unit change of a set of variables (injections, PST tap positions, HVDC set-points), optionally re-evaluated after a list of contingencies (N-K analysis).

It provides the following capabilities:

- **Run sensitivity analysis computations** on a network using a configurable provider (OpenLoadFlow by default, others resolvable via SPI).
- **Build sensitivity factors** from user-defined sets (injections, PSTs, HVDCs, nodes) resolved against filters and contingency lists from other services.
- **Guard against oversized computations** by estimating the number of factors/variables before running (`factor-count` endpoint and thresholds).
- **Store** results (per-factor, N and N-K tabs) in a relational database and **query** them with filtering, sorting, and pagination.
- **Export** results as CSV (zipped, locale-aware number formatting).
- **Manage parameter sets** (create, read, update, duplicate, delete) for the different sensitivity factor types (injections-set, injection, HVDC, PST, nodes).
- Run computations either **synchronously** (in-memory, no persistence) or **asynchronously** (persisted, via a RabbitMQ message queue).

---
## Technical Stack

- Spring Boot (Web, Data JPA, Actuator, Cloud Stream)
- PostgreSQL
- Liquibase
- RabbitMQ via Spring Cloud Stream
- API documentation : OpenAPI / Swagger (`springdoc`)
- Micrometer / Prometheus
- [gridsuite-computation](https://github.com/gridsuite/computation)
- [gridsuite-filter](https://github.com/gridsuite/filter)
- [powsybl-sensitivity-analysis-api](https://powsybl.readthedocs.io/projects/powsybl-core/en/stable/simulation/sensitivity/index.html): powsybl API/SPI for sensitivity analysis computation, used to run the analysis and model its inputs/outputs (parameters, factors, results). The concrete computation engine is a pluggable provider (OpenLoadFlow by default), resolved at runtime via `ServiceLoader`.

---

## Development Scripts

Build Docker image

```shell
mvn install -DskipTests -Dpowsybl.docker.install
```

Please read [liquibase usage](https://github.com/powsybl/powsybl-parent/#liquibase-usage) for instructions to automatically generate changesets. After you generated a changeset do not forget to add it to git and in src/resource/db/changelog/db.changelog-master.yml

---

## Interactions with Other Microservices

```text
┌──────────────────────────────┐
│ sensitivity-analysis-server  │──► network-store-server  (read network topology)
│                              │──► filter-server          (resolve equipment filters for factors/results)
│                              │──► actions-server         (resolve contingency lists)
│                              │──► loadflow-server        (fetch load flow parameter values)
│                              │──► report-server          (post computation functional logs)
└──────────────────────────────┘
             ▲  ▼
   RabbitMQ (sensitivityanalysis.run / .cancel / .result / .stopped / .cancelfailed)
```

---

## Asynchronous Execution Flow

1. The controller publishes a message on the `sensitivityanalysis.run` queue.
2. Parallel consumers (`consumeRun1`, `consumeRun2`) process messages concurrently for load balancing.
3. Results are persisted incrementally in DB while the computation runs, and the final status is published on `sensitivityanalysis.result`.
4. Cancellation of a running computation goes through the `sensitivityanalysis.cancel` queue, acknowledged on `sensitivityanalysis.stopped` (or `sensitivityanalysis.cancelfailed` on error).
5. Dead-letter queues (`sensitivityanalysis.run.dlx`) and quorum queues ensure reliability.

The synchronous `/run` endpoint bypasses RabbitMQ and the database entirely: the computation runs in-memory and the result is returned directly in the HTTP response.

---

## Result Data

A sensitivity analysis result is composed of several complementary datasets exposed through the REST API:

| Dataset | Description |
|---|---|
| **N results** | Base-case (no contingency) sensitivity values per factor: function/variable identifiers, function reference, sensitivity value. Supports filtering, sorting, and pagination. |
| **N-K results** | Post-contingency sensitivity values, linked back to their base-case (N) row, per contingency. Supports global filters (network-element-based), column filters, sorting, and pagination. |
| **CSV export** | Zipped CSV export of the selected result tab, with locale-aware (FR/EN) number formatting. |

Supported sensitivity factor types: **injections-set**, **injection**, **HVDC**, **PST**, and **nodes** (voltage). A safety guard rejects computations exceeding factor/variable count thresholds (checked upfront via the `factor-count` endpoint).

---

## Micrometer observability

Major computation steps are wrapped in named Micrometer observations via `SensitivityAnalysisObserver` (asynchronous, persisted runs) and `SensitivityAnalysisInMemoryObserver` (synchronous, in-memory runs), enabling distributed tracing and metric collection without cluttering business logic.

---

## Built on gridsuite-computation

The following capabilities are provided by the gridsuite-computation shared library:

 - asynchronous run/cancel pipeline,
 - transactional result notifications,
 - network equipment filtering,
 - report integration,
 - Micrometer observability.

The sensitivity-analysis-server itself focuses on sensitivity analysis-specific logic (factor building, parameters, result model, CSV export) and delegates the common computation infrastructure to this lib.

---

## Useful Links

You can find [information on openLoadFlow here](https://powsybl.readthedocs.io/projects/powsybl-open-loadflow/en/latest/)
