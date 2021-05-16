/**
 * Copyright 2012-2021 The Feign Authors
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
package feign.jackson.jr;

import com.fasterxml.jackson.jr.ob.JSON;
import com.fasterxml.jackson.jr.ob.JacksonJrExtension;

/**
 * Common implementation of the holder of a {@link JSON}, DRYing out construction.
 */
abstract class JacksonJrMapper {
  protected final JSON mapper;

  protected JacksonJrMapper() {
    this(JSON.std);
  }

  /**
   * Construct with a custom {@link JSON} to use for decoding/encoding
   * 
   * @param mapper the mapper to use
   */
  protected JacksonJrMapper(JSON mapper) {
    this.mapper = mapper;
  }

  /**
   * Construct with a series of {@link JacksonJrExtension} objects that are registered into the
   * {@link JSON}
   * 
   * @param iterable the source of the extensions
   */
  protected JacksonJrMapper(Iterable<JacksonJrExtension> iterable) {
    JSON.Builder builder = JSON.builder();
    iterable.forEach(builder::register);
    this.mapper = builder.build();
  }
}
