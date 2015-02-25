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

import java.util.List;

import feign.Feign;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;

/**
 * adapted from {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {

  public static void main(String... args) throws InterruptedException {
    Decoder decoder = new GsonDecoder();
    GitHub github = Feign.builder()
        .decoder(decoder)
        .errorDecoder(new GitHubErrorDecoder(decoder))
        .logger(new Logger.ErrorLogger())
        .logLevel(Logger.Level.BASIC)
        .target(GitHub.class, "https://api.github.com");

    System.out.println("Let's fetch and print a list of the contributors to this library.");
    List<Contributor> contributors = github.contributors("netflix", "feign");
    for (Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }

    try {
      contributors = github.contributors("netflix", "some-unknown-project");
    } catch (GitHubClientError e) {
      System.out.println(e.error.message);
    }
  }

  interface GitHub {

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Param("owner") String owner, @Param("repo") String repo);
  }

  static class Contributor {

    String login;
    int contributions;
  }

  static class ClientError {

    String message;
    List<Error> errors;
  }

  static class Error {
    String resource;
    String field;
    String code;
  }

  static class GitHubErrorDecoder implements ErrorDecoder {

    final Decoder decoder;
    final ErrorDecoder defaultDecoder = new ErrorDecoder.Default();

    public GitHubErrorDecoder(Decoder decoder) {
      this.decoder = decoder;
    }

    public Exception decode(String methodKey, Response response) {
      if (response.status() >= 400 && response.status() < 500) {
        try {
          ClientError error = (ClientError) decoder.decode(response, ClientError.class );
          return new GitHubClientError(response.status(), error);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
      return defaultDecoder.decode(methodKey, response);
    }
  }

  static class GitHubClientError extends RuntimeException {

    private static final long serialVersionUID = 0;

    ClientError error;

    protected GitHubClientError(int status, ClientError error) {
      super("client error " + status);
      this.error = error;
    }

  }
}
