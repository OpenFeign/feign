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
package feign.reactive;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;

import feign.Contract;
import feign.Param;
import feign.RequestLine;
import io.reactivex.Flowable;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ReactiveDelegatingContractTest {

  @Test
  void onlyReactiveReturnTypesSupported() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              Contract contract = new ReactiveDelegatingContract(new Contract.Default());
              contract.parseAndValidateMetadata(TestSynchronousService.class);
            });
  }

  @Test
  void reactorTypes() {
    Contract contract = new ReactiveDelegatingContract(new Contract.Default());
    contract.parseAndValidateMetadata(TestReactorService.class);
  }

  @Test
  void reactivexTypes() {
    Contract contract = new ReactiveDelegatingContract(new Contract.Default());
    contract.parseAndValidateMetadata(TestReactiveXService.class);
  }

  @Test
  void streamsAreNotSupported() {
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(
            () -> {
              Contract contract = new ReactiveDelegatingContract(new Contract.Default());
              contract.parseAndValidateMetadata(StreamsService.class);
            });
  }

  public interface TestSynchronousService {
    @RequestLine("GET /version")
    String version();
  }

  public interface TestReactiveXService {
    @RequestLine("GET /version")
    Flowable<String> version();
  }

  public interface TestReactorService {
    @RequestLine("GET /version")
    Mono<String> version();

    @RequestLine("GET /users/{username}")
    Flux<String> user(@Param("username") String username);
  }

  public interface StreamsService {

    @RequestLine("GET /version")
    Mono<Stream<String>> version();
  }
}
