/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.example.github;

import feign.Feign;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Inspired by {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {

  interface GitHub {

    class Repository {
      String name;
    }

    class Contributor {
      String login;
    }

    @RequestLine("GET /users/{username}/repos?sort=full_name")
    List<Repository> repos(@Param("username") String owner);

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);

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
      return Feign.builder()
          .decoder(decoder)
          .errorDecoder(new GitHubErrorDecoder(decoder))
          .logger(new Logger.ErrorLogger())
          .logLevel(Logger.Level.BASIC)
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
    List<String> contributors = github.contributors("netflix");
    for (String contributor : contributors) {
      System.out.println(contributor);
    }

    System.out.println("Now, let's cause an error.");
    try {
      github.contributors("netflix", "some-unknown-project");
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
        return (Exception) decoder.decode(response, GitHubClientError.class);
      } catch (IOException fallbackToDefault) {
        return defaultDecoder.decode(methodKey, response);
      }
    }
  }
}
