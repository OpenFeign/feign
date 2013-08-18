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
import feign.Observable;
import feign.Observer;
import feign.RequestLine;
import feign.codec.Decoder;
import feign.codec.IncrementalDecoder;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static dagger.Provides.Type.SET;

/**
 * adapted from {@code com.example.retrofit.GitHubClient}
 */
public class GitHubExample {

  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<Contributor> contributors(@Named("owner") String owner, @Named("repo") String repo);

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    Observable<Contributor> observable(@Named("owner") String owner, @Named("repo") String repo);
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

    System.out.println("Let's treat our contributors as an observable.");
    Observable<Contributor> observable = github.observable("netflix", "feign");

    CountDownLatch latch = new CountDownLatch(2);

    System.out.println("Let's add 2 subscribers.");
    observable.subscribe(new ContributorObserver(latch));
    observable.subscribe(new ContributorObserver(latch));

    // wait for the task to complete.
    latch.await();

    System.exit(0);
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

    @Provides(type = SET) Decoder decoder(GsonDecoder gsonDecoder) {
      return gsonDecoder;
    }

    @Provides(type = SET) IncrementalDecoder incrementalDecoder(GsonDecoder gsonDecoder) {
      return gsonDecoder;
    }
  }

  static class GsonDecoder implements Decoder.TextStream<Object>, IncrementalDecoder.TextStream<Object> {
    private final Gson gson;

    @Inject GsonDecoder(Gson gson) {
      this.gson = gson;
    }

    @Override public Object decode(Reader reader, Type type) throws IOException {
      return fromJson(new JsonReader(reader), type);
    }

    @Override
    public void decode(Reader reader, Type type, Observer<? super Object> observer, AtomicBoolean subscribed) throws IOException {
      JsonReader jsonReader = new JsonReader(reader);
      jsonReader.beginArray();
      while (jsonReader.hasNext() && subscribed.get()) {
        observer.onNext(fromJson(jsonReader, type));
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

  static class ContributorObserver implements Observer<Contributor> {

    private final CountDownLatch latch;
    public int count;

    public ContributorObserver(CountDownLatch latch) {
      this.latch = latch;
    }

    // parsed directly from the text stream without an intermediate collection.
    @Override public void onNext(Contributor contributor) {
      count++;
    }

    @Override public void onSuccess() {
      System.out.println("found " + count + " contributors");
      latch.countDown();
    }

    @Override public void onFailure(Throwable cause) {
      cause.printStackTrace();
      latch.countDown();
    }
  }
}
