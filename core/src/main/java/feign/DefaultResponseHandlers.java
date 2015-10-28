package feign;

import feign.codec.DecodeException;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

import java.io.IOException;

public class DefaultResponseHandlers {

  /**
   * Handles responses if the method's return type (indicated by {@link MethodMetadata#returnType})
   * is {@code Response.class}.
   */
  public static class ResponseResponseHandler implements ResponseHandler {
    @Override
    public boolean handle(Response response, MethodMetadata metadata, ResultHolder result)
        throws IOException {

      if (Response.class == metadata.returnType()) {
        if (response.body() == null) {
          result.setResult(response);
        } else {
          // Ensure the response body is disconnected
          byte[] bodyData = Util.toByteArray(response.body().asInputStream());
          result.setResult(Response.create(response.status(), response.reason(), response.headers(), bodyData));
        }
        return true; // handled successfully

      } else {
        return false; // did not handle
      }
    }
  }

  /**
   * Accepts responses with error code [200, 300) and decodes the response using the provided {@link Decoder}.
   */
  public static class DecodingReponseHandler implements ResponseHandler {
    private final Decoder decoder;

    public DecodingReponseHandler(Decoder decoder) {
      this.decoder = decoder;
    }

    @Override
    public boolean handle(Response response, MethodMetadata metadata, ResultHolder result) throws Throwable {
      if (response.status() >= 200 && response.status() < 300) {
        if (void.class == metadata.returnType()) {
          result.setResult(null);
        } else {
          result.setResult(decode(response, metadata));
        }

        return true; // handled successfully
      } else {
        return false; // not handled
      }
    }

    private Object decode(Response response, MethodMetadata metadata) throws Throwable {
      try {
        return decoder.decode(response, metadata.returnType());
      } catch (FeignException e) {
        throw e;
      } catch (RuntimeException e) {
        throw new DecodeException(e.getMessage(), e);
      }
    }
  }

  /**
   * Handles every response by throwing the exception produced by the given {@link ErrorDecoder}, never returns true.
   */
  public static class ErrorResponseHandler implements ResponseHandler {
    private final ErrorDecoder errorDecoder;

    public ErrorResponseHandler(ErrorDecoder errorDecoder) {
      this.errorDecoder = errorDecoder;
    }

    @Override
    public boolean handle(Response response, MethodMetadata metadata, ResultHolder result) throws Exception {
      throw errorDecoder.decode(metadata.configKey(), response);
    }
  }
}
