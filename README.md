# Feign makes writing java http clients easier
Feign is a java to http client binder inspired by [Dagger](https://github.com/square/dagger), [Retrofit](https://github.com/square/retrofit), [jclouds](https://github.com/jclouds/jclouds), and [JAXRS-2.0](https://jax-rs-spec.java.net/nonav/2.0/apidocs/index.html).  Feign's first goal was reducing the complexity of binding [Denominator](https://github.com/Netflix/Denominator) uniformly to http apis regardless of [restfulness](http://www.slideshare.net/adrianfcole/99problems).

### Why Feign and not X?

You can use tools like Jersey and CXF to write java clients for ReST or SOAP services.  You can write your own code on top of http transport libraries like Apache HC.  Feign aims to connect your code to http apis with minimal overhead and code. Via customizable decoders and error handling, you should be able to write to any text-based http api.

### How does Feign work?

Feign works by processing annotations into a templatized request.  Just before sending it off, arguments are applied to these templates in a straightforward fashion.  While this limits Feign to only supporting text-based apis, it dramatically simplified system aspects such as replaying requests.  It is also stupid easy to unit test your conversions knowing this.

### Basics

Usage typically looks like this, an adaptation of the [canonical Retrofit sample](https://github.com/square/retrofit/blob/master/retrofit-samples/github-client/src/main/java/com/example/retrofit/GitHubClient.java).

```java
interface GitHub {
  @GET @Path("/repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@PathParam("owner") String owner, @PathParam("repo") String repo);
}

static class Contributor {
  String login;
  int contributions;
}

public static void main(String... args) {
  GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GsonModule());

  // Fetch and print a list of the contributors to this library.
  List<Contributor> contributors = github.contributors("netflix", "feign");
  for (Contributor contributor : contributors) {
    System.out.println(contributor.login + " (" + contributor.contributions + ")");
  }
}
```
### Decoders
The last argument to `Feign.create` specifies how to decode the responses.  You can plug-in your favorite library, such as gson, or use builtin RegEx Pattern decoders.  Here's how the Gson module looks.

```java
@Module(overrides = true, library = true)
static class GsonModule {
  @Provides @Singleton Map<String, Decoder> decoders() {
    return ImmutableMap.of("GitHub", gsonDecoder);
  }

  final Decoder gsonDecoder = new Decoder() {
    Gson gson = new Gson();

    @Override public Object decode(String methodKey, Reader reader, TypeToken<?> type) {
      return gson.fromJson(reader, type.getType());
    }
  };
}
```
Feign doesn't offer a built-in json decoder as you can see above it is very few lines of code to wire yours in.  If you are a jackson user, you'd probably thank us for not dragging in a dependency you don't use.

### Multiple Interfaces
Feign can produce multiple api interfaces.  These are defined as `Target<T>` (default `HardCodedTarget<T>`), which allow for dynamic discovery and decoration of requests prior to execution.

For example, the following pattern might decorate each request with the current url and auth token from the identity service.

```java
CloudDNS cloudDNS =  Feign.create().newInstance(new CloudIdentityTarget<CloudDNS>(user, apiKey));
```

You can find [several examples](https://github.com/Netflix/feign/tree/master/feign-core/src/test/java/feign/examples) in the test tree.  Do take time to look at them, as seeing is believing!

### Integrations
Feign intends to work well within Netflix and other Open Source communities.  Modules are welcome to integrate with your favorite projects!
### Ribbon
[RibbonModule](https://github.com/Netflix/feign/tree/master/feign-ribbon) overrides URL resolution of Feign's client, adding smart routing and resiliency capabilities provided by [Ribbon](https://github.com/Netflix/ribbon).

Integration requires you to pass your ribbon client name as the host part of the url, for example `myAppProd`.
```java
MyService api = Feign.create(MyService.class, "https://myAppProd", new RibbonModule());
```
### Advanced usage and Dagger
#### Dagger
Feign can be directly wired into Dagger which keeps things at compile time and Android friendly.  As opposed to exposing builders for config, Feign intends users to embed their config in Dagger.

Almost all configuration of Feign is represented as Map bindings, where the key is either the simple name (ex. `GitHub`) or the method (ex. `GitHub#contributors()`) in javadoc link format. For example, the following routes all decoding to gson:
```java
@Provides @Singleton Map<String, Decoder> decoders() {
  return ImmutableMap.of("GitHub", gsonDecoder);
}
```
#### Wire Logging
You can log the http messages going to and from the target by setting up a `Wire`.  Here's the easiest way to do that:
```java
@Module(overrides = true)
class Overrides {
  @Provides @Singleton Wire provideWire() {
    return new Wire.LoggingWire().appendToFile("logs/http-wire.log");
  }
}
GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GsonGitHubModule(), new Overrides());
```
#### Pattern Decoders
If you have to only grab a single field from a server response, you may find regular expressions less maintenance than writing a type adapter.

Here's how our IAM example grabs only one xml element from a response. 
```java
@Module(overrides = true, library = true)
static class IAMModule {
  @Provides @Singleton Map<String, Decoder> decoders() {
    return ImmutableMap.of("IAM#arn()", Decoders.firstGroup("<Arn>([\\S&&[^<]]+)</Arn>"));
  }
}
```

