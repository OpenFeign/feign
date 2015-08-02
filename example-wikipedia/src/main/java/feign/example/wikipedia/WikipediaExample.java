/*
 * Copyright 2013 Netflix, Inc.
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
package feign.example.wikipedia;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import feign.Feign;
import feign.Logger;
import feign.Param;
import feign.RequestLine;
import feign.gson.GsonDecoder;

public class WikipediaExample {

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

  public static void main(String... args) throws InterruptedException {
    Gson gson = new GsonBuilder()
        .registerTypeAdapter(new TypeToken<Response<Page>>() {
        }.getType(), pagesAdapter)
        .create();

    Wikipedia wikipedia = Feign.builder()
        .decoder(new GsonDecoder(gson))
        .logger(new Logger.ErrorLogger())
        .logLevel(Logger.Level.BASIC)
        .target(Wikipedia.class, "https://en.wikipedia.org");

    System.out.println("Let's search for PTAL!");
    Iterator<Page> pages = lazySearch(wikipedia, "PTAL");
    while (pages.hasNext()) {
      System.out.println(pages.next().title);
    }
  }

  /**
   * this will lazily continue searches, making new http calls as necessary.
   *
   * @param wikipedia used to search
   * @param query     see {@link Wikipedia#search(String)}.
   */
  static Iterator<Page> lazySearch(final Wikipedia wikipedia, final String query) {
    final Response<Page> first = wikipedia.search(query);
    if (first.nextOffset == null) {
      return first.iterator();
    }
    return new Iterator<Page>() {
      Iterator<Page> current = first.iterator();
      Long nextOffset = first.nextOffset;

      @Override
      public boolean hasNext() {
        while (!current.hasNext() && nextOffset != null) {
          System.out.println("Wow.. even more results than " + nextOffset);
          Response<Page> nextPage = wikipedia.resumeSearch(query, nextOffset);
          current = nextPage.iterator();
          nextOffset = nextPage.nextOffset;
        }
        return current.hasNext();
      }

      @Override
      public Page next() {
        return current.next();
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }

  public static interface Wikipedia {

    @RequestLine("GET /w/api.php?action=query&continue=&generator=search&prop=info&format=json&gsrsearch={search}")
    Response<Page> search(@Param("search") String search);

    @RequestLine("GET /w/api.php?action=query&continue=&generator=search&prop=info&format=json&gsrsearch={search}&gsroffset={offset}")
    Response<Page> resumeSearch(@Param("search") String search, @Param("offset") long offset);
  }

  static class Page {

    long id;
    String title;
  }

  public static class Response<X> extends ArrayList<X> {

    /**
     * when present, the position to resume the list.
     */
    Long nextOffset;
  }
}
