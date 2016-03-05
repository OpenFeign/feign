package feign.hystrix;

import static feign.Util.resolveLastTypeParameter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import com.netflix.hystrix.HystrixCommand;

import feign.Contract;
import feign.MethodMetadata;
import rx.Observable;
import rx.Single;

/**
 * This special cases methods that return {@link HystrixCommand}, {@link Observable}, or {@link Single} so that they
 * are decoded properly.
 *
 * <p>For example, {@literal HystrixCommand<Foo>} and {@literal Observable<Foo>} will decode {@code Foo}.
 */
// Visible for use in custom Hystrix invocation handlers
public final class HystrixDelegatingContract extends Contract.DelegatingContract {

  public HystrixDelegatingContract(Contract delegate) {
    super(delegate);
  }

  @Override
  public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
    List<MethodMetadata> metadatas = super.parseAndValidatateMetadata(targetType);

    for (MethodMetadata metadata : metadatas) {
      Type type = metadata.returnType();

      if (type instanceof ParameterizedType && ((ParameterizedType) type).getRawType().equals(HystrixCommand.class)) {
        Type actualType = resolveLastTypeParameter(type, HystrixCommand.class);
        metadata.returnType(actualType);
      } else if (type instanceof ParameterizedType && ((ParameterizedType) type).getRawType().equals(Observable.class)) {
        Type actualType = resolveLastTypeParameter(type, Observable.class);
        metadata.returnType(actualType);
      } else if (type instanceof ParameterizedType && ((ParameterizedType) type).getRawType().equals(Single.class)) {
        Type actualType = resolveLastTypeParameter(type, Single.class);
        metadata.returnType(actualType);
      }
    }

    return metadatas;
  }

}
