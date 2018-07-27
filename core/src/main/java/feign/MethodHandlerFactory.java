package feign;

import feign.Request.Options;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;

import java.lang.reflect.InvocationHandler;
import java.util.List;

import static feign.Util.checkNotNull;

public interface MethodHandlerFactory {

    MethodHandler create(Target<?> target, MethodMetadata md,
                         RequestTemplate.Factory buildTemplateFromArgs,
                         Request.Options options, Decoder decoder, ErrorDecoder errorDecoder);

    /**
     * Like {@link InvocationHandler#invoke(Object, java.lang.reflect.Method, Object[])}, except for a
     * single method.
     */
    interface MethodHandler {

        Object invoke(Object[] argv) throws Throwable;
    }

    static class Builder {

        private Client client;
        private Retryer retryer;
        private List<RequestInterceptor> requestInterceptors;
        private Logger logger;
        private Logger.Level logLevel;
        private boolean decode404;
        private boolean closeAfterDecode;

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public Builder retryer(Retryer retryer) {
            this.retryer = retryer;
            return this;
        }

        public Builder requestInterceptors(List<RequestInterceptor> requestInterceptors) {
            this.requestInterceptors = requestInterceptors;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder logLevel(Logger.Level logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder decode404(boolean decode404) {
            this.decode404 = decode404;
            return this;
        }

        public Builder closeAfterDecode(boolean closeAfterDecode) {
            this.closeAfterDecode = closeAfterDecode;
            return this;
        }

        public MethodHandlerFactory build() {
            return new Factory(client, retryer, requestInterceptors, logger, logLevel, decode404, closeAfterDecode);
        }
    }

    static class Factory implements MethodHandlerFactory {

        private final Client client;
        private final Retryer retryer;
        private final List<RequestInterceptor> requestInterceptors;
        private final Logger logger;
        private final Logger.Level logLevel;
        private final boolean decode404;
        private final boolean closeAfterDecode;

        Factory(Client client, Retryer retryer, List<RequestInterceptor> requestInterceptors,
                Logger logger, Logger.Level logLevel, boolean decode404, boolean closeAfterDecode) {
            this.client = checkNotNull(client, "client");
            this.retryer = checkNotNull(retryer, "retryer");
            this.requestInterceptors = checkNotNull(requestInterceptors, "requestInterceptors");
            this.logger = checkNotNull(logger, "logger");
            this.logLevel = checkNotNull(logLevel, "logLevel");
            this.decode404 = decode404;
            this.closeAfterDecode = closeAfterDecode;
        }

        @Override
        public MethodHandler create(Target<?> target,
                                    MethodMetadata md,
                                    RequestTemplate.Factory buildTemplateFromArgs,
                                    Options options,
                                    Decoder decoder,
                                    ErrorDecoder errorDecoder) {
            return new SynchronousMethodHandler(target, client, retryer, requestInterceptors, logger,
                logLevel, md, buildTemplateFromArgs, options, decoder,
                errorDecoder, decode404, closeAfterDecode);
        }
    }
}
