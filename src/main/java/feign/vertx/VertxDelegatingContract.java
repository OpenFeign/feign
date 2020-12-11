package feign.vertx;

import static feign.Util.checkNotNull;
import static feign.Util.resolveLastTypeParameter;

import feign.Contract;
import feign.MethodMetadata;
import io.vertx.core.Future;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Contract allowing only {@link Future} return type.
 *
 * @author Alexei KLENIN
 */
public final class VertxDelegatingContract implements Contract {
  private final Contract delegate;

  public VertxDelegatingContract(final Contract delegate) {
    this.delegate = checkNotNull(delegate, "delegate must not be null");
  }

  @Override
  public List<MethodMetadata> parseAndValidateMetadata(final Class<?> targetType) {
    checkNotNull(targetType, "Argument targetType must be not null");

    final List<MethodMetadata> metadatas = delegate.parseAndValidateMetadata(targetType);

    for (final MethodMetadata metadata : metadatas) {
      final Type type = metadata.returnType();

      if (type instanceof ParameterizedType
          && ((ParameterizedType) type).getRawType().equals(Future.class)) {
        final Type actualType = resolveLastTypeParameter(type, Future.class);
        metadata.returnType(actualType);
      } else {
        throw new IllegalStateException(String.format(
            "Method %s of contract %s doesn't returns io.vertx.core.Future",
            metadata.configKey(), targetType.getSimpleName()));
      }
    }

    return metadatas;
  }
}
