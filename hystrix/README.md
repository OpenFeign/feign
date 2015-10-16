Hystrix
===================

This module wraps Feign's http requests in [Hystrix](https://github.com/Netflix/Hystrix/), which enables the [Circuit Breaker Pattern](https://en.wikipedia.org/wiki/Circuit_breaker_design_pattern).

To use Hystrix with Feign, add the Hystrix module to your classpath. Then, configure Feign to use the `HystrixInvocationHandler`:

```java
GitHub github = HystrixFeign.builder()
        .target(GitHub.class, "https://api.github.com");
```

Methods that do *not* return [`HystrixCommand`](https://netflix.github.io/Hystrix/javadoc/com/netflix/hystrix/HystrixCommand.html) are still wrapped in a `HystrixCommand`, but `execute()` is automatically called for you.

For asynchronous or reactive use, return `HystrixCommand<YourType>` rather than just `YourType`.

```java
interface YourApi {
  @RequestLine("GET /yourtype/{id}")
  HystrixCommand<YourType> getYourType(@Param("id") String id);
  
  @RequestLine("GET /yourtype/{id}")
  YourType getYourTypeSynchronous(@Param("id") String id);
}

YourApi api = HystrixFeign.builder()
                  .target(YourApi.class, "https://example.com");

// for reactive
api.getYourType("a").toObservable();

// for asynchronous
api.getYourType("a").queue();

// for synchronous
api.getYourType("a").execute();

// or to apply hystrix to existing feign methods.
api.getYourTypeSynchronous("a");
``` 