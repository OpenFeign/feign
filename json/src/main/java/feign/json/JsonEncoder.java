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
package feign.json;

import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Type;
import static java.lang.String.format;

/**
 * Encodes requests using JSON-java.
 * <p>
 * Basic example with {@link feign.Feign.Builder}:
 *
 * <pre>
 *   interface GitHub {
 *
 *     {@literal @}RequestLine("POST /repos/{owner}/{repo}/contributors")
 *     JSONObject create({@literal @}Param("owner") String owner,
 *                       {@literal @}@Param("repo") String repo,
 *                       JSONObject contributor);
 *
 *   }
 *
 *   GitHub github = Feign.builder()
 *                      .decoder(new JsonDecoder())
 *                      .encoder(new JsonEncoder())
 *                      .target(GitHub.class, "https://api.github.com");
 *
 *   JSONObject contributor = new JSONObject();
 *
 *   contributor.put("login", "radio-rogal");
 *   contributor.put("contributions", 0);
 *   github.create("openfeign", "feign", contributor);
 * </pre>
 */
public class JsonEncoder implements Encoder {

  @Override
  public void encode(Object object, Type bodyType, RequestTemplate template)
      throws EncodeException {
    if (object == null)
      return;
    if (object instanceof JSONArray || object instanceof JSONObject) {
      template.body(object.toString());
    } else {
      throw new EncodeException(format("%s is not a type supported by this encoder.", bodyType));
    }
  }

}
