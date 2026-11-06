# feign-kotlin

Kotlin support for Feign, including coroutine (suspend function) clients.

## CoroutineFeign

`CoroutineFeign` builds clients whose `suspend` functions are executed asynchronously:

```kotlin
val api = CoroutineFeign.builder()
  .client(AsyncClient.Default(Client.Default(null, null), Executors.newCachedThreadPool()))
  .target(GitHubApi::class.java, "https://api.github.com")

val repositories = api.listRepositories("openfeign") // suspend fun
```

## Using suspend functions with the blocking builder

`CoroutineFeign` already resolves the declared Kotlin return type for `suspend`
functions. If you use the plain blocking `Feign.builder()` with an interface that
contains `suspend` functions, the JVM signature reports `java.lang.Object` as the
return type, and decoders would receive `Object` instead of the declared type
(see [#2019](https://github.com/OpenFeign/feign/issues/2019)).

Wrap your contract with `KotlinDelegatingContract` to fix the resolved type:

```kotlin
val api = Feign.builder()
  .contract(KotlinDelegatingContract(Contract.Default()))
  .decoder(JacksonDecoder())
  .target(GitHubApi::class.java, "https://api.github.com")
```

## Truly non-blocking execution

By default `CoroutineFeign` wraps a blocking `Client` with
`DefaultAsyncClient`, which runs the blocking I/O on a cached thread pool. The
suspending call never blocks the caller, but a thread is still occupied per
in-flight request.

For fully non-blocking execution, plug in any `AsyncClient` implementation that
completes its `CompletableFuture` without an extra thread, e.g.
`feign-http2client` (`Http2Client`, Java 11+ `HttpClient`), `feign-hc5`
(`AsyncApacheHttp5Client`), `feign-vertx`, or `feign-webflux`
(`WebClientAsyncClient`, Spring `WebClient` on Reactor Netty):

```kotlin
val api = CoroutineFeign.builder<GitHubApi>()
  .client(Http2Client() as AsyncClient<GitHubApi>) // or AsyncApacheHttp5Client()
  .decoder(JacksonDecoder())
  .target(GitHubApi::class.java, "https://api.github.com")
```

The HTTP request now runs on the async client's own I/O machinery, and response
decoding happens on the thread that completes the future rather than on a
dedicated pool thread. See `NonBlockingCoroutineFeignTest` for a runnable
example.

## Flow support

`CoroutineFeign` supports `suspend` functions returning `kotlinx.coroutines.flow.Flow<T>`.

The response body is decoded as a buffered `List<T>` (the same semantics applied to
`Flux` returns by the reactive module) and bridged back into a cold `Flow`, so the
`suspend` call still suspends until the full response arrives while the caller can
consume elements as a stream:

```kotlin
interface EventsApi {
  @GET("/events")
  suspend fun events(): Flow<Event>
}
```

Note that decoding is fully buffered on the server response, so this is not a true
streaming/SSE transport; see [#971](https://github.com/OpenFeign/feign/issues/971) for
the background discussion.
