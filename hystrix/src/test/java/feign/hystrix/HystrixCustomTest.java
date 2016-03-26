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
            CoreMatchers.equalTo("MyKey")
        );
    }

    @Test
    public void testTimeout() {
        final HystrixCommand<String> status = HystrixFeign.builder()
            .target(HystrixCustomTest.TypeTimeout.class, HystrixCustomTest.URL)
            .status();
        MatcherAssert.assertThat(
            status.getExecutionTimeInMilliseconds(),
            CoreMatchers.equalTo(2000)
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
            .target(NoCustomConfig.class, HystrixCustomTest.URL)
            .status();
        MatcherAssert.assertThat(
            status.getCommandGroup().name(),
            CoreMatchers.equalTo(HystrixCustomTest.URL)
        );
    }

    @HystrixConfig(key = "MyKey")
    interface GroupKey {
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }

    @HystrixConfig(timeout = 2000)
    interface TypeTimeout {
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
}
