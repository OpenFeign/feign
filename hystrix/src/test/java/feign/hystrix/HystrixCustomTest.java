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
    public void testEmptyKey() {
        final HystrixCommand<String> status = HystrixFeign.builder()
            .target(HystrixCustomTest.EmptyGroupKey.class, HystrixCustomTest.URL)
            .status();
        MatcherAssert.assertThat(
            status.getCommandGroup().name(),
            CoreMatchers.equalTo(HystrixCustomTest.URL)
        );
    }


    @HystrixGroupKey("MyKey")
    interface GroupKey {
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }

    @HystrixGroupKey("")
    interface EmptyGroupKey {
        @RequestLine("GET /status")
        HystrixCommand<String> status();
    }
}
