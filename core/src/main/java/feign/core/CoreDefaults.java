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
package feign.core;

import com.google.auto.service.AutoService;
import feign.Client;
import feign.Contract;
import feign.FeignDefaults;
import feign.InvocationHandlerFactory;
import feign.Logger;
import feign.QueryMapEncoder;
import feign.Retryer;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.core.codec.DefaultDecoder;
import feign.core.codec.DefaultEncoder;
import feign.core.codec.DefaultErrorDecoder;
import feign.core.querymap.FieldQueryMapEncoder;

/** Supplies feign-core's standard implementations of Feign's pluggable components. */
@AutoService(FeignDefaults.class)
public final class CoreDefaults implements FeignDefaults {

  @Override
  public Contract contract() {
    return new DefaultContract();
  }

  @Override
  public Retryer retryer() {
    return new DefaultRetryer();
  }

  @Override
  public Logger logger() {
    return new Logger.NoOpLogger();
  }

  @Override
  public Encoder encoder() {
    return new DefaultEncoder();
  }

  @Override
  public Decoder decoder() {
    return new DefaultDecoder();
  }

  @Override
  public QueryMapEncoder queryMapEncoder() {
    return new FieldQueryMapEncoder();
  }

  @Override
  public ErrorDecoder errorDecoder() {
    return new DefaultErrorDecoder();
  }

  @Override
  public InvocationHandlerFactory invocationHandlerFactory() {
    return new DefaultInvocationHandlerFactory();
  }

  @Override
  public Client client() {
    return new DefaultClient();
  }
}
