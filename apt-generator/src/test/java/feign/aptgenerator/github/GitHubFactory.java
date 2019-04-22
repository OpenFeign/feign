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

import java.util.Arrays;
import java.util.List;
import example.github.GitHubExample.*;
import feign.*;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.Request.HttpMethod;
import feign.Target.HardCodedTarget;

public class GitHubFactory implements GitHub {

  private static final MethodMetadata __repos_metadata;
  static {
    final MethodMetadata md = new MethodMetadata();
    __repos_metadata = md;
    md.returnType(new TypeReference<List<GitHub.Repository>>() {}.getType());
    md.configKey("GitHub#repos(String)");

    md.template().method(HttpMethod.GET);
    md.template().uri("/users/{username}/repos?sort=full_name");
    md.template().decodeSlash(true);
    md.template().collectionFormat(CollectionFormat.EXPLODED);

    md.indexToName().put(0, Arrays.asList("username"));
    md.indexToEncoded().put(0, false);
  }

  private static final MethodMetadata __contributors_metadata;
  static {
    final MethodMetadata md = new MethodMetadata();
    __contributors_metadata = md;
    md.returnType(new TypeReference<List<GitHub.Contributor>>() {}.getType());
    md.configKey("GitHub#contributors(String,String)");

    md.template().method(HttpMethod.GET);
    md.template().uri("/repos/{owner}/{repo}/contributors");
    md.template().decodeSlash(true);
    md.template().collectionFormat(CollectionFormat.EXPLODED);

    md.indexToName().put(0, Arrays.asList("owner"));
    md.indexToEncoded().put(0, false);

    md.indexToName().put(1, Arrays.asList("repo"));
    md.indexToEncoded().put(1, false);
  }

  private static final MethodMetadata __createIssue_metadata;
  static {
    final MethodMetadata md = new MethodMetadata();
    __createIssue_metadata = md;
    md.returnType(new TypeReference<List<GitHub.Contributor>>() {}.getType());
    md.configKey("GitHub#createIssue(Issue,String,String)");

    md.template().method(HttpMethod.POST);
    md.template().uri("/repos/{owner}/{repo}/issues");
    md.template().decodeSlash(true);
    md.template().collectionFormat(CollectionFormat.EXPLODED);

    md.indexToName().put(0, Arrays.asList("owner"));
    md.indexToEncoded().put(0, false);

    md.indexToName().put(1, Arrays.asList("repo"));
    md.indexToEncoded().put(1, false);
  }

  private final MethodHandler __repos_handler;
  private final MethodHandler __contributors_handler;
  private SynchronousMethodHandler __createIssue_handler;

  public GitHubFactory(FeignConfig feignConfig) {
    final HardCodedTarget<GitHub> target =
        new HardCodedTarget<GitHub>(GitHub.class, feignConfig.url);

    __repos_handler = new SynchronousMethodHandler(
        target,
        feignConfig,
        __repos_metadata,
        ReflectiveFeign.from(__repos_metadata, feignConfig));

    __contributors_handler = new SynchronousMethodHandler(
        target,
        feignConfig,
        __contributors_metadata,
        ReflectiveFeign.from(__contributors_metadata, feignConfig));

    __createIssue_handler = new SynchronousMethodHandler(
        target,
        feignConfig,
        __contributors_metadata,
        ReflectiveFeign.from(__contributors_metadata, feignConfig));

  }

  @Override
  public List<GitHub.Repository> repos(String owner) {
    try {
      return __repos_handler.invoke(owner);
    } catch (final FeignException e) {
      throw e;
    } catch (final Throwable e) {
      throw new FeignException(-1, "", e) {};
    }
  }

  @Override
  public List<GitHub.Contributor> contributors(String owner, String repo) {
    try {
      return __contributors_handler.invoke(owner, repo);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Throwable e) {
      throw new FeignException(-1, "", e) {};
    }
  }

  @Override
  public void createIssue(Issue issue, String owner, String repo) {
    try {
      __createIssue_handler.invoke(issue, owner, repo);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final Throwable e) {
      throw new FeignException(-1, "", e) {};
    }

  }

}
