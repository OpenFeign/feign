package feign.optionals;

import feign.Response;
import feign.Util;
import feign.codec.Decoder;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.Optional;

public final class OptionalDecoder implements Decoder {
    final Decoder delegate;

    public OptionalDecoder(Decoder delegate) {
        Objects.requireNonNull(delegate, "Decoder must not be null. ");
        this.delegate = delegate;
    }

    @Override public Object decode(Response response, Type type) throws IOException {
        if(!isOptional(type)) {
            return delegate.decode(response, type);
        }

        if(response.status() == 404) {
            return Optional.empty();
        }
        Type enclosedType = Util.resolveLastTypeParameter(type, Optional.class);
        return Optional.of(delegate.decode(response, enclosedType));
    }

    static boolean isOptional(Type type) {
        if(!(type instanceof ParameterizedType)) {
            return false;
        }
        ParameterizedType parameterizedType = (ParameterizedType) type;
        return parameterizedType.getRawType().equals(Optional.class);
    }
}
