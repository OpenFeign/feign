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
package example.wikipedia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import example.wikipedia.WikipediaClient.Page;
import example.wikipedia.WikipediaClient.Response;
import feign.codec.Decoder;
import feign.gson.GsonDecoder;
import java.io.IOException;
import org.springframework.context.annotation.Bean;

public class WikipediaClientConfiguration {

  static ResponseAdapter<Page> pagesAdapter = new ResponseAdapter<Page>() {

    @Override
    protected String query() {
      return "pages";
    }

    @Override
    protected Page build(JsonReader reader) throws IOException {
      Page page = new Page();
      while (reader.hasNext()) {
        String key = reader.nextName();
        if (key.equals("pageid")) {
          page.id = reader.nextLong();
        } else if (key.equals("title")) {
          page.title = reader.nextString();
        } else {
          reader.skipValue();
        }
      }
      return page;
    }
  };


  @Bean
  public Decoder decoder() {
    Gson gson = new GsonBuilder()
        .registerTypeAdapter(new TypeToken<Response<Page>>() {}.getType(), pagesAdapter)
        .create();

    return new GsonDecoder(gson);
  }

}
