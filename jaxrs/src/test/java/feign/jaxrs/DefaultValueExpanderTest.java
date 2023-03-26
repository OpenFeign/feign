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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

public final class DefaultValueExpanderTest {
	@Test
	public void valueIsNull() {
		DefaultValueExpander expander = new DefaultValueExpander("defaultValue", Optional.empty());
		String value = expander.expand(null);
		Assert.assertEquals(value, "defaultValue");
	}
	@Test
	public void valueIsEmptyList() {
		DefaultValueExpander expander = new DefaultValueExpander("defaultValue", Optional.empty());
		String value = expander.expand(Arrays.asList());
		Assert.assertEquals(value, "defaultValue");
	}
	@Test
	public void valueIsEmptySet() {
		DefaultValueExpander expander = new DefaultValueExpander("defaultValue", Optional.empty());
		String value = expander.expand(new HashSet<>());
		Assert.assertEquals(value, "defaultValue");
	}
	@Test
	public void valueNotNullAndExpanderIsEmpty() {
		DefaultValueExpander expander = new DefaultValueExpander("defaultValue", Optional.empty());
		String value = expander.expand("value");
		Assert.assertEquals(value, "value");
	}

	@Test
	public void valueNotNullAndExpanderNotEmpty() {
		DefaultValueExpander expander = new DefaultValueExpander("defaultValue", Optional.of((x) -> x + "-"));
		String value = expander.expand("value");
		Assert.assertEquals(value, "value-");
	}

}
