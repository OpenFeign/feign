/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign.slf4j;

import java.io.File;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;
import org.slf4j.impl.SimpleLoggerFactory;

/**
 * A testing utility to allow control over {@link SimpleLogger}. In some cases, reflection is used
 * to bypass access restrictions.
 */
class SimpleLoggerUtil {
  static void initialize(File file, String logLevel) throws Exception {
    System.setProperty(SimpleLogger.SHOW_THREAD_NAME_KEY, "false");
    System.setProperty(SimpleLogger.LOG_FILE_KEY, file.getAbsolutePath());
    System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, logLevel);
    resetSlf4j();
  }

  static void resetToDefaults() throws Exception {
    System.clearProperty(SimpleLogger.SHOW_THREAD_NAME_KEY);
    System.clearProperty(SimpleLogger.LOG_FILE_KEY);
    System.clearProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY);
    resetSlf4j();
  }

  private static void resetSlf4j() throws Exception {
    ReflectionUtil.setStaticField(SimpleLogger.class, "INITIALIZED", false);
    ReflectionUtil.invokeVoidNoArgMethod(
        SimpleLoggerFactory.class, "reset", LoggerFactory.getILoggerFactory());
  }
}
