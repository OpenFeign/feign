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

import java.util.stream.Stream;

/**
 * A functional interface for resolving a {@link PartContext} into a stream of {@link PartContext}
 * instances.
 */
@FunctionalInterface
public interface PartContextResolver {
  /**
   * Resolves a {@link PartContext} into a stream of {@link PartContext} instances.
   *
   * @param partContext the {@link PartContext} to resolve
   * @param chain the {@link PartContextResolverChain} to use for resolving the {@link PartContext}
   * @return a stream of {@link PartContext} instances
   */
  Stream<PartContext> resolve(PartContext partContext, PartContextResolverChain chain);
}
