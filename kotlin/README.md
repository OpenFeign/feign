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
