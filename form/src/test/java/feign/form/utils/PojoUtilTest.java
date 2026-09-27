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
package feign.form.utils;

import static org.assertj.core.api.Assertions.assertThat;

import feign.form.FormProperty;
import feign.form.util.PojoUtil;
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
    var pojo = new UserPojo();

    assertThat(PojoUtil.isUserPojo(pojo)).isTrue();
  }

  @Test
  void shouldNotIdentifyJavaObjectAsUserPojo() {
    var object = new HashMap<String, Object>();

    assertThat(PojoUtil.isUserPojo(object)).isFalse();
  }

  @Test
  void shouldIdentifyUserPojoFromClassType() {
    Type type = UserPojo.class;

    assertThat(PojoUtil.isUserPojo(type)).isTrue();
  }

  @Test
  void shouldNotIdentifyJavaClassAsUserPojo() {
    Type type = HashMap.class;

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldConvertPojoFieldsToMap() {
    var pojo = new UserPojo();
    pojo.name = "Eduardo";
    pojo.age = 30;

    assertThat(PojoUtil.toMap(pojo))
        .containsExactlyInAnyOrderEntriesOf(Map.of("custom_name", "Eduardo", "age", 30));
  }

  @Test
  void shouldIgnoreNullFields() {
    var pojo = new UserPojo();
    pojo.name = "Eduardo";

    assertThat(PojoUtil.toMap(pojo)).containsExactly(Map.entry("custom_name", "Eduardo"));
  }

  @Test
  void shouldIgnoreStaticFields() {
    var pojo = new UserPojo();
    pojo.name = "Eduardo";

    assertThat(PojoUtil.toMap(pojo)).doesNotContainKey("staticField");
  }

  @Test
  void shouldIgnoreFinalFields() {
    var pojo = new UserPojo();
    pojo.name = "Eduardo";

    assertThat(PojoUtil.toMap(pojo)).doesNotContainKey("finalField");
  }

  @Test
  void shouldUseFormPropertyAsMapKey() {
    var pojo = new UserPojo();
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
  void shouldNotIdentifyParameterizedMapAsUserPojo() {
    Type type = new TypeReference<Map<String, String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldNotIdentifyParameterizedListAsUserPojo() {
    Type type = new TypeReference<List<String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldNotIdentifyParameterizedHashMapAsUserPojo() {
    Type type = new TypeReference<HashMap<String, String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldNotIdentifyParameterizedCollectionAsUserPojo() {
    Type type = new TypeReference<Collection<String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldIdentifyParameterizedUserPojoAsUserPojo() {
    Type type = new TypeReference<UserPojo<String>>() {}.getType();

    assertThat(PojoUtil.isUserPojo(type)).isTrue();
  }

  @Test
  void shouldNotIdentifyTypeVariableAsUserPojo() {
    Type type = UserPojo.class.getTypeParameters()[0];

    assertThat(PojoUtil.isUserPojo(type)).isFalse();
  }

  @Test
  void shouldNotIdentifyWildcardTypeAsUserPojo() {
    Type type = new TypeReference<List<? extends UserPojo>>() {}.getType();

    Type wildcard = ((ParameterizedType) type).getActualTypeArguments()[0];

    assertThat(PojoUtil.isUserPojo(wildcard)).isFalse();
  }

  static class UserPojo<T> {

    @FormProperty("custom_name")
    private String name;

    private Integer age;

    private T value;

    private static String staticField;

    private final String finalField = "ignored";
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

    private static final String STATIC = "ignored";

    private final String FINAL = "ignored";
  }

  private abstract static class TypeReference<T> {

    private final Type type;

    protected TypeReference() {
      type =
          ((java.lang.reflect.ParameterizedType) getClass().getGenericSuperclass())
              .getActualTypeArguments()[0];
    }

    Type getType() {
      return type;
    }
  }
}
