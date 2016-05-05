package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import feign.FeignException;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.Target;
import feign.Target.HardCodedTarget;
import feign.gson.GsonDecoder;
import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.observers.TestSubscriber;

import static feign.assertj.MockWebServerAssertions.assertThat;
import static org.hamcrest.core.Is.isA;

public class HystrixBuilderTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  @Rule
  public final MockWebServer server = new MockWebServer();

  @Test
  public void hystrixCommand() {
    server.enqueue(new MockResponse().setBody("\"foo\""));

    TestInterface api = target();

    HystrixCommand<String> command = api.command();

    assertThat(command).isNotNull();
    assertThat(command.execute()).isEqualTo("foo");
  }

  @Test
  public void hystrixCommandFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    HystrixCommand<String> command = api.command();

    assertThat(command).isNotNull();
    assertThat(command.execute()).isEqualTo("fallback");
  }

  @Test
  public void hystrixCommandInt() {
    server.enqueue(new MockResponse().setBody("1"));

    TestInterface api = target();

    HystrixCommand<Integer> command = api.intCommand();

    assertThat(command).isNotNull();
    assertThat(command.execute()).isEqualTo(new Integer(1));
  }

  @Test
  public void hystrixCommandIntFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    HystrixCommand<Integer> command = api.intCommand();

    assertThat(command).isNotNull();
    assertThat(command.execute()).isEqualTo(new Integer(0));
  }

  @Test
  public void hystrixCommandList() {
    server.enqueue(new MockResponse().setBody("[\"foo\",\"bar\"]"));

    TestInterface api = target();

    HystrixCommand<List<String>> command = api.listCommand();

    assertThat(command).isNotNull();
    assertThat(command.execute()).containsExactly("foo", "bar");
  }

  @Test
  public void hystrixCommandListFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    HystrixCommand<List<String>> command = api.listCommand();

    assertThat(command).isNotNull();
    assertThat(command.execute()).containsExactly("fallback");
  }

  // When dealing with fallbacks, it is less tedious to keep interfaces small.
  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<String> contributors(@Param("owner") String owner, @Param("repo") String repo);
  }

  interface GitHubHystrix {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    HystrixCommand<List<String>> contributorsHystrixCommand(@Param("owner") String owner,
                                                            @Param("repo") String repo);
  }

  @Test
  public void fallbacksApplyOnError() {
    server.enqueue(new MockResponse().setResponseCode(500));

    GitHub fallback = new GitHub() {
      @Override
      public List<String> contributors(String owner, String repo) {
        if (owner.equals("Netflix") && repo.equals("feign")) {
          return Arrays.asList("stuarthendren"); // inspired this approach!
        } else {
          return Collections.emptyList();
        }
      }
    };

    GitHub api = HystrixFeign.builder()
        .target(GitHub.class, "http://localhost:" + server.getPort(), fallback);

    List<String> result = api.contributors("Netflix", "feign");

    assertThat(result).containsExactly("stuarthendren");
  }

  @Test
  public void errorInFallbackHasExpectedBehavior() {
    thrown.expect(HystrixRuntimeException.class);
    thrown.expectMessage("contributors failed and fallback failed.");
    thrown.expectCause(
        isA(FeignException.class)); // as opposed to RuntimeException (from the fallback)

    server.enqueue(new MockResponse().setResponseCode(500));

    GitHub fallback = new GitHub() {
      @Override
      public List<String> contributors(String owner, String repo) {
        throw new RuntimeException("oops");
      }
    };

    GitHub api = HystrixFeign.builder()
        .target(GitHub.class, "http://localhost:" + server.getPort(), fallback);

    api.contributors("Netflix", "feign");
  }

  @Test
  public void hystrixRuntimeExceptionPropagatesOnException() {
    thrown.expect(HystrixRuntimeException.class);
    thrown.expectMessage("contributors failed and no fallback available.");
    thrown.expectCause(isA(FeignException.class));

    server.enqueue(new MockResponse().setResponseCode(500));

    GitHub api = HystrixFeign.builder()
        .target(GitHub.class, "http://localhost:" + server.getPort());

    api.contributors("Netflix", "feign");
  }

  @Test
  public void rxObservable() {
    server.enqueue(new MockResponse().setBody("\"foo\""));

    TestInterface api = target();

    Observable<String> observable = api.observable();

    assertThat(observable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<String> testSubscriber = new TestSubscriber<String>();
    observable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    Assertions.assertThat(testSubscriber.getOnNextEvents().get(0)).isEqualTo("foo");
  }

  @Test
  public void rxObservableFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    Observable<String> observable = api.observable();

    assertThat(observable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<String> testSubscriber = new TestSubscriber<String>();
    observable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    Assertions.assertThat(testSubscriber.getOnNextEvents().get(0)).isEqualTo("fallback");
  }

  @Test
  public void rxObservableInt() {
    server.enqueue(new MockResponse().setBody("1"));

    TestInterface api = target();

    Observable<Integer> observable = api.intObservable();

    assertThat(observable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>();
    observable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    Assertions.assertThat(testSubscriber.getOnNextEvents().get(0)).isEqualTo(new Integer(1));
  }

  @Test
  public void rxObservableIntFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    Observable<Integer> observable = api.intObservable();

    assertThat(observable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>();
    observable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    Assertions.assertThat(testSubscriber.getOnNextEvents().get(0)).isEqualTo(new Integer(0));
  }

  @Test
  public void rxObservableList() {
    server.enqueue(new MockResponse().setBody("[\"foo\",\"bar\"]"));

    TestInterface api = target();

    Observable<List<String>> observable = api.listObservable();

    assertThat(observable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<List<String>> testSubscriber = new TestSubscriber<List<String>>();
    observable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    assertThat(testSubscriber.getOnNextEvents().get(0)).containsExactly("foo", "bar");
  }

  @Test
  public void rxObservableListFall() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    Observable<List<String>> observable = api.listObservable();

    assertThat(observable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<List<String>> testSubscriber = new TestSubscriber<List<String>>();
    observable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    assertThat(testSubscriber.getOnNextEvents().get(0)).containsExactly("fallback");
  }

  @Test
  public void rxSingle() {
    server.enqueue(new MockResponse().setBody("\"foo\""));

    TestInterface api = target();

    Single<String> single = api.single();

    assertThat(single).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<String> testSubscriber = new TestSubscriber<String>();
    single.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    Assertions.assertThat(testSubscriber.getOnNextEvents().get(0)).isEqualTo("foo");
  }

  @Test
  public void rxSingleFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    Single<String> single = api.single();

    assertThat(single).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<String> testSubscriber = new TestSubscriber<String>();
    single.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    Assertions.assertThat(testSubscriber.getOnNextEvents().get(0)).isEqualTo("fallback");
  }

  @Test
  public void rxSingleInt() {
    server.enqueue(new MockResponse().setBody("1"));

    TestInterface api = target();

    Single<Integer> single = api.intSingle();

    assertThat(single).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>();
    single.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    Assertions.assertThat(testSubscriber.getOnNextEvents().get(0)).isEqualTo(new Integer(1));
  }

  @Test
  public void rxSingleIntFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    Single<Integer> single = api.intSingle();

    assertThat(single).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<Integer> testSubscriber = new TestSubscriber<Integer>();
    single.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    Assertions.assertThat(testSubscriber.getOnNextEvents().get(0)).isEqualTo(new Integer(0));
  }

  @Test
  public void rxSingleList() {
    server.enqueue(new MockResponse().setBody("[\"foo\",\"bar\"]"));

    TestInterface api = target();

    Single<List<String>> single = api.listSingle();

    assertThat(single).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<List<String>> testSubscriber = new TestSubscriber<List<String>>();
    single.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    assertThat(testSubscriber.getOnNextEvents().get(0)).containsExactly("foo", "bar");
  }

  @Test
  public void rxSingleListFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    Single<List<String>> single = api.listSingle();

    assertThat(single).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<List<String>> testSubscriber = new TestSubscriber<List<String>>();
    single.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();
    assertThat(testSubscriber.getOnNextEvents().get(0)).containsExactly("fallback");
  }

  @Test
  public void rxCompletableEmptyBody() {
    server.enqueue(new MockResponse());

    TestInterface api = target();

    Completable completable = api.completable();

    assertThat(completable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<String> testSubscriber = new TestSubscriber<String>();
    completable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
  }

  @Test
  public void rxCompletableWithBody() {
    server.enqueue(new MockResponse().setBody("foo"));

    TestInterface api = target();

    Completable completable = api.completable();

    assertThat(completable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<String> testSubscriber = new TestSubscriber<String>();
    completable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    testSubscriber.assertCompleted();
    testSubscriber.assertNoErrors();
  }

  @Test
  public void rxCompletableFailWithoutFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = HystrixFeign.builder()
        .target(TestInterface.class, "http://localhost:" + server.getPort());

    Completable completable = api.completable();

    assertThat(completable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<String> testSubscriber = new TestSubscriber<String>();
    completable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    testSubscriber.assertError(HystrixRuntimeException.class);
  }

  @Test
  public void rxCompletableFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    Completable completable = api.completable();

    assertThat(completable).isNotNull();
    assertThat(server.getRequestCount()).isEqualTo(0);

    TestSubscriber<String> testSubscriber = new TestSubscriber<String>();
    completable.subscribe(testSubscriber);
    testSubscriber.awaitTerminalEvent();

    testSubscriber.assertCompleted();
  }

  @Test
  public void plainString() {
    server.enqueue(new MockResponse().setBody("\"foo\""));

    TestInterface api = target();

    String string = api.get();

    assertThat(string).isEqualTo("foo");
  }

  @Test
  public void plainStringFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    String string = api.get();

    assertThat(string).isEqualTo("fallback");
  }

  @Test
  public void plainList() {
    server.enqueue(new MockResponse().setBody("[\"foo\",\"bar\"]"));

    TestInterface api = target();

    List<String> list = api.getList();

    assertThat(list).isNotNull().containsExactly("foo", "bar");
  }

  @Test
  public void plainListFallback() {
    server.enqueue(new MockResponse().setResponseCode(500));

    TestInterface api = target();

    List<String> list = api.getList();

    assertThat(list).isNotNull().containsExactly("fallback");
  }

  @Test
  public void equalsHashCodeAndToStringWork() {
    Target<TestInterface> t1 =
        new HardCodedTarget<TestInterface>(TestInterface.class, "http://localhost:8080");
    Target<TestInterface> t2 =
        new HardCodedTarget<TestInterface>(TestInterface.class, "http://localhost:8888");
    Target<OtherTestInterface> t3 =
        new HardCodedTarget<OtherTestInterface>(OtherTestInterface.class, "http://localhost:8080");
    TestInterface i1 = HystrixFeign.builder().target(t1);
    TestInterface i2 = HystrixFeign.builder().target(t1);
    TestInterface i3 = HystrixFeign.builder().target(t2);
    OtherTestInterface i4 = HystrixFeign.builder().target(t3);

    assertThat(i1)
        .isEqualTo(i2)
        .isNotEqualTo(i3)
        .isNotEqualTo(i4);

    assertThat(i1.hashCode())
        .isEqualTo(i2.hashCode())
        .isNotEqualTo(i3.hashCode())
        .isNotEqualTo(i4.hashCode());

    assertThat(i1.toString())
        .isEqualTo(i2.toString())
        .isNotEqualTo(i3.toString())
        .isNotEqualTo(i4.toString());

    assertThat(t1)
        .isNotEqualTo(i1);

    assertThat(t1.hashCode())
        .isEqualTo(i1.hashCode());

    assertThat(t1.toString())
        .isEqualTo(i1.toString());
  }

  private TestInterface target() {
    return HystrixFeign.builder()
        .decoder(new GsonDecoder())
        .target(TestInterface.class, "http://localhost:" + server.getPort(),
            new FallbackTestInterface());
  }

  interface OtherTestInterface {

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    HystrixCommand<List<String>> listCommand();
  }

  interface TestInterface {

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    HystrixCommand<List<String>> listCommand();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    HystrixCommand<String> command();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    HystrixCommand<Integer> intCommand();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    Observable<List<String>> listObservable();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    Observable<String> observable();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    Single<Integer> intSingle();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    Single<List<String>> listSingle();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    Single<String> single();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    Observable<Integer> intObservable();


    @RequestLine("GET /")
    @Headers("Accept: application/json")
    String get();

    @RequestLine("GET /")
    @Headers("Accept: application/json")
    List<String> getList();

    @RequestLine("GET /")
    Completable completable();
  }

  class FallbackTestInterface implements TestInterface {
    @Override
    public HystrixCommand<String> command() {
      return new HystrixCommand<String>(HystrixCommandGroupKey.Factory.asKey("Test")) {
        @Override
        protected String run() throws Exception {
          return "fallback";
        }
      };
    }

    @Override
    public HystrixCommand<List<String>> listCommand() {
      return new HystrixCommand<List<String>>(HystrixCommandGroupKey.Factory.asKey("Test")) {
        @Override
        protected List<String> run() throws Exception {
          List<String> fallbackResult = new ArrayList<String>();
          fallbackResult.add("fallback");
          return fallbackResult;
        }
      };
    }

    @Override
    public HystrixCommand<Integer> intCommand() {
      return new HystrixCommand<Integer>(HystrixCommandGroupKey.Factory.asKey("Test")) {
        @Override
        protected Integer run() throws Exception {
          return 0;
        }
      };
    }

    @Override
    public Observable<List<String>> listObservable() {
      List<String> fallbackResult = new ArrayList<String>();
      fallbackResult.add("fallback");
      return Observable.just(fallbackResult);
    }

    @Override
    public Observable<String> observable() {
      return Observable.just("fallback");
    }

    @Override
    public Single<Integer> intSingle() {
      return Single.just(0);
    }

    @Override
    public Single<List<String>> listSingle() {
      List<String> fallbackResult = new ArrayList<String>();
      fallbackResult.add("fallback");
      return Single.just(fallbackResult);
    }

    @Override
    public Single<String> single() {
      return Single.just("fallback");
    }

    @Override
    public Observable<Integer> intObservable() {
      return Observable.just(0);
    }

    @Override
    public String get() {
      return "fallback";
    }

    @Override
    public List<String> getList() {
      List<String> fallbackResult = new ArrayList<String>();
      fallbackResult.add("fallback");
      return fallbackResult;
    }

    @Override
    public Completable completable() {
      return Completable.complete();
    }
  }
}
