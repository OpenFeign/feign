package feign.hystrix;

import static feign.Util.resolveLastTypeParameter;

import com.netflix.hystrix.HystrixCommand;
import feign.Contract;
import feign.MethodMetadata;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

/**
 * This special cases methods that return {@link HystrixCommand}, so that they are decoded properly.
 *
 * <p>For example, {@literal HystrixCommand<Foo>} will decode {@code Foo}.
 */
// Visible for use in custom Hystrix invocation handlers
public final class HystrixDelegatingContract implements Contract {

  private final Contract delegate;

  public HystrixDelegatingContract(Contract delegate) {
    this.delegate = delegate;
  }

  @Override
  public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
    List<MethodMetadata> metadatas = this.delegate.parseAndValidatateMetadata(targetType);

    for (MethodMetadata metadata : metadatas) {
      Type type = metadata.returnType();

      if (type instanceof ParameterizedType
          && ((ParameterizedType) type).getRawType().equals(HystrixCommand.class)) {
        Type actualType = resolveLastTypeParameter(type, HystrixCommand.class);
        metadata.returnType(actualType);
      }
    }

    return metadatas;
  }
}
