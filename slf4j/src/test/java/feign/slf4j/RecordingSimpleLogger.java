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

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.LoggerFactory;
import org.slf4j.impl.SimpleLogger;
import org.slf4j.impl.SimpleLoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY;
import static org.slf4j.impl.SimpleLogger.SHOW_THREAD_NAME_KEY;

/**
 * A testing utility to allow control over {@link org.slf4j.impl.SimpleLogger}. In some cases,
 * reflection is used to bypass access restrictions.
 */
final class RecordingSimpleLogger implements TestRule {

  private String expectedMessages = "";

  /**
   * Resets {@link org.slf4j.impl.SimpleLogger} to the new log level.
   */
  RecordingSimpleLogger logLevel(String logLevel) throws Exception {
    System.setProperty(SHOW_THREAD_NAME_KEY, "false");
    System.setProperty(DEFAULT_LOG_LEVEL_KEY, logLevel);

    Field field = SimpleLogger.class.getDeclaredField("INITIALIZED");
    field.setAccessible(true);
    field.set(null, false);

    Method method = SimpleLoggerFactory.class.getDeclaredMethod("reset");
    method.setAccessible(true);
    method.invoke(LoggerFactory.getILoggerFactory());
    return this;
  }

  /**
   * Newline delimited output that would be sent to stderr.
   */
  RecordingSimpleLogger expectMessages(String expectedMessages) {
    this.expectedMessages = expectedMessages;
    return this;
  }

  /**
   * Steals the output of stderr as that's where the log events go.
   */
  @Override
  public Statement apply(final Statement base, Description description) {
    return new Statement() {
      @Override
      public void evaluate() throws Throwable {
        ByteArrayOutputStream buff = new ByteArrayOutputStream();
        PrintStream stderr = System.err;
        try {
          System.setErr(new PrintStream(buff));
          base.evaluate();
          assertEquals(expectedMessages, buff.toString());
        } finally {
          System.setErr(stderr);
        }
      }
    };
  }
}
