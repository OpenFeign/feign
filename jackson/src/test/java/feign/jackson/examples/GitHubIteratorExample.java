package feign.jackson.examples;

import feign.Feign;
import feign.Param;
import feign.RequestLine;
import feign.jackson.JacksonIteratorDecoder;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

/**
 * adapted from {@code com.example.retrofit.GitHubClient}
 */
public class GitHubIteratorExample {

  public static void main(String... args) throws IOException {
    GitHub github = Feign.builder()
        .decoder(JacksonIteratorDecoder.Factory.create())
        .closeAfterDecode(false)
        .target(GitHub.class, "https://api.github.com");

    System.out.println("Let's fetch and print a list of the contributors to this library.");
    Iterator<Contributor> contributors = github.contributors("OpenFeign", "feign");
    try {
      while (contributors.hasNext()) {
        Contributor contributor = contributors.next();
        System.out.println(contributor.login + " (" + contributor.contributions + ")");
      }
    } finally {
      ((Closeable) contributors).close();
    }
  }

  interface GitHub {

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    Iterator<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
  }

  static class Contributor {

    private String login;
    private int contributions;

    void setLogin(String login) {
      this.login = login;
    }

    void setContributions(int contributions) {
      this.contributions = contributions;
    }
  }
}
