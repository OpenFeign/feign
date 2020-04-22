/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.apttestgenerator;

import com.google.common.base.CaseFormat;
import com.google.common.base.Converter;
import java.util.List;

public class MethodDefinition {

  private static final Converter<String, String> TO_UPPER_CASE =
      CaseFormat.LOWER_CAMEL.converterTo(CaseFormat.UPPER_CAMEL);
  private final String name;
  private final String uname;
  private final String returnType;
  private final boolean isVoid;
  private final List<ArgumentDefinition> args;

  public MethodDefinition(String name, String returnType, boolean isVoid,
      List<ArgumentDefinition> args) {
    super();
    this.name = name;
    this.uname = TO_UPPER_CASE.convert(name);
    this.returnType = returnType;
    this.isVoid = isVoid;
    this.args = args;
  }

}
