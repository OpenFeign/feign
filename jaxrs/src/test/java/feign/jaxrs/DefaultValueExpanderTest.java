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
