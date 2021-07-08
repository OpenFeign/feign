Annotation Error Decoder
=========================
[![Join the chat at https://gitter.im/OpenFeign/feign](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/OpenFeign/feign?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Build Status](https://travis-ci.org/OpenFeign/feign-annotation-error-decoder.svg?branch=master)](https://travis-ci.org/OpenFeign/feign-annotation-error-decoder)
[![Download](https://api.bintray.com/packages/openfeign/maven/feign-annotation-error-decoder/images/download.svg) ](https://bintray.com/openfeign/maven/feign-annotation-error-decoder/_latestVersion)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.openfeign/feign-annotation-error-decoder/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.openfeign/feign-annotation-error-decoder)

This module allows to annotate Feign's interfaces with annotations to generate Exceptions based on error codes

To use AnnotationErrorDecoder with Feign, add the Annotation Error Decoder module to your classpath. Then, configure
Feign to use the AnnotationErrorDecoder:

```java
GitHub github = Feign.builder()
                     .errorDecoder(
                         AnnotationErrorDecoder.builderFor(GitHub.class).build()
                     )
                     .target(GitHub.class, "https://api.github.com");
```

## Leveraging the annotations and priority order
For annotation decoding to work, the class must be annotated with `@ErrorHandling` tags.
The tags are valid in both the class level as well as method level. They will be treated from 'most specific' to 
'least specific' in the following order:
* A code specific exception defined on the method 
* A code specific exception defined on the class
* The default exception of the method
* The default exception of the class

```java
@ErrorHandling(codeSpecific =
    {
        @ErrorCodes( codes = {401}, generate = UnAuthorizedException.class),
        @ErrorCodes( codes = {403}, generate = ForbiddenException.class),
        @ErrorCodes( codes = {404}, generate = UnknownItemException.class),
    },
    defaultException = ClassLevelDefaultException.class
)
interface GitHub {

    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NonExistentRepoException.class),
            @ErrorCodes( codes = {502, 503, 504}, generate = RetryAfterCertainTimeException.class),
        },
        defaultException = FailedToGetContributorsException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}
```
In the above example, error responses to 'contributors' would hence be mapped as follows by status codes:

| Code        | Exception                          | Reason                |
| ----------- | --------------------------------   | --------------------- |
| 401         | `UnAuthorizedException`            | from Class definition |
| 403         | `ForbiddenException`               | from Class definition |
| 404         | `NonExistenRepoException`          | from Method definition, note that the class generic exception won't be thrown here |
| 502,503,504 | `RetryAfterCertainTimeException`   | from method definition. Note that you can have multiple error codes generate the same type of exception |
| Any Other   | `FailedToGetContributorsException` | from Method default   |

For a class level default exception to be thrown, the method must not have a `defaultException` defined, nor must the error code
be mapped at either the method or class level.

If the return code cannot be mapped to any code and no default exceptions have been configured, then the decoder will
drop to a default decoder (by default, the standard one provided by feign). You can change the default drop-into decoder
as follows:

```java
GitHub github = Feign.builder()
                     .errorDecoder(
                         AnnotationErrorDecoder.builderFor(GitHub.class)
                            .withDefaultDecoder(new MyOtherErrorDecoder())
                            .build()
                     )
                     .target(GitHub.class, "https://api.github.com");
```

 
## Complex Exceptions

Any exception can be used if they have a default constructor:

```java
class DefaultConstructorException extends Exception {}
```

However, if you want to have parameters (such  as the feign.Request object or response body or response headers), you have to annotate its 
constructor appropriately (the body annotation is optional, provided there aren't paramters which will clash)

All the following examples are valid exceptions:
```java
class JustBody extends Exception {

    @FeignExceptionConstructor
    public JustBody(String body) {

    }
}
class JustRequest extends Exception {

    @FeignExceptionConstructor
    public JustRequest(Request request) {

    }
}
class RequestAndResponseBody extends Exception {

    @FeignExceptionConstructor
    public RequestAndResponseBody(Request request, String body) {

    }
}
//Headers must be of type Map<String, Collection<String>>
class BodyAndHeaders extends Exception {

    @FeignExceptionConstructor
    public BodyAndHeaders(@ResponseBody String body, @ResponseHeaders Map<String, Collection<String>> headers) {

    }
}
class RequestAndResponseBodyAndHeaders extends Exception {

    @FeignExceptionConstructor
    public RequestAndResponseBodyAndHeaders(Request request, @ResponseBody String body, @ResponseHeaders Map<String, Collection<String>> headers) {

    }
}
class JustHeaders extends Exception {

    @FeignExceptionConstructor
    public JustHeaders(@ResponseHeaders Map<String, Collection<String>> headers) {

    }
}
```

If you want to have the body decoded, you'll need to pass a decoder at construction time (just as for normal responses):

```java
GitHub github = Feign.builder()
                     .errorDecoder(
                         AnnotationErrorDecoder.builderFor(GitHub.class)
                            .withResponseBodyDecoder(new JacksonDecoder())
                            .build()
                     )
                     .target(GitHub.class, "https://api.github.com");
```

This will enable you to create exceptions where the body is a complex pojo:

```java
class ComplexPojoException extends Exception {

    @FeignExceptionConstructor
    public ComplexPojoException(GithubExceptionResponse body) {
        if (body != null) {
            // extract data
        } else {
            // fallback code
        }
    }
}
//The pojo can then be anything you'd like provided the decoder can manage it
class GithubExceptionResponse {
    public String message;
    public int githubCode;
    public List<String> urlsForHelp;
}
```

It's worth noting that at setup/startup time, the generators are checked with a null value of the body.
If you don't do the null-checker, you'll get an NPE and startup will fail.


## Inheriting from other interface definitions
You can create a client interface that inherits from a different one. However, there are some limitations that
you should be aware of (for most cases, these shouldn't be an issue):
* The inheritance is not natural java inheritance of annotations - as these don't work on interfaces
* Instead, the error looks at the class and if it finds the `@ErrorHandling` annotation, it uses that one.
* If not, it will look at *all* the interfaces the main interface `extends` - but it does so in the order the
java API gives it - so order is not guaranteed.
* If it finds the annotation in one of those parents, it uses that definition, without looking at any other
* That means that if more than one interface was extended which contained the `@ErrorHandling` annotation, we can't
really guarantee which one of the parents will be selected and you should really do handling at the child interface
  * so far, the java API seems to return in order of definition after the `extends`, but it's a really bad practice
  if you have to depend on that... so our suggestion: don't.

That means that as long as you only ever extend from a base interface (where you may decide that all 404's are "NotFoundException", for example)
then you should be ok. But if you get complex in polymorphism, all bets are off - so don't go crazy!

Example:
In the following code:
* The base `FeignClientBase` interface defines a default set of exceptions at class level
* the `GitHub1` and `GitHub2` interfaces will inherit the class-level error handling, which means that
any 401/403/404 will be handled correctly (provided the method doesn't specify a more specific exception)
* the `GitHub3` interface however, by defining its own error handling, will handle all 401's, but not the
403/404's since there's no merging/etc (not really in the plan to implement either...)
```java

@ErrorHandling(codeSpecific =
    {
        @ErrorCodes( codes = {401}, generate = UnAuthorizedException.class),
        @ErrorCodes( codes = {403}, generate = ForbiddenException.class),
        @ErrorCodes( codes = {404}, generate = UnknownItemException.class),
    },
    defaultException = ClassLevelDefaultException.class
)
interface FeignClientBase {}

interface GitHub1 extends FeignClientBase {

    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NonExistentRepoException.class),
            @ErrorCodes( codes = {502, 503, 504}, generate = RetryAfterCertainTimeException.class),
        },
        defaultException = FailedToGetContributorsException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}

interface GitHub2 extends FeignClientBase {

    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NonExistentRepoException.class),
            @ErrorCodes( codes = {502, 503, 504}, generate = RetryAfterCertainTimeException.class),
        },
        defaultException = FailedToGetContributorsException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}

@ErrorHandling(codeSpecific =
    {
        @ErrorCodes( codes = {401}, generate = UnAuthorizedException.class)
    },
    defaultException = ClassLevelDefaultException.class
)
interface GitHub3 extends FeignClientBase {

    @ErrorHandling(codeSpecific =
        {
            @ErrorCodes( codes = {404}, generate = NonExistentRepoException.class),
            @ErrorCodes( codes = {502, 503, 504}, generate = RetryAfterCertainTimeException.class),
        },
        defaultException = FailedToGetContributorsException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}
```