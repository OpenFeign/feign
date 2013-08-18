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

import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.Logger;
import feign.Observable;
import feign.Observer;
import feign.RequestLine;
import feign.gson.GsonModule;

import javax.inject.Named;
import java.util.List;
import java.util.concurrent.CountDownLatch;

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
    GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GsonModule(), new LogToStderr());

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

  @Module(overrides = true, library = true)
  static class LogToStderr {

    @Provides Logger.Level loggingLevel() {
      return Logger.Level.BASIC;
    }

    @Provides Logger logger() {
      return new Logger.ErrorLogger();
    }
  }
}
