# feign-webflux

A truly non-blocking [AsyncClient](https://github.com/OpenFeign/feign/blob/master/core/src/main/java/feign/AsyncClient.java)
for Feign, backed by Spring's
[WebClient](https://docs.spring.io/spring-framework/reference/integration/web-clients.html).

Requests execute on the `WebClient`'s reactive transport (Reactor Netty by default) and the
`CompletableFuture` returned to Feign is completed from the transport's completion thread. No
thread is parked waiting for a response, so this client is a natural companion to
`CoroutineFeign` (see [`feign-kotlin`](../kotlin/README.md)) and other non-blocking setups.

## Maven

```xml
<dependency>
  <groupId>io.github.openfeign</groupId>
  <artifactId>feign-webflux</artifactId>
  <version>13.18-SNAPSHOT</version>
</dependency>
```

`feign-webflux` depends on `spring-webflux` only. The actual HTTP connector is a runtime choice:
add [Reactor Netty](https://projectreactor.io/docs/netty/release/reference/index.html) (used by
default when present), Jetty, Apache HttpComponents or rely on the JDK `HttpClient` connector
provided by Spring.

```xml
<dependency>
  <groupId>io.projectreactor.netty</groupId>
  <artifactId>reactor-netty-http</artifactId>
  <version>1.3.8</version>
</dependency>
<!-- required by the Reactor Netty connector, already present in Spring Boot applications -->
<dependency>
  <groupId>org.springframework</groupId>
  <artifactId>spring-context</artifactId>
</dependency>
```

## Usage with AsyncFeign

```java
interface GitHub {

  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  CompletableFuture<List<Contributor>> contributors(@Param("owner") String owner, @Param("repo") String repo);
}

GitHub github = AsyncFeign.builder()
    .client(new WebClientAsyncClient<>())
    .decoder(new GsonDecoder())
    .target(GitHub.class, "https://api.github.com");
```

## Usage with CoroutineFeign

Combined with the [`feign-kotlin`](../kotlin/README.md) module, `suspend` calls never block a
thread: the request runs on the event loop and decoding happens on the thread that completes the
future.

```kotlin
interface GitHubApi {
  @RequestLine("GET /repos/{owner}/repos")
  suspend fun listRepositories(@Param("owner") owner: String): List<Repo>
}

val api = CoroutineFeign.builder<GitHubApi>()
  .client(WebClientAsyncClient<Any>() as AsyncClient<GitHubApi>)
  .decoder(JacksonDecoder())
  .target(GitHubApi::class.java, "https://api.github.com")

val repositories = api.listRepositories("openfeign") // suspend fun
```

## Reusing WebClient configuration

`WebClientAsyncClient` accepts a `WebClient` or a `WebClient.Builder`, so connection pooling,
timeouts, filters and codecs configured there are reused as is. In Netty environments the event
loop group and connection pool are shared with the rest of the application:

```java
HttpClient httpClient = HttpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2_000)
    .responseTimeout(Duration.ofSeconds(5))
    .compress(true);

WebClient webClient = WebClient.builder()
    .clientConnector(new ReactorClientHttpConnector(httpClient))
    .build();

GitHub github = AsyncFeign.builder()
    .client(new WebClientAsyncClient<>(webClient))
    .target(GitHub.class, "https://api.github.com");
```

## Behavior notes

- Response bodies are fully aggregated in memory before decoding; there is no streaming support.
- Error statuses (4xx/5xx) are delivered as regular Feign `Response` objects and remain under the
  control of the `ErrorDecoder`; the WebClient is never allowed to turn them into exceptions.
- Of the per-request `Request.Options`, only the read timeout is applied (as a reactive timeout
  that cancels the request). Connect timeout, redirects and connection pool limits are transport
  concerns: configure them on the connector as shown above.
