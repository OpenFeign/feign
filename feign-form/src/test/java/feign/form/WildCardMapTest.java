package feign.form;

import feign.*;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;
import java.util.Map;

import static feign.Logger.Level.FULL;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.DEFINED_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(
        webEnvironment = DEFINED_PORT,
        classes = Server.class
)
public class WildCardMapTest {

    private static FormUrlEncodedApi API;

    @BeforeClass
    public static void configureClient() {
        API = Feign.builder()
                .encoder(new FormEncoder())
                .logger(new Logger.JavaLogger().appendToFile("log.txt"))
                .logLevel(FULL)
                .target(FormUrlEncodedApi.class, "http://localhost:8080");
    }

    @Test
    public void testOk() {
        Map<String, Object> param = new HashMap<String, Object>() {{put("key1", "1"); put("key2", "1");}};
        Response response = API.wildCardMap(param);
        Assert.assertEquals(200, response.status());
    }

    @Test
    public void testBadRequest() {
        Map<String, Object> param = new HashMap<String, Object>() {{put("key1", "1"); put("key2", "2");}};
        Response response = API.wildCardMap(param);
        Assert.assertEquals(400, response.status());
    }

    interface FormUrlEncodedApi {

        @RequestLine("POST /wild-card-map")
        @Headers("Content-Type: application/x-www-form-urlencoded")
        Response wildCardMap(Map<String, ?> param);
    }
}
