/**
 * Copyright 2012-2018 The Feign Authors
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
        .decoder(JacksonIteratorDecoder.create())
        .doNotCloseAfterDecode()
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
