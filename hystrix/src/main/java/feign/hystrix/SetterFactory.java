package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;

import java.lang.reflect.Method;

import feign.Feign;
import feign.Target;

/**
 * Used to control properties of a hystrix command. Use cases include reading from static
 * configuration or custom annotations.
 *
 * <p>This is parsed up-front, like {@link feign.Contract}, so will not be invoked for each command
 * invocation.
 *
 * <p>Note: when deciding the {@link com.netflix.hystrix.HystrixCommand.Setter#andCommandKey(HystrixCommandKey)
 * command key}, recall it lives in a shared cache, so make sure it is unique.
 */
public interface SetterFactory {

  /**
   * Returns a hystrix setter appropriate for the given target and method
   */
  HystrixCommand.Setter create(Target<?> target, Method method);

  /**
   * Default behavior is to derive the group key from {@link Target#name()} and the command key from
   * {@link Feign#configKey(Class, Method)}.
   */
  final class Default implements SetterFactory {

    @Override
    public HystrixCommand.Setter create(Target<?> target, Method method) {
      String groupKey = target.name();
      String commandKey = Feign.configKey(target.type(), method);
      return HystrixCommand.Setter
          .withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
          .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey));
    }
  }
}