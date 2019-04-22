/**
 * Copyright 2012-2019 The Feign Authors
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
package feign;

import static feign.ExceptionPropagationPolicy.NONE;
import java.util.ArrayList;
import java.util.List;
import feign.Logger.NoOpLogger;
import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FeignConfig {

  @Builder.Default
  public final List<RequestInterceptor> requestInterceptors = new ArrayList<RequestInterceptor>();
  @Builder.Default
  public final Logger.Level logLevel = Logger.Level.NONE;
  @Builder.Default
  public final Contract contract = new Contract.Default();
  @Builder.Default
  public final Client client = new Client.Default(null, null);
  @Builder.Default
  public final Retryer retryer = new Retryer.Default();
  @Builder.Default
  public final Logger logger = new NoOpLogger();
  @Builder.Default
  public final Encoder encoder = new Encoder.Default();
  @Builder.Default
  public final Decoder decoder = new Decoder.Default();
  @Builder.Default
  public final QueryMapEncoder queryMapEncoder = new QueryMapEncoder.Default();
  @Builder.Default
  public final ErrorDecoder errorDecoder = new ErrorDecoder.Default();
  @Builder.Default
  public final Options options = new Options();
  @Builder.Default
  public final InvocationHandlerFactory invocationHandlerFactory =
      new InvocationHandlerFactory.Default();
  public final boolean decode404;
  @Builder.Default
  public final boolean closeAfterDecode = true;
  @Builder.Default
  public final ExceptionPropagationPolicy propagationPolicy = NONE;
  public final String url;

}
