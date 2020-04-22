/**
 * Copyright 2012-2020 The Feign Authors
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
package feign.hystrix;

import com.netflix.hystrix.HystrixCommand;
import rx.Subscription;
import java.util.concurrent.CompletableFuture;

final class ObservableCompletableFuture<T> extends CompletableFuture<T> {

  private final Subscription sub;

  ObservableCompletableFuture(final HystrixCommand<T> command) {
    this.sub = command.toObservable().single().subscribe(ObservableCompletableFuture.this::complete,
        ObservableCompletableFuture.this::completeExceptionally);
  }


  @Override
  public boolean cancel(final boolean b) {
    sub.unsubscribe();
    return super.cancel(b);
  }
}
