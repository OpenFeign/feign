/**
 * Copyright 2012-2021 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.micrometer;


import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import feign.*;
import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;

/**
 * Warp feign {@link InvocationHandler} with metrics.
 */
public class MeteredInvocationHandleFactory implements InvocationHandlerFactory {

  /**
   * Methods that are declared by super class object and, if invoked, we don't wanna record metrics
   * for
   */
  private static final List<String> JAVA_OBJECT_METHODS =
      Arrays.asList("equals", "toString", "hashCode");

  private final InvocationHandlerFactory invocationHandler;

  private final MeterRegistry meterRegistry;

  private final MetricName metricName;

  public MeteredInvocationHandleFactory(InvocationHandlerFactory invocationHandler,
      MeterRegistry meterRegistry) {
    this(invocationHandler, meterRegistry, new FeignMetricName(Feign.class));
  }

  public MeteredInvocationHandleFactory(InvocationHandlerFactory invocationHandler,
      MeterRegistry meterRegistry, MetricName metricName) {
    this.invocationHandler = invocationHandler;
    this.meterRegistry = meterRegistry;
    this.metricName = metricName;
  }

  @Override
  public InvocationHandler create(Target target, Map<Method, MethodHandler> dispatch) {
    final InvocationHandler invocationHandle = invocationHandler.create(target, dispatch);
    return (proxy, method, args) -> {

      if (JAVA_OBJECT_METHODS.contains(method.getName())
          || Util.isDefault(method)) {
        return invocationHandle.invoke(proxy, method, args);
      }

      final Timer.Sample sample = Timer.start(meterRegistry);
      try {
        try {
          final Object invoke = invocationHandle.invoke(proxy, method, args);
          sample.stop(createSuccessTimer(target, method, args));
          return invoke;
        } catch (Exception e) {
          throw e;
        } catch (Throwable e) {
          throw new Exception(e);
        }
      } catch (final FeignException e) {
        sample.stop(createThrowableTimer(target, method, args, e));
        createFeignExceptionCounter(target, method, args, e).increment();
        throw e;
      } catch (final Throwable e) {
        sample.stop(createThrowableTimer(target, method, args, e));
        createThrowableCounter(target, method, args, e).increment();
        throw e;
      }
    };
  }

  private Timer createSuccessTimer(Target target, Method method, Object[] args) {
    final List<Tag> successTags = extraSuccessTags(target, method, args);
    final Tag[] tags = successTags.toArray(new Tag[] {});
    final Tags allTags = metricName.tag(target.type(), method, target.url(), tags);
    return meterRegistry.timer(metricName.name(), allTags);
  }

  private Timer createThrowableTimer(Target target, Method method, Object[] args, Throwable e) {
    final List<Tag> throwableTags = extraThrowableTags(target, method, args, e);
    final Tag[] tags = throwableTags.toArray(new Tag[] {});
    final Tags allTags = metricName.tag(target.type(), method, target.url(), tags);
    return meterRegistry.timer(metricName.name("exception"), allTags);
  }

  private Counter createThrowableCounter(Target target, Method method, Object[] args, Throwable e) {
    final List<Tag> throwableTags = extraThrowableTags(target, method, args, e);
    final Tag[] tags = throwableTags.toArray(new Tag[] {});
    final Tags allTags = metricName.tag(target.type(), method, target.url(), tags);
    return meterRegistry.counter(metricName.name("exception"), allTags);
  }

  private Counter createFeignExceptionCounter(Target target,
                                              Method method,
                                              Object[] args,
                                              FeignException e) {
    final List<Tag> throwableTags = extraFeignExceptionTags(target, method, args, e);
    final Tag[] tags = throwableTags.toArray(new Tag[] {});
    final Tags allTags = metricName.tag(target.type(), method, target.url(), tags);
    return meterRegistry.counter(metricName.name("http_error"), allTags);
  }

  protected List<Tag> extraSuccessTags(Target target, Method method, Object[] args) {
    return Collections.emptyList();
  }

  protected List<Tag> extraThrowableTags(Target target,
                                         Method method,
                                         Object[] args,
                                         Throwable throwable) {
    final List<Tag> result = new ArrayList<>();
    result.add(Tag.of("exception_name", throwable.getClass().getSimpleName()));
    return result;
  }

  protected List<Tag> extraFeignExceptionTags(Target target,
                                              Method method,
                                              Object[] args,
                                              FeignException e) {
    final List<Tag> tags = extraThrowableTags(target, method, args, e);
    tags.add(Tag.of("http_status", String.valueOf(e.status())));
    tags.add(Tag.of("error_group", e.status() / 100 + "xx"));
    return tags;
  }
}
