Micrometer
===================

This module integrates Feign with [Micrometer](https://micrometer.io/) so that
HTTP calls made through Feign clients are observable through any Micrometer-supported
monitoring system (Prometheus, Datadog, CloudWatch, etc.).

Two capabilities are provided:

* `MicrometerCapability` — publishes timers, counters and distribution summaries
  to a `MeterRegistry`.
* `MicrometerObservationCapability` — publishes Micrometer `Observation`s to an
  `ObservationRegistry` (which can in turn produce metrics and tracing spans).

Pick one — both record the same underlying call, so registering both would
double-count.

## Usage

### `MicrometerCapability`

```java
GitHub github = Feign.builder()
                     .addCapability(new MicrometerCapability())
                     .target(GitHub.class, "https://api.github.com");
```

By default, metrics are registered with `io.micrometer.core.instrument.Metrics.globalRegistry`.
Pass your own registry and/or common tags if you want explicit control:

```java
MeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

GitHub github = Feign.builder()
                     .addCapability(new MicrometerCapability(registry, Tags.of("application", "my-app")))
                     .target(GitHub.class, "https://api.github.com");
```

### `MicrometerObservationCapability`

```java
ObservationRegistry observationRegistry = ObservationRegistry.create();

GitHub github = Feign.builder()
                     .addCapability(new MicrometerObservationCapability(observationRegistry))
                     .target(GitHub.class, "https://api.github.com");
```

To customize the tags or name of the observation, implement
`FeignObservationConvention` (or extend `DefaultFeignObservationConvention`) and
pass it to the constructor:

```java
new MicrometerObservationCapability(observationRegistry, new MyFeignObservationConvention());
```

## Metrics published by `MicrometerCapability`

The capability instruments five stages of a Feign call. Each stage emits a
timer; counters and distribution summaries are emitted where noted.

| Name | Type | Description |
| ---- | ---- | ----------- |
| `feign.Feign` | Timer | Total time spent in the Feign method invocation, including encoding, the HTTP call and decoding. Recorded by `MeteredInvocationHandleFactory`. |
| `feign.Feign.exception` | Timer | Same as above, recorded when the invocation throws (any `Throwable`). |
| `feign.Feign.http_error` | Counter | Incremented once per `FeignException` thrown by an invocation. Adds `http_status` and `error_group` tags. |
| `feign.Client` | Timer | Time spent in the underlying HTTP client for a single request. Recorded by `MeteredClient`. |
| `feign.Client.exception` | Timer | Same as above, recorded when the HTTP call throws. |
| `feign.Client.http_response_code` | Counter | Incremented once per HTTP response (including error responses). Adds `http_status`, `status_group`, `http_method` and `uri` tags. |
| `feign.AsyncClient` | Timer | Async-equivalent of `feign.Client`. Recorded by `MeteredAsyncClient`. |
| `feign.AsyncClient.exception` | Timer | Async-equivalent of `feign.Client.exception`. |
| `feign.AsyncClient.http_response_code` | Counter | Async-equivalent of `feign.Client.http_response_code`. |
| `feign.codec.Encoder` | Timer | Time spent encoding the request body. Recorded by `MeteredEncoder`. |
| `feign.codec.Encoder.response_size` | DistributionSummary | Size, in bytes, of the encoded **request** body. The metric name predates the current behavior. |
| `feign.codec.Decoder` | Timer | Time spent decoding the response body. Recorded by `MeteredDecoder`. |
| `feign.codec.Decoder.exception` | Timer | Same as above, recorded when decoding throws. |
| `feign.codec.Decoder.response_size` | DistributionSummary | Size, in bytes, of the response body read by the decoder. |

### Tags

Every metric carries the following tags, populated by `FeignMetricTagResolver`:

| Tag | Source | Example |
| --- | ------ | ------- |
| `client` | Target interface FQN | `com.example.GitHub` |
| `method` | Java method name | `contributors` |
| `host` | Host extracted from the target URL | `api.github.com` |
| `exception_name` | Exception simple name (only present on error) | `FeignException` |
| `root_cause_name` | Root cause exception simple name (only present on error) | `SocketTimeoutException` |

Additional tags are added by specific metrics:

* `feign.Client.http_response_code` / `feign.AsyncClient.http_response_code` —
  adds `http_status` (e.g. `404`), `status_group` (e.g. `4xx`), `http_method`
  (e.g. `GET`) and `uri` (the templated path, e.g. `/repos/{owner}/{repo}/contributors`).
* `feign.Feign.http_error` — adds `http_status` and `error_group` (e.g. `5xx`).
* `feign.codec.Decoder.*` — adds `uri`.

You can attach extra common tags either through `MeterRegistry.config().commonTags(...)`
or via the `MicrometerCapability(MeterRegistry, List<Tag>)` /
`MicrometerCapability(MeterRegistry, Map<String, String>)` constructors.

## Observation published by `MicrometerObservationCapability`

A single observation is produced per HTTP call:

| Name | Contextual name | Description |
| ---- | --------------- | ----------- |
| `http.client.requests` | `HTTP {method}` (e.g. `HTTP GET`) | One observation per HTTP request issued by the Feign client. The observation is stopped after the response is received or an exception is signalled. |

The default convention (`DefaultFeignObservationConvention`) attaches the
following low-cardinality key values:

| Key | Value |
| --- | ----- |
| `http.method` | The HTTP method (`GET`, `POST`, ...). `UNKNOWN` if the request is null. |
| `http.url` | The templated URL of the method. |
| `http.status_code` | The response status code, or `CLIENT_ERROR` if no response was received. |
| `clientName` | The target interface FQN. |

The following key names are also declared on `FeignObservationDocumentation` and
are available for custom conventions to populate: `http.scheme`, `net.peer.host`,
`net.peer.port`.

Whatever observation handlers are registered on the `ObservationRegistry`
(for example `DefaultMeterObservationHandler` for metrics, a tracing handler
for spans) decide what is ultimately emitted.
