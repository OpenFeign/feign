/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.form.multipart;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * A chain of {@link PartContextResolver} instances that can be used to resolve a {@link
 * PartContext} into a stream of {@link PartContext} instances.
 */
@RequiredArgsConstructor
public class PartContextResolverChain {
  @NonNull private final List<PartContextResolver> resolvers;
  private final int index;

  public PartContextResolverChain() {
    this(defaultResolvers());
  }

  /**
   * Creates a new {@link PartContextResolverChain} with the given list of {@link
   * PartContextResolver} instances.
   *
   * @param resolvers the list of {@link PartContextResolver} instances to use for resolving {@link
   *     PartContext} instances
   */
  public PartContextResolverChain(List<PartContextResolver> resolvers) {
    this(resolvers, 0);
  }

  /**
   * Creates a new {@link PartContextResolverChain} with the customized list of {@link
   * PartContextResolver} instances.
   *
   * @param resolversCustomizer a {@link Consumer} that can be used to customize the list of {@link
   *     PartContextResolver}
   */
  public PartContextResolverChain(
      @NonNull Consumer<List<PartContextResolver>> resolversCustomizer) {
    this(customResolvers(resolversCustomizer));
  }

  private static List<PartContextResolver> defaultResolvers() {
    var resolvers = new ArrayList<PartContextResolver>();

    resolvers.add(new FormDataPartContextResolver());
    resolvers.add(new ArrayPartContextResolver());
    resolvers.add(new CollectionPartContextResolver());
    resolvers.add(new FilePartContextResolver());
    resolvers.add(new PathPartContextResolver());

    return resolvers;
  }

  private static List<PartContextResolver> customResolvers(
      Consumer<List<PartContextResolver>> resolversCustomizer) {
    var resolvers = defaultResolvers();

    resolversCustomizer.accept(resolvers);

    return resolvers;
  }

  /**
   * Resolves the given {@link PartContext} using the chain of {@link PartContextResolver}
   * instances.
   *
   * @param partContext the {@link PartContext} to resolve
   * @return a stream of resolved {@link PartContext} instances
   */
  public Stream<PartContext> resolve(PartContext partContext) {
    if (index >= resolvers.size()) {
      return Stream.of(partContext);
    }

    return resolvers
        .get(index)
        .resolve(partContext, new PartContextResolverChain(resolvers, index + 1));
  }
}
