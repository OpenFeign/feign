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
package feign.jaxrs;

import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import feign.jaxrs.AbstractParameterValidator.DefaultParameterExpander;

public abstract class AbstractParameterValidatorTest {

	private AbstractParameterValidator<?> expander = new DefaultParameterExpander();

	@Test
	public void valueIsNull() {
		Assert.assertTrue(expander.test(null));
	}

	@Test
	public void valueIsPrimitive() {

		Assert.assertTrue(expander.test(1));
	}

	public static class ClassHadStringConstructor {
		public ClassHadStringConstructor(String s) {

		}
	}

	@Test
	public void valueClassHasConstructor() {
		Assert.assertTrue(expander.test(new ClassHadStringConstructor("")));
	}

	public static class ClassHadStaticValueOfMethod {
		static void valueOf(String s) {

		}
	}

	@Test
	public void valueClassHadStaticValueOfMethod() {
		Assert.assertTrue(expander.test(new ClassHadStaticValueOfMethod()));
	}

	public static class ClassHadStaticFromStringMethod {
		static void fromString(String s) {

		}
	}

	@Test
	public void valueClassHadStaticFromStringMethod() {
		Assert.assertTrue(expander.test(new ClassHadStaticFromStringMethod()));
	}

	@Test
	public void valueIsList() {
		Assert.assertTrue(expander.test(List.of(new ClassHadStringConstructor(""))));
		Assert.assertTrue(expander.test(List.of(new ClassHadStaticValueOfMethod())));
		Assert.assertTrue(expander.test(List.of(new ClassHadStaticFromStringMethod())));

	}

	@Test
	public void valueIsSet() {
		Assert.assertTrue(expander.test(Set.of(new ClassHadStringConstructor(""))));
		Assert.assertTrue(expander.test(Set.of(new ClassHadStaticValueOfMethod())));
		Assert.assertTrue(expander.test(Set.of(new ClassHadStaticFromStringMethod())));

	}

	@Test
	public void isAllowedType() {
		Assert.assertTrue(!expander.isAllowedType(Void.class, Void.class));
	}
}
