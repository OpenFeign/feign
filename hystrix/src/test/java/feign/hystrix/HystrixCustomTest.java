package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import feign.RequestLine;
import org.hamcrest.CoreMatchers;
import org.hamcrest.MatcherAssert;
import org.junit.Test;

public final class HystrixCustomTest {
    private static final String URL = "http://localhost";

    @Test
    public void testKey() {
        final HystrixCommand<String> status = HystrixFeign.builder()
            .target(HystrixCustomTest.GroupKey.class, HystrixCustomTest.URL)
            .status();
        MatcherAssert.assertThat(
            status.getCommandGroup().name(),
            CoreMatchers.equalTo("MyKey1")
        );
    }

    @Test
    public void testCommandKey() {
        final HystrixCommand<String> status = HystrixFeign.builder()
            .target(HystrixCustomTest.CommandKey.class, HystrixCustomTest.URL)
            .status();
        MatcherAssert.assertThat(
            status.getCommandKey().name(),
            CoreMatchers.equalTo("MyKey2")
        );
    }

    @Test
    public void testTimeout() {
        final HystrixCommand<String> status = HystrixFeign.builder()
            .target(HystrixCustomTest.TypeTimeout.class, HystrixCustomTest.URL)
            .status();
        MatcherAssert.assertThat(
            status.getProperties().executionTimeoutInMilliseconds().get(),
            CoreMatchers.equalTo(200)
        );
    }

    @Test
    public void testCommandTimeout() {
        final HystrixCommand<String> status = HystrixFeign.builder()
            .target(HystrixCustomTest.CommandTimeout.class, HystrixCustomTest.URL)
            .status();
        MatcherAssert.assertThat(
            status.getProperties().executionTimeoutInMilliseconds().get(),
            CoreMatchers.equalTo(200)
        );
    }

    @Test
    public void testEmptyKey() {
        final HystrixCommand<String> status = HystrixFeign.builder()
            .target(HystrixCustomTest.EmptyGroupKey.class, HystrixCustomTest.URL)
            .status();
        MatcherAssert.assertThat(
            status.getCommandGroup().name(),
            CoreMatchers.equalTo(HystrixCustomTest.URL)
        );
    }

    @Test
    public void testNoCustomConfig() {
        final HystrixCommand<String> status = HystrixFeign.builder()
            .target(HystrixCustomTest.NoCustomConfig.class, HystrixCustomTest.URL)
            .status();
        MatcherAssert.assertThat(
            status.getCommandGroup().name(),
            CoreMatchers.equalTo(HystrixCustomTest.URL)
        );
    }

    @Test
    public void testMixedAnnotations() {
        final HystrixCustomTest.ComplexService service = HystrixFeign.builder()
            .target(HystrixCustomTest.ComplexService.class, HystrixCustomTest.URL);
        MatcherAssert.assertThat(
            service.status().getCommandGroup().name(),
            CoreMatchers.equalTo("MyKey3")
        );
        MatcherAssert.assertThat(
            service.status().getProperties().executionTimeoutInMilliseconds().get(),
            CoreMatchers.equalTo(150)
        );
        MatcherAssert.assertThat(
            service.status().getCommandKey().name(),
            CoreMatchers.equalTo("status")
        );
        MatcherAssert.assertThat(
            service.slower().getCommandGroup().name(),
            CoreMatchers.equalTo("MyKey3")
        );
        MatcherAssert.assertThat(
            service.slower().getProperties().executionTimeoutInMilliseconds().get(),
            CoreMatchers.equalTo(15000)
        );
        MatcherAssert.assertThat(
            service.slower().getCommandKey().name(),
            CoreMatchers.equalTo("Slow")
        );
    }

    @HystrixConfig(key = "MyKey1")
    interface GroupKey {
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }

    interface CommandKey {
        @HystrixCommandConfig(key = "MyKey2")
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }

    @HystrixConfig(timeout = 200)
    interface TypeTimeout {
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }

    interface CommandTimeout {
        @HystrixCommandConfig(timeout = 1200)
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }

    @HystrixConfig(key = "")
    interface EmptyGroupKey {
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }

    interface NoCustomConfig {
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }

    @HystrixConfig(key = "MyKey3", timeout = 150)
    interface ComplexService {
        @RequestLine("GET /status")
        HystrixCommand<String> status();

        @HystrixCommandConfig(key = "Slow", timeout = 15000)
        @RequestLine("GET /slower")
        HystrixCommand<String> slower();
    }
}
