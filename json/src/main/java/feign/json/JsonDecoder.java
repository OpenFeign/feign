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

import feign.Response;
import feign.Util;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import static java.lang.String.format;

/**
 * Decodes responses using JSON-java.
 * <p>
 * Basic example with {@link feign.Feign.Builder}:
 *
 * <pre>
 *   interface GitHub {
 *
 *     {@literal @}RequestLine("GET /repos/{owner}/{repo}/contributors")
 *     JSONArray contributors({@literal @}Param("owner") String owner, {@literal @}Param("repo") String repo);
 *
 *   }
 *
 *   GitHub github = Feign.builder()
 *                      .decoder(new JsonDecoder())
 *                      .target(GitHub.class, "https://api.github.com");
 *
 *   JSONArray contributors = github.contributors("openfeign", "feign");
 *
 *   System.out.println(contributors.getJSONObject(0).getString("login"));
 * </pre>
 */
public class JsonDecoder implements Decoder {

  @Override
  public Object decode(Response response, Type type) throws IOException, DecodeException {
    if (response.status() == 404 || response.status() == 204)
      if (JSONObject.class.isAssignableFrom((Class<?>) type))
        return new JSONObject();
      else if (JSONArray.class.isAssignableFrom((Class<?>) type))
        return new JSONArray();
      else if (String.class.equals(type))
        return null;
      else
        throw new DecodeException(response.status(),
            format("%s is not a type supported by this decoder.", type), response.request());
    if (response.body() == null)
      return null;
    try (Reader reader = response.body().asReader(response.charset())) {
      Reader bodyReader = (reader.markSupported()) ? reader : new BufferedReader(reader);
      bodyReader.mark(1);
      if (bodyReader.read() == -1) {
        return null; // Empty body
      }
      bodyReader.reset();
      return decodeBody(response, type, bodyReader);
    } catch (JSONException jsonException) {
      if (jsonException.getCause() != null && jsonException.getCause() instanceof IOException) {
        throw (IOException) jsonException.getCause();
      }
      throw new DecodeException(response.status(), jsonException.getMessage(), response.request(),
          jsonException);
    }
  }

  private Object decodeBody(Response response, Type type, Reader reader) throws IOException {
    if (String.class.equals(type))
      return Util.toString(reader);
    JSONTokener tokenizer = new JSONTokener(reader);
    if (JSONObject.class.isAssignableFrom((Class<?>) type))
      return new JSONObject(tokenizer);
    else if (JSONArray.class.isAssignableFrom((Class<?>) type))
      return new JSONArray(tokenizer);
    else
      throw new DecodeException(response.status(),
          format("%s is not a type supported by this decoder.", type), response.request());
  }

}
