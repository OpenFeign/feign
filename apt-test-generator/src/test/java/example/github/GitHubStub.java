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
package example.github;

import java.util.concurrent.atomic.AtomicInteger;

public class GitHubStub
    implements example.github.GitHubExample.GitHub {

  protected final AtomicInteger __invocation_count_method_0 = new AtomicInteger(0);

  public int reposInvocationCount() {
    return __invocation_count_method_0.get();
  }


  protected java.util.List<example.github.GitHubExample.GitHub.Repository> __answer_method_0;

  public GitHubStub withRepos(java.util.List<example.github.GitHubExample.GitHub.Repository> arg) {
    this.__answer_method_0 = arg;
    return this;
  }

  @Override
  public java.util.List<example.github.GitHubExample.GitHub.Repository> repos(java.lang.String owner) {
    __invocation_count_method_0.incrementAndGet();
    return this.__answer_method_0;
  }

  protected final AtomicInteger __invocation_count_method_1 = new AtomicInteger(0);

  public int contributorsInvocationCount() {
    return __invocation_count_method_1.get();
  }

  protected java.util.List<example.github.GitHubExample.GitHub.Contributor> __answer_method_1;

  public GitHubStub withContributors(java.util.List<example.github.GitHubExample.GitHub.Contributor> arg) {
    this.__answer_method_1 = arg;
    return this;
  }

  @Override
  public java.util.List<example.github.GitHubExample.GitHub.Contributor> contributors(java.lang.String owner,
                                                                                      java.lang.String repo) {
    __invocation_count_method_1.incrementAndGet();
    return this.__answer_method_1;
  }

  protected final AtomicInteger __invocation_count_method_2 = new AtomicInteger(0);

  public int createIssueInvocationCount() {
    return __invocation_count_method_2.get();
  }

  @Override
  public void createIssue(example.github.GitHubExample.GitHub.Issue issue,
                          java.lang.String owner,
                          java.lang.String repo) {
    __invocation_count_method_2.incrementAndGet();
  }


}
