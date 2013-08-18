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

import com.google.gson.stream.JsonReader;
import dagger.Module;
import dagger.Provides;
import feign.Feign;
import feign.Logger;
import feign.RequestLine;
import feign.codec.Decoder;
import feign.gson.GsonModule;

import javax.inject.Named;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;

import static dagger.Provides.Type.SET;

public class WikipediaExample {

  public static interface Wikipedia {
    @RequestLine("GET /w/api.php?action=query&generator=search&prop=info&format=json&gsrsearch={search}")
    Response<Page> search(@Named("search") String search);

    @RequestLine("GET /w/api.php?action=query&generator=search&prop=info&format=json&gsrsearch={search}&gsroffset={offset}")
    Response<Page> resumeSearch(@Named("search") String search, @Named("offset") long offset);
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

  public static void main(String... args) throws InterruptedException {
    Wikipedia wikipedia = Feign.create(Wikipedia.class, "http://en.wikipedia.org",
        new WikipediaDecoder(), new LogToStderr());

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
    if (first.nextOffset == null)
      return first.iterator();
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

  @Module(library = true, includes = GsonModule.class)
  static class WikipediaDecoder {

    /**
     * add to the set of Decoders one that handles {@code Response<Page>}.
     */
    @Provides(type = SET) Decoder pagesDecoder() {
      return new ResponseDecoder<Page>() {

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
    }
  }

  @Module(overrides = true, library = true)
  static class LogToStderr {

    @Provides Logger.Level loggingLevel() {
      return Logger.Level.BASIC;
    }

    @Provides Logger logger() {
      return new Logger.ErrorLogger();
    }
  }
}
