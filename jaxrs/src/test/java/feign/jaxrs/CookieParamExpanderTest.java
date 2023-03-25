package feign.jaxrs;

import java.util.List;
import java.util.Set;

import javax.ws.rs.core.Cookie;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class CookieParamExpanderTest {
	private CookieParamExpander expander = new CookieParamExpander("name");

	@SuppressWarnings("deprecation")
	@Rule
	public final ExpectedException thrown = ExpectedException.none();

	@Test
	public void nameIsNull() {
		thrown.expect(NullPointerException.class);
		thrown.expectMessage(CookieParamExpander.NULL_NAME_ERROR_MESSAGE);
		new CookieParamExpander(null);
	}

	@Test
	public void isAllowedType() {
		Assert.assertTrue(expander.isAllowedType(Cookie.class, Cookie.class));
	}

	@Test
	public void valueIsList() {
		Assert.assertTrue(expander.test(List.of(new Cookie("", ""))));
	}

	@Test
	public void valueIsSet() {
		Assert.assertTrue(expander.test(Set.of(new Cookie("", ""))));

	}
	@Test
	public void valueIsCookieTypeWithWrongName() {
		thrown.expect(IllegalArgumentException.class);
		thrown.expectMessage(String.format(CookieParamExpander.MISMATCH_ERROR_MESSAGE, "wrongName", "name")

		);
		expander.expand(new Cookie("wrongName", ""));

	}

	@Test
	public void valueIsCookieTypeWithCorrectName() {
		Assert.assertEquals(expander.expand(new Cookie("name", "")), "");
	}

	@Test
	public void valueIsStringType() {
		Assert.assertEquals(expander.expand("cookie1=cookie1value"), "cookie1=cookie1value");
	}
}
