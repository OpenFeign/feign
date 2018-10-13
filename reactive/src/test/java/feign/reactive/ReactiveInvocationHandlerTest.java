/**
 * Copyright 2012-2018 The Feign Authors
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
import feign.FeignException;
import feign.InvocationHandlerFactory.MethodHandler;
import feign.RequestLine;
import feign.Target;
import feign.reactive.ReactorInvocationHandler;
import feign.reactive.RxJavaInvocationHandler;
import io.reactivex.Flowable;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import reactor.core.publisher.Mono;

@RunWith(MockitoJUnitRunner.class)
public class ReactiveInvocationHandlerTest {

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  private Target target;

  @Mock
  private MethodHandler methodHandler;

  private Method method;

  @SuppressWarnings("unchecked")
  @Test
  public void invokeOnSubscribeReactor() throws Throwable {
    Method method = TestReactorService.class.getMethod("version");
    ReactorInvocationHandler handler = new ReactorInvocationHandler(this.target,
        Collections.singletonMap(method, this.methodHandler));

    Object result = handler.invoke(method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Mono.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method */
    Mono mono = (Mono) result;
    mono.log().block();
    verify(this.methodHandler, times(1)).invoke(any());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void invokeFailureReactor() throws Throwable {
    this.thrown.expect(RuntimeException.class);
    given(this.methodHandler.invoke(any())).willThrow(new RuntimeException("Could Not Decode"));
    given(this.method.getReturnType()).willReturn((Class) Class.forName(Mono.class.getName()));
    ReactorInvocationHandler handler = new ReactorInvocationHandler(this.target,
        Collections.singletonMap(this.method, this.methodHandler));

    Object result = handler.invoke(this.method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Mono.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method, should result in an error */
    Mono mono = (Mono) result;
    mono.log().block();
    verify(this.methodHandler, times(1)).invoke(any());
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Test
  public void invokeOnSubscribeRxJava() throws Throwable {
    given(this.methodHandler.invoke(any())).willReturn("Result");
    RxJavaInvocationHandler handler =
        new RxJavaInvocationHandler(this.target,
            Collections.singletonMap(this.method, this.methodHandler));

    Object result = handler.invoke(this.method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Flowable.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method */
    Flowable flow = (Flowable) result;
    flow.firstElement().blockingGet();
    verify(this.methodHandler, times(1)).invoke(any());
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
  @Test
  public void invokeFailureRxJava() throws Throwable {
    this.thrown.expect(RuntimeException.class);
    given(this.methodHandler.invoke(any())).willThrow(new RuntimeException("Could Not Decode"));
    RxJavaInvocationHandler handler =
        new RxJavaInvocationHandler(this.target,
            Collections.singletonMap(this.method, this.methodHandler));

    Object result = handler.invoke(this.method, this.methodHandler, new Object[] {});
    assertThat(result).isInstanceOf(Flowable.class);
    verifyZeroInteractions(this.methodHandler);

    /* subscribe and execute the method */
    Flowable flow = (Flowable) result;
    flow.firstElement().blockingGet();
    verify(this.methodHandler, times(1)).invoke(any());
  }


  public interface TestReactorService {
    @RequestLine("GET /version")
    Mono<String> version();
  }

}
