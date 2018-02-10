/**
 * Copyright 2013 Netflix, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package feign.jaxb.mixedns;

import static java.lang.System.out;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.JAXBContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.anarsoft.vmlens.concurrent.junit.ConcurrentTestRunner;
import com.anarsoft.vmlens.concurrent.junit.ThreadCount;

import feign.jaxb.JAXBContextFactory;

/**
 * Instead of switching to http://testng.org/ to get concurrent unit testing, we stick with JUnit 
 * but add <tt>concurrent-junit</tt> library.
 * 
 * @see https://github.com/ThomasKrieger/concurrent-junit
 * @see http://vmlens.com/articles/a-new-way-to-junit-test-your-multithreaded-java-code/
 * 
 * @author wolfch
 */
@RunWith(ConcurrentTestRunner.class)
public class ConcurrentTest {
    
    Class<?>[] pojos = {
            NonSchemaMixedNamespacesTest.Envelope.class,
            NonSchemaMixedNamespacesTest.Body.class,
            NonSchemaMixedNamespacesTest.Login.class
    };

    JAXBContextFactory contextFactory ;
    Method getContext;
    
    @Before
    public void setup() throws Exception {
        contextFactory = new JAXBContextFactory.Builder()
                .withJaxbClasses(pojos)
                .build();
        Class<?>[] foo = new Class<?>[0];
        getContext = JAXBContextFactory.class.getDeclaredMethod("getContext", foo.getClass());
        getContext.setAccessible(true);
        JAXBContext ctx = (JAXBContext) getContext.invoke(contextFactory, new Object[] {pojos});
        out.printf("%s %08x\n", Thread.currentThread().getName(), ctx.hashCode());
    }
    
    List<Result> results = Collections.synchronizedList(new ArrayList<>());
    
    @Test
    @ThreadCount(7)
    public void contextMap() throws Exception {

        JAXBContext ctx = (JAXBContext) getContext.invoke(contextFactory, new Object[] {pojos});
        // let's print after the test to maximize concurrency
        results.add(new Result(Thread.currentThread().getName(), ctx));
        
    }
    
    @After
    public void after() {
        Result lastResult = null;
        for (Result r : results) {
            if (lastResult != null) {
                assertEquals(lastResult, r);
                lastResult = r;
            }
            out.printf("%s %08x\n", r.threadName, r.context.hashCode());
        }
    }
    
    static class Result {
        public Result(final String threadName, final JAXBContext context) {
            this.threadName = threadName;
            this.context = context;
        }
        public final String threadName;
        public final JAXBContext context;
    }
}
