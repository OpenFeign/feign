/**
 * Copyright 2012-2019 The Feign Authors
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

import feign.*;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Inspired by {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {

  private static final String GITHUB_TOKEN = "GITHUB_TOKEN";

  interface GitHub {

    class Repository {
      String name;
    }

    class Contributor {
      String login;
    }

    class Issue {

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
      Decoder decoder = new GsonDecoder();
      Encoder encoder = new GsonEncoder();
      return Feign.builder()
          .encoder(encoder)
          .decoder(decoder)
          .errorDecoder(new GitHubErrorDecoder(decoder))
          .logger(new Logger.ErrorLogger())
          .logLevel(Logger.Level.BASIC)
          .requestInterceptor(template -> {
            if (System.getenv().containsKey(GITHUB_TOKEN)) {
              System.out.println("Detected Authorization token from environment variable");
              template.header(
                  "Authorization",
                  "token " + System.getenv(GITHUB_TOKEN));
            }
          })
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
    GitHub github = GitHub.connect();

    System.out.println("Let's fetch and print a list of the contributors to this org.");
    List<String> contributors = github.contributors("openfeign");
    for (String contributor : contributors) {
      System.out.println(contributor);
    }

    System.out.println("Now, let's cause an error.");
    try {
      github.contributors("openfeign", "some-unknown-project");
    } catch (GitHubClientError e) {
      System.out.println(e.getMessage());
    }

    System.out.println("Now, try to create an issue - which will also cause an error.");
    try {
      GitHub.Issue issue = new GitHub.Issue();
      issue.title = "The title";
      issue.body = "Some Text";
      github.createIssue(issue, "OpenFeign", "SomeRepo");
    } catch (GitHubClientError e) {
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
      } catch (IOException fallbackToDefault) {
        return defaultDecoder.decode(methodKey, response);
      }
    }
  }
}
