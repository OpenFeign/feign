### Version 8.18
* Adds support for expansion of @Param lists
* Content-Length response bodies with lengths greater than Integer.MAX_VALUE report null length
  * Previously the OkhttpClient would throw an exception, and ApacheHttpClient
    would report a wrong, possibly negative value
* Adds support for encoded query parameters in `@QueryMap` via `@QueryMap(encoded = true)`
* Keys in `Response.headers` are now lower-cased. This map is now case-insensitive with regards to keys,
  and iterates in lexicographic order.
  * This is a step towards supporting http2, as header names in http1 are treated as case-insensitive
    and http2 down-cases header names.

### Version 8.17
* Adds support to RxJava Completable via `HystrixFeign` builder with fallback support
* Upgraded hystrix-core to 1.4.26
* Upgrades dependency version for OkHttp/MockWebServer 3.2.0

### Version 8.16
* Adds `@HeaderMap` annotation to support dynamic header fields and values
* Add support for default and static methods on interfaces

### Version 8.15
* Adds `@QueryMap` annotation to support dynamic query parameters
* Supports runtime injection of `Param.Expander` via `MethodMetadata.indexToExpander`
* Adds fallback support for HystrixCommand, Observable, and Single results
* Supports PUT without a body parameter
* Supports substitutions in `@Headers` like in `@Body`. (#326)
  * **Note:** You might need to URL-encode literal values of `{` or `%` in your existing code.

### Version 8.14
* Add support for RxJava Observable and Single return types via the `HystrixFeign` builder.
* Adds fallback implementation configuration to the `HystrixFeign` builder
* Bumps dependency versions, most notably Gson 2.5 and OkHttp 2.7

### Version 8.13
* Never expands >8kb responses into memory

### Version 8.12
* Adds `Feign.Builder.decode404()` to reduce boilerplate for empty semantics.

### Version 8.11
* Adds support for Hystrix via a `HystrixFeign` builder.

### Version 8.10
* Adds HTTP status to FeignException for easier response handling
* Reads class-level @Produces/@Consumes JAX-RS annotations
* Supports POST without a body parameter

### Version 8.9
* Skips error handling when return type is `Response`

### Version 8.8
* Adds jackson-jaxb codec
* Bumps dependency versions for integrations
 * OkHttp/MockWebServer 2.5.0
 * Jackson 2.6.1
 * Apache Http Client 4.5
 * JMH 1.10.5

### Version 8.7
* Bumps dependency versions for integrations
 * OkHttp/MockWebServer 2.4.0
 * Gson 2.3.1
 * Jackson 2.6.0
 * Ribbon 2.1.0
 * SLF4J 1.7.12

### Version 8.6
* Adds base api support via single-inheritance interfaces

### Version 7.5/8.5
* Added possibility to leave slash encoded in path parameters

### Version 8.4
* Correct Retryer bug that prevented it from retrying requests after the first 5 retry attempts.
  * **Note:** If you have a custom `feign.Retryer` implementation you now must now implement `public Retryer clone()`.
  It is suggested that you simply return a new instance of your Retryer class.

### Version 8.3
* Adds client implementation for Apache Http Client

### Version 8.2
* Allows customized request construction by exposing `Request.create()`
* Adds JMH benchmark module
* Enforces source compatibility with animal-sniffer

### Version 8.1
* Allows `@Headers` to be applied to a type

### Version 8.0
* Removes Dagger 1.x Dependency
* Removes support for parameters annotated with `javax.inject.@Named`. Use `feign.@Param` instead.
* Makes body parameter type explicit.

### Version 7.4
* Allows `@Headers` to be applied to a type

### Version 7.3
* Adds Request.Options support to RibbonClient
* Adds LBClientFactory to enable caching of Ribbon LBClients
* Updates to Ribbon 2.0-RC13
* Updates to Jackson 2.5.1
* Supports query parameters without values

### Version 7.2
* Adds `Feign.Builder.build()`
* Opens constructor for Gson and Jackson codecs which accepts type adapters
* Adds EmptyTarget for interfaces who exclusively declare URI methods
* Reformats code according to [Google Java Style](https://google-styleguide.googlecode.com/svn/trunk/javaguide.html)

### Version 7.1
* Introduces feign.@Param to annotate template parameters. Users must migrate from `javax.inject.@Named` to `feign.@Param` before updating to Feign 8.0.
  * Supports custom expansion via `@Param(value = "name", expander = CustomExpander.class)`
* Adds OkHttp integration
* Allows multiple headers with the same name.
* Ensures Accept headers default to `*/*`

### Version 7.0
* Expose reflective dispatch hook: InvocationHandlerFactory
* Add JAXB integration
* Add SLF4J integration
* Upgrade to Dagger 1.2.2.
  * **Note:** Dagger-generated code prior to version 1.2.0 is incompatible with Dagger 1.2.0 and beyond. Dagger users should upgrade Dagger to at least version 1.2.0, and recompile any dependency-injected classes.

### Version 6.1.3
* Updates to Ribbon 2.0-RC5

### Version 6.1.1
* Fix for #85

### Version 6.1.0
* Add [SLF4J](http://www.slf4j.org/) integration

### Version 6.0.1
* Fix for BasicAuthRequestInterceptor when username and/or password are long.

### Version 6.0
* Support binary request and response bodies.
* Don't throw http status code exceptions when return type is `Response`.

### Version 5.4.0
* Add `BasicAuthRequestInterceptor`
* Add Jackson integration

### Version 5.3.0
* Split `GsonCodec` into `GsonEncoder` and `GsonDecoder`, which are easy to use with `Feign.Builder`
* Deprecate `GsonCodec`
* Update to Ribbon 0.2.3

### Version 5.2.0
* Support usage of `GsonCodec` via `Feign.Builder`

### Version 5.1.0
* Correctly handle IOExceptions wrapped by Ribbon.
* Miscellaneous findbugs fixes.

### Version 5.0.1
* `Decoder.decode()` is no longer called for `Response` or `void` types.

### Version 5.0
* Remove support for Observable methods.
* Use single non-generic Decoder/Encoder instead of sets of type-specific Decoders/Encoders.
* Decoders/Encoders are now more flexible, having access to the Response/RequestTemplate respectively.
* Moved SaxDecoder into `feign-sax` dependency.
  * SaxDecoder now decodes multiple types.
  * Remove pattern decoders in favor of SaxDecoder.
* Added Feign.Builder to simplify client customizations without using Dagger.
* Gson type adapters can be registered as Dagger set bindings.
* `Feign.create(...)` now requires specifying an encoder and decoder.

### Version 4.4.1
* Fix NullPointerException on calling equals and hashCode.

### Version 4.4
* Support overriding default HostnameVerifier.
* Support GZIP content encoding for request bodies.
* Support Iterable args for query parameters.
* Support urls which have query parameters.

### Version 4.3
* Add ability to configure zero or more RequestInterceptors.
* Remove `overrides = true` on codec modules.

### Version 4.2/3.3
* Document and enforce JAX-RS annotation processing from server POV
* Skip query template parameters when corresponding java arg is null

### Version 4.1/3.2
* update to dagger 1.1
* Add wikipedia search example
* Allow `@Path` on types in feign-jaxrs

### Version 4.0
* Support RxJava-style Observers.
  * Return type can be `Observable<T>` for an async equiv of `Iterable<T>`.
  * `Observer<T>` replaces `IncrementalCallback<T>` and is passed to `Observable.subscribe()`.
  * On `Subscription.unsubscribe()`, `Observer.onNext()` will stop being called.

### Version 3.1
* Log when an http request is retried or a response fails due to an IOException.

### Version 3.0
* Added support for asynchronous callbacks via `IncrementalCallback<T>` and `IncrementalDecoder.TextStream<T>`.
* Wire is now Logger, with configurable Logger.Level.
* Added `feign-gson` codec, used via `new GsonModule()`
* changed codec to be similar to [WebSocket JSR 356](http://docs.oracle.com/javaee/7/api/javax/websocket/package-summary.html)
  * Decoder is now `Decoder.TextStream<T>`
  * BodyEncoder is now `Encoder.Text<T>`
  * FormEncoder is now `Encoder.Text<Map<String, ?>>`
* Encoder and Decoders are specified via `Provides.Type.SET` binding.
* Default Encoder and Form Encoder is `Encoder.Text<Object>`
* Default Decoder is `Decoder.TextStream<Object>`
* ErrorDecoder now returns Exception, not fallback.
* There can only be one `ErrorDecoder` and `Request.Options` binding now.

### Version 2.0.0
* removes guava and jax-rs dependencies
* adds JAX-RS integration

### Version 1.1.0
* adds Ribbon integration
* adds cli example
* exponential backoff customizable via Retryer.Default ctor

### Version 1.0.0

* Initial open source release
