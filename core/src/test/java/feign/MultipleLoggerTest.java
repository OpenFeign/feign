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
package feign;

import java.io.File;
import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class MultipleLoggerTest {

  @TempDir
  public File tmp;

  private static java.util.logging.Logger getInnerLogger(Logger.JavaLogger logger)
      throws Exception {
    Field inner = logger.getClass().getDeclaredField("logger");
    inner.setAccessible(true);
    return (java.util.logging.Logger) inner.get(logger);
  }

  @SuppressWarnings("deprecation")
  @Test
  void appendSeveralFilesToOneJavaLogger() throws Exception {
    Logger.JavaLogger logger = new Logger.JavaLogger()
        .appendToFile(File.createTempFile("1.log", null, tmp).getAbsolutePath())
        .appendToFile(File.createTempFile("2.log", null, tmp).getAbsolutePath());
    java.util.logging.Logger inner = getInnerLogger(logger);
    assert (inner.getHandlers().length == 2);
  }

  @Test
  void javaLoggerInstantiationWithLoggerName() throws Exception {
    Logger.JavaLogger l1 = new Logger.JavaLogger("First client")
        .appendToFile(File.createTempFile("1.log", null, tmp).getAbsolutePath());
    Logger.JavaLogger l2 = new Logger.JavaLogger("Second client")
        .appendToFile(File.createTempFile("2.log", null, tmp).getAbsolutePath());
    java.util.logging.Logger logger1 = getInnerLogger(l1);
    assert (logger1.getHandlers().length == 1);
    java.util.logging.Logger logger2 = getInnerLogger(l2);
    assert (logger2.getHandlers().length == 1);
  }

  @Test
  void javaLoggerInstantationWithClazz() throws Exception {
    Logger.JavaLogger l1 = new Logger.JavaLogger(String.class)
        .appendToFile(File.createTempFile("1.log", null, tmp).getAbsolutePath());
    Logger.JavaLogger l2 = new Logger.JavaLogger(Integer.class)
        .appendToFile(File.createTempFile("2.log", null, tmp).getAbsolutePath());
    java.util.logging.Logger logger1 = getInnerLogger(l1);
    assert (logger1.getHandlers().length == 1);
    java.util.logging.Logger logger2 = getInnerLogger(l2);
    assert (logger2.getHandlers().length == 1);
  }

}
