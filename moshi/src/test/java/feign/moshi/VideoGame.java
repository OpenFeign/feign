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
package feign.moshi;

import com.squareup.moshi.Json;

public class VideoGame {

  @Json(name = "name")
  public final String name;

  @Json(name = "hero")
  public final Hero hero;

  public VideoGame(String name, String hero, String enemy) {
    this.name = name;
    this.hero = new Hero(hero, enemy);
  }

  static class Hero {
    @Json(name = "name")
    public final String name;

    @Json(name = "enemy")
    public final String enemyName;

    Hero(String name, String enemyName) {
      this.name = name;
      this.enemyName = enemyName;
    }
  }


}
