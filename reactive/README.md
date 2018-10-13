Reactive Streams Wrapper
---

This module wraps Feign's http requests in a [Reactive Streams](https://reactive-streams.org) 
Publisher, enabling the use of Reactive Stream `Publisher` return types.  Supported Reactive Streams implementations are:
 
* [Reactor](https://project-reactor.org) (`Mono` and `Flux`)
* [ReactiveX (RxJava)](https://reactivex.io) (`Flowable` only)

To use these wrappers, add the `feign-reactive-wrappers` module, and your desired `reactive-streams` 
implementation to your classpath.  Then configure Feign to use the reactive streams wrappers.

```java
public interface GitHubReactor {
      
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  Flux<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
  
  class Contributor {
    String login;
    
    public Contributor(String login) {
      this.login = login;
    }
  }
}

public class ExampleReactor {
  public static void main(String args[]) {
    GitHubReactor gitHub = ReactorFeign.builder()      
      .target(GitHubReactor.class, "https://api.github.com");
    
    List<Contributor> contributors = gitHub.contributors("OpenFeign", "feign")
      .map(Contributor::new)
      .collect(Collectors.toList())
      .block();
  }
}

public interface GitHubReactiveX {
      
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  Flowable<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
  
  class Contributor {
    String login;
    
    public Contributor(String login) {
      this.login = login;
    }
  }
}

public class ExampleRxJava2 {
  public static void main(String args[]) {
    GitHubReactiveX gitHub = RxJavaFeign.builder()      
      .target(GitHub.class, "https://api.github.com");
    
    List<Contributor> contributors = gitHub.contributors("OpenFeign", "feign")
      .map(Contributor::new)
      .collect(Collectors.toList())
      .block();
  }
}

```

Considerations
---

These wrappers are not *reactive all the way down*, given that Feign generated requests are
synchronous.  Requests still block, but execution is controlled by the `Publisher` and their 
related `Scheduler`.  While this may not be ideal in terms of a fully reactive application, providing these
wrappers provide an intermediate upgrade path for Feign.

### Streaming 

Methods that return `java.util.streams` Types are not supported.  Responses are read fully, 
the wrapped in the appropriate reactive wrappers.

### Iterable and Collections responses

Due to the Synchronous nature of Feign requests, methods that return `Iterable` types must specify the collection 
in the `Publisher`.  For `Reactor` types, this limits the use of `Flux` as a response type.  If you
want to use `Flux`, you will need to manually convert the `Mono` or `Iterable` response types into
`Flux` using the `fromIterable` method.
 

```java
public interface GitHub {
      
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  Mono<List<Contributor>> contributors(@Param("owner") String owner, @Param("repo") String repo);
  
  class Contributor {
    String login;
    
    public Contributor(String login) {
      this.login = login;
    }
  }
}

public class ExampleApplication {
  public static void main(String[] args) {
    GitHub gitHub = ReactorFeign.builder()
      .target(GitHub.class, "https://api.github.com");
    
    Mono<List<Contributor>> contributors = gitHub.contributors("OpenFeign", "feign");
    Flux<Contributor> contributorFlux = Flux.fromIterable(contributors.block());
  }
}
```
