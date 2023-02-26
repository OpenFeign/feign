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

/**
 * {@link DeclarativeContract} extension that allows user provided custom encoders to define the
 * request message payload using only the request template and the method parameters, not requiring
 * a specific and unique body object.
 *
 * This type of contract is useful when an application needs a Feign client whose request payload is
 * defined entirely by a custom Feign encoder regardless of how many parameters are declared at the
 * client method. In this case, even with no presence of body parameter the provided encoder will
 * have to know how to define the request payload (for example, based on the method name, method
 * return type, and other metadata provided by custom annotations, all available via the provided
 * {@link RequestTemplate} object).
 *
 * @author fabiocarvalho777@gmail.com
 */
public abstract class AlwaysEncodeBodyContract extends DeclarativeContract {
}
