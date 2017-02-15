package feign.spring;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import feign.Feign;
import feign.Request;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.mock.HttpMethod;
import feign.mock.MockClient;

public class SpringContractTest
{

  private MockClient mockClient;
  private HealthResource resource;

  @Before
  public void setup() throws IOException
  {
    mockClient = new MockClient()
        .noContent(HttpMethod.GET, "mock:///health")
        .noContent(HttpMethod.GET, "mock:///health/1?deep=true")
        .noContent(HttpMethod.GET, "mock:///health/1?deep=true&dryRun=true")
        .ok(HttpMethod.GET, "mock:///health/generic", "{}");
    resource = Feign.builder()
        .contract(new SpringContract())
        .encoder(new JacksonEncoder())
        .decoder(new JacksonDecoder())
        .client(mockClient)
        .target(HealthResource.class, "mock://");
  }

  @Test
  public void requestParam()
  {
    resource.check("1", true);

    mockClient.verifyOne(HttpMethod.GET, "mock:///health/1?deep=true");
  }

  @Test
  public void requestTwoParams()
  {
    resource.check("1", true, true);

    mockClient.verifyOne(HttpMethod.GET, "mock:///health/1?deep=true&dryRun=true");
  }

  @Test
  public void inheritance()
  {
    Data data = resource.getData(new Data());
    assertThat(data, notNullValue());

    Request request = mockClient.verifyOne(HttpMethod.GET, "mock:///health/generic");
    assertThat(request.headers(), hasEntry(equalTo("Content-Type"), contains("application/json")));
  }

}
