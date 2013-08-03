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
package feign.rxjava;

import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.gson.GsonModule;
import org.testng.annotations.Test;
import rx.Observable;
import rx.Observer;

import javax.inject.Named;
import java.util.concurrent.CountDownLatch;

/**
 * Explores the interactions between Feign and RxJava.
 */
@Test
public class RxJavaLearningTest {

  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    feign.Observable<Contributor> observable(@Named("owner") String owner, @Named("repo") String repo);
  }

  static class Contributor {
    String login;
    int contributions;
  }

  public static void main(String... args) throws InterruptedException {
    GitHub github = Feign.create(GitHub.class, "https://api.github.com", new GitHubModule());

    System.out.println("Let's treat our contributors as an observable.");
    Observable<Contributor> observable = ToRxObservable.convert(github.observable("netflix", "feign"));

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

    @Override public void onCompleted() {
      System.out.println("found " + count + " contributors");
      latch.countDown();
    }

    @Override public void onError(Throwable cause) {
      cause.printStackTrace();
      latch.countDown();
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
}
