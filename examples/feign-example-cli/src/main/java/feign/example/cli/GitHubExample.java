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

import feign.Feign;
import feign.IncrementalCallback;
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
    void contributors(@Named("owner") String owner, @Named("repo") String repo,
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
    IncrementalCallback<Contributor> task = new IncrementalCallback<Contributor>() {

      public int count;

      // parsed directly from the text stream without an intermediate collection.
      @Override public void onNext(Contributor contributor) {
        System.out.println(contributor.login + " (" + contributor.contributions + ")");
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
    };

    // fire a task in the background.
    github.contributors("netflix", "feign", task);

    // wait for the task to complete.
    latch.await();

    System.exit(0);
  }
}
