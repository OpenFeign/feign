/**
 * Copyright 2012-2019 The Feign Authors
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
package feign.aptgenerator.github;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * This generic abstract class is used for obtaining full generics type information by sub-classing;
 * it must be converted to {@link ResolvedType} implementation (implemented by <code>JavaType</code>
 * from "databind" bundle) to be used. Class is based on ideas from
 * <a href="http://gafter.blogspot.com/2006/12/super-type-tokens.html"
 * >http://gafter.blogspot.com/2006/12/super-type-tokens.html</a>, Additional idea (from a
 * suggestion made in comments of the article) is to require bogus implementation of
 * <code>Comparable</code> (any such generic interface would do, as long as it forces a method with
 * generic type to be implemented). to ensure that a Type argument is indeed given.
 * <p>
 * Usage is by sub-classing: here is one way to instantiate reference to generic type
 * <code>List&lt;Integer&gt;</code>:
 * 
 * <pre>
 * TypeReference ref = new TypeReference&lt;List&lt;Integer&gt;&gt;() {};
 * </pre>
 * 
 * which can be passed to methods that accept TypeReference, or resolved using
 * <code>TypeFactory</code> to obtain {@link ResolvedType}.
 */
public abstract class TypeReference<T> implements Comparable<TypeReference<T>> {
  protected final Type _type;

  protected TypeReference() {
    final Type superClass = getClass().getGenericSuperclass();
    if (superClass instanceof Class<?>) { // sanity check, should never happen
      throw new IllegalArgumentException(
          "Internal error: TypeReference constructed without actual type information");
    }
    /*
     * 22-Dec-2008, tatu: Not sure if this case is safe -- I suspect it is possible to make it fail?
     * But let's deal with specific case when we know an actual use case, and thereby suitable
     * workarounds for valid case(s) and/or error to throw on invalid one(s).
     */
    _type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
  }

  public Type getType() {
    return _type;
  }

  /**
   * The only reason we define this method (and require implementation of <code>Comparable</code>)
   * is to prevent constructing a reference without type information.
   */
  @Override
  public int compareTo(TypeReference<T> o) {
    return 0;
  }
  // just need an implementation, not a good one... hence ^^^
}

