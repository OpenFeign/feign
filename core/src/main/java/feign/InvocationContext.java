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
import java.io.IOException;
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
    try {
      final boolean shouldDecodeResponseBody =
          (response.status() >= 200 && response.status() < 300)
              || (response.status() == 404 && dismiss404 && !isVoidType(returnType));

      if (!shouldDecodeResponseBody) {
        if (!shouldDecodeErrorResponseBody(response)) {
          throw decodeError(configKey, response);
        }
        // Buffer once: both the error decoder and the body decoder below need to read it, and a
        // response body is not generally replayable.
        response = bufferBody(response);
        Exception error = errorDecoder.decode(configKey, response);
        if (error instanceof RetryableException) {
          // Retryable failures stay exceptions, so Retryer keeps behaving as it does today.
          ensureClosed(response.body());
          throw error;
        }
        return decodeErrorResponseBody(response, error);
      }

      if (isVoidType(returnType) && !decodeVoid) {
        ensureClosed(response.body());
        return kotlinUnitInstance(returnType);
      }

      Class<?> rawType = Types.getRawType(returnType);
      if (TypedResponse.class.isAssignableFrom(rawType)) {
        Type bodyType = Types.resolveLastTypeParameter(returnType, TypedResponse.class);
        return TypedResponse.builder(response).body(decode(response, bodyType)).build();
      }

      return decode(response, returnType);
    } finally {
      if (closeAfterDecode) {
        ensureClosed(response.body());
      }
    }
  }

  /**
   * Whether the error body should be returned as a value rather than thrown, per {@link
   * feign.BaseBuilder#decodeErrorResponses()}. The return type is deliberately not inspected: the
   * method's signature is the declaration of what an error body should decode to. Retryability is
   * not checked here either, since it requires reading the body, which the caller only does once it
   * knows the flag is in play.
   */
  private boolean shouldDecodeErrorResponseBody(Response response) {
    if (!decodeErrorResponses || response.status() < 400) {
      return false;
    }
    // A decoder that declares what it handles gets to refuse an HTML error page from a proxy. One
    // that declares nothing cannot be asked, so the status range above is the only gate.
    return !(decoder instanceof PredicatedDecoder)
        || ((PredicatedDecoder) decoder).canDecode(response, returnType);
  }

  /**
   * Decodes the error body, falling back to throwing {@code error} if it does not decode. The
   * decoder is handed a response whose status reads {@code 200}: decoders commonly short-circuit
   * 404 and 204 to an empty value without reading the body, which would discard the envelope that
   * was asked for. {@link TypedResponse} is still built from the real response, so its status stays
   * truthful.
   */
  private Object decodeErrorResponseBody(Response response, Exception error) throws Exception {
    Response decodable = response.toBuilder().status(200).build();
    Class<?> rawType = Types.getRawType(returnType);
    try {
      if (TypedResponse.class.isAssignableFrom(rawType)) {
        Type bodyType = Types.resolveLastTypeParameter(returnType, TypedResponse.class);
        return TypedResponse.builder(response).body(decode(decodable, bodyType)).build();
      }
      return decode(decodable, returnType);
    } catch (FeignException e) {
      if (error == null) {
        // A custom ErrorDecoder may return null; the decode failure is then the only diagnosis.
        throw e;
      }
      error.addSuppressed(e);
      throw error;
    }
  }

  private static Response bufferBody(Response response) throws IOException {
    if (response.body() == null) {
      return response;
    }
    try {
      return response.toBuilder().body(Util.toByteArray(response.body().asInputStream())).build();
    } finally {
      ensureClosed(response.body());
    }
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
