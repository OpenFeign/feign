package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.MockWebServer;

import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import feign.FeignException;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import feign.gson.GsonDecoder;
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
  public void hystrixCommandInt() {
    server.enqueue(new MockResponse().setBody("1"));

    TestInterface api = target();

    HystrixCommand<Integer> command = api.intCommand();

    assertThat(command).isNotNull();
    assertThat(command.execute()).isEqualTo(new Integer(1));
  }

  @Test
  public void hystrixCommandList() {
    server.enqueue(new MockResponse().setBody("[\"foo\",\"bar\"]"));

    TestInterface api = target();

    HystrixCommand<List<String>> command = api.listCommand();

    assertThat(command).isNotNull();
    assertThat(command.execute()).containsExactly("foo", "bar");
  }

  // When dealing with fallbacks, it is less tedious to keep interfaces small.
  interface GitHub {
    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    List<String> contributors(@Param("owner") String owner, @Param("repo") String repo);
  }

  @Test
  public void fallbacksApplyOnError() {
    server.enqueue(new MockResponse().setResponseCode(500));

    GitHub fallback = new GitHub(){
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
    thrown.expectCause(isA(FeignException.class)); // as opposed to RuntimeException (from the fallback)

    server.enqueue(new MockResponse().setResponseCode(500));

    GitHub fallback = new GitHub(){
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
  public void plainString() {
    server.enqueue(new MockResponse().setBody("\"foo\""));

    TestInterface api = target();

    String string = api.get();

    assertThat(string).isEqualTo("foo");
  }

  @Test
  public void plainList() {
    server.enqueue(new MockResponse().setBody("[\"foo\",\"bar\"]"));

    TestInterface api = target();

    List<String> list = api.getList();

    assertThat(list).isNotNull().containsExactly("foo", "bar");
  }

  private TestInterface target() {
    return HystrixFeign.builder()
        .decoder(new GsonDecoder())
        .target(TestInterface.class, "http://localhost:" + server.getPort());
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
  }
}
