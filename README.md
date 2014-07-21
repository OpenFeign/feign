# Feign makes writing java http clients easier
Feign is a java to http client binder inspired by [Dagger](https://github.com/square/dagger), [Retrofit](https://github.com/square/retrofit), [JAXRS-2.0](https://jax-rs-spec.java.net/nonav/2.0/apidocs/index.html), and [WebSocket](http://www.oracle.com/technetwork/articles/java/jsr356-1937161.html).  Feign's first goal was reducing the complexity of binding [Denominator](https://github.com/Netflix/Denominator) uniformly to http apis regardless of [restfulness](http://www.slideshare.net/adrianfcole/99problems).

### Why Feign and not X?

You can use tools like Jersey and CXF to write java clients for ReST or SOAP services.  You can write your own code on top of http transport libraries like Apache HC.  Feign aims to connect your code to http apis with minimal overhead and code. Via customizable decoders and error handling, you should be able to write to any text-based http api.

### How does Feign work?

Feign works by processing annotations into a templatized request.  Just before sending it off, arguments are applied to these templates in a straightforward fashion.  While this limits Feign to only supporting text-based apis, it dramatically simplified system aspects such as replaying requests.  It is also stupid easy to unit test your conversions knowing this.

### Basics

Usage typically looks like this, an adaptation of the [canonical Retrofit sample](https://github.com/square/retrofit/blob/master/retrofit-samples/github-client/src/main/java/com/example/retrofit/GitHubClient.java).

```java
interface GitHub {
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@Named("owner") String owner, @Named("repo") String repo);
}

static class Contributor {
  String login;
  int contributions;
}

public static void main(String... args) {
  GitHub github = Feign.builder()
                       .decoder(new GsonDecoder())
                       .target(GitHub.class, "https://api.github.com");

  // Fetch and print a list of the contributors to this library.
  List<Contributor> contributors = github.contributors("netflix", "feign");
  for (Contributor contributor : contributors) {
    System.out.println(contributor.login + " (" + contributor.contributions + ")");
  }
}
```

### Customization

Feign has several aspects that can be customized.  For simple cases, you can use `Feign.builder()` to construct an API interface with your custom components.  For example:

```java
interface Bank {
  @RequestLine("POST /account/{id}")
  Account getAccountInfo(@Named("id") String id);
}
...
Bank bank = Feign.builder().decoder(new AccountDecoder()).target(Bank.class, "https://api.examplebank.com");
```

For further flexibility, you can use Dagger modules directly.  See the `Dagger` section for more details.

### Request Interceptors
When you need to change all requests, regardless of their target, you'll want to configure a `RequestInterceptor`.
For example, if you are acting as an intermediary, you might want to propagate the `X-Forwarded-For` header.

```java
static class ForwardedForInterceptor implements RequestInterceptor {
  @Override public void apply(RequestTemplate template) {
    template.header("X-Forwarded-For", "origin.host.com");
  }
}
...
Bank bank = Feign.builder().decoder(accountDecoder).requestInterceptor(new ForwardedForInterceptor()).target(Bank.class, "https://api.examplebank.com");
```

Another common example of an interceptor would be authentication, such as using the built-in `BasicAuthRequestInterceptor`.

```java
Bank bank = Feign.builder().decoder(accountDecoder).requestInterceptor(new BasicAuthRequestInterceptor(username, password)).target(Bank.class, "https://api.examplebank.com");
```

### Multiple Interfaces
Feign can produce multiple api interfaces.  These are defined as `Target<T>` (default `HardCodedTarget<T>`), which allow for dynamic discovery and decoration of requests prior to execution.

For example, the following pattern might decorate each request with the current url and auth token from the identity service.

```java
CloudDNS cloudDNS = Feign.builder().target(new CloudIdentityTarget<CloudDNS>(user, apiKey));
```

You can find [several examples](https://github.com/Netflix/feign/tree/master/core/src/test/java/feign/examples) in the test tree.  Do take time to look at them, as seeing is believing!

### Integrations
Feign intends to work well within Netflix and other Open Source communities.  Modules are welcome to integrate with your favorite projects!

### Gson
[GsonModule](https://github.com/Netflix/feign/tree/master/gson) adds default encoders and decoders so you get get started with a JSON api.

Add `GsonEncoder` and/or `GsonDecoder` to your `Feign.Builder` like so:

```java
GsonCodec codec = new GsonCodec();
GitHub github = Feign.builder()
                     .encoder(new GsonEncoder())
                     .decoder(new GsonDecoder())
                     .target(GitHub.class, "https://api.github.com");
```

### Jackson
[JacksonModule](https://github.com/Netflix/feign/tree/master/jackson) adds an encoder and decoder you can use with a JSON API.

Add `JacksonEncoder` and/or `JacksonDecoder` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .encoder(new JacksonEncoder())
                     .decoder(new JacksonDecoder())
                     .target(GitHub.class, "https://api.github.com");
```

### Sax
[SaxDecoder](https://github.com/Netflix/feign/tree/master/sax) allows you to decode XML in a way that is compatible with normal JVM and also Android environments.

Here's an example of how to configure Sax response parsing:
```java
api = Feign.builder()
           .decoder(SAXDecoder.builder()
                              .registerContentHandler(UserIdHandler.class)
                              .build())
           .target(Api.class, "https://apihost");
```

### JAXB
[JAXBModule](https://github.com/Netflix/feign/tree/master/jaxb) allows you to encode and decode XML using JAXB.

Add `JAXBEncoder` and/or `JAXBDecoder` to your `Feign.Builder` like so:

```java
api = Feign.builder()
           .encoder(new JAXBEncoder())
           .decoder(new JAXBDecoder())
           .target(Api.class, "https://apihost");
```

### JAX-RS
[JAXRSModule](https://github.com/Netflix/feign/tree/master/jaxrs) overrides annotation processing to instead use standard ones supplied by the JAX-RS specification.  This is currently targeted at the 1.1 spec.

Here's the example above re-written to use JAX-RS:
```java
interface GitHub {
  @GET @Path("/repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@PathParam("owner") String owner, @PathParam("repo") String repo);
}
```
### Ribbon
[RibbonModule](https://github.com/Netflix/feign/tree/master/ribbon) overrides URL resolution of Feign's client, adding smart routing and resiliency capabilities provided by [Ribbon](https://github.com/Netflix/ribbon).

Integration requires you to pass your ribbon client name as the host part of the url, for example `myAppProd`.
```java
MyService api = Feign.create(MyService.class, "https://myAppProd", new RibbonModule());
```

### SLF4J
[SLF4JModule](https://github.com/Netflix/feign/tree/master/slf4j) allows directing Feign's logging to [SLF4J](http://www.slf4j.org/), allowing you to easily use a logging backend of your choice (Logback, Log4J, etc.)

To use SLF4J with Feign, add both the SLF4J module and an SLF4J binding of your choice to your classpath.  Then, configure Feign to use the Slf4jLogger:

```java
GitHub github = Feign.builder()
                     .logger(new Slf4jLogger())
                     .target(GitHub.class, "https://api.github.com");
```

### Decoders
`Feign.builder()` allows you to specify additional configuration such as how to decode a response.

If any methods in your interface return types besides `Response`, `String`, `byte[]` or `void`, you'll need to configure a non-default `Decoder`.

Here's how to configure JSON decoding (using the `feign-gson` extension):

```java
GitHub github = Feign.builder()
                     .decoder(new GsonDecoder())
                     .target(GitHub.class, "https://api.github.com");
```

### Encoders
`Feign.builder()` allows you to specify additional configuration such as how to encode a request.

If any methods in your interface use parameters types besides `String` or `byte[]`, you'll need to configure a non-default `Encoder`.

Here's how to configure JSON encoding (using the `feign-gson` extension):

```json
GitHub github = Feign.builder()
                     .encoder(new GsonEncoder())
                     .target(GitHub.class, "https://api.github.com");
```

### Advanced usage and Dagger
#### Dagger
Feign can be directly wired into Dagger which keeps things at compile time and Android friendly.  As opposed to exposing builders for config, Feign intends users to embed their config in Dagger.

Where possible, Feign configuration uses normal Dagger conventions.  For example, `RequestInterceptor` bindings are of `Provider.Type.SET`, meaning you can have multiple interceptors.  Here's an example of multiple interceptor bindings.
```java
@Provides(type = SET) RequestInterceptor forwardedForInterceptor() {
  return new RequestInterceptor() {
    @Override public void apply(RequestTemplate template) {
      template.header("X-Forwarded-For", "origin.host.com");
    }
  };
}

@Provides(type = SET) RequestInterceptor userAgentInterceptor() {
  return new RequestInterceptor() {
    @Override public void apply(RequestTemplate template) {
      template.header("User-Agent", "My Cool Client");
    }
  };
}
```
#### Logging
You can log the http messages going to and from the target by setting up a `Logger`.  Here's the easiest way to do that:
```java
GitHub github = Feign.builder()
                     .decoder(new GsonDecoder())
                     .logger(new Logger.JavaLogger().appendToFile("logs/http.log"))
                     .logLevel(Logger.Level.FULL)
                     .target(GitHub.class, "https://api.github.com");
```

The SLF4JModule (see above) may also be of interest.
