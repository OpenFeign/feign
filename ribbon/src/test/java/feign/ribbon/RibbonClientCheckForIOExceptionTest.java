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
package feign.ribbon;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class RibbonClientCheckForIOExceptionTest {

    private Throwable throwable;

    @Before
    public void setUp() {

        throwable = null;
    }

    @Test(expected = IOException.class)
    public void assureThatIOExceptionIsFound() throws IOException {

        givenAThrowableThatIsAnIOException();
        whenCheckForIOExceptionIsCalled();
    }

    @Test(expected = IOException.class)
    public void assureThatNestedIOExceptionIsFound() throws IOException {

        givenAThrowableThatContainsAnIOException();
        whenCheckForIOExceptionIsCalled();
    }

    @Test
    public void assureNothingHappensIfThereIsNoIOException() throws IOException {

        givenAThrowableWithoutIOException();
        whenCheckForIOExceptionIsCalled();
    }

    @Test
    public void assureNothingHappensIfThrowableIsNull() throws IOException {

        givenThrowableIsNull();
        whenCheckForIOExceptionIsCalled();
    }

    @Test
    public void assureCyclesWillNotEndInAnEndlessLoop() throws IOException {

        givenAThrowableWithCycles();
        whenCheckForIOExceptionIsCalled();
    }

    private void givenAThrowableWithCycles() {

        throwable = new RuntimeException("e1");
        throwable.fillInStackTrace();
        RuntimeException e2 = new RuntimeException("e2", throwable);
        RuntimeException e3 = new RuntimeException("e3", e2);
        throwable.initCause(e3);
    }

    private void givenThrowableIsNull() {

        throwable = null;
    }

    private void givenAThrowableWithoutIOException() {

        throwable = new RuntimeException();
        throwable.fillInStackTrace();
    }

    private void givenAThrowableThatIsAnIOException() {

        try {
            methodThatThrowsIOException();
            Assert.fail("IOException expected");
        } catch (IOException e) {
            throwable = e;
        }
    }

    private void givenAThrowableThatContainsAnIOException() {

        try {
            methodThatThrowsNestedIOException();
            Assert.fail("IOException expected");
        } catch (RuntimeException e) {
            throwable = e;
        }
    }

    private void whenCheckForIOExceptionIsCalled() throws IOException {

        RibbonClient.checkForIOException(throwable);
    }

    private void methodThatThrowsNestedIOException() {

        try {
            methodThatThrowsIOException();
            Assert.fail("IOException expected");
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private void methodThatThrowsIOException() throws IOException {

        throw new IOException("test");
    }

}
