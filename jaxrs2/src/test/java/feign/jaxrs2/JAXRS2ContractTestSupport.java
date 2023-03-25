/*
 * Copyright 2012-2023 The Feign Authors
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
package feign.jaxrs2;

import static feign.assertj.MockWebServerAssertions.assertThat;
import static java.util.Arrays.asList;
import static org.assertj.core.data.MapEntry.entry;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import javax.ws.rs.BeanParam;
import javax.ws.rs.POST;
import org.junit.Test;
import feign.DeclarativeContract;
import feign.jaxrs.JAXRSContractTestSupport;
import okhttp3.mockwebserver.MockResponse;

/**
 * Tests interfaces defined per {@link JAXRS2Contract} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
public abstract class JAXRS2ContractTestSupport<T extends DeclarativeContract>
    extends JAXRSContractTestSupport<T> {


  @SuppressWarnings("unchecked")
  @Test
  public void injectBeanParamAndRequest() throws Exception {
    server.enqueue(new MockResponse());
    BaseJaxrs2Internals<Object, Object> api =
        (BaseJaxrs2Internals<Object, Object>) new JAXRSTestBuilder().target(
            "http://localhost:" + server.getPort(),
            BeanClass());
    Object i = api.valueOf("testPath", ":", "testHeader", "cookie1=cookie1Value", "cookie2Value");


    api.beanParameters(i);
    assertThat(server.takeRequest()).hasPath("/testPath")
        .hasBody("form=:")
        .hasHeaders(entry("X-Custom-Header", asList("testHeader")),
            entry("Cookie", Arrays.asList("cookie1=cookie1Value, cookie2=cookie2Value")));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void injectBeanParamAndRequestWithDefaultValue() throws Exception {
    server.enqueue(new MockResponse());
    BaseJaxrs2Internals<Object, Object> api =
        (BaseJaxrs2Internals<Object, Object>) new JAXRSTestBuilder().target(
            "http://localhost:" + server.getPort(),
            BeanClass());
    Object i = api.valueOf(null, null, null, null, null);
    api.beanParameters(i);
    assertThat(server.takeRequest()).hasPath("/defaultPath")
        .hasBody("form=defaultForm")
        .hasHeaders(entry("X-Custom-Header", asList("defaultHeader")), entry("Cookie",
            Arrays.asList("cookie1=cookie1DefaultValue, cookie2=cookie2DefaultValue")));
  }



  @SuppressWarnings("unchecked")
  @Test
  public void injectRecordParamAndRequest() throws Exception {
    server.enqueue(new MockResponse());
    BaseJaxrs2InternalsRecord<Object, Object> api =
        (BaseJaxrs2InternalsRecord<Object, Object>) new JAXRSTestBuilder().target(
            "http://localhost:" + server.getPort(),
            RecordClass());
    Object i = api.valueOf("testPath", ":", "testHeader", "cookie1=cookie1Value", "cookie2Value");
    api.beanParameters(i);
    assertThat(server.takeRequest()).hasPath("/testPath")
        .hasBody("form=:")
        .hasHeaders(entry("X-Custom-Header", asList("testHeader")),
            entry("Cookie", Arrays.asList("cookie1=cookie1Value, cookie2=cookie2Value")));
  }

  @SuppressWarnings("unchecked")
  @Test
  public void injectRecordParamAndRequestWithDefaultValue() throws Exception {
    server.enqueue(new MockResponse());
    BaseJaxrs2InternalsRecord<Object, Object> api =
        (BaseJaxrs2InternalsRecord<Object, Object>) new JAXRSTestBuilder().target(
            "http://localhost:" + server.getPort(),
            RecordClass());
    Object i = api.valueOf(null, null, null, "cookie1=cookie1DefaultValue", null);
    System.out.println(i);
    api.beanParameters(i);
    assertThat(server.takeRequest()).hasPath("/defaultPath")
        .hasBody("form=defaultForm")
        .hasHeaders(entry("X-Custom-Header", asList("defaultHeader")), entry("Cookie",
            Arrays.asList("cookie1=cookie1DefaultValue, cookie2=cookie2DefaultValue")));
  }

  protected interface BaseJaxrs2Internals<T, C> {
    @SuppressWarnings("unchecked")
    default T valueOf(String path, String form, String header, String cookie1, String cookie2)
        throws InstantiationException, IllegalAccessException, IllegalArgumentException,
        InvocationTargetException, NoSuchMethodException, SecurityException,
        IntrospectionException {
      Type[] types = ParameterizedType.class
          .cast(((Class<?>) getClass().getGenericInterfaces()[0]).getGenericInterfaces()[0])
          .getActualTypeArguments();
      Class<?> clazz = (Class<?>) types[0];
      Class<?> cookieClazz = (Class<?>) types[1];
      T obj = (T) clazz.getDeclaredConstructor().newInstance();
      PropertyDescriptor pathPropertyDescriptor = new PropertyDescriptor("path", clazz);
      PropertyDescriptor formPropertyDescriptor = new PropertyDescriptor("form", clazz);
      PropertyDescriptor headerPropertyDescriptor = new PropertyDescriptor("header", clazz);
      PropertyDescriptor cookie1PropertyDescriptor = new PropertyDescriptor("cookie1", clazz);
      PropertyDescriptor cookie2PropertyDescriptor =
          new PropertyDescriptor("cookie2", clazz);
      pathPropertyDescriptor.getWriteMethod().invoke(obj, path);
      formPropertyDescriptor.getWriteMethod().invoke(obj, form);
      headerPropertyDescriptor.getWriteMethod().invoke(obj, header);
      if (cookie1 != null) {
        C cookie1Value = (C) newCookie(cookie1, cookieClazz);
        cookie1PropertyDescriptor.getWriteMethod().invoke(obj, cookie1Value);
      }
      cookie2PropertyDescriptor.getWriteMethod().invoke(obj, cookie2);
      return obj;

    }


    @POST
    void beanParameters(@BeanParam T input);
  }
  protected interface BaseJaxrs2InternalsRecord<T, C> {
    @SuppressWarnings("unchecked")
    default T valueOf(String path, String form, String header, String cookie1, String cookie2)
        throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
        NoSuchMethodException, SecurityException {
      Type[] types = ParameterizedType.class
          .cast(((Class<?>) getClass().getGenericInterfaces()[0]).getGenericInterfaces()[0])
          .getActualTypeArguments();
      Class<?> clazz = (Class<?>) types[0];
      Class<?> cookieClazz = (Class<?>) types[1];
      Object[] arr = new Object[5];
      arr[0] = path;
      arr[1] = form;
      arr[2] = header;
      C cookie1Value = (C) newCookie(cookie1, cookieClazz);
      arr[3] = cookie1Value;
      arr[4] = cookie2;
      return (T) RecordInvokeUtilsImpl.invokeCanonicalConstructor(clazz, arr);
    }

    void beanParameters(T input);
  }


  protected abstract Class<? extends BaseJaxrs2Internals<?, ?>> BeanClass();

  protected abstract Class<? extends BaseJaxrs2InternalsRecord<?, ?>> RecordClass();

}
