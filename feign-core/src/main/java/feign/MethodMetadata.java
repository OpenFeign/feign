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
package feign;

import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MethodMetadata implements Serializable {

  MethodMetadata() {
  }

  private String configKey;
  private transient Type decodeInto;
  private Integer urlIndex;
  private Integer incrementalCallbackIndex;
  private Integer bodyIndex;
  private transient Type bodyType;
  private RequestTemplate template = new RequestTemplate();
  private List<String> formParams = new ArrayList<String>();
  private Map<Integer, Collection<String>> indexToName = new LinkedHashMap<Integer, Collection<String>>();

  /**
   * @see Feign#configKey(java.lang.reflect.Method)
   */
  public String configKey() {
    return configKey;
  }

  MethodMetadata configKey(String configKey) {
    this.configKey = configKey;
    return this;
  }

  /**
   * Method return type unless there is an {@link IncrementalCallback} arg.  In this case, it is the type parameter of the
   * incrementalCallback.
   */
  public Type decodeInto() {
    return decodeInto;
  }

  MethodMetadata decodeInto(Type decodeInto) {
    this.decodeInto = decodeInto;
    return this;
  }

  public Integer urlIndex() {
    return urlIndex;
  }

  MethodMetadata urlIndex(Integer urlIndex) {
    this.urlIndex = urlIndex;
    return this;
  }

  public Integer incrementalCallbackIndex() {
    return incrementalCallbackIndex;
  }

  MethodMetadata incrementalCallbackIndex(Integer incrementalCallbackIndex) {
    this.incrementalCallbackIndex = incrementalCallbackIndex;
    return this;
  }

  public Integer bodyIndex() {
    return bodyIndex;
  }

  MethodMetadata bodyIndex(Integer bodyIndex) {
    this.bodyIndex = bodyIndex;
    return this;
  }

  public Type bodyType() {
    return bodyType;
  }

  MethodMetadata bodyType(Type bodyType) {
    this.bodyType = bodyType;
    return this;
  }

  public RequestTemplate template() {
    return template;
  }

  public List<String> formParams() {
    return formParams;
  }

  public Map<Integer, Collection<String>> indexToName() {
    return indexToName;
  }

  private static final long serialVersionUID = 1L;

}
