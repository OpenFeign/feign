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
package feign.json.examples;

import feign.Feign;
import feign.Param;
import feign.RequestLine;
import feign.json.JsonDecoder;
import org.json.JSONArray;
import org.json.JSONObject;

interface GitHub {

  @RequestLine("GET /repos/{owner}/{repo}/contributors")
  JSONArray contributors(@Param("owner") String owner, @Param("repo") String repo);

}


/**
 * adapted from {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {

  public static void main(String... args) {
    GitHub github = Feign.builder()
        .decoder(new JsonDecoder())
        .target(GitHub.class, "https://api.github.com");

    System.out.println("Let's fetch and print a list of the contributors to this library.");
    JSONArray contributors = github.contributors("netflix", "feign");
    contributors.forEach(contributor -> {
      System.out.println(((JSONObject) contributor).getString("login"));
    });
  }

}
