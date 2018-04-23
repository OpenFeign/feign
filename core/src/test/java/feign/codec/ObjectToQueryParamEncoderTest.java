/**
 * Copyright 2013 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.lang.reflect.Type;
import java.util.Map;

import feign.RequestTemplate;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Check the encoder can add expected parameters
 */
public class ObjectToQueryParamEncoderTest {

  @InjectMocks
  private ObjectToQueryParamEncoder encoder;
  @Mock
  private RequestTemplate template;
  @Captor
  private ArgumentCaptor<Map> mapArgumentCaptor;

  @Before
  public void initMock() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void encodeShouldIgnoreNonProperties() {
    Type type = null;
    encoder.encode(new NoPropertyBean(), type, template);
    verifyNoMoreInteractions(template);
  }

  @Test
  public void encodeShouldFindStringProperty() {
    Type type = null;
    String paramValue = "Yes";
    String paramName = "stringParam";
    DummyBen queryParametersObject = new DummyBen(null, paramValue, null, null);

    assertEncoded(queryParametersObject, paramValue, paramName, type);
  }

  @Test
  public void encodeShouldFindBooleanProperty() {
    Type type = null;
    boolean paramValue = true;
    String paramName = "booleanParam";
    DummyBen queryParametersObject = new DummyBen(null, null, paramValue, null);

    assertEncoded(queryParametersObject, paramValue, paramName, type);
  }

  private void assertEncoded(DummyBen queryParametersObject, Object paramValue, String paramName, Type type) {
    encoder.encode(queryParametersObject, type, template);

    verify(template).query(paramName, ObjectToQueryParamEncoder.toParamTemplate(paramName));
    verify(template).resolve(mapArgumentCaptor.capture());
    assertThat(mapArgumentCaptor.getValue())
      .containsOnlyKeys(paramName)
      .containsValue(paramValue)
    ;
    verifyNoMoreInteractions(template);
  }

  static class DummyBen {
    private final Character charParam;
    private String stringParam;
    private Boolean booleanParam;
    private Integer intParam;

    public DummyBen() {
      charParam = 'M';
    }

    public DummyBen(Character charParam, String stringParam, Boolean booleanParam, Integer intParam) {
      this.charParam = charParam;
      this.stringParam = stringParam;
      this.booleanParam = booleanParam;
      this.intParam = intParam;
    }

    public Character getCharParam() {
      return charParam;
    }

    public String getStringParam() {
      return stringParam;
    }

    public void setStringParam(String stringParam) {
      this.stringParam = stringParam;
    }

    public Boolean getBooleanParam() {
      return booleanParam;
    }

    public void setBooleanParam(Boolean booleanParam) {
      this.booleanParam = booleanParam;
    }

    public Integer getIntParam() {
      return intParam;
    }

    public void setIntParam(Integer intParam) {
      this.intParam = intParam;
    }

    @Override
    public String toString() {
      return "DummyBen{" +
        "charParam=" + charParam +
        ", stringParam='" + stringParam + '\'' +
        ", booleanParam=" + booleanParam +
        ", intParam=" + intParam +
        '}';
    }
  }

  static class NoPropertyBean {
    public void get() {
      //noop
    }

    public Object getObject(Object o) {
      return o;
    }

    public String getNo(String way) {
      return "no";
    }
  }

}
