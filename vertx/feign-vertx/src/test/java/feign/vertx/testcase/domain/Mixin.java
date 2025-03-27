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
package feign.vertx.testcase.domain;

import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Ice cream mix-ins.
 *
 * @author Alexei KLENIN
 */
public enum Mixin {
  COOKIES,
  MNMS,
  CHOCOLATE_SIROP,
  STRAWBERRY_SIROP,
  NUTS,
  RAINBOW;

  public static final String MIXINS_JSON =
      Stream.of(Mixin.values())
          .map(flavor -> "\"" + flavor + "\"")
          .collect(Collectors.joining(", ", "[ ", " ]"));
}
