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

/**
 * A {@link Contract} that understands the annotations of several {@link DeclarativeContract
 * declarative contracts} at once, so a single interface can mix annotation styles. This is useful,
 * for example, while moving an interface from Feign's own annotations to Spring MVC ones:
 *
 * <pre>
 * Feign.builder()
 *     .contract(new CompositeContract(new DefaultContract(), new SpringContract()))
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
 * <p>The composite only uses the annotation processors that each delegate registered. Behaviour a
 * delegate adds by overriding {@link Contract.BaseContract} methods is not carried over, and {@link
 * AlwaysEncodeBodyContract} delegates are rejected because the composite cannot honour that marker
 * for them.
 */
@Experimental
public class CompositeContract extends DeclarativeContract {

  private final List<DeclarativeContract> delegates;

  /**
   * @param delegates contracts to combine, highest precedence first.
   */
  public CompositeContract(DeclarativeContract... delegates) {
    this(Arrays.asList(checkNotNull(delegates, "delegates")));
  }

  /**
   * @param delegates contracts to combine, highest precedence first.
   */
  public CompositeContract(List<? extends DeclarativeContract> delegates) {
    checkNotNull(delegates, "delegates");
    checkArgument(!delegates.isEmpty(), "CompositeContract needs at least one delegate");
    List<DeclarativeContract> registered = new ArrayList<>(delegates.size());
    for (DeclarativeContract delegate : delegates) {
      checkNotNull(delegate, "delegate");
      checkArgument(
          !(delegate instanceof AlwaysEncodeBodyContract),
          "%s is an AlwaysEncodeBodyContract, which CompositeContract does not support",
          delegate.getClass().getName());
      registerProcessorsOf(delegate, new ArrayList<>(registered));
      registered.add(delegate);
    }
    this.delegates = Collections.unmodifiableList(registered);
  }

  /** The combined contracts, highest precedence first. */
  public List<DeclarativeContract> delegates() {
    return delegates;
  }
}
