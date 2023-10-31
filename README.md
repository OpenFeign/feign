# Feign makes writing Java http clients easier

[![Join the chat at https://gitter.im/OpenFeign/feign](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/OpenFeign/feign?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![CircleCI](https://circleci.com/gh/OpenFeign/feign/tree/master.svg?style=svg)](https://circleci.com/gh/OpenFeign/feign/tree/master)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.openfeign/feign-core/badge.png)](https://search.maven.org/artifact/io.github.openfeign/feign-core/)

Feign is a Java to HTTP client binder inspired by [Retrofit](https://github.com/square/retrofit), [JAXRS-2.0](https://jax-rs-spec.java.net/nonav/2.0/apidocs/index.html), and [WebSocket](http://www.oracle.com/technetwork/articles/java/jsr356-1937161.html).  Feign's first goal was reducing the complexity of binding [Denominator](https://github.com/Netflix/Denominator) uniformly to HTTP APIs regardless of [ReSTfulness](http://www.slideshare.net/adrianfcole/99problems).

---
### Why Feign and not X?

Feign uses tools like Jersey and CXF to write Java clients for ReST or SOAP services. Furthermore, Feign allows you to write your own code on top of http libraries such as Apache HC. Feign connects your code to http APIs with minimal overhead and code via customizable decoders and error handling, which can be written to any text-based http API.

### How does Feign work?

Feign works by processing annotations into a templatized request. Arguments are applied to these templates in a straightforward fashion before output.  Although Feign is limited to supporting text-based APIs, it dramatically simplifies system aspects such as replaying requests. Furthermore, Feign makes it easy to unit test your conversions knowing this.

### Java Version Compatibility

Feign 10.x and above are built on Java 8 and should work on Java 9, 10, and 11.  For those that need JDK 6 compatibility, please use Feign 9.x

## Feature overview

This is a map with current key features provided by feign:

![MindMap overview](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/OpenFeign/feign/master/src/docs/overview-mindmap.iuml)

# Roadmap
## Feign 11 and beyond
Making _API_ clients easier

Short Term - What we're working on now. ⏰
---
* Response Caching
  * Support caching of api responses.  Allow for users to define under what conditions a response is eligible for caching and what type of caching mechanism should be used.
  * Support in-memory caching and external cache implementations (EhCache, Google, Spring, etc...)
* Complete URI Template expression support
  * Support [level 1 through level 4](https://tools.ietf.org/html/rfc6570#section-1.2) URI template expressions.
  * Use [URI Templates TCK](https://github.com/uri-templates/uritemplate-test) to verify compliance.
* `Logger` API refactor
  * Refactor the `Logger` API to adhere closer to frameworks like SLF4J providing a common mental model for logging within Feign.  This model will be used by Feign itself throughout and provide clearer direction on how the `Logger` will be used.
* `Retry` API refactor
  * Refactor the `Retry` API to support user-supplied conditions and better control over back-off policies. **This may result in non-backward-compatible breaking changes**

Medium Term - What's up next. ⏲
---
* Async execution support via `CompletableFuture`
  * Allow for `Future` chaining and executor management for the request/response lifecycle.  **Implementation will require non-backward-compatible breaking changes**.  However this feature is required before Reactive execution can be considered.
* Reactive execution support via [Reactive Streams](https://www.reactive-streams.org/)
  * For JDK 9+, consider a native implementation that uses `java.util.concurrent.Flow`.
  * Support for [Project Reactor](https://projectreactor.io/) and [RxJava 2+](https://github.com/ReactiveX/RxJava) implementations on JDK 8.

Long Term - The future ☁️
---
* Additional Circuit Breaker Support.
  * Support additional Circuit Breaker implementations like [Resilience4J](https://resilience4j.readme.io/) and Spring Circuit Breaker

---

# Usage

The feign library is available from [Maven Central](https://central.sonatype.com/artifact/io.github.openfeign/feign-core).

```xml
<dependency>
    <groupId>io.github.openfeign</groupId>
    <artifactId>feign-core</artifactId>
    <version>??feign.version??</version>
</dependency>
```

### Basics

Usage typically looks like this, an adaptation of the [canonical Retrofit sample](https://github.com/square/retrofit/blob/master/samples/src/main/java/com/example/retrofit/SimpleService.java).

```java
interface GitHub {
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

  @RequestLine("POST /repos/{owner}/{repo}/issues")
  void createIssue(Issue issue, @Param("owner") String owner, @Param("repo") String repo);

}

public static class Contributor {
  String login;
  int contributions;
}

public static class Issue {
  String title;
  String body;
  List<String> assignees;
  int milestone;
  List<String> labels;
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
| `@Param`       | Parameter        | Defines a template variable, whose value will be used to resolve the corresponding template `Expression`, by name provided as annotation value. If value is missing it will try to get the name from bytecode method parameter name (if the code was compiled with `-parameters` flag). |
| `@Headers`     | Method, Type     | Defines a `HeaderTemplate`; a variation on a `UriTemplate`.  that uses `@Param` annotated values to resolve the corresponding `Expressions`.  When used on a `Type`, the template will be applied to every request.  When used on a `Method`, the template will apply only to the annotated method. |
| `@QueryMap`    | Parameter        | Defines a `Map` of name-value pairs, or POJO, to expand into a query string. |
| `@HeaderMap`   | Parameter        | Defines a `Map` of name-value pairs, to expand into `Http Headers` |
| `@Body`        | Method           | Defines a `Template`, similar to a `UriTemplate` and `HeaderTemplate`, that uses `@Param` annotated values to resolve the corresponding `Expressions`.|


> **Overriding the Request Line**
>
> If there is a need to target a request to a different host then the one supplied when the Feign client was created, or
> you want to supply a target host for each request, include a `java.net.URI` parameter and Feign will use that value
> as the request target.
>
> ```java
> @RequestLine("POST /repos/{owner}/{repo}/issues")
> void createIssue(URI host, Issue issue, @Param("owner") String owner, @Param("repo") String repo);
> ```
>

### Templates and Expressions

Feign `Expressions` represent Simple String Expressions (Level 1) as defined by [URI Template - RFC 6570](https://tools.ietf.org/html/rfc6570).  `Expressions` are expanded using
their corresponding `Param` annotated method parameters.

*Example*

```java
public interface GitHub {

  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repository);

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

We also have limited support for Level 3, Path Style Expressions, with the following restrictions:

* Maps and Lists are expanded by default.
* Only Single variable templates are supported.

*Examples:*

```
{;who}             ;who=fred
{;half}            ;half=50%25
{;empty}           ;empty
{;list}            ;list=red;list=green;list=blue
{;map}             ;semi=%3B;dot=.;comma=%2C
```

```java
public interface MatrixService {

  @RequestLine("GET /repos{;owners}")
  List<Contributor> contributors(@Param("owners") List<String> owners);

  class Contributor {
    String login;
    int contributions;
  }
}
```

If `owners` in the above example is defined as `Matt, Jeff, Susan`, the uri will expand to `/repos;owners=Matt;owners=Jeff;owners=Susan` 

For more information see [RFC 6570, Section 3.2.7](https://datatracker.ietf.org/doc/html/rfc6570#section-3.2.7)

#### Undefined vs. Empty Values ####

Undefined expressions are expressions where the value for the expression is an explicit `null` or no value is provided.
Per [URI Template - RFC 6570](https://tools.ietf.org/html/rfc6570), it is possible to provide an empty value
for an expression.  When Feign resolves an expression, it first determines if the value is defined, if it is then
the query parameter will remain.  If the expression is undefined, the query parameter is removed.  See below
for a complete breakdown.

*Empty String*
```java
public void test() {
   Map<String, Object> parameters = new LinkedHashMap<>();
   parameters.put("param", "");
   this.demoClient.test(parameters);
}
```
Result
```
http://localhost:8080/test?param=
```

*Missing*
```java
public void test() {
   Map<String, Object> parameters = new LinkedHashMap<>();
   this.demoClient.test(parameters);
}
```
Result
```
http://localhost:8080/test
```

*Undefined*
```java
public void test() {
   Map<String, Object> parameters = new LinkedHashMap<>();
   parameters.put("param", null);
   this.demoClient.test(parameters);
}
```
Result
```
http://localhost:8080/test
```

See [Advanced Usage](#advanced-usage) for more examples.

> **What about slashes? `/`**
>
> @RequestLine templates do not encode slash `/` characters by default.  To change this behavior, set the `decodeSlash` property on the `@RequestLine` to `false`.

> **What about plus? `+`**
>
> Per the URI specification, a `+` sign is allowed in both the path and query segments of a URI, however, handling of
> the symbol on the query can be inconsistent.  In some legacy systems, the `+` is equivalent to the a space.  Feign takes the approach of modern systems, where a
> `+` symbol should not represent a space and is explicitly encoded as `%2B` when found on a query string.
>
> If you wish to use `+` as a space, then use the literal ` ` character or encode the value directly as `%20`

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
>   @Headers("Accept: {contentType}")
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

Feign has several aspects that can be customized.  
For simple cases, you can use `Feign.builder()` to construct an API interface with your custom components.<br>
For request setting, you can use `options(Request.Options options)` on `target()` to set connectTimeout, connectTimeoutUnit, readTimeout, readTimeoutUnit, followRedirects.<br>
For example:

```java
interface Bank {
  @RequestLine("POST /account/{id}")
  Account getAccountInfo(@Param("id") String id);
}

public class BankService {
  public static void main(String[] args) {
    Bank bank = Feign.builder()
        .decoder(new AccountDecoder())
        .options(new Request.Options(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, true))
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

### Encoder/Decoder

#### Gson
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

#### Jackson
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

For the lighter weight Jackson Jr, use `JacksonJrEncoder` and `JacksonJrDecoder` from
the [Jackson Jr Module](./jackson-jr).

#### Moshi
[Moshi](./moshi) includes an encoder and decoder you can use with a JSON API.
Add `MoshiEncoder` and/or `MoshiDecoder` to your `Feign.Builder` like so:

```java
GitHub github = Feign.builder()
                     .encoder(new MoshiEncoder())
                     .decoder(new MoshiDecoder())
                     .target(GitHub.class, "https://api.github.com");
```

#### Sax
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

#### JAXB
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

#### SOAP
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

### Contract

#### JAX-RS
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

### Client

#### OkHttp
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

#### Ribbon
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

#### Java 11 Http2
[Http2Client](./java11) directs Feign's http requests to Java11 [New HTTP/2 Client](https://openjdk.java.net/jeps/321) that implements HTTP/2.

To use New HTTP/2 Client with Feign, use Java SDK 11. Then, configure Feign to use the Http2Client:

```java
GitHub github = Feign.builder()
                     .client(new Http2Client())
                     .target(GitHub.class, "https://api.github.com");
```

### Breaker

#### Hystrix
[HystrixFeign](./hystrix) configures circuit breaker support provided by [Hystrix](https://github.com/Netflix/Hystrix).

To use Hystrix with Feign, add the Hystrix module to your classpath. Then use the `HystrixFeign` builder:

```java
public class Example {
  public static void main(String[] args) {
    MyService api = HystrixFeign.builder().target(MyService.class, "https://myAppProd");
  }
}
```

### Logger

#### SLF4J
[SLF4JModule](./slf4j) allows directing Feign's logging to [SLF4J](http://www.slf4j.org/), allowing you to easily use a logging backend of your choice (Logback, Log4J, etc.)

To use SLF4J with Feign, add both the SLF4J module and an SLF4J binding of your choice to your classpath.  Then, configure Feign to use the Slf4jLogger:

```java
public class Example {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                     .logger(new Slf4jLogger())
                     .logLevel(Level.FULL)
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

If any methods in your interface return type `Stream`, you'll need to configure a `StreamDecoder`.

Here's how to configure Stream decoder without delegate decoder:

```java
public class Example {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
            .decoder(StreamDecoder.create((r, t) -> {
              BufferedReader bufferedReader = new BufferedReader(r.body().asReader(UTF_8));
              return bufferedReader.lines().iterator();
            }))
            .target(GitHub.class, "https://api.github.com");
  }
}
``` 

Here's how to configure Stream decoder with delegate decoder:

```java

public class Example {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
            .decoder(StreamDecoder.create((r, t) -> {
              BufferedReader bufferedReader = new BufferedReader(r.body().asReader(UTF_8));
              return bufferedReader.lines().iterator();
            }, (r, t) -> "this is delegate decoder"))
            .target(GitHub.class, "https://api.github.com");
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
To customize headers for each request method on a Target, a RequestInterceptor can be used. RequestInterceptors can be
shared across Target instances and are expected to be thread-safe. RequestInterceptors are applied to all request
methods on a Target.

If you need per method customization, a custom Target is required, as the a RequestInterceptor does not have access to
the current method metadata.

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
                     .logger(new Logger.JavaLogger("GitHub.Logger").appendToFile("logs/http.log"))
                     .logLevel(Logger.Level.FULL)
                     .target(GitHub.class, "https://api.github.com");
  }
}
```

> **A Note on JavaLogger**:
> Avoid using of default ```JavaLogger()``` constructor - it was marked as deprecated and will be removed soon.

The SLF4JLogger (see above) may also be of interest.

To filter out sensitive information like authorization or tokens
override methods `shouldLogRequestHeader` or `shouldLogResponseHeader`.

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

When used in this manner, without specifying a custom `QueryMapEncoder`, the query map will be generated using member variable names as query parameter names. You can annotate a specific field of `CustomPojo` with the `@Param` annotation to specify a different name to the query parameter. The following POJO will generate query params of "/find?name={name}&number={number}&region_id={regionId}" (order of included query parameters not guaranteed, and as usual, if any value is null, it will be left out).

```java
public class CustomPojo {
  private final String name;
  private final int number;
  @Param("region_id")
  private final String regionId;

  public CustomPojo (String name, int number, String regionId) {
    this.name = name;
    this.number = number;
    this.regionId = regionId;
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
`Retryer`.

### Retry
Feign, by default, will automatically retry `IOException`s, regardless of HTTP method, treating them as transient network
related exceptions, and any `RetryableException` thrown from an `ErrorDecoder`.  To customize this
behavior, register a custom `Retryer` instance via the builder.

The following example shows how to refresh token and retry with `ErrorDecoder` and `Retryer` when received a 401 response.

```java
public class Example {
    public static void main(String[] args) {
        var github = Feign.builder()
                .decoder(new GsonDecoder())
                .retryer(new MyRetryer(100, 3))
                .errorDecoder(new MyErrorDecoder())
                .target(Github.class, "https://api.github.com");

        var contributors = github.contributors("foo", "bar", "invalid_token");
        for (var contributor : contributors) {
            System.out.println(contributor.login + " " + contributor.contributions);
        }
    }

    static class MyErrorDecoder implements ErrorDecoder {

        private final ErrorDecoder defaultErrorDecoder = new Default();

        @Override
        public Exception decode(String methodKey, Response response) {
            // wrapper 401 to RetryableException in order to retry
            if (response.status() == 401) {
                return new RetryableException(response.status(), response.reason(), response.request().httpMethod(), null, response.request());
            }
            return defaultErrorDecoder.decode(methodKey, response);
        }
    }

    static class MyRetryer implements Retryer {

        private final long period;
        private final int maxAttempts;
        private int attempt = 1;

        public MyRetryer(long period, int maxAttempts) {
            this.period = period;
            this.maxAttempts = maxAttempts;
        }

        @Override
        public void continueOrPropagate(RetryableException e) {
            if (++attempt > maxAttempts) {
                throw e;
            }
            if (e.status() == 401) {
                // remove Authorization first, otherwise Feign will add a new Authorization header
                // cause github responses a 400 bad request
                e.request().requestTemplate().removeHeader("Authorization");
                e.request().requestTemplate().header("Authorization", "Bearer " + getNewToken());
                try {
                    Thread.sleep(period);
                } catch (InterruptedException ex) {
                    throw e;
                }
            } else {
                throw e;
            }
        }

        // Access an external api to obtain new token
        // In this example, we can simply return a fixed token to demonstrate how Retryer works
        private String getNewToken() {
            return "newToken";
        }

        @Override
        public Retryer clone() {
            return new MyRetryer(period, maxAttempts);
        }
}
```

`Retryer`s are responsible for determining if a retry should occur by returning either a `true` or
`false` from the method `continueOrPropagate(RetryableException e);`  A `Retryer` instance will be
created for each `Client` execution, allowing you to maintain state bewteen each request if desired.

If the retry is determined to be unsuccessful, the last `RetryException` will be thrown.  To throw the original
cause that led to the unsuccessful retry, build your Feign client with the `exceptionPropagationPolicy()` option.

#### Response Interceptor
If you need to treat what would otherwise be an error as a success and return a result rather than throw an exception then you may use a `ResponseInterceptor`.

As an example Feign includes a simple `RedirectionInterceptor` that can be used to extract the location header from redirection responses.
```java
public interface Api {
  // returns a 302 response
  @RequestLine("GET /location")
  String location();
}

public class MyApp {
  public static void main(String[] args) {
    // Configure the HTTP client to ignore redirection
    Api api = Feign.builder()
                   .options(new Options(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, false))
                   .responseInterceptor(new RedirectionInterceptor())
                   .target(Api.class, "https://redirect.example.com");
  }
}
```

### Metrics
By default, feign won't collect any metrics.

But, it's possible to add metric collection capabilities to any feign client.

Metric Capabilities provide a first-class Metrics API that users can tap into to gain insight into the request/response lifecycle.

#### Dropwizard Metrics 4

```
public class MyApp {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                         .addCapability(new Metrics4Capability())
                         .target(GitHub.class, "https://api.github.com");

    github.contributors("OpenFeign", "feign");
    // metrics will be available from this point onwards
  }
}
```

#### Dropwizard Metrics 5

```
public class MyApp {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                         .addCapability(new Metrics5Capability())
                         .target(GitHub.class, "https://api.github.com");

    github.contributors("OpenFeign", "feign");
    // metrics will be available from this point onwards
  }
}
```

#### Micrometer

```
public class MyApp {
  public static void main(String[] args) {
    GitHub github = Feign.builder()
                         .addCapability(new MicrometerCapability())
                         .target(GitHub.class, "https://api.github.com");

    github.contributors("OpenFeign", "feign");
    // metrics will be available from this point onwards
  }
}
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


### Async execution via `CompletableFuture`

Feign 10.8 introduces a new builder `AsyncFeign` that allow methods to return `CompletableFuture` instances.

```java
interface GitHub {
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  CompletableFuture<List<Contributor>> contributors(@Param("owner") String owner, @Param("repo") String repo);
}

public class MyApp {
  public static void main(String... args) {
    GitHub github = AsyncFeign.builder()
                         .decoder(new GsonDecoder())
                         .target(GitHub.class, "https://api.github.com");

    // Fetch and print a list of the contributors to this library.
    CompletableFuture<List<Contributor>> contributors = github.contributors("OpenFeign", "feign");
    for (Contributor contributor : contributors.get(1, TimeUnit.SECONDS)) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }
  }
}
```

Initial implementation include 2 async clients:
- `AsyncClient.Default`
- `AsyncApacheHttp5Client`

## Maven’s Bill of Material (BOM)

Keeping all feign libraries on the same version is essential to avoid incompatible binaries. When consuming external dependencies, can be tricky to make sure only one version is present.

With that in mind, feign build generates a module called `feign-bom` that locks the versions for all `feign-*` modules.

The Bill Of Material is a special POM file that groups dependency versions that are known to be valid and tested to work together. This will reduce the developers’ pain of having to test the compatibility of different versions and reduce the chances to have version mismatches.


[Here](https://repo1.maven.org/maven2/io/github/openfeign/feign-bom/11.9/feign-bom-11.9.pom) is one example of what feign BOM file looks like.

#### Usage

```xml
<project>

...

  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>io.github.openfeign</groupId>
        <artifactId>feign-bom</artifactId>
        <version>??feign.version??</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
</project>
```
