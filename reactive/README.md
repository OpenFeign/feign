Reactive Streams Wrapper
---

This module wraps Feign's http requests in a [Reactive Streams](https://reactive-streams.org) 
Publisher, enabling the use of Reactive Stream `Publisher` return types.  Supported Reactive Streams implementations are:
 
* [Reactor](https://projectreactor.io/) (`Mono` and `Flux`)
* [ReactiveX (RxJava)](https://reactivex.io) (`Flowable` only)

To use these wrappers, add the `feign-reactive-wrappers` module, and your desired `reactive-streams` 
implementation to your classpath.  Then configure Feign to use the reactive streams wrappers.

```java
public interface GitHubReactor {
      
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  Flux<Contributor> contributorsFlux(@Param("owner") String owner, @Param("repo") String repo);
      
  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  Mono<List<Contributor>> contributorsMono(@Param("owner") String owner, @Param("repo") String repo);
  
  class Contributor {
      String login;

      public String getLogin() {
          return login;
      }

      public void setLogin(String login) {
          this.login = login;
      }
  }
}

public class ExampleReactor {
  public static void main(String args[]) {
    GitHubReactor gitHub = ReactorFeign.builder() 
      .decoder(new ReactorDecoder(new JacksonDecoder()))     
      .target(GitHubReactor.class, "https://api.github.com");
    
    List<GitHubReactor.Contributor> contributorsFromFlux = gitHub.contributorsFlux("OpenFeign", "feign")
      .collectList()
      .block();
    List<GitHubReactor.Contributor> contributorsFromMono = gitHub.contributorsMono("OpenFeign", "feign")
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
      .decoder(new RxJavaDecoder(new JacksonDecoder()))     
      .target(GitHub.class, "https://api.github.com");
    
    List<Contributor> contributors = gitHub.contributors("OpenFeign", "feign")
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
in the `Publisher`.  For `Reactor` types, this limits the use of `Flux` as a response type.

