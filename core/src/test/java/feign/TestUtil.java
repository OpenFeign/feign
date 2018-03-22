package feign;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

public class TestUtil {

	public static Matcher<?> bodyMatcher(String string) {
		return new BaseMatcher<Exception>() {
			@Override
			public boolean matches(Object item) {
				if (item instanceof FeignException) {
					FeignException e = (FeignException) item;
					return string.equals(e.body());
				}
				return false;
			}
	
			@Override
			public void describeTo(Description description) { 
				// empty on purpose
			}
		};
	}

}
