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
package feign;

import static feign.FeignException.errorReading;
import static feign.Util.ensureClosed;

import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import feign.codec.PredicatedDecoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.lang.reflect.Type;

public class InvocationContext {
  private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;
  private final String configKey;
  private final Decoder decoder;
  private final ErrorDecoder errorDecoder;
  private final boolean dismiss404;
  private final boolean closeAfterDecode;
  private final boolean decodeVoid;
  private final boolean decodeErrorResponses;
  private final Response response;
  private final Type returnType;

  InvocationContext(
      String configKey,
      Decoder decoder,
      ErrorDecoder errorDecoder,
      boolean dismiss404,
      boolean closeAfterDecode,
      boolean decodeVoid,
      Response response,
      Type returnType) {
    this(
        configKey,
        decoder,
        errorDecoder,
        dismiss404,
        closeAfterDecode,
        decodeVoid,
        false,
        response,
        returnType);
  }

  InvocationContext(
      String configKey,
      Decoder decoder,
      ErrorDecoder errorDecoder,
      boolean dismiss404,
      boolean closeAfterDecode,
      boolean decodeVoid,
      boolean decodeErrorResponses,
      Response response,
      Type returnType) {
    this.configKey = configKey;
    this.decoder = decoder;
    this.errorDecoder = errorDecoder;
    this.dismiss404 = dismiss404;
    this.closeAfterDecode = closeAfterDecode;
    this.decodeVoid = decodeVoid;
    this.decodeErrorResponses = decodeErrorResponses;
    this.response = response;
    this.returnType = returnType;
  }

  public Decoder decoder() {
    return decoder;
  }

  public Type returnType() {
    return returnType;
  }

  public Response response() {
    return response;
  }

  public Object proceed() throws Exception {
    if (returnType == Response.class) {
      return disconnectResponseBodyIfNeeded(response);
    }

    Response response = this.response;
    boolean shouldClose = closeAfterDecode;

    try {
      final boolean shouldDecodeResponseBody =
          (response.status() >= 200 && response.status() < 300)
              || (response.status() == 404 && dismiss404 && !isVoidType(returnType));

      if (!shouldDecodeResponseBody) {
        if (!shouldDecodeErrorResponseBody(response)) {
          throw decodeError(configKey, response);
        }

        response = bufferBodyWithinLimit(response);
        if (!isBufferedAndNonEmpty(response.body())) {
          throw decodeError(configKey, response);
        }
        Exception error = errorDecoder.decode(configKey, response);
        if (error instanceof RetryableException) {
          throw error;
        }
        return decodeErrorResponseBody(response, error);
      }

      // By default, Closeable return types will leave the body stream open, but there may be
      // downstream logic that reverses this decision
      if (isReturnTypeCloseable()) {
        shouldClose = false;
      }

      if (isVoidType(returnType)) {
    	  	shouldClose = true; // override closeAfterDecode if void return type
        if (!decodeVoid) return kotlinUnitInstance(returnType);
      }

      if (TypedResponse.class.isAssignableFrom(Types.getRawType(returnType))) {
        Type bodyType = Types.resolveLastTypeParameter(returnType, TypedResponse.class);
        Object decodeResult = decode(response, bodyType);
        if (decodeResult == null) shouldClose = true;
        return TypedResponse.builder(response).body(decodeResult).build();
      } else {
        Object result = decode(response, returnType);
        if (result == null) shouldClose = true;
        return result;
      }

    } finally {
      if (shouldClose) {
        ensureClosed(response.body());
      }
    }
  }

  private boolean isReturnTypeCloseable() {

    if (isVoidType(returnType)) return false;

    Type rawType = Types.getRawType(returnType);

    if (TypedResponse.class.isAssignableFrom(Types.getRawType(rawType))) {
      rawType = Types.resolveLastTypeParameter(returnType, TypedResponse.class);
    }

    return Closeable.class.isAssignableFrom(Types.getRawType(rawType));
  }

  private boolean shouldDecodeErrorResponseBody(Response response) {
    if (!decodeErrorResponses
        || response.status() < 400
        || response.status() == 404
        || response.body() == null
        || isVoidType(returnType)) {
      return false;
    }
    return !(decoder instanceof PredicatedDecoder)
        || ((PredicatedDecoder) decoder).canDecode(response, returnType);
  }

  private Object decodeErrorResponseBody(Response response, Exception error) throws Exception {
    Class<?> rawType = Types.getRawType(returnType);
    try {
      if (TypedResponse.class.isAssignableFrom(rawType)) {
        Type bodyType = Types.resolveLastTypeParameter(returnType, TypedResponse.class);
        return TypedResponse.builder(response).body(decode(response, bodyType)).build();
      }
      return decode(response, returnType);
    } catch (RuntimeException e) {
      if (error == null) {
        throw e;
      }
      error.addSuppressed(e);
      throw error;
    }
  }

  private static boolean isBufferedAndNonEmpty(Response.Body body) {
    return body.isRepeatable()
        && body.length() != null
        && body.length() > 0
        && body.length() <= MAX_RESPONSE_BUFFER_SIZE;
  }

  private static Response bufferBodyWithinLimit(Response response) throws IOException {
    Integer length = response.body().length();
    if (length != null && length > MAX_RESPONSE_BUFFER_SIZE) {
      return response;
    }
    InputStream stream = response.body().asInputStream();
    byte[] head = readAtMost(stream, (int) MAX_RESPONSE_BUFFER_SIZE + 1);
    if (head.length > MAX_RESPONSE_BUFFER_SIZE) {
      return response.toBuilder()
          .body(new SequenceInputStream(new ByteArrayInputStream(head), stream), null)
          .build();
    }
    ensureClosed(response.body());
    return response.toBuilder().body(head).build();
  }

  private static byte[] readAtMost(InputStream stream, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] chunk = new byte[1024];
    int read;
    while (out.size() < limit
        && (read = stream.read(chunk, 0, Math.min(chunk.length, limit - out.size()))) != -1) {
      out.write(chunk, 0, read);
    }
    return out.toByteArray();
  }

  private static Response disconnectResponseBodyIfNeeded(Response response) throws IOException {
    final boolean shouldDisconnectResponseBody =
        response.body() != null
            && response.body().length() != null
            && response.body().length() <= MAX_RESPONSE_BUFFER_SIZE;
    if (!shouldDisconnectResponseBody) {
      return response;
    }

    try {
      final byte[] bodyData = Util.toByteArray(response.body().asInputStream());
      return response.toBuilder().body(bodyData).build();
    } finally {
      ensureClosed(response.body());
    }
  }

  private Object decode(Response response, Type returnType) {
    try {
      return decoder.decode(response, returnType);
    } catch (final FeignException e) {
      throw e;
    } catch (final RuntimeException e) {
      throw new DecodeException(response.status(), e.getMessage(), response.request(), e);
    } catch (IOException e) {
      throw errorReading(response.request(), response, e);
    }
  }

  private Exception decodeError(String methodKey, Response response) {
    try {
      return errorDecoder.decode(methodKey, response);
    } finally {
      ensureClosed(response.body());
    }
  }

  private boolean isVoidType(Type returnType) {
    return returnType == Void.class
        || returnType == void.class
        || returnType.getTypeName().equals("kotlin.Unit");
  }

  /**
   * Kotlin's {@code Unit} is non-nullable, so a suspend function declared to return it must get
   * back the singleton {@code Unit.INSTANCE} rather than {@code null}. Resolved reflectively since
   * feign-core has no compile-time dependency on kotlin-stdlib.
   */
  private static Object kotlinUnitInstance(Type returnType) {
    if (!(returnType instanceof Class) || !returnType.getTypeName().equals("kotlin.Unit")) {
      return null;
    }
    try {
      return ((Class<?>) returnType).getField("INSTANCE").get(null);
    } catch (ReflectiveOperationException e) {
      return null;
    }
  }
}
