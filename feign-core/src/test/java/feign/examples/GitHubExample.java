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

import static dagger.Provides.Type.SET;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.stream.JsonReader;
import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.IncrementalCallback;
import feign.RequestLine;
import feign.codec.Decoder;
import feign.codec.IncrementalDecoder;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/** adapted from {@code com.example.retrofit.GitHubClient} */
public class GitHubExample {

  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Named("owner") String owner, @Named("repo") String repo);

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    void contributors(
        @Named("owner") String owner,
        @Named("repo") String repo,
        IncrementalCallback<Contributor> contributors);
  }

  static class Contributor {
    String login;
    int contributions;
  }

  public static void main(String... args) throws InterruptedException {
    GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GsonModule());

    System.out.println("Let's fetch and print a list of the contributors to this library.");
    List<Contributor> contributors = github.contributors("netflix", "feign");
    for (Contributor contributor : contributors) {
      System.out.println(contributor.login + " (" + contributor.contributions + ")");
    }

    final CountDownLatch latch = new CountDownLatch(1);

    System.out.println("Now, let's do it as an incremental async task.");
    IncrementalCallback<Contributor> task =
        new IncrementalCallback<Contributor>() {

          public int count;

          // parsed directly from the text stream without an intermediate collection.
          @Override
          public void onNext(Contributor contributor) {
            System.out.println(contributor.login + " (" + contributor.contributions + ")");
            count++;
          }

          @Override
          public void onSuccess() {
            System.out.println("found " + count + " contributors");
            latch.countDown();
          }

          @Override
          public void onFailure(Throwable cause) {
            cause.printStackTrace();
            latch.countDown();
          }
        };

    // fire a task in the background.
    github.contributors("netflix", "feign", task);

    // wait for the task to complete.
    latch.await();

    System.exit(0);
  }

  /** Here's how to wire gson deserialization. */
  @Module(overrides = true, library = true)
  static class GsonModule {
    @Provides
    @Singleton
    Gson gson() {
      return new Gson();
    }

    @Provides(type = SET)
    Decoder decoder(GsonDecoder gsonDecoder) {
      return gsonDecoder;
    }

    @Provides(type = SET)
    IncrementalDecoder incrementalDecoder(GsonDecoder gsonDecoder) {
      return gsonDecoder;
    }
  }

  static class GsonDecoder
      implements Decoder.TextStream<Object>, IncrementalDecoder.TextStream<Object> {
    private final Gson gson;

    @Inject
    GsonDecoder(Gson gson) {
      this.gson = gson;
    }

    @Override
    public Object decode(Reader reader, Type type) throws IOException {
      return fromJson(new JsonReader(reader), type);
    }

    @Override
    public void decode(
        Reader reader, Type type, IncrementalCallback<? super Object> incrementalCallback)
        throws IOException {
      JsonReader jsonReader = new JsonReader(reader);
      jsonReader.beginArray();
      while (jsonReader.hasNext()) {
        incrementalCallback.onNext(fromJson(jsonReader, type));
      }
      jsonReader.endArray();
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
