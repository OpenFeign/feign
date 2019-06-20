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
package feign.aptgenerator.github;

import com.google.gson.GsonBuilder;
import java.util.List;
import example.github.GitHubClientError;
import example.github.GitHubExample.GitHub;
import example.github.GitHubExample.GitHubErrorDecoder;
import feign.FeignConfig;
import feign.Logger;
import feign.codec.Decoder;
import feign.gson.GsonDecoder;

/**
 * Inspired by {@code com.example.retrofit.GitHubClient}
 */
public class GitHubFactoryExample {

  static FeignConfig config() {
    final Decoder decoder = new GsonDecoder(new GsonBuilder()
        .create());
    return FeignConfig.builder()
        .decoder(decoder)
        .errorDecoder(new GitHubErrorDecoder(decoder))
        .logger(new Logger.ErrorLogger())
        .logLevel(Logger.Level.FULL)
        .url("https://api.github.com")
        .build();
  }

  public static void main(String[] args) {
    final GitHub github = new GitHubFactory(config());

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
  }

}
