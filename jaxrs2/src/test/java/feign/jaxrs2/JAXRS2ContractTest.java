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

import javax.ws.rs.BeanParam;
import javax.ws.rs.CookieParam;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.Encoded;
import javax.ws.rs.FormParam;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Cookie;
import feign.MethodMetadata;
import feign.jaxrs2.JAXRS2ContractTest.Jaxrs2Internals.Input;
import feign.jaxrs2.JAXRS2ContractTest.Jaxrs2InternalsRecord.InputRecord;

/**
 * Tests interfaces defined per {@link JAXRS2Contract} are interpreted into expected
 * {@link feign .RequestTemplate template} instances.
 */
public final class JAXRS2ContractTest extends JAXRS2ContractTestSupport<JAXRS2Contract> {

  @Override
  protected JAXRS2Contract createContract() {
    return new JAXRS2Contract();
  }

  @Override
  protected MethodMetadata parseAndValidateMetadata(Class<?> targetType,
                                                    String method,
                                                    Class<?>... parameterTypes)
      throws NoSuchMethodException {
    return contract.parseAndValidateMetadata(targetType,
        targetType.getMethod(method, parameterTypes));
  }



  @Path("/{path}")
  public interface Jaxrs2Internals extends BaseJaxrs2Internals<Input, Cookie> {

    @Override
    @POST
    void beanParameters(@BeanParam Input input);

    class Input {

      @DefaultValue("defaultPath")
      @PathParam("path")
      private String path;

      @Encoded
      @DefaultValue("defaultForm")
      @FormParam("form")
      private String form;

      @DefaultValue("defaultHeader")
      @HeaderParam("X-Custom-Header")
      private String header;

      @CookieParam("cookie1")
      @DefaultValue("cookie1DefaultValue")
      private Cookie cookie1;


      @CookieParam("cookie2")
      @DefaultValue("cookie2DefaultValue")
      private String cookie2;


      public String getPath() {
        return path;
      }


      public void setPath(String path) {
        this.path = path;
      }


      public String getForm() {
        return form;
      }


      public void setForm(String form) {
        this.form = form;
      }


      public String getHeader() {
        return header;
      }


      public void setHeader(String header) {
        this.header = header;
      }


      public Cookie getCookie1() {
        return cookie1;
      }


      public void setCookie1(Cookie cookie1) {
        this.cookie1 = cookie1;
      }


      public String getCookie2() {
        return cookie2;
      }


      public void setCookie2(String cookie2) {
        this.cookie2 = cookie2;
      }

    }
  }



  @Path("/{path}")
  public interface Jaxrs2InternalsRecord extends BaseJaxrs2InternalsRecord<InputRecord, Cookie> {

    @Override
    @POST
    void beanParameters(@BeanParam InputRecord input);

    record InputRecord(
        @DefaultValue("defaultPath") @PathParam("path") String path,
        @Encoded @DefaultValue("defaultForm") @FormParam("form") String form,
        @DefaultValue("defaultHeader") @HeaderParam("X-Custom-Header") String header,
        @CookieParam("cookie1") Cookie cookie1,
        @CookieParam("cookie2") @DefaultValue("cookie2DefaultValue") String cookie2


    ) {

    }
  }

  @Override
  protected Class<? extends BaseJaxrs2Internals<?, ?>> BeanClass() {
    return Jaxrs2Internals.class;
  }

  @Override
  protected Class<? extends Jaxrs2InternalsRecord> RecordClass() {
    return Jaxrs2InternalsRecord.class;
  }



}
