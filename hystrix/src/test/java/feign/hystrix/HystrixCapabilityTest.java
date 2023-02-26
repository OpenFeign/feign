/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.hystrix;

import feign.Feign;
import feign.Target;
import feign.gson.GsonDecoder;

public class HystrixCapabilityTest extends HystrixBuilderTest {

  @Override
  protected <E> E target(Class<E> api, String url) {
    return Feign.builder()
        .addCapability(
            new HystrixCapability())
        .target(api, url);
  }

  @Override
  protected <E> E target(Target<E> api) {
    return Feign.builder()
        .addCapability(
            new HystrixCapability())
        .target(api);
  }

  @Override
  protected <E> E target(Class<E> api, String url, E fallback) {
    return Feign.builder()
        .addCapability(new HystrixCapability()
            .fallback(api, fallback))
        .target(api, url);
  }

  @Override
  protected TestInterface target() {
    return Feign.builder()
        .addCapability(new HystrixCapability()
            .fallback(TestInterface.class,
                new FallbackTestInterface()))
        .decoder(new GsonDecoder())
        .target(TestInterface.class, "http://localhost:" + server.getPort());
  }

  @Override
  protected TestInterface targetWithoutFallback() {
    return Feign.builder()
        .addCapability(
            new HystrixCapability())
        .decoder(new GsonDecoder())
        .target(TestInterface.class, "http://localhost:" + server.getPort());
  }

}
