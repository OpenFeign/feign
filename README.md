# Feign makes writing java http clients easier

[![Join the chat at https://gitter.im/OpenFeign/feign](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/OpenFeign/feign?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/OpenFeign/feign.svg?branch=master)](https://travis-ci.org/OpenFeign/feign)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.openfeign/feign-core/badge.png)](https://search.maven.org/artifact/io.github.openfeign/feign-core/)

Feign is a Java to HTTP client binder inspired by [Retrofit](https://github.com/square/retrofit), [JAXRS-2.0](https://jax-rs-spec.java.net/nonav/2.0/apidocs/index.html), and [WebSocket](http://www.oracle.com/technetwork/articles/java/jsr356-1937161.html).  Feign's first goal was reducing the complexity of binding [Denominator](https://github.com/Netflix/Denominator) uniformly to HTTP APIs regardless of [ReSTfulness](http://www.slideshare.net/adrianfcole/99problems).

### Why Feign and not X?

Feign uses tools like Jersey and CXF to write java clients for ReST or SOAP services. Furthermore, Feign allows you to write your own code on top of http libraries such as Apache HC. Feign connects your code to http APIs with minimal overhead and code via customizable decoders and error handling, which can be written to any text-based http API.

### How does Feign work?

Feign works by processing annotations into a templatized request. Arguments are applied to these templates in a straightforward fashion before output.  Although Feign is limited to supporting text-based APIs, it dramatically simplifies system aspects such as replaying requests. Furthermore, Feign makes it easy to unit test your conversions knowing this.

### Java Version Compatibility

Feign 10.x and above are built on Java 8 and should work on Java 9, 10, and 11.  For those that need JDK 6 compatibility, please use Feign 9.x

### Basics

Usage typically looks like this, an adaptation of the [canonical Retrofit sample](https://github.com/square/retrofit/blob/master/samples/src/main/java/com/example/retrofit/SimpleService.java).

```java
interface GitHub {
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}

public static class Contributor {
  String login;
  int contributions;
}

public class MyApp {
  public static void main(String... args) {
    GitHub github = Feign.builder()
                         .decoder(new GsonDecoder())
                         .target(GitHub.class, "https://api.github.com");
  
    // Fetch and print a list of the contributors to this library.
    List<Contributor> contributors = github.contributors("OpenFeign", "feign");
    for (Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }
  }
}
```

### Interface Annotations

Feign annotations define the `Contract` between the interface and how the underlying client
should work.  Feign's default contract defines the following annotations:

| Annotation     | Interface Target | Usage |
|----------------|------------------|-------|
| `@RequestLine` | Method           | Defines the `HttpMethod` and `UriTemplate` for request.  `Expressions`, values wrapped in curly-braces `{expression}` are resolved using their corresponding `@Param` annotated parameters. |
| `@Param`       | Parameter        | Defines a template variable, whose value will be used to resolve the corresponding template `Expression`, by name. |
| `@Headers`     | Method, Type     | Defines a `HeaderTemplate`; a variation on a `UriTemplate`.  that uses `@Param` annotated values to resolve the corresponding `Expressions`.  When used on a `Type`, the template will be applied to every request.  When used on a `Method`, the template will apply only to the annotated method. |
| `@QueryMap`    | Parameter        | Defines a `Map` of name-value pairs, or POJO, to expand into a query string. |
| `@HeaderMap`   | Parameter        | Defines a `Map` of name-value pairs, to expand into `Http Headers` |
| `@Body`        | Method           | Defines a `Template`, similar to a `UriTemplate` and `HeaderTemplate`, that uses `@Param` annotated values to resolve the corresponding `Expressions`.|

### Templates and Expressions

Feign `Expressions` represent Simple String Expressions (Level 1) as defined by [URI Template - RFC 6570](https://tools.ietf.org/html/rfc6570).  `Expressions` are expanded using
their corresponding `Param` annotated method parameters.  

*Example*

```java
public interface GitHub {
  
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  List<Contributor> getContributors(@Param("owner") String owner, @Param("repo") String repository);
  
  class Contributor {
    String login;
    int contributions;
  }
}

public class MyApp {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                         .decoder(new GsonDecoder())
                         .target(GitHub.class, "https://api.github.com");
    
    /* The owner and repository parameters will be used to expand the owner and repo expressions
     * defined in the RequestLine.
     * 
     * the resulting uri will be https://api.github.com/repos/OpenFeign/feign/contributors
     */
    github.contributors("OpenFeign", "feign");
  }
}
```

Expressions must be enclosed in curly braces `{}` and may contain regular expression patterns, separated by a colon `:`  to restrict
resolved values.  *Example* `owner` must be alphabetic. `{owner:[a-zA-Z]*}`

#### Request Parameter Expansion

`RequestLine` and `QueryMap` templates follow the [URI Template - RFC 6570](https://tools.ietf.org/html/rfc6570) specification for Level 1 templates, which specifies the following:

* Unresolved expressions are omitted.
* All literals and variable values are pct-encoded, if not already encoded or marked `encoded` via a `@Param` annotation.

See [Advanced Usage](#advanced-usage) for more examples.

> **What about slashes? `/`**
>
> `@RequestLine` and `@QueryMap` templates do not encode slash `/` characters by default.  To change this behavior, set the `decodeSlash` property on the `@RequestLine` to `false`.  

##### Custom Expansion

The `@Param` annotation has an optional property `expander` allowing for complete control over the individual parameter's expansion.
The `expander` property must reference a class that implements the `Expander` interface:

```java
public interface Expander {
    String expand(Object value);
}
```
The result of this method adheres to the same rules stated above.  If the result is `null` or an empty string,
the value is omitted.  If the value is not pct-encoded, it will be.  See [Custom @Param Expansion](#custom-param-expansion) for more examples.

#### Request Headers Expansion 

`Headers` and `HeaderMap` templates follow the same rules as [Request Parameter Expansion](#request-parameter-expansion) 
with the following alterations:

* Unresolved expressions are omitted.  If the result is an empty header value, the entire header is removed.
* No pct-encoding is performed.

See [Headers](#headers) for examples.

> **A Note on `@Param` parameters and their names**: 
>
> All expressions with the same name, regardless of their position on the `@RequestLine`, `@QueryMap`, `@BodyTemplate`, or `@Headers` will resolve to the same value.
> In the following example, the value of `contentType`, will be used to resolve both the header and path expression:
>
> ```java
> public interface ContentService {
>   @RequestLine("GET /api/documents/{contentType}")
>   @Headers("Accept {contentType}")
>   String getDocumentByType(@Param("contentType") String type);
> }
>```
> 
> Keep this in mind when designing your interfaces.

#### Request Body Expansion

`Body` templates follow the same rules as [Request Parameter Expansion](#request-parameter-expansion) 
with the following alterations:

* Unresolved expressions are omitted.
* Expanded value will **not** be passed through an `Encoder` before being placed on the request body.
* A `Content-Type` header must be specified.  See [Body Templates](#body-templates) for examples.

---
### Customization

Feign has several aspects that can be customized.  For simple cases, you can use `Feign.builder()` to construct an API interface with your custom components.  For example:

```java
interface Bank {
  @RequestLine("POST /account/{id}")
  Account getAccountInfo(@Param("id") String id);
}

public class BankService {
  public static void main(String[] args) {
    Bank bank = Feign.builder().decoder(
        new AccountDecoder())
        .target(Bank.class, "https://api.examplebank.com");
  }
}
```

### Multiple Interfaces
Feign can produce multiple api interfaces.  These are defined as `Target<T>` (default `HardCodedTarget<T>`), which allow for dynamic discovery and decoration of requests prior to execution.

For example, the following pattern might decorate each request with the current url and auth token from the identity service.

```java
public class CloudService {
  public static void main(String[] args) {
    CloudDNS cloudDNS = Feign.builder()
      .target(new CloudIdentityTarget<CloudDNS>(user, apiKey));
  }
  
  class CloudIdentityTarget extends Target<CloudDNS> {
    /* implementation of a Target */
  }
}
```

### Examples
Feign includes example [GitHub](./example-github) and [Wikipedia](./example-wikipedia) clients. The denominator project can also be scraped for Feign in practice. Particularly, look at its [example daemon](https://github.com/Netflix/denominator/tree/master/example-daemon).

---
### Integrations
Feign intends to work well with other Open Source tools.  Modules are welcome to integrate with your favorite projects!

### Gson
[Gson](./gson) includes an encoder and decoder you can use with a JSON API.

Add `GsonEncoder` and/or `GsonDecoder` to your `Feign.Builder` like so:

```java
public class Example {
  public static void main(String[] args) {
    GsonCodec codec = new GsonCodec();
    GitHub github = Feign.builder()
                         .encoder(new GsonEncoder())
                         .decoder(new GsonDecoder())
                         .target(GitHub.class, "https://api.github.com");
  }
}
```

### Jackson
[Jackson](./jackson) includes an encoder and decoder you can use with a JSON API.

Add `JacksonEncoder` and/or `JacksonDecoder` to your `Feign.Builder` like so:

```java
public class Example {
  public static void main(String[] args) {
      GitHub github = Feign.builder()
                     .encoder(new JacksonEncoder())
                     .decoder(new JacksonDecoder())
                     .target(GitHub.class, "https://api.github.com");
  }
}
```

### Sax
[SaxDecoder](./sax) allows you to decode XML in a way that is compatible with normal JVM and also Android environments.

Here's an example of how to configure Sax response parsing:
```java
public class Example {
  public static void main(String[] args) {
      Api api = Feign.builder()
         .decoder(SAXDecoder.builder()
                            .registerContentHandler(UserIdHandler.class)
                            .build())
         .target(Api.class, "https://apihost");
    }
}
```

### JAXB
[JAXB](./jaxb) includes an encoder and decoder you can use with an XML API.

Add `JAXBEncoder` and/or `JAXBDecoder` to your `Feign.Builder` like so:

```java
public class Example {
  public static void main(String[] args) {
    Api api = Feign.builder()
             .encoder(new JAXBEncoder())
             .decoder(new JAXBDecoder())
             .target(Api.class, "https://apihost");
  }
}
```

### JAX-RS
[JAXRSContract](./jaxrs) overrides annotation processing to instead use standard ones supplied by the JAX-RS specification.  This is currently targeted at the 1.1 spec.

Here's the example above re-written to use JAX-RS:
```java
interface GitHub {
  @GET @Path("/repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@PathParam("owner") String owner, @PathParam("repo") String repo);
}

public class Example {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                       .contract(new JAXRSContract())
                       .target(GitHub.class, "https://api.github.com");
  }
}
```

### OkHttp
[OkHttpClient](./okhttp) directs Feign's http requests to [OkHttp](http://square.github.io/okhttp/), which enables SPDY and better network control.

To use OkHttp with Feign, add the OkHttp module to your classpath. Then, configure Feign to use the OkHttpClient:

```java
public class Example {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                     .client(new OkHttpClient())
                     .target(GitHub.class, "https://api.github.com");
  }
}
```

### Ribbon
[RibbonClient](./ribbon) overrides URL resolution of Feign's client, adding smart routing and resiliency capabilities provided by [Ribbon](https://github.com/Netflix/ribbon).

Integration requires you to pass your ribbon client name as the host part of the url, for example `myAppProd`.
```java
public class Example {
  public static void main(String[] args) {
    MyService api = Feign.builder()
          .client(RibbonClient.create())
          .target(MyService.class, "https://myAppProd");
  }
}
```

### Java 11 Http2
[Http2Client](./java11) directs Feign's http requests to Java11 [New HTTP/2 Client](http://www.javamagazine.mozaicreader.com/JulyAug2017#&pageSet=39&page=0) that implements HTTP/2.

To use New HTTP/2 Client with Feign, use Java SDK 11. Then, configure Feign to use the Http2Client:

```java
GitHub github = Feign.builder()
                     .client(new Http2Client())
                     .target(GitHub.class, "https://api.github.com");
```

### Hystrix
[HystrixFeign](./hystrix) configures circuit breaker support provided by [Hystrix](https://github.com/Netflix/Hystrix).

To use Hystrix with Feign, add the Hystrix module to your classpath. Then use the `HystrixFeign` builder:

```java
public class Example {
  public static void main(String[] args) {
    MyService api = HystrixFeign.builder().target(MyService.class, "https://myAppProd");
  }
}
```

### SOAP
[SOAP](./soap) includes an encoder and decoder you can use with an XML API.


This module adds support for encoding and decoding SOAP Body objects via JAXB and SOAPMessage. It also provides SOAPFault decoding capabilities by wrapping them into the original `javax.xml.ws.soap.SOAPFaultException`, so that you'll only need to catch `SOAPFaultException` in order to handle SOAPFault.

Add `SOAPEncoder` and/or `SOAPDecoder` to your `Feign.Builder` like so:

```java
public class Example {
  public static void main(String[] args) {
    Api api = Feign.builder()
	     .encoder(new SOAPEncoder(jaxbFactory))
	     .decoder(new SOAPDecoder(jaxbFactory))
	     .errorDecoder(new SOAPErrorDecoder())
	     .target(MyApi.class, "http://api");
  }
}
```

NB: you may also need to add `SOAPErrorDecoder` if SOAP Faults are returned in response with error http codes (4xx, 5xx, ...)

### SLF4J
[SLF4JModule](./slf4j) allows directing Feign's logging to [SLF4J](http://www.slf4j.org/), allowing you to easily use a logging backend of your choice (Logback, Log4J, etc.)

To use SLF4J with Feign, add both the SLF4J module and an SLF4J binding of your choice to your classpath.  Then, configure Feign to use the Slf4jLogger:

```java
public class Example {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                     .logger(new Slf4jLogger())
                     .target(GitHub.class, "https://api.github.com");
  }
}
```

### Decoders
`Feign.builder()` allows you to specify additional configuration such as how to decode a response.

If any methods in your interface return types besides `Response`, `String`, `byte[]` or `void`, you'll need to configure a non-default `Decoder`.

Here's how to configure JSON decoding (using the `feign-gson` extension):

```java
public class Example {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                     .decoder(new GsonDecoder())
                     .target(GitHub.class, "https://api.github.com");
  }
}
```

If you need to pre-process the response before give it to the Decoder, you can use the `mapAndDecode` builder method.
An example use case is dealing with an API that only serves jsonp, you will maybe need to unwrap the jsonp before
send it to the Json decoder of your choice:

```java
public class Example {
  public static void main(String[] args) {
    JsonpApi jsonpApi = Feign.builder()
                         .mapAndDecode((response, type) -> jsopUnwrap(response, type), new GsonDecoder())
                         .target(JsonpApi.class, "https://some-jsonp-api.com");
  }
}
```

### Encoders
The simplest way to send a request body to a server is to define a `POST` method that has a `String` or `byte[]` parameter without any annotations on it. You will likely need to add a `Content-Type` header.

```java
interface LoginClient {
  @RequestLine("POST /")
  @Headers("Content-Type: application/json")
  void login(String content);
}

public class Example {
  public static void main(String[] args) {
    client.login("{\"user_name\": \"denominator\", \"password\": \"secret\"}");
  }
}
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

public class Example {
  public static void main(String[] args) {
    LoginClient client = Feign.builder()
                              .encoder(new GsonEncoder())
                              .target(LoginClient.class, "https://foo.com");
    
    client.login(new Credentials("denominator", "secret"));
  }
}
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

public class Example {
  public static void main(String[] args) {
    client.xml("denominator", "secret"); // <login "user_name"="denominator" "password"="secret"/>
    client.json("denominator", "secret"); // {"user_name": "denominator", "password": "secret"}
  }
}
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
  void put(@Param("key") String key, V value);
}
```

Methods can specify dynamic content for static headers using variable expansion in `@Headers`.

```java
public interface Api {
   @RequestLine("POST /")
   @Headers("X-Ping: {token}")
   void post(@Param("token") String token);
}
```

In cases where both the header field keys and values are dynamic and the range of possible keys cannot
be known ahead of time and may vary between different method calls in the same api/client (e.g. custom
metadata header fields such as "x-amz-meta-\*" or "x-goog-meta-\*"), a Map parameter can be annotated
with `HeaderMap` to construct a query that uses the contents of the map as its header parameters.

```java
public interface Api {
   @RequestLine("POST /")
   void post(@HeaderMap Map<String, Object> headerMap);
}
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
  
  public class Example {
    public static void main(String[] args) {
      Bank bank = Feign.builder()
              .target(new DynamicAuthTokenTarget(Bank.class, provider, requestIdProvider));
    }
  }
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
  V get(@Param("key") String key);

  @RequestLine("GET /api")
  List<V> list();

  @Headers("Content-Type: application/json")
  @RequestLine("PUT /api/{key}")
  void put(@Param("key") String key, V value);
}

interface FooApi extends BaseApi<Foo> { }

interface BarApi extends BaseApi<Bar> { }
```

#### Logging
You can log the http messages going to and from the target by setting up a `Logger`.  Here's the easiest way to do that:
```java
public class Example {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                     .decoder(new GsonDecoder())
                     .logger(new Logger.JavaLogger().appendToFile("logs/http.log"))
                     .logLevel(Logger.Level.FULL)
                     .target(GitHub.class, "https://api.github.com");
  }
}
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

public class Example {
  public static void main(String[] args) {
    Bank bank = Feign.builder()
                 .decoder(accountDecoder)
                 .requestInterceptor(new ForwardedForInterceptor())
                 .target(Bank.class, "https://api.examplebank.com");
  }
}
```

Another common example of an interceptor would be authentication, such as using the built-in `BasicAuthRequestInterceptor`.

```java
public class Example {
  public static void main(String[] args) {
    Bank bank = Feign.builder()
                 .decoder(accountDecoder)
                 .requestInterceptor(new BasicAuthRequestInterceptor(username, password))
                 .target(Bank.class, "https://api.examplebank.com");
  }
}
```

#### Custom @Param Expansion
Parameters annotated with `Param` expand based on their `toString`. By
specifying a custom `Param.Expander`, users can control this behavior,
for example formatting dates.

```java
public interface Api {
  @RequestLine("GET /?since={date}") Result list(@Param(value = "date", expander = DateToMillis.class) Date date);
}
```

#### Dynamic Query Parameters
A Map parameter can be annotated with `QueryMap` to construct a query that uses the contents of the map as its query parameters.

```java
public interface Api {
  @RequestLine("GET /find")
  V find(@QueryMap Map<String, Object> queryMap);
}
```

This may also be used to generate the query parameters from a POJO object using a `QueryMapEncoder`.

```java
public interface Api {
  @RequestLine("GET /find")
  V find(@QueryMap CustomPojo customPojo);
}
```

When used in this manner, without specifying a custom `QueryMapEncoder`, the query map will be generated using member variable names as query parameter names. The following POJO will generate query params of "/find?name={name}&number={number}" (order of included query parameters not guaranteed, and as usual, if any value is null, it will be left out).

```java
public class CustomPojo {
  private final String name;
  private final int number;

  public CustomPojo (String name, int number) {
    this.name = name;
    this.number = number;
  }
}
```

To setup a custom `QueryMapEncoder`:

```java
public class Example {
  public static void main(String[] args) {
    MyApi myApi = Feign.builder()
                 .queryMapEncoder(new MyCustomQueryMapEncoder())
                 .target(MyApi.class, "https://api.hostname.com");
  }
}
```

When annotating objects with @QueryMap, the default encoder uses reflection to inspect provided objects Fields to expand the objects values into a query string. If you prefer that the query string be built using getter and setter methods, as defined in the Java Beans API, please use the BeanQueryMapEncoder

```java
public class Example {
  public static void main(String[] args) {
    MyApi myApi = Feign.builder()
                 .queryMapEncoder(new BeanQueryMapEncoder())
                 .target(MyApi.class, "https://api.hostname.com");
  }
}
```

### Error Handling
If you need more control over handling unexpected responses, Feign instances can
register a custom `ErrorDecoder` via the builder.

```java
public class Example {
  public static void main(String[] args) {
    MyApi myApi = Feign.builder()
                 .errorDecoder(new MyErrorDecoder())
                 .target(MyApi.class, "https://api.hostname.com");
  }
}
```

All responses that result in an HTTP status not in the 2xx range will trigger the `ErrorDecoder`'s `decode` method, allowing
you to handle the response, wrap the failure into a custom exception or perform any additional processing.
If you want to retry the request again, throw a `RetryableException`.  This will invoke the registered
`Retyer`.

### Retry
Feign, by default, will automatically retry `IOException`s, regardless of HTTP method, treating them as transient network
related exceptions, and any `RetryableException` thrown from an `ErrorDecoder`.  To customize this
behavior, register a custom `Retryer` instance via the builder.

```java
public class Example {
  public static void main(String[] args) {
    MyApi myApi = Feign.builder()
                 .retryer(new MyRetryer())
                 .target(MyApi.class, "https://api.hostname.com");
  }
}
```

`Retryer`s are responsible for determining if a retry should occur by returning either a `true` or
`false` from the method `continueOrPropagate(RetryableException e);`  A `Retryer` instance will be 
created for each `Client` execution, allowing you to maintain state bewteen each request if desired.

If the retry is determined to be unsuccessful, the last `RetryException` will be thrown.  To throw the original
cause that led to the unsuccessful retry, build your Feign client with the `exceptionPropagationPolicy()` option.

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
