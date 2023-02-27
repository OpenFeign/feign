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
package feign.benchmark;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.MethodMetadata;
import feign.Request;
import feign.Response;
import feign.Target.HardCodedTarget;

@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class WhatShouldWeCacheBenchmarks {

  private Contract feignContract;
  private Contract cachedContact;
  private Client fakeClient;
  private Feign cachedFakeFeign;
  private FeignTestInterface cachedFakeApi;

  @Setup
  public void setup() {
    feignContract = new Contract.Default();
    cachedContact = new Contract() {
      private final List<MethodMetadata> cached =
          new Default().parseAndValidateMetadata(FeignTestInterface.class);

      public List<MethodMetadata> parseAndValidateMetadata(Class<?> declaring) {
        return cached;
      }
    };
    fakeClient = new Client() {
      public Response execute(Request request, Request.Options options) throws IOException {
        Map<String, Collection<String>> headers = new LinkedHashMap<String, Collection<String>>();
        return Response.builder()
            .body((byte[]) null)
            .status(200)
            .headers(headers)
            .reason("ok")
            .request(request)
            .build();
      }
    };
    cachedFakeFeign = Feign.builder().client(fakeClient).build();
    cachedFakeApi = cachedFakeFeign.newInstance(
        new HardCodedTarget<FeignTestInterface>(FeignTestInterface.class, "http://localhost"));
  }

  /**
   * How fast is parsing an api interface?
   */
  @Benchmark
  public List<MethodMetadata> parseFeignContract() {
    return feignContract.parseAndValidateMetadata(FeignTestInterface.class);
  }

  /**
   * How fast is creating a feign instance for each http request, without considering network?
   */
  @Benchmark
  public Response buildAndQuery_fake() {
    return Feign.builder().client(fakeClient)
        .target(FeignTestInterface.class, "http://localhost").query();
  }

  /**
   * How fast is creating a feign instance for each http request, without considering network, and
   * without re-parsing the annotated http api?
   */
  @Benchmark
  public Response buildAndQuery_fake_cachedContract() {
    return Feign.builder().contract(cachedContact).client(fakeClient)
        .target(FeignTestInterface.class, "http://localhost").query();
  }

  /**
   * How fast re-parsing the annotated http api for each http request, without considering network?
   */
  @Benchmark
  public Response buildAndQuery_fake_cachedFeign() {
    return cachedFakeFeign.newInstance(
        new HardCodedTarget<FeignTestInterface>(FeignTestInterface.class, "http://localhost"))
        .query();
  }

  /**
   * How fast is our advice to use a cached api for each http request, without considering network?
   */
  @Benchmark
  public Response buildAndQuery_fake_cachedApi() {
    return cachedFakeApi.query();
  }
}
