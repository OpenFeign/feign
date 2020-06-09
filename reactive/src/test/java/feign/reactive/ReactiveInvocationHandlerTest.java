/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.RequestLine;
import feign.Target;
import io.reactivex.Flowable;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

@RunWith(MockitoJUnitRunner.class)
public class ReactiveInvocationHandlerTest {

  @Mock
  private Target target;

  @Mock
  private MethodHandler methodHandler;

  private Method method;

  @Before
  public void setUp() throws NoSuchMethodException {
    method = TestReactorService.class.getMethod("version");
  }

  @SuppressWarnings("unchecked")
  @Test
  public void invokeOnSubscribeReactor() throws Throwable {
    given(this.methodHandler.invoke(any())).willReturn("Result");
    ReactorInvocationHandler handler = new ReactorInvocationHandler(this.target,
        Collections.singletonMap(method, this.methodHandler), Schedulers.elastic());

    Object result = handler.invoke(method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Mono.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method */
    StepVerifier.create((Mono) result)
        .expectNext("Result")
        .expectComplete()
        .verify();
    verify(this.methodHandler, times(1)).invoke(any());
  }

  @Test
  public void invokeOnSubscribeEmptyReactor() throws Throwable {
    given(this.methodHandler.invoke(any())).willReturn(null);
    ReactorInvocationHandler handler = new ReactorInvocationHandler(this.target,
        Collections.singletonMap(method, this.methodHandler), Schedulers.elastic());

    Object result = handler.invoke(method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Mono.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method */
    StepVerifier.create((Mono) result)
        .expectComplete()
        .verify();
    verify(this.methodHandler, times(1)).invoke(any());
  }

  @Test
  public void invokeFailureReactor() throws Throwable {
    given(this.methodHandler.invoke(any())).willThrow(new IOException("Could Not Decode"));
    ReactorInvocationHandler handler = new ReactorInvocationHandler(this.target,
        Collections.singletonMap(this.method, this.methodHandler), Schedulers.elastic());

    Object result = handler.invoke(this.method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Mono.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method, should result in an error */
    StepVerifier.create((Mono) result)
        .expectError(IOException.class)
        .verify();
    verify(this.methodHandler, times(1)).invoke(any());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void invokeOnSubscribeRxJava() throws Throwable {
    given(this.methodHandler.invoke(any())).willReturn("Result");
    RxJavaInvocationHandler handler =
        new RxJavaInvocationHandler(this.target,
            Collections.singletonMap(this.method, this.methodHandler),
            io.reactivex.schedulers.Schedulers.trampoline());

    Object result = handler.invoke(this.method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Flowable.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method */
    StepVerifier.create((Flowable) result)
        .expectNext("Result")
        .expectComplete()
        .verify();
    verify(this.methodHandler, times(1)).invoke(any());
  }

  @Test
  public void invokeOnSubscribeEmptyRxJava() throws Throwable {
    given(this.methodHandler.invoke(any())).willReturn(null);
    RxJavaInvocationHandler handler =
        new RxJavaInvocationHandler(this.target,
            Collections.singletonMap(this.method, this.methodHandler),
            io.reactivex.schedulers.Schedulers.trampoline());

    Object result = handler.invoke(this.method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Flowable.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method */
    StepVerifier.create((Flowable) result)
        .expectComplete()
        .verify();
    verify(this.methodHandler, times(1)).invoke(any());
  }

  @Test
  public void invokeFailureRxJava() throws Throwable {
    given(this.methodHandler.invoke(any())).willThrow(new IOException("Could Not Decode"));
    RxJavaInvocationHandler handler =
        new RxJavaInvocationHandler(this.target,
            Collections.singletonMap(this.method, this.methodHandler),
            io.reactivex.schedulers.Schedulers.trampoline());

    Object result = handler.invoke(this.method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Flowable.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method */
    StepVerifier.create((Flowable) result)
        .expectError(IOException.class)
        .verify();
    verify(this.methodHandler, times(1)).invoke(any());
  }


  public interface TestReactorService {
    @RequestLine("GET /version")
    Mono<String> version();
  }

}
