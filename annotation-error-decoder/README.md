Annotation Error Decoder
=========================

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

##Leveraging the annotations and priority order
For annotation decoding to work, the class must be annotated with @ErrorHandling tags.
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
            @ErrorCodes( codes = {404}, generate = NonExistenRepoException.class),
        },
        defaultException = FailedToGetContributorsException.class
    )
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
}
```
In the above example, error responses to 'contributors' would hence be mapped as follows by status codes:
* 401 => UnAuthorizedException (from Class definition)
* 403 => ForbiddenException (from Class definition)
* 404 => NonExistenRepoException (from Method definition, note that the class generic exception won't be thrown here)
* any other => FailedToGetContributorsException (from Method default)

For a class level default exception to be thrown, the method must not have a defaultException defined, nor must the error code
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

 
##Complex Exceptions

Finally, Any exception can be used if they have a default constructor:

```java
class DefaultConstructorException extends Exception {}
```

However, if you want to have parameters (such  as the body in the error response or headers), you have to annotate its 
constructor appropriately (the body annotation is optional, provided there aren't paramters which will clash)

All the following examples are valid exceptions:
```java
class JustBody extends Exception {

    @FeignExceptionConstructor
    public JustBody(String body) {

    }
}
//Headers must be of type Map<String, Collection<String>>
class BodyAndHeaders extends Exception {

    @FeignExceptionConstructor
    public BodyAndHeaders(@ResponseBody String body, @ResponseHeaders Map<String, Collection<String>> headers) {

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

    }
}
//The pojo can then be anything you'd like provided the decoder can manage it
class GithubExceptionResponse {
    public String message;
    public int githubCode;
    public List<String> urlsForHelp;
}
```