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
package feign.micrometer;

import feign.Request;
import feign.Response;
import io.micrometer.observation.transport.RequestReplySenderContext;
import io.micrometer.observation.transport.SenderContext;

/**
 * A {@link SenderContext} for Feign.
 *
 * @author Marcin Grzejszczak
 * @since 12.1
 */
public class FeignContext extends RequestReplySenderContext<Request, Response> {

  public FeignContext(Request request) {
    super(Request::header);
    setCarrier(request);
  }

}
