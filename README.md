# Feign makes writing java http clients easier

[![Join the chat at https://gitter.im/Netflix/feign](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/Netflix/feign?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/Netflix/feign.svg?branch=master)](https://travis-ci.org/Netflix/feign)

Feign is a java to http client binder inspired by [Retrofit](https://github.com/square/retrofit), [JAXRS-2.0](https://jax-rs-spec.java.net/nonav/2.0/apidocs/index.html), and [WebSocket](http://www.oracle.com/technetwork/articles/java/jsr356-1937161.html).  Feign's first goal was reducing the complexity of binding [Denominator](https://github.com/Netflix/Denominator) uniformly to http apis regardless of [restfulness](http://www.slideshare.net/adrianfcole/99problems).

### Why Feign and not X?

You can use tools like Jersey and CXF to write java clients for ReST or SOAP services.  You can write your own code on top of http transport libraries like Apache HC.  Feign aims to connect your code to http apis with minimal overhead and code. Via customizable decoders and error handling, you should be able to write to any text-based http api.

### How does Feign work?

Feign works by processing annotations into a templatized request.  Just before sending it off, arguments are applied to these templates in a straightforward fashion.  While this limits Feign to only supporting text-based apis, it dramatically simplified system aspects such as replaying requests.  It is also stupid easy to unit test your conversions knowing this.

### Basics

Usage typically looks like this, an adaptation of the [canonical Retrofit sample](https://github.com/square/retrofit/blob/master/samples/src/main/java/com/example/retrofit/SimpleService.java).

```java
interface GitHub {
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
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
  Account getAccountInfo(@Param("id") String id);
}
...
Bank bank = Feign.builder().decoder(new AccountDecoder()).target(Bank.class, "https://api.examplebank.com");
```

### Multiple Interfaces
Feign can produce multiple api interfaces.  These are defined as `Target<T>` (default `HardCodedTarget<T>`), which allow for dynamic discovery and decoration of requests prior to execution.

For example, the following pattern might decorate each request with the current url and auth token from the identity service.

```java
Feign feign = Feign.builder().build();
CloudDNS cloudDNS = feign.target(new CloudIdentityTarget<CloudDNS>(user, apiKey));
```

### Examples
Feign includes example [GitHub](./example-github) and [Wikipedia](./example-wikipedia) clients. The denominator project can also be scraped for Feign in practice. Particularly, look at its [example daemon](https://github.com/Netflix/denominator/tree/master/example-daemon).

### Integrations
Feign intends to work well within Netflix and other Open Source communities.  Modules are welcome to integrate with your favorite projects!

### Gson
[Gson](./gson) includes an encoder and decoder you can use with a JSON API.

Add `GsonEncoder` and/or `GsonDecoder` to your `Feign.Builder` like so:

```java
GsonCodec codec = new GsonCodec();
GitHub github = Feign.builder()
                     .encoder(new GsonEncoder())
                     .decoder(new GsonDecoder())
                     .target(GitHub.class, "https://api.github.com");
```

### Jackson
[Jackson](./jackson) includes an encoder and decoder you can use with a JSON API.

Add `JacksonEncoder` and/or `JacksonDecoder` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .encoder(new JacksonEncoder())
                     .decoder(new JacksonDecoder())
                     .target(GitHub.class, "https://api.github.com");
```

### Sax
[SaxDecoder](./sax) allows you to decode XML in a way that is compatible with normal JVM and also Android environments.

Here's an example of how to configure Sax response parsing:
```java
api = Feign.builder()
           .decoder(SAXDecoder.builder()
                              .registerContentHandler(UserIdHandler.class)
                              .build())
           .target(Api.class, "https://apihost");
```

### JAXB
[JAXB](./jaxb) includes an encoder and decoder you can use with an XML API.

Add `JAXBEncoder` and/or `JAXBDecoder` to your `Feign.Builder` like so:

```java
api = Feign.builder()
           .encoder(new JAXBEncoder())
           .decoder(new JAXBDecoder())
           .target(Api.class, "https://apihost");
```

### JAX-RS
[JAXRSContract](./jaxrs) overrides annotation processing to instead use standard ones supplied by the JAX-RS specification.  This is currently targeted at the 1.1 spec.

Here's the example above re-written to use JAX-RS:
```java
interface GitHub {
  @GET @Path("/repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@PathParam("owner") String owner, @PathParam("repo") String repo);
}
```
```java
GitHub github = Feign.builder()
                     .contract(new JAXRSContract())
                     .target(GitHub.class, "https://api.github.com");
```
### OkHttp
[OkHttpClient](./okhttp) directs Feign's http requests to [OkHttp](http://square.github.io/okhttp/), which enables SPDY and better network control.

To use OkHttp with Feign, add the OkHttp module to your classpath. Then, configure Feign to use the OkHttpClient:

```java
GitHub github = Feign.builder()
                     .client(new OkHttpClient())
                     .target(GitHub.class, "https://api.github.com");
```

### Ribbon
[RibbonClient](./ribbon) overrides URL resolution of Feign's client, adding smart routing and resiliency capabilities provided by [Ribbon](https://github.com/Netflix/ribbon).

Integration requires you to pass your ribbon client name as the host part of the url, for example `myAppProd`.
```java
MyService api = Feign.builder().client(RibbonClient.create()).target(MyService.class, "https://myAppProd");

```

### Hystrix
[HystrixFeign](./hystrix) configures circuit breaker support provided by [Hystrix](https://github.com/Netflix/Hystrix).

To use Hystrix with Feign, add the Hystrix module to your classpath. Then use the `HystrixFeign` builder:

```java
MyService api = HystrixFeign.builder().target(MyService.class, "https://myAppProd");

```

### SLF4J
[SLF4JModule](./slf4j) allows directing Feign's logging to [SLF4J](http://www.slf4j.org/), allowing you to easily use a logging backend of your choice (Logback, Log4J, etc.)

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
The simplest way to send a request body to a server is to define a `POST` method that has a `String` or `byte[]` parameter without any annotations on it. You will likely need to add a `Content-Type` header.

```java
interface LoginClient {
  @RequestLine("POST /")
  @Headers("Content-Type: application/json")
  void login(String content);
}
...
client.login("{\"user_name\": \"denominator\", \"password\": \"secret\"}");
```

By configuring an `Encoder`, you can send a type-safe request body. Here's an example using the `feign-gson` extension:

```java
static class Credentials {
  final String user_name;
  final String password;

  Credentials(String user_name, String password) {
    this.user_name = user_name;
    this.password = password;
  }
}

interface LoginClient {
  @RequestLine("POST /")
  void login(Credentials creds);
}
...
LoginClient client = Feign.builder()
                          .encoder(new GsonEncoder())
                          .target(LoginClient.class, "https://foo.com");

client.login(new Credentials("denominator", "secret"));
```

### @Body templates
The `@Body` annotation indicates a template to expand using parameters annotated with `@Param`. You will likely need to add a `Content-Type` header.

```java
interface LoginClient {

  @RequestLine("POST /")
  @Headers("Content-Type: application/xml")
  @Body("<login \"user_name\"=\"{user_name}\" \"password\"=\"{password}\"/>")
  void xml(@Param("user_name") String user, @Param("password") String password);

  @RequestLine("POST /")
  @Headers("Content-Type: application/json")
  // json curly braces must be escaped!
  @Body("%7B\"user_name\": \"{user_name}\", \"password\": \"{password}\"%7D")
  void json(@Param("user_name") String user, @Param("password") String password);
}
...
client.xml("denominator", "secret"); // <login "user_name"="denominator" "password"="secret"/>
client.json("denominator", "secret"); // {"user_name": "denominator", "password": "secret"}
```

### Headers
Feign supports settings headers on requests either as part of the api or as part of the client
depending on the use case.

#### Set headers using apis
In cases where specific interfaces or calls should always have certain header values set, it
makes sense to define headers as part of the api.

Static headers can be set on an api interface or method using the `@Headers` annotation.

```java
@Headers("Accept: application/json")
interface BaseApi<V> {
  @Headers("Content-Type: application/json")
  @RequestLine("PUT /api/{key}")
  void put(@Param("key") String, V value);
}
```

Methods can specify dynamic content for static headers using variable expansion in `@Headers`.

```java
 @RequestLine("POST /")
 @Headers("X-Ping: {token}")
 void post(@Param("token") String token);
```

In cases where both the header field keys and values are dynamic and the range of possible keys cannot
be known ahead of time and may vary between different method calls in the same api/client (e.g. custom
metadata header fields such as "x-amz-meta-\*" or "x-goog-meta-\*"), a Map parameter can be annotated
with `HeaderMap` to construct a query that uses the contents of the map as its header parameters.

```java
 @RequestLine("POST /")
 void post(@HeaderMap Map<String, Object> headerMap);
```

These approaches specify header entries as part of the api and do not require any customizations
when building the Feign client.

#### Setting headers per target
In cases where headers should differ for the same api based on different endpoints or where per-request
customization is required, headers can be set as part of the client using a `RequestInterceptor` or a
`Target`.

For an example of setting headers using a `RequestInterceptor`, see the `Request Interceptors` section.

Headers can be set as part of a custom `Target`.

```java
  static class DynamicAuthTokenTarget<T> implements Target<T> {
    public DynamicAuthTokenTarget(Class<T> clazz,
                                  UrlAndTokenProvider provider,
                                  ThreadLocal<String> requestIdProvider);
    ...
    @Override
    public Request apply(RequestTemplate input) {
      TokenIdAndPublicURL urlAndToken = provider.get();
      if (input.url().indexOf("http") != 0) {
        input.insert(0, urlAndToken.publicURL);
      }
      input.header("X-Auth-Token", urlAndToken.tokenId);
      input.header("X-Request-ID", requestIdProvider.get());

      return input.request();
    }
  }
  ...
  Bank bank = Feign.builder()
          .target(new DynamicAuthTokenTarget(Bank.class, provider, requestIdProvider));
```

These approaches depend on the custom `RequestInterceptor` or `Target` being set on the Feign
client when it is built and can be used as a way to set headers on all api calls on a per-client
basis. This can be useful for doing things such as setting an authentication token in the header
of all api requests on a per-client basis. The methods are run when the api call is made on the
thread that invokes the api call, which allows the headers to be set dynamically at call time and
in a context-specific manner -- for example, thread-local storage can be used to set different
header values depending on the invoking thread, which can be useful for things such as setting
thread-specific trace identifiers for requests.

### Advanced usage

#### Base Apis
In many cases, apis for a service follow the same conventions. Feign supports this pattern via single-inheritance interfaces.

Consider the example:
```java
interface BaseAPI {
  @RequestLine("GET /health")
  String health();

  @RequestLine("GET /all")
  List<Entity> all();
}
```

You can define and target a specific api, inheriting the base methods.
```java
interface CustomAPI extends BaseAPI {
  @RequestLine("GET /custom")
  String custom();
}
```

In many cases, resource representations are also consistent. For this reason, type parameters are supported on the base api interface.

```java
@Headers("Accept: application/json")
interface BaseApi<V> {

  @RequestLine("GET /api/{key}")
  V get(@Param("key") String);

  @RequestLine("GET /api")
  List<V> list();

  @Headers("Content-Type: application/json")
  @RequestLine("PUT /api/{key}")
  void put(@Param("key") String, V value);
}

interface FooApi extends BaseApi<Foo> { }

interface BarApi extends BaseApi<Bar> { }
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

The SLF4JLogger (see above) may also be of interest.


#### Request Interceptors
When you need to change all requests, regardless of their target, you'll want to configure a `RequestInterceptor`.
For example, if you are acting as an intermediary, you might want to propagate the `X-Forwarded-For` header.

```java
static class ForwardedForInterceptor implements RequestInterceptor {
  @Override public void apply(RequestTemplate template) {
    template.header("X-Forwarded-For", "origin.host.com");
  }
}
...
Bank bank = Feign.builder()
                 .decoder(accountDecoder)
                 .requestInterceptor(new ForwardedForInterceptor())
                 .target(Bank.class, "https://api.examplebank.com");
```

Another common example of an interceptor would be authentication, such as using the built-in `BasicAuthRequestInterceptor`.

```java
Bank bank = Feign.builder()
                 .decoder(accountDecoder)
                 .requestInterceptor(new BasicAuthRequestInterceptor(username, password))
                 .target(Bank.class, "https://api.examplebank.com");
```

#### Custom @Param Expansion
Parameters annotated with `Param` expand based on their `toString`. By
specifying a custom `Param.Expander`, users can control this behavior,
for example formatting dates.

```java
@RequestLine("GET /?since={date}") Result list(@Param(value = "date", expander = DateToMillis.class) Date date);
```

#### Dynamic Query Parameters
A Map parameter can be annotated with `QueryMap` to construct a query that uses the contents of the map as its query parameters.

```java
@RequestLine("GET /find")
V find(@QueryMap Map<String, Object> queryMap);
```

#### Static and Default Methods
Interfaces targeted by Feign may have static or default methods (if using Java 8+).
These allows Feign clients to contain logic that is not expressly defined by the underlying API.
For example, static methods make it easy to specify common client build configurations; default methods can be used to compose queries or define default parameters.

```java
interface GitHub {
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

  @RequestLine("GET /users/{username}/repos?sort={sort}")
  List<Repo> repos(@Param("username") String owner, @Param("sort") String sort);

  default List<Repo> repos(String owner) {
    return repos(owner, "full_name");
  }

  /**
   * Lists all contributors for all repos owned by a user.
   */
  default List<Contributor> contributors(String user) {
    MergingContributorList contributors = new MergingContributorList();
    for(Repo repo : this.repos(owner)) {
      contributors.addAll(this.contributors(user, repo.getName()));
    }
    return contributors.mergeResult();
  }

  static GitHub connect() {
    return Feign.builder()
                .decoder(new GsonDecoder())
                .target(GitHub.class, "https://api.github.com");
  }
}
```
