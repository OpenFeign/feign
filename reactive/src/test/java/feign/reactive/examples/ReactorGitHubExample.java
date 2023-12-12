/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.reactive.examples;

import java.util.List;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.jackson.JacksonDecoder;
import feign.reactive.ReactorDecoder;
import feign.reactive.ReactorFeign;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * adapted from {@code com.example.retrofit.GitHubClient}
 */
public class ReactorGitHubExample {

  public static void main(String... args) {
    GitHub github = ReactorFeign.builder()
        .decoder(new ReactorDecoder(new JacksonDecoder()))
        .logger(new ConsoleLogger())
        .logLevel(Logger.Level.FULL)
        .target(GitHub.class, "https://api.github.com");

    System.out
        .println("Let's fetch and print a list of the contributors to this library (Using Flux).");
    List<Contributor> contributorsFromFlux = github.contributorsFlux("OpenFeign", "feign")
        .collectList()
        .block();
    for (Contributor contributor : contributorsFromFlux) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }

    System.out
        .println("Let's fetch and print a list of the contributors to this library (Using Mono).");
    List<Contributor> contributorsFromMono = github.contributorsMono("OpenFeign", "feign")
        .block();
    for (Contributor contributor : contributorsFromMono) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }
  }

  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    Flux<Contributor> contributorsFlux(@Param("owner") String owner, @Param("repo") String repo);

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    Mono<List<Contributor>> contributorsMono(@Param("owner") String owner,
                                             @Param("repo") String repo);
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
