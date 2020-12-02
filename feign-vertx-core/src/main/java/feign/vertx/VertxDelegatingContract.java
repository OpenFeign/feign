package feign.vertx;

import static feign.Util.checkNotNull;

import feign.Contract;
import feign.MethodMetadata;
import feign.vertx.adaptor.AbstractVertxAdaptor;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * Contract allowing only Vertx {@code Future} return type.
 *
 * @author Alexei KLENIN
 */
public final class VertxDelegatingContract implements Contract {
  private final Contract delegate;

  public VertxDelegatingContract(final Contract delegate) {
    this.delegate = checkNotNull(delegate, "delegate must not be null");
  }

  @Override
  public List<MethodMetadata> parseAndValidatateMetadata(final Class<?> targetType) {
    checkNotNull(targetType, "Argument targetType must be not null");

    final List<MethodMetadata> metadatas = delegate.parseAndValidatateMetadata(targetType);
    final AbstractVertxAdaptor adaptor = VertxAdaptors.getAdaptor();

    for (final MethodMetadata metadata : metadatas) {
      final Type type = metadata.returnType();

      if (type instanceof ParameterizedType
          && adaptor.isVertxFuture(((ParameterizedType) type).getRawType())) {
        final Type actualType = adaptor.futureContentType(type);
        metadata.returnType(actualType);
      } else {
        throw new IllegalStateException(String.format(
            "Method %s of contract %s doesn't returns %s",
            metadata.configKey(), targetType.getSimpleName(), adaptor.vertxFutureClassname()));
      }
    }

    return metadatas;
  }
}
