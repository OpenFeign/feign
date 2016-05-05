/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static feign.assertj.MockWebServerAssertions.assertThat;

public class ContractWithRuntimeInjectionTest {

  static class CaseExpander implements Param.Expander {

    private final boolean lowercase;

    CaseExpander() {
      this(false);
    }

    CaseExpander(boolean lowercase) {
      this.lowercase = lowercase;
    }


    @Override
    public String expand(Object value) {
      return lowercase ? value.toString().toLowerCase() : value.toString();
    }
  }

  @Rule
  public final MockWebServer server = new MockWebServer();

  interface TestExpander {

    @RequestLine("GET /path?query={query}")
    Response get(@Param(value = "query", expander = CaseExpander.class) String query);
  }

  @Test
  public void baseCaseExpanderNewInstance() throws InterruptedException {
    server.enqueue(new MockResponse());

    String baseUrl = server.url("/default").toString();

    Feign.builder().target(TestExpander.class, baseUrl).get("FOO");

    assertThat(server.takeRequest()).hasPath("/default/path?query=FOO");
  }

  @Configuration
  static class FeignConfiguration {

    @Bean
    CaseExpander lowercaseExpander() {
      return new CaseExpander(true);
    }

    @Bean
    Contract contract(BeanFactory beanFactory) {
      return new ContractWithRuntimeInjection(beanFactory);
    }
  }

  static class ContractWithRuntimeInjection extends Contract.Default {
    final BeanFactory beanFactory;

    ContractWithRuntimeInjection(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
    }

    /**
     * Injects {@link MethodMetadata#indexToExpander(Map)} via {@link BeanFactory#getBean(Class)}.
     */
    @Override
    public List<MethodMetadata> parseAndValidatateMetadata(Class<?> targetType) {
      List<MethodMetadata> result = super.parseAndValidatateMetadata(targetType);
      for (MethodMetadata md : result) {
        Map<Integer, Param.Expander> indexToExpander = new LinkedHashMap<Integer, Param.Expander>();
        for (Map.Entry<Integer, Class<? extends Param.Expander>> entry : md.indexToExpanderClass().entrySet()) {
          indexToExpander.put(entry.getKey(), beanFactory.getBean(entry.getValue()));
        }
        md.indexToExpander(indexToExpander);
      }
      return result;
    }
  }

  @Test
  public void contractWithRuntimeInjection() throws InterruptedException {
    server.enqueue(new MockResponse());

    String baseUrl = server.url("/default").toString();
    ApplicationContext context = new AnnotationConfigApplicationContext(FeignConfiguration.class);

    Feign.builder()
        .contract(context.getBean(Contract.class))
        .target(TestExpander.class, baseUrl).get("FOO");

    assertThat(server.takeRequest()).hasPath("/default/path?query=foo");
  }
}
