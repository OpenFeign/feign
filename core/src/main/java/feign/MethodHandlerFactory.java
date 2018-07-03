package feign;

import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

import static feign.InvocationHandlerFactory.*;

public interface MethodHandlerFactory {
    MethodHandler create(Target<?> target,
                                                  MethodMetadata md,
                                                  RequestTemplate.Factory buildTemplateFromArgs,
                                                  Request.Options options,
                                                  Decoder decoder,
                                                  ErrorDecoder errorDecoder);
}
