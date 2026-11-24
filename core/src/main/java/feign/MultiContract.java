/*
 * Copyright © 2012 The Feign Authors (feign@commonhaus.dev)
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
package feign;

import static feign.Util.checkArgument;
import static feign.Util.checkNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A {@link Contract} that understands the annotations of several {@link DeclarativeContract
 * declarative contracts} at once, so a single interface can mix annotation styles. This is useful,
 * for example, while moving an interface from Feign's own annotations to Spring MVC ones:
 *
 * <pre>
 * Feign.builder()
 *     .contracts(new DefaultContract(), new SpringContract())
 *     .target(GitHub.class, "https://api.github.com");
 * </pre>
 *
 * <p>Every class, method and parameter annotation is handled by exactly one delegate: the first
 * one, in the order given, that registered a processor for it. Delegates further down the list only
 * see the annotations that no earlier delegate understands, so list the contract whose
 * interpretation should win first. Annotations are dispatched one at a time, so annotations
 * understood by different delegates can be combined on the same interface, method or parameter.
 * That also means a method should declare its HTTP method and path in one style only: two different
 * annotations that each set the request line (say {@code @RequestLine} and {@code @GetMapping}) are
 * both applied.
 *
 * <p>A multi-contract is itself a {@link DeclarativeContract}, so one can be passed to another. The
 * same precedence rule applies to the nested contract's delegates as a group.
 *
 * <p>The multi-contract only uses the annotation processors that each delegate registered.
 * Behaviour a delegate adds by overriding {@link Contract.BaseContract} methods is not carried
 * over, and {@link AlwaysEncodeBodyContract} delegates are rejected because the multi-contract
 * cannot honour that marker for them.
 */
@Experimental
public class MultiContract extends DeclarativeContract {

  private final List<DeclarativeContract> delegates;

  private MultiContract(List<? extends DeclarativeContract> delegates) {
    checkNotNull(delegates, "delegates");
    checkArgument(!delegates.isEmpty(), "MultiContract needs at least one delegate");
    List<DeclarativeContract> registered = new ArrayList<>(delegates.size());
    for (DeclarativeContract delegate : delegates) {
      checkNotNull(delegate, "delegate");
      checkArgument(
          !(delegate instanceof AlwaysEncodeBodyContract),
          "%s is an AlwaysEncodeBodyContract, which MultiContract does not support",
          delegate.getClass().getName());
      registerProcessorsOf(delegate, new ArrayList<>(registered));
      registered.add(delegate);
    }
    this.delegates = Collections.unmodifiableList(registered);
  }

  /**
   * Combines the given contracts.
   *
   * @param delegates contracts to combine, highest precedence first
   */
  public static MultiContract of(DeclarativeContract... delegates) {
    return of(Arrays.asList(checkNotNull(delegates, "delegates")));
  }

  /**
   * Combines the given contracts.
   *
   * @param delegates contracts to combine, highest precedence first
   */
  public static MultiContract of(List<? extends DeclarativeContract> delegates) {
    return new MultiContract(delegates);
  }

  @Override
  public String toString() {
    return "MultiContract"
        + delegates.stream().map(MultiContract::describe).collect(Collectors.toList());
  }

  private static String describe(DeclarativeContract delegate) {
    if (delegate instanceof MultiContract) {
      return delegate.toString();
    }
    String name = delegate.getClass().getSimpleName();
    return name.isEmpty() ? delegate.getClass().getName() : name;
  }
}
