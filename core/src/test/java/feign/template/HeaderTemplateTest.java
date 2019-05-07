package feign.template;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

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

    @Test
    public void it_should_return_name() {
        HeaderTemplate headerTemplate = HeaderTemplate.create("test", Arrays.asList("test 1", "test 2"));
        assertEquals("test", headerTemplate.getName());
    }

    @Test
    public void it_should_return_expanded() {
        HeaderTemplate headerTemplate = HeaderTemplate.create("hello", Arrays.asList("emre", "savci"));
        assertEquals("hello emre, savci", headerTemplate.expand(Collections.emptyMap()));
        assertEquals("hello emre, savci", headerTemplate.expand(Collections.singletonMap("name", "firsts")));
    }

}