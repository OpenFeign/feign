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
package feign;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

import feign.codec.EncodeException;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

class RequestTemplateFactoryResolverTest {
  @Nested
  class BuildFormEncodedTemplateFromArgsTest {
    @Test
    void shouldThrowEncodeException() {
      var methodMetadata = new MethodMetadata();
      var variables = Map.<String, Object>of("data", "Hello, World!");
      var factory =
          new RequestTemplateFactoryResolver.BuildFormEncodedTemplateFromArgs(
              methodMetadata, mock(), mock(), mock());

      methodMetadata.formParams().add("data");

      assertThrows(
          EncodeException.class,
          () -> factory.resolve(new Object[0], new RequestTemplate(), variables));
    }
  }

  @Nested
  class BuildEncodedTemplateFromArgsTest {
    private static final Supplier<Stream<Arguments>> shouldThrowEncodeException =
        () -> {
          var methodMetadata1 = new MethodMetadata();
          methodMetadata1.alwaysEncodeBody(true);

          var methodMetadata2 = new MethodMetadata();
          methodMetadata2.bodyIndex(0);

          return Stream.of(
              arguments(methodMetadata1, new Object[0]),
              arguments(methodMetadata2, new Object[] {"Hello, World!"}));
        };

    @ParameterizedTest
    @FieldSource
    void shouldThrowEncodeException(MethodMetadata methodMetadata, Object[] argv) {
      var factory =
          new RequestTemplateFactoryResolver.BuildEncodedTemplateFromArgs(
              methodMetadata, mock(), mock(), mock());
      var mutable = new RequestTemplate();

      mutable.methodMetadata(methodMetadata);

      assertThrows(EncodeException.class, () -> factory.resolve(argv, mutable, Map.of()));
    }
  }
}
