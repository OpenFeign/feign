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

import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;

/**
 * Aggregated factory for Feign's built-in default components. A single implementation is discovered
 * once through {@link java.util.ServiceLoader}, replacing what would otherwise be a separate lookup
 * per component. {@code feign-core} supplies the standard implementation; an application may
 * override the whole set by registering its own provider (any implementation outside the {@code
 * feign.core} package takes precedence).
 *
 * <p>Each accessor returns a fresh instance, mirroring the per-builder defaults Feign created
 * before the lookups were consolidated.
 */
public interface FeignDefaults {

  Contract contract();

  Retryer retryer();

  Logger logger();

  Encoder encoder();

  Decoder decoder();

  QueryMapEncoder queryMapEncoder();

  ErrorDecoder errorDecoder();

  InvocationHandlerFactory invocationHandlerFactory();

  Client client();
}
