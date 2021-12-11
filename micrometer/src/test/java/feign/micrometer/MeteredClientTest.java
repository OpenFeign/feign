/**
 * Copyright 2012-2021 The Feign Authors
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
package feign.micrometer;

import feign.*;
import feign.mock.HttpMethod;
import feign.mock.MockClient;
import feign.mock.MockTarget;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MeteredClientTest {

  public interface SimpleSource {

    @RequestLine("GET /get")
    String get();

  }


  @Mock
  private Clock clock;
  @Mock
  private MeterRegistry.Config config;
  @Mock
  private Counter counter;
  @Mock
  private MeterRegistry meterRegistry;
  @Mock
  private Timer timer;

  MockClient mockClient;
  MeteredClient meteredClient;

  @SuppressWarnings("unchecked")
  @Before
  public void setUp() {
    mockClient = new MockClient();
    when(meterRegistry.config()).thenReturn(config);
    when(meterRegistry.counter(anyString(), isA(Iterable.class))).thenReturn(counter);
    when(meterRegistry.timer(anyString(), isA(Iterable.class))).thenReturn(timer);
    when(config.clock()).thenReturn(clock);
    when(clock.monotonicTime()).thenReturn(123L, 456L);
  }

  @Test
  public void stopTimerOnHappyPath() {
    mockClient = mockClient.ok(HttpMethod.GET, "/get", "1234567890abcde");
    meteredClient = new MeteredClient(mockClient, meterRegistry);
    SimpleSource simpleSource =
        Feign.builder().client(meteredClient).target(new MockTarget<>(SimpleSource.class));

    String response = simpleSource.get();

    assertEquals("response", "1234567890abcde", response);
    verify(timer).record(anyLong(), isA(TimeUnit.class));
  }

  @Test
  public void stopTimerOnFeignException() {
    mockClient = mockClient.add(HttpMethod.GET, "/get", HttpsURLConnection.HTTP_NOT_FOUND);
    meteredClient = new MeteredClient(mockClient, meterRegistry);
    SimpleSource simpleSource =
        Feign.builder().client(meteredClient).target(new MockTarget<>(SimpleSource.class));

    Exception exception = assertThrows(FeignException.class, simpleSource::get);

    assertEquals("message", "[404 Mocked] during [GET] to [/get] [SimpleSource#get()]: []",
        exception.getMessage());
    verify(timer).record(anyLong(), isA(TimeUnit.class));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void stopTimerOnRuntimeException() {
    when(meterRegistry.counter(anyString(), isA(Iterable.class))).thenThrow(
        new RuntimeException("test runtime exception"));
    mockClient = mockClient.ok(HttpMethod.GET, "/get", "1234567890abcde");
    meteredClient = new MeteredClient(mockClient, meterRegistry);
    SimpleSource simpleSource =
        Feign.builder().client(meteredClient).target(new MockTarget<>(SimpleSource.class));

    Exception exception = assertThrows(RuntimeException.class, simpleSource::get);

    assertEquals("message", "test runtime exception", exception.getMessage());
    verify(timer).record(anyLong(), isA(TimeUnit.class));
  }

  @Test
  public void stopTimerOnIOException() throws IOException {
    mockClient = spy(mockClient.ok(HttpMethod.GET, "/get", "1234567890abcde"));
    doThrow(new IOException("test input/output exception")).when(mockClient)
        .execute(isA(Request.class), isA(Request.Options.class));
    meteredClient = new MeteredClient(mockClient, meterRegistry);
    SimpleSource simpleSource =
        Feign.builder().client(meteredClient).target(new MockTarget<>(SimpleSource.class));

    Exception exception = assertThrows(RetryableException.class, simpleSource::get);

    assertEquals("message", "test input/output exception executing GET /get",
        exception.getMessage());
    verify(timer, times(5)).record(anyLong(), isA(TimeUnit.class));
  }

}
