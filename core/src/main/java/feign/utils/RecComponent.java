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

package feign.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import feign.Experimental;

/**
 * 
 * A record component, which has a name, a type and an index.
 *
 * (If running on Java 14+, this should be a record class ;) )
 *
 * The latter is the index of the record components in the class file's record attribute, required
 * to invoke the record's canonical constructor .
 * 
 * @author FrauBoes
 * @see RecordInvokeUtils
 * @see <a href="https://github.com/FrauBoes/record-s11n-util">record-s11n-util</a>
 */
@Experimental
public final class RecComponent {
  private final String name;
  private final Type type;
  private final int index;
  private final Annotation[] annotations;

  public RecComponent(String name, Type type, int index, Annotation[] annotations) {
    this.name = name;
    this.type = type;
    this.index = index;
    this.annotations = annotations;
  }

  public String name() {
    return name;
  }

  public Type type() {
    return type;
  }

  public int index() {
    return index;
  }

  public Annotation[] annotations() {
    return annotations;
  }
}
