/**
 * 
 */
package feign;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests for {@link StatusInterpreter}.
 */
public class StatusInterpreterTest {

  @Test
  public void default_isInterpretable_200_class() {
    isTrue(new StatusInterpreter.Default(), 200, 201, 204, 210);  
  }
  @Test
  public void default_isInterpretable_300_class() {
    isFalse(new StatusInterpreter.Default(), 301, 302);
  }

  @Test
  public void default_isInterpretable_400_class() {
    isFalse(new StatusInterpreter.Default(), 400, 401, 403, 404);
  }
  @Test
  public void default_isInterpretable_500_class() {
    isFalse(new StatusInterpreter.Default(), 400, 401, 403, 500);
  }
  
  @Test
  public void include404_isInterpretable_400_class() {
    isFalse(new StatusInterpreter.Include404(), 400, 401, 403);
    isTrue(new StatusInterpreter.Include404(), 404);
  }
  protected void isFalse(StatusInterpreter interpreter, Integer... statuses) {
    for(Integer status: statuses) {
      assertFalse(interpreter.isInterpretableResponse(status));
    }
  }
  protected void isTrue(StatusInterpreter interpreter, Integer... statuses) {
    for(Integer status: statuses) {
      assertTrue(interpreter.isInterpretableResponse(status));
    }
  }
}
