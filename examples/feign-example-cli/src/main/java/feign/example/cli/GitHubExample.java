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
package feign.example.cli;

import com.google.gson.Gson;
import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.RequestLine;
import feign.codec.Decoder;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Named;
import javax.inject.Singleton;

/** adapted from {@code com.example.retrofit.GitHubClient} */
public class GitHubExample {

  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Named("owner") String owner, @Named("repo") String repo);
  }

  static class Contributor {
    String login;
    int contributions;
  }

  public static void main(String... args) {
    GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GsonModule());

    // Fetch and print a list of the contributors to this library.
    List<Contributor> contributors = github.contributors("netflix", "feign");
    for (Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }
  }

  /** Here's how to wire gson deserialization. */
  @Module(overrides = true, library = true)
  static class GsonModule {
    @Provides
    @Singleton
    Map<String, Decoder> decoders() {
      Map<String, Decoder> decoders = new LinkedHashMap<String, Decoder>();
      decoders.put("GitHub", jsonDecoder);
      return decoders;
    }

    final Decoder jsonDecoder =
        new Decoder() {
          Gson gson = new Gson();

          @Override
          public Object decode(String methodKey, Reader reader, Type type) {
            return gson.fromJson(reader, type);
          }
        };
  }
}
