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
