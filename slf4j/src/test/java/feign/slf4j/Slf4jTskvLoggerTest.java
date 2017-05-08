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

import feign.Feign;
import feign.Logger;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

public class Slf4jTskvLoggerTest {

    private static final String CONFIG_KEY = "someMethod()";
    private static final Request REQUEST =
            new RequestTemplate().method("GET").append("http://api.example.com").request();
    private static final Response RESPONSE =
            Response.builder()
                    .status(200)
                    .reason("OK")
                    .headers(Collections.<String, Collection<String>>emptyMap())
                    .body(new byte[0])
                    .build();
    @Rule
    public final RecordingSimpleLogger slf4j = new RecordingSimpleLogger();
    private Slf4jTskvLogger logger;

    @Test
    public void useFeignLoggerByDefault() throws Exception {
        slf4j.logLevel("debug");
        slf4j.expectMessages("DEBUG feign.Logger - This is my message\n");

        logger = new Slf4jTskvLogger();
        logger.log(CONFIG_KEY, "This is my message");
    }

    @Test
    public void useLoggerByNameIfRequested() throws Exception {
        slf4j.logLevel("debug");
        slf4j.expectMessages("DEBUG named.logger - This is my message\n");

        logger = new Slf4jTskvLogger("named.logger");
        logger.log(CONFIG_KEY, "This is my message");
    }

    @Test
    public void useLoggerByClassIfRequested() throws Exception {
        slf4j.logLevel("debug");
        slf4j.expectMessages("DEBUG feign.Feign - This is my message\n");

        logger = new Slf4jTskvLogger(Feign.class);
        logger.log(CONFIG_KEY, "This is my message");
    }

    @Test
    public void useSpecifiedLoggerIfRequested() throws Exception {
        slf4j.logLevel("debug");
        slf4j.expectMessages("DEBUG specified.logger - This is my message\n");

        logger = new Slf4jTskvLogger(LoggerFactory.getLogger("specified.logger"));
        logger.log(CONFIG_KEY, "This is my message");
    }

    @Test
    public void logOnlyIfDebugEnabled() throws Exception {
        slf4j.logLevel("info");

        logger = new Slf4jTskvLogger();
        logger.log(CONFIG_KEY, "A message with %d formatting %s.", 2, "tokens");
        logger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
        logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, RESPONSE, 273);
    }

    @Test
    public void logRequestsAndResponses() throws Exception {
        slf4j.logLevel("debug");
        slf4j.expectMessages(
                allOf(
                        containsString("DEBUG feign.Logger - A message with 2 formatting tokens."),
                        containsString("DEBUG feign.Logger - http\treq-id=["),
                        containsString("]\tcall=[someMethod()]\tmethod=[GET]\turi=[http://api.example.com]"),
                        containsString("]\tstatus=[200]\treason=[OK]\telapsed-ms=[273]\tlength=[0]\n")
                )
        );

        logger = new Slf4jTskvLogger();
        logger.log(CONFIG_KEY, "A message with {} formatting {}.", 2, "tokens");
        logger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
        logger.logAndRebufferResponse(CONFIG_KEY, Logger.Level.BASIC, RESPONSE, 273);
    }

    @Test
    public void logRetry() throws Exception {
        slf4j.logLevel("debug");
        slf4j.expectMessages(
                allOf(
                        containsString("DEBUG feign.Logger - http\treq-id=["),
                        containsString("]\tcall=[someMethod()]\tmethod=[GET]\turi=[http://api.example.com]"),
                        containsString("DEBUG feign.Logger - http\tstate=[retry]\treq-id=["),
                        not(containsString("[null]"))
                )
        );

        logger = new Slf4jTskvLogger();
        logger.logRequest(CONFIG_KEY, Logger.Level.BASIC, REQUEST);
        logger.logRetry(CONFIG_KEY, Logger.Level.BASIC);
    }
}
