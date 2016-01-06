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
api.getYourTypeObservable("a").toObservable

// or apply hystrix to RxJava methods
api.getYourTypeObservable("a")

// for asynchronous
api.getYourType("a").queue();

// for synchronous
api.getYourType("a").execute();

// or to apply hystrix to existing feign methods.
api.getYourTypeSynchronous("a");
``` 