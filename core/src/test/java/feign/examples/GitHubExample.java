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
package feign.examples;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.stream.JsonReader;
import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.Response;
import feign.codec.Decoder;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;

import static feign.Util.ensureClosed;

/**
 * adapted from {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {

  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Named("owner") String owner, @Named("repo") String repo);
  }

  static class Contributor {
    String login;
    int contributions;
  }

  public static void main(String... args) throws InterruptedException {
    GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GitHubModule());

    System.out.println("Let's fetch and print a list of the contributors to this library.");
    List<Contributor> contributors = github.contributors("netflix", "feign");
    for (Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }
  }

  @Module(overrides = true, library = true, includes = GsonModule.class)
  static class GitHubModule {

    @Provides Logger.Level loggingLevel() {
      return Logger.Level.BASIC;
    }

    @Provides Logger logger() {
      return new Logger.ErrorLogger();
    }
  }

  /**
   * Here's how it looks to wire json codecs.  Note, that you can always instead use {@code feign-gson}!
   */
  @Module(library = true)
  static class GsonModule {

    @Provides @Singleton Gson gson() {
      return new Gson();
    }

    @Provides Decoder decoder(GsonDecoder gsonDecoder) {
      return gsonDecoder;
    }
  }

  static class GsonDecoder implements Decoder {
    private final Gson gson;

    @Inject GsonDecoder(Gson gson) {
      this.gson = gson;
    }

    @Override public Object decode(Response response, Type type) throws IOException {
      if (response.body() == null) {
        return null;
      }
      Reader reader = response.body().asReader();
      try {
        return fromJson(new JsonReader(reader), type);
      } finally {
        ensureClosed(reader);
      }
    }

    private Object fromJson(JsonReader jsonReader, Type type) throws IOException {
      try {
        return gson.fromJson(jsonReader, type);
      } catch (JsonIOException e) {
        if (e.getCause() != null && e.getCause() instanceof IOException) {
          throw IOException.class.cast(e.getCause());
        }
        throw e;
      }
    }
  }
}
