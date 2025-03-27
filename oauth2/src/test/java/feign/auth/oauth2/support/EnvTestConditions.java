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
package feign.auth.oauth2.support;

import io.github.cdimascio.dotenv.Dotenv;

public final class EnvTestConditions {
  public static Dotenv ENV = Dotenv.configure().filename("test.env").ignoreIfMissing().load();

  private EnvTestConditions() {}

  public static boolean testsWithCognitoEnabled() {
    return ENV.get("AWS_CLIENT_ID") != null;
  }

  public static boolean testsWithAuth0Enabled() {
    return ENV.get("AUTH0_CLIENT_ID") != null;
  }
}
