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
package feign.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dagger.Provides;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import java.util.Collections;
import java.util.Set;
import javax.inject.Singleton;

/**
 *
 *
 * <h3>Custom serializers/deserializers</h3>
 *
 * <br>
 * In order to specify custom json parsing, Jackson's {@code ObjectMapper} supports {@link
 * JsonSerializer serializers} and {@link JsonDeserializer deserializers}, which can be bundled
 * together in {@link Module modules}.
 *
 * <p><br>
 * Here's an example of adding a custom module.
 *
 * <p>
 *
 * <pre>
 * public class ObjectIdSerializer extends StdSerializer&lt;ObjectId&gt; {
 *     public ObjectIdSerializer() {
 *         super(ObjectId.class);
 *     }
 *
 *     &#064;Override
 *     public void serialize(ObjectId value, JsonGenerator jsonGenerator, SerializerProvider provider) throws IOException {
 *         jsonGenerator.writeString(value.toString());
 *     }
 * }
 *
 * public class ObjectIdDeserializer extends StdDeserializer&lt;ObjectId&gt; {
 *     public ObjectIdDeserializer() {
 *         super(ObjectId.class);
 *     }
 *
 *     &#064;Override
 *     public ObjectId deserialize(JsonParser jsonParser, DeserializationContext context) throws IOException {
 *         return ObjectId.massageToObjectId(jsonParser.getValueAsString());
 *     }
 * }
 *
 * public class ObjectIdModule extends SimpleModule {
 *     public ObjectIdModule() {
 *         // first deserializers
 *         addDeserializer(ObjectId.class, new ObjectIdDeserializer());
 *
 *         // then serializers:
 *         addSerializer(ObjectId.class, new ObjectIdSerializer());
 *     }
 * }
 *
 * &#064;Provides(type = Provides.Type.SET)
 * Module objectIdModule() {
 *     return new ObjectIdModule();
 * }
 * </pre>
 */
@dagger.Module(injects = Feign.class, addsTo = Feign.Defaults.class)
public final class JacksonModule {
  @Provides
  Encoder encoder(ObjectMapper mapper) {
    return new JacksonEncoder(mapper);
  }

  @Provides
  Decoder decoder(ObjectMapper mapper) {
    return new JacksonDecoder(mapper);
  }

  @Provides
  @Singleton
  ObjectMapper mapper(Set<Module> modules) {
    return new ObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .configure(SerializationFeature.INDENT_OUTPUT, true)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModules(modules);
  }

  @Provides(type = Provides.Type.SET_VALUES)
  Set<Module> noDefaultModules() {
    return Collections.emptySet();
  }
}
