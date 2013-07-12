# Feign makes writing java http clients easier
Feign is a java to http client binder inspired by [Dagger](https://github.com/square/dagger), [Retrofit](https://github.com/square/retrofit), [JAXRS-2.0](https://jax-rs-spec.java.net/nonav/2.0/apidocs/index.html), and [WebSockets](http://www.oracle.com/technetwork/articles/java/jsr356-1937161.html).  Feign's first goal was reducing the complexity of binding [Denominator](https://github.com/Netflix/Denominator) uniformly to http apis regardless of [restfulness](http://www.slideshare.net/adrianfcole/99problems).

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
  GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GsonModule());

  // Fetch and print a list of the contributors to this library.
  List<Contributor> contributors = github.contributors("netflix", "feign");
  for (Contributor contributor : contributors) {
    System.out.println(contributor.login + " (" + contributor.contributions + ")");
  }
}
```
### Decoders
The last argument to `Feign.create` specifies how to decode the responses, modeled in Dagger.  Here's how it looks to wire in a default gson decoder:

```java
@Module(overrides = true, library = true)
static class GsonModule {
  @Provides(type = SET) Decoder decoder() {
    return new Decoder.TextStream<Object>() {
      Gson gson = new Gson();

      @Override public Object decode(Reader reader, Type type) throws IOException {
        try {
          return gson.fromJson(reader, type);
        } catch (JsonIOException e) {
          if (e.getCause() != null && e.getCause() instanceof IOException) {
            throw IOException.class.cast(e.getCause());
          }
          throw e;
        }
      }
    };
  }
}
```
Feign doesn't offer a built-in json decoder as you can see above it is very few lines of code to wire yours in.  If you are a jackson user, you'd probably thank us for not dragging in a dependency you don't use.

#### Type-specific Decoders
The generic parameter of `Decoder.TextStream<T>` designates which The type parameter is either a concrete type, or `Object`, if your decoder can handle multiple types.  To add a type-specific decoder, ensure your type parameter is correct.  Here's an example of an xml decoder that will only apply to methods that return `ZoneList`.

```
@Provides(type = SET) Decoder zoneListDecoder(Provider<ListHostedZonesResponseHandler> handlers) {
  return new SAXDecoder<ZoneList>(handlers){};
}
```
### Asynchronous Incremental Callbacks
If specified as the last argument of a method `IncrementalCallback<T>` fires a background task to add new elements to the callback as they are decoded.  Think of `IncrementalCallback<T>` as an asynchronous equivalent to a lazy sequence.

Here's how one looks:
```java
IncrementalCallback<Contributor> printlnObserver = new IncrementalCallback<Contributor>() {

  public int count;

  @Override public void onNext(Contributor element) {
    count++;
  }

  @Override public void onSuccess() {
    System.out.println("found " + count + " contributors");
  }

  @Override public void onFailure(Throwable cause) {
    cause.printStackTrace();
  }
};
github.contributors("netflix", "feign", printlnObserver);
```
#### Incremental Decoding
When using an `IncrementalCallback<T>`, you'll need to configure an `IncrementalDecoderi.TextStream<T>` or a general one for all types (`IncrementalDecoder.TextStream<Object>`).

Here's how to wire in a reflective incremental json decoder:
```java
@Provides(type = SET) IncrementalDecoder incrementalDecoder(final Gson gson) {
  return new IncrementalDecoder.TextStream<Object>() {

    @Override
    public void decode(Reader reader, Type type, IncrementalCallback<? super Object> incrementalCallback) throws IOException {
      JsonReader jsonReader = new JsonReader(reader);
      jsonReader.beginArray();
      while (jsonReader.hasNext()) {
        try {
          incrementalCallback.onNext(gson.fromJson(jsonReader, type));
        } catch (JsonIOException e) {
          if (e.getCause() != null && e.getCause() instanceof IOException) {
            throw IOException.class.cast(e.getCause());
          }
          throw e;
        }
      }
      jsonReader.endArray();
    }
  };
}
```



### Multiple Interfaces
Feign can produce multiple api interfaces.  These are defined as `Target<T>` (default `HardCodedTarget<T>`), which allow for dynamic discovery and decoration of requests prior to execution.

For example, the following pattern might decorate each request with the current url and auth token from the identity service.

```java
CloudDNS cloudDNS =  Feign.create().newInstance(new CloudIdentityTarget<CloudDNS>(user, apiKey));
```

You can find [several examples](https://github.com/Netflix/feign/tree/master/feign-core/src/test/java/feign/examples) in the test tree.  Do take time to look at them, as seeing is believing!

### Integrations
Feign intends to work well within Netflix and other Open Source communities.  Modules are welcome to integrate with your favorite projects!
### JAX-RS
[JAXRSModule](https://github.com/Netflix/feign/tree/master/feign-jaxrs) overrides annotation processing to instead use standard ones supplied by the JAX-RS specification.  This is currently targeted at the 1.1 spec.

Here's the example above re-written to use JAX-RS:
```java
interface GitHub {
  @GET @Path("/repos/{owner}/{repo}/contributors")
  List<Contributor> contributors(@PathParam("owner") String owner, @PathParam("repo") String repo);
}
```
### Ribbon
[RibbonModule](https://github.com/Netflix/feign/tree/master/feign-ribbon) overrides URL resolution of Feign's client, adding smart routing and resiliency capabilities provided by [Ribbon](https://github.com/Netflix/ribbon).

Integration requires you to pass your ribbon client name as the host part of the url, for example `myAppProd`.
```java
MyService api = Feign.create(MyService.class, "https://myAppProd", new RibbonModule());
```
### Advanced usage and Dagger
#### Dagger
Feign can be directly wired into Dagger which keeps things at compile time and Android friendly.  As opposed to exposing builders for config, Feign intends users to embed their config in Dagger.

Where possible, Feign configuration uses normal Dagger conventions.  For example, `Decoder` bindings are of `Provider.Type.SET`, meaning you can make multiple bindings for all the different types you return.  Here's an example of multiple decoder bindings.
```java
@Provides(type = SET) Decoder recordListDecoder(Provider<RecordListHandler> handlers) {
  return new SAXDecoder<List<Record>>(handlers){};
}

@Provides(type = SET) Decoder directionalRecordListDecoder(Provider<DirectionalRecordListHandler> handlers) {
  return new SAXDecoder<List<DirectionalRecord>>(handlers){};
}
```
#### Logging
You can log the http messages going to and from the target by setting up a `Logger`.  Here's the easiest way to do that:
```java
@Module(overrides = true)
class Overrides {
  @Provides @Singleton Logger.Level provideLoggerLevel() {
    return Logger.Level.FULL;
  }

  @Provides @Singleton Logger provideLogger() {
    return new Logger.JavaLogger().appendToFile("logs/http.log");
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
  @Provides(type = SET) Decoder arnDecoder() {
    return Decoders.firstGroup("<Arn>([\\S&&[^<]]+)</Arn>");
  }
}
```

