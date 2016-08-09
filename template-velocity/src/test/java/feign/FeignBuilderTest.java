package feign;

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.rule.MockWebServerRule;
import feign.template.velocity.VelocityTemplate;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Created by david on 14/04/16.
 */
public class FeignBuilderTest {

    @Rule
    public final MockWebServerRule server = new MockWebServerRule();


    @Test
    public void testConvertVelocity() throws Exception {
        server.enqueue(new MockResponse().setBody("response data"));

        String url = "http://localhost:" + server.getPort();
        TestInterface api = Feign.builder().target(TestInterface.class, url);
        Response response = api.getPathVelocity("foo");
        Assert.assertEquals(server.takeRequest().getPath(),"/test/do/foo");


    }

    @Test
    public void testConvertVelocityNull() throws Exception {
        server.enqueue(new MockResponse().setBody("response data"));

        String url = "http://localhost:" + server.getPort();
        TestInterface api = Feign.builder().target(TestInterface.class, url);
        Response response = api.getPathVelocity(null);
        Assert.assertEquals(server.takeRequest().getPath(),"/test/do/");


    }

    interface TestInterface {
        @RequestLine("GET /test/do/$!{name}")
        @TemplateEngine(VelocityTemplate.class)
        @Body("#if( $name )\n" +
                "   <strong>Velocity!</strong>\n" +
                "#end")
        Response getPathVelocity(@Param("name") String name);

        @RequestLine("GET /test/do/{name}")
        @TemplateEngine
        Response getPath(@Param("name") String name);

    }
}
