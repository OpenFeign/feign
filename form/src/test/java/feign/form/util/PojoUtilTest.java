/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign.form.util;

import static org.assertj.core.api.Assertions.assertThat;

import feign.form.FormProperty;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PojoUtilTest {

  @Test
  void shouldIdentifyUserPojoFromObject() {
    var pojo = new FormFieldsPojo();

    assertThat(PojoUtil.isUserPojo(pojo)).isTrue();
  }

  @Test
  void shouldNotIdentifyJavaObjectAsFormFieldsPojo() {
    var javaHashMap = new HashMap<String, Object>();

    assertThat(PojoUtil.isUserPojo(javaHashMap)).isFalse();
  }

  @Test
  void shouldIdentifyUserPojoFromClassType() {
    Type type = FormFieldsPojo.class;

    assertThat(PojoUtil.isUserPojo(type)).isTrue();
  }

  @Test
  void shouldNotIdentifyJavaClassAsFormFieldsPojo() {
    Type type = HashMap.class;

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldConvertPojoFieldsToMap() {
    var pojo = new FormFieldsPojo();
    pojo.name = "Eduardo";
    pojo.age = 30;

    assertThat(PojoUtil.toMap(pojo))
        .containsExactlyInAnyOrderEntriesOf(Map.of("custom_name", "Eduardo", "age", 30));
  }

  @Test
  void shouldIgnoreNullFields() {
    var pojo = new FormFieldsPojo();
    pojo.name = "Eduardo";

    assertThat(PojoUtil.toMap(pojo)).containsExactly(Map.entry("custom_name", "Eduardo"));
  }

  @Test
  void shouldIgnoreStaticFields() {
    var pojo = new FormFieldsPojo();
    pojo.name = "Eduardo";

    assertThat(PojoUtil.toMap(pojo)).doesNotContainKey("staticField");
  }

  @Test
  void shouldIgnoreFinalFields() {
    var pojo = new FormFieldsPojo();
    pojo.name = "Eduardo";

    assertThat(PojoUtil.toMap(pojo)).doesNotContainKey("finalField");
  }

  @Test
  void shouldUseFormPropertyAsMapKey() {
    var pojo = new FormFieldsPojo();
    pojo.name = "Eduardo";

    assertThat(PojoUtil.toMap(pojo))
        .containsEntry("custom_name", "Eduardo")
        .doesNotContainKey("name");
  }

  @Test
  void shouldReadPrivateFields() {
    var pojo = new PrivateFieldsPojo("Eduardo");

    assertThat(PojoUtil.toMap(pojo)).containsEntry("name", "Eduardo");
  }

  @Test
  void shouldNotConvertInheritedFields() {
    var pojo = new ChildPojo();
    pojo.child = "child";

    assertThat(PojoUtil.toMap(pojo)).containsEntry("child", "child").doesNotContainKey("parent");
  }

  @Test
  void shouldReturnEmptyMapWhenPojoHasNoEligibleFields() {
    var pojo = new EmptyPojo();

    assertThat(PojoUtil.toMap(pojo)).isEmpty();
  }

  @Test
  void shouldNotIdentifyParameterizedMapAsFormFieldsPojo() {
    Type type = new TypeReference<Map<String, String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldNotIdentifyParameterizedListAsFormFieldsPojo() {
    Type type = new TypeReference<List<String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldNotIdentifyParameterizedHashMapAsFormFieldsPojo() {
    Type type = new TypeReference<HashMap<String, String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldNotIdentifyParameterizedCollectionAsFormFieldsPojo() {
    Type type = new TypeReference<Collection<String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldIdentifyParameterizedUserPojoAsFormFieldsPojo() {
    Type type = new TypeReference<GenericPojo<String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isTrue();
  }

  @Test
  void shouldNotIdentifyTypeVariableAsFormFieldsPojo() {
    Type typeVariable = GenericPojo.class.getTypeParameters()[0];

    assertThat(PojoUtil.isUserPojo(typeVariable)).isFalse();
  }

  @Test
  void shouldIdentifyWildcardBoundedByUserPojoAsFormFieldsPojo() {
    Type parameterizedListType = new TypeReference<List<? extends FormFieldsPojo>>() {}.getType();

    Type wildcard = ((ParameterizedType) parameterizedListType).getActualTypeArguments()[0];

    assertThat(PojoUtil.isUserPojo(wildcard)).isTrue();
  }

  @Test
  void shouldNotIdentifyPrimitiveAsFormFieldsPojo() {
    assertThat(PojoUtil.isUserPojo(int.class)).isFalse();
  }

  @Test
  void shouldNotIdentifyByteArrayAsFormFieldsPojo() {
    assertThat(PojoUtil.isUserPojo(byte[].class)).isFalse();
  }

  @Test
  void shouldNotIdentifyObjectArrayAsFormFieldsPojo() {
    assertThat(PojoUtil.isUserPojo(String[].class)).isFalse();
  }

  static class FormFieldsPojo {

    @FormProperty("custom_name")
    private String name;

    private Integer age;

    private static String staticField;

    private final String finalField = "ignored";
  }

  static class GenericPojo<T> {

    private T typeVariableField;
  }

  private static class PrivateFieldsPojo {

    private String name;

    private PrivateFieldsPojo(String name) {
      this.name = name;
    }
  }

  static class ParentPojo {

    private String parent;
  }

  static class ChildPojo extends ParentPojo {

    private String child;
  }

  static class EmptyPojo {

    private static final String IGNORED_STATIC_FIELD = "ignored";

    private final String ignoredFinalField = "ignored";
  }

  private abstract static class TypeReference<T> {

    private final Type capturedType;

    protected TypeReference() {
      capturedType =
          ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
    }

    Type getType() {
      return capturedType;
    }
  }
}
