/**
 * Copyright 2012-2020 The Feign Authors
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
package example.github;

import java.util.concurrent.atomic.AtomicInteger;
import feign.Experimental;

public class GitHubStub
    implements example.github.GitHubExample.GitHub {

  @Experimental
  public class GitHubInvokations {

    private final AtomicInteger repos = new AtomicInteger(0);

    public int repos() {
      return repos.get();
    }

    private final AtomicInteger contributors = new AtomicInteger(0);

    public int contributors() {
      return contributors.get();
    }

    private final AtomicInteger createIssue = new AtomicInteger(0);

    public int createIssue() {
      return createIssue.get();
    }

  }

  @Experimental
  public class GitHubAnwsers {

    private java.util.List<example.github.GitHubExample.GitHub.Repository> reposDefault;

    private java.util.List<example.github.GitHubExample.GitHub.Contributor> contributorsDefault;

  }

  public GitHubInvokations invokations;
  public GitHubAnwsers answers;

  public GitHubStub() {
    this.invokations = new GitHubInvokations();
    this.answers = new GitHubAnwsers();
  }

  @Experimental
  public GitHubStub withRepos(java.util.List<example.github.GitHubExample.GitHub.Repository> repos) {
    answers.reposDefault = repos;
    return this;
  }

  @Override
  public java.util.List<example.github.GitHubExample.GitHub.Repository> repos(java.lang.String owner) {
    invokations.repos.incrementAndGet();

    return answers.reposDefault;
  }

  @Experimental
  public GitHubStub withContributors(java.util.List<example.github.GitHubExample.GitHub.Contributor> contributors) {
    answers.contributorsDefault = contributors;
    return this;
  }


  @Override
  public java.util.List<example.github.GitHubExample.GitHub.Contributor> contributors(java.lang.String owner,
                                                                                      java.lang.String repo) {
    invokations.contributors.incrementAndGet();

    return answers.contributorsDefault;
  }

  @Override
  public void createIssue(example.github.GitHubExample.GitHub.Issue issue,
                          java.lang.String owner,
                          java.lang.String repo) {
    invokations.createIssue.incrementAndGet();

  }

}
