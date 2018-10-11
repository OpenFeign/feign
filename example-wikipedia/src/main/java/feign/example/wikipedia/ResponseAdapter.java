/**
 * Copyright 2012-2018 The Feign Authors
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
package feign.example.wikipedia;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;

abstract class ResponseAdapter<X> extends TypeAdapter<WikipediaExample.Response<X>> {

  /**
   * name of the key inside the {@code query} dict which holds the elements desired. ex. {@code
   * pages}.
   */
  protected abstract String query();

  /**
   * Parses the contents of a result object.
   * <p/>
   * <br>
   * ex. If {@link #query()} is {@code pages}, then this would parse the value of each key in the
   * dict {@code pages}. In the example below, this would first start at line {@code 3}.
   * <p/>
   * 
   * <pre>
   * "pages": {
   *   "2576129": {
   *     "pageid": 2576129,
   *     "title": "Burchell's zebra",
   * --snip--
   * </pre>
   */
  protected abstract X build(JsonReader reader) throws IOException;

  /**
   * the wikipedia api doesn't use json arrays, rather a series of nested objects.
   */
  @Override
  public WikipediaExample.Response<X> read(JsonReader reader) throws IOException {
    WikipediaExample.Response<X> pages = new WikipediaExample.Response<X>();
    reader.beginObject();
    while (reader.hasNext()) {
      String nextName = reader.nextName();
      if ("query".equals(nextName)) {
        reader.beginObject();
        while (reader.hasNext()) {
          if (query().equals(reader.nextName())) {
            reader.beginObject();
            while (reader.hasNext()) {
              // each element is in form: "id" : { object }
              // this advances the pointer to the value and skips the key
              reader.nextName();
              reader.beginObject();
              pages.add(build(reader));
              reader.endObject();
            }
            reader.endObject();
          } else {
            reader.skipValue();
          }
        }
        reader.endObject();
      } else if ("continue".equals(nextName)) {
        reader.beginObject();
        while (reader.hasNext()) {
          if ("gsroffset".equals(reader.nextName())) {
            pages.nextOffset = reader.nextLong();
          } else {
            reader.skipValue();
          }
        }
        reader.endObject();
      } else {
        reader.skipValue();
      }
    }
    reader.endObject();
    return pages;
  }

  @Override
  public void write(JsonWriter out, WikipediaExample.Response<X> response) throws IOException {
    throw new UnsupportedOperationException();
  }
}
