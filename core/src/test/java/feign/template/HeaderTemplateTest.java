package feign.template;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;

public class HeaderTemplateTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test(expected = IllegalArgumentException.class)
    public void it_should_throw_exception_when_name_is_null() {
        HeaderTemplate.create(null, Arrays.asList("test"));
        exception.expectMessage("name is required.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void it_should_throw_exception_when_name_is_empty() {
        HeaderTemplate.create("", Arrays.asList("test"));
        exception.expectMessage("name is required.");
    }

    @Test(expected = IllegalArgumentException.class)
    public void it_should_throw_exception_when_value_is_null() {
        HeaderTemplate.create("test", null);
        exception.expectMessage("values are required");
    }



}