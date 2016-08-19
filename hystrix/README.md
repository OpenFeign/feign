Hystrix
===================

This module wraps Feign's http requests in [Hystrix](https://github.com/Netflix/Hystrix/), which enables the [Circuit Breaker Pattern](https://en.wikipedia.org/wiki/Circuit_breaker_design_pattern).

To use Hystrix with Feign, add the Hystrix module to your classpath. Then, configure Feign to use the `HystrixInvocationHandler`:

```java
GitHub github = HystrixFeign.builder()
        .target(GitHub.class, "https://api.github.com");
```

For asynchronous or reactive use, return `HystrixCommand<YourType>`.

For RxJava compatibility, use `rx.Observable<YourType>` or `rx.Single<YourType>`. Rx types are <a href="http://reactivex.io/documentation/observable.html">cold</a>, which means a http call isn't made until there's a subscriber.

Methods that do *not* return [`HystrixCommand`](https://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html), [`rx.Observable`](http://reactivex.io/RxJava/javadoc/rx/Observable.html) or [`rx.Single`] are still wrapped in a `HystrixCommand`, but `execute()` is automatically called for you.

```java
interface YourApi {
  @RequestLine("GET /yourtype/{id}")
  HystrixCommand<YourType> getYourType(@Param("id") String id);

  @RequestLine("GET /yourtype/{id}")
  Observable<YourType> getYourTypeObservable(@Param("id") String id);

  @RequestLine("GET /yourtype/{id}")
  Single<YourType> getYourTypeSingle(@Param("id") String id);

  @RequestLine("GET /yourtype/{id}")
  YourType getYourTypeSynchronous(@Param("id") String id);
}

YourApi api = HystrixFeign.builder()
                  .target(YourApi.class, "https://example.com");

// for reactive
api.getYourType("a").toObservable

// or apply hystrix to RxJava methods
api.getYourTypeObservable("a")

// for asynchronous
api.getYourType("a").queue();

// for synchronous
api.getYourType("a").execute();

// or to apply hystrix to existing feign methods.
api.getYourTypeSynchronous("a");
```

### Group and Command keys

By default, Hystrix group keys match the target name, and the target name is usually the base url.
Hystrix command keys are the same as logging keys, which are equivalent to javadoc references.

For example, for the canonical GitHub example...

* the group key would be "https://api.github.com" and
* the command key would be "GitHub#contributors(String,String)"

You can use `HystrixFeign.Builder#setterFactory(SetterFactory)` to customize this, for example, to
read key mappings from configuration or annotations.

Ex.
```java
SetterFactory commandKeyIsRequestLine = (target, method) -> {
  String groupKey = target.name();
  String commandKey = method.getAnnotation(RequestLine.class).value();
  return HystrixCommand.Setter
      .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
      .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));
};

api = HystrixFeign.builder()
                  .setterFactory(commandKeyIsRequestLine)
                  ...
```

### Fallback support

Fallbacks are known values, which you return when there's an error invoking an http method.
For example, you can return a cached result as opposed to raising an error to the caller. To use
this feature, pass a safe implementation of your target interface as the last parameter to `HystrixFeign.Builder.target`.

Here's an example:

```java
// When dealing with fallbacks, it is less tedious to keep interfaces small.
interface GitHub {
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  List<String> contributors(@Param("owner") String owner, @Param("repo") String repo);
}

// This instance will be invoked if there are errors of any kind.
GitHub fallback = (owner, repo) -> {
  if (owner.equals("Netflix") && repo.equals("feign")) {
    return Arrays.asList("stuarthendren"); // inspired this approach!
  } else {
    return Collections.emptyList();
  }
};

GitHub github = HystrixFeign.builder()
                            ...
                            .target(GitHub.class, "https://api.github.com", fallback);
```

#### Considering the cause

The cause of the fallback is logged by default to FINE level. You can programmatically inspect
the cause by making your own `FallbackFactory`. In many cases, the cause will be a `FeignException`,
which includes the http status.

Here's an example of using `FallbackFactory`:

```java
// This instance will be invoked if there are errors of any kind.
FallbackFactory<GitHub> fallbackFactory = cause -> (owner, repo) -> {
  if (cause instanceof FeignException && ((FeignException) cause).status() == 403) {
    return Collections.emptyList();
  } else {
    return Arrays.asList("yogi");
  }
};

GitHub github = HystrixFeign.builder()
                            ...
                            .target(GitHub.class, "https://api.github.com", fallbackFactory);
```
