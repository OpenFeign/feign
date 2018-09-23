/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.mock;

import feign.Request;
import feign.RequestTemplate;
import feign.Target;

public class MockTarget<E> implements Target<E> {

  private final Class<E> type;

  public MockTarget(Class<E> type) {
    this.type = type;
  }

  @Override
  public Class<E> type() {
    return type;
  }

  @Override
  public String name() {
    return type.getSimpleName();
  }

  @Override
  public String url() {
    return "";
  }

  @Override
  public Request apply(RequestTemplate input) {
    input.target(url());
    return input.request();
  }

}
