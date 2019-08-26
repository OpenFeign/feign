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
package feign;

import org.junit.Test;
import java.lang.reflect.Field;

public class MultipleLoggerTest {

  private static java.util.logging.Logger getInnerLogger(Logger.JavaLogger logger)
      throws Exception {
    Field inner = logger.getClass().getDeclaredField("logger");
    inner.setAccessible(true);
    return (java.util.logging.Logger) inner.get(logger);
  }

  @Test
  public void testAppendSeveralFilesToOneJavaLogger() throws Exception {
    Logger.JavaLogger logger = new Logger.JavaLogger().appendToFile("1.log").appendToFile("2.log");
    java.util.logging.Logger inner = getInnerLogger(logger);
    assert (inner.getHandlers().length == 2);
  }

  @Test
  public void testJavaLoggerInstantationWithLoggerName() throws Exception {
    Logger.JavaLogger l1 = new Logger.JavaLogger("First client").appendToFile("1.log");
    Logger.JavaLogger l2 = new Logger.JavaLogger("Second client").appendToFile("2.log");
    java.util.logging.Logger logger1 = getInnerLogger(l1);
    assert (logger1.getHandlers().length == 1);
    java.util.logging.Logger logger2 = getInnerLogger(l2);
    assert (logger2.getHandlers().length == 1);
  }

  @Test
  public void testJavaLoggerInstantationWithClazz() throws Exception {
    Logger.JavaLogger l1 = new Logger.JavaLogger(String.class).appendToFile("1.log");
    Logger.JavaLogger l2 = new Logger.JavaLogger(Integer.class).appendToFile("2.log");
    java.util.logging.Logger logger1 = getInnerLogger(l1);
    assert (logger1.getHandlers().length == 1);
    java.util.logging.Logger logger2 = getInnerLogger(l2);
    assert (logger2.getHandlers().length == 1);
  }

}
