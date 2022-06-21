/*
 * Copyright 2012-2022 The Feign Authors
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

import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;

public class InvocationContext {

  private final Decoder decoder;
  private final MethodMetadata metadata;
  private final Response response;

  public InvocationContext(Decoder decoder, MethodMetadata metadata, Response response) {
    this.decoder = decoder;
    this.metadata = metadata;
    this.response = response;
  }

  public Object proceed() {
    try {
      return decoder.decode(response, metadata.returnType());
    } catch (IOException e) {
      throw new DecodeException(response.status(), "decode error cause of io exception",
          response.request(), e);
    }
  }

  public Decoder getDecoder() {
    return decoder;
  }

  public MethodMetadata getMetadata() {
    return metadata;
  }

  public Response getResponse() {
    return response;
  }

}
