package feign.example.wikipedia;

import com.google.gson.stream.JsonReader;
import feign.codec.Decoder;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;

abstract class ResponseDecoder<X> implements Decoder.TextStream<WikipediaExample.Response<X>> {

  /**
   * name of the key inside the {@code query} dict which holds the elements desired.  ex. {@code pages}.
   */
  protected abstract String query();

  /**
   * Parses the contents of a result object.
   * <p/>
   * <br>
   * ex. If {@link #query()} is {@code pages}, then this would parse the value of each key in the dict {@code pages}.
   * In the example below, this would first start at line {@code 3}.
   * <p/>
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
  public WikipediaExample.Response<X> decode(Reader ireader, Type type) throws IOException {
    WikipediaExample.Response<X> pages = new WikipediaExample.Response<X>();
    JsonReader reader = new JsonReader(ireader);
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
      } else if ("query-continue".equals(nextName)) {
        reader.beginObject();
        while (reader.hasNext()) {
          if ("search".equals(reader.nextName())) {
            reader.beginObject();
            while (reader.hasNext()) {
              if ("gsroffset".equals(reader.nextName())) {
                pages.nextOffset = reader.nextLong();
              }
            }
            reader.endObject();
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
    reader.close();
    return pages;
  }
}
