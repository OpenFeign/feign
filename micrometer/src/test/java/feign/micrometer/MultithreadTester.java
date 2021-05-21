/**
 * Copyright 2012-2021 The Feign Authors
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
package feign.micrometer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;

public class MultithreadTester<T, R> {

  private final T input;
  private final int threads;

  public MultithreadTester(final T input) {
    this(input, Double.valueOf(Math.pow(Runtime.getRuntime().availableProcessors(), 2)).intValue());
    // log.info("MultithreadTester with {} threads",
    // Math.pow(Runtime.getRuntime().availableProcessors(), 2));
  }


  /**
   * Constructor del runner.
   *
   * @param input object to be tested concurrently.
   * @param threads number of threads to simulate concurrently.
   */
  public MultithreadTester(final T input, final int threads) {
    this.input = input;
    this.threads = threads;
  }

  public int getThreads() {
    return threads;
  }

  /**
   * Execution of a block of code concurrently.
   *
   * @param func code block to execute concurrently.
   * @return the result of executing the code in each of the threads.
   */
  public List<Future<R>> run(final Function<T, R> func) {
    final ExecutorService service = Executors.newFixedThreadPool(threads);
    final CountDownLatch latch = new CountDownLatch(1);
    final List<Future<R>> futures = new ArrayList<>(threads);
    final Callable<R> task = () -> {
      latch.await();
      return func.apply(input);
    };
    for (int thread = 0; thread < threads; thread++) {
      futures.add(service.submit(task));
    }
    latch.countDown();
    service.shutdown();
    return futures;
  }

}
