/**
 * Copyright 2012-2021 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package example.github;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import feign.*;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;

/**
 * Inspired by {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {

  public interface GitHub {

    public class Repository {
      String name;
    }

    public class Contributor {
      String login;
    }

    public class Issue {

      Issue() {

      }

      String title;
      String body;
      List<String> assignees;
      int milestone;
      List<String> labels;
    }

    @RequestLine("GET /users/{username}/repos?sort=full_name")
    List<Repository> repos(@Param("username") String owner);

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("POST /repos/{owner}/{repo}/issues")
    void createIssue(Issue issue, @Param("owner") String owner, @Param("repo") String repo);

    /** Lists all contributors for all repos owned by a user. */
    default List<String> contributors(String owner) {
      return repos(owner).stream()
          .flatMap(repo -> contributors(owner, repo.name).stream())
          .map(c -> c.login)
          .distinct()
          .collect(Collectors.toList());
    }

    static GitHub connect() {
      final Decoder decoder = new GsonDecoder();
      final Encoder encoder = new GsonEncoder();
      return Feign.builder()
          .encoder(encoder)
          .decoder(decoder)
          .errorDecoder(new GitHubErrorDecoder(decoder))
          .logger(new Logger.ErrorLogger())
          .logLevel(Logger.Level.BASIC)
          .requestInterceptor(template -> {
            template.header(
                // not available when building PRs...
                // https://docs.travis-ci.com/user/environment-variables/#defining-encrypted-variables-in-travisyml
                "Authorization",
                "token 383f1c1b474d8f05a21e7964976ab0d403fee071");
          })
          .options(new Request.Options(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, true))
          .target(GitHub.class, "https://api.github.com");
    }
  }


  static class GitHubClientError extends RuntimeException {
    private String message; // parsed from json

    @Override
    public String getMessage() {
      return message;
    }
  }

  public static void main(String... args) {
    final GitHub github = GitHub.connect();

    System.out.println("Let's fetch and print a list of the contributors to this org.");
    final List<String> contributors = github.contributors("openfeign");
    for (final String contributor : contributors) {
      System.out.println(contributor);
    }

    System.out.println("Now, let's cause an error.");
    try {
      github.contributors("openfeign", "some-unknown-project");
    } catch (final GitHubClientError e) {
      System.out.println(e.getMessage());
    }

    System.out.println("Now, try to create an issue - which will also cause an error.");
    try {
      final GitHub.Issue issue = new GitHub.Issue();
      issue.title = "The title";
      issue.body = "Some Text";
      github.createIssue(issue, "OpenFeign", "SomeRepo");
    } catch (final GitHubClientError e) {
      System.out.println(e.getMessage());
    }
  }

  static class GitHubErrorDecoder implements ErrorDecoder {

    final Decoder decoder;
    final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    GitHubErrorDecoder(Decoder decoder) {
      this.decoder = decoder;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
      try {
        // must replace status by 200 other GSONDecoder returns null
        response = response.toBuilder().status(200).build();
        return (Exception) decoder.decode(response, GitHubClientError.class);
      } catch (final IOException fallbackToDefault) {
        return defaultDecoder.decode(methodKey, response);
      }
    }
  }
}
