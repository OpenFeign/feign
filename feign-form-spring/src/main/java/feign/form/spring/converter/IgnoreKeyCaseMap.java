package feign.form.spring.converter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A Map<String, String> implementation that normalizes the key to UPPER CASE, so that value
 * retrieval via the key is case insensitive.
 */
final class IgnoreKeyCaseMap implements Map<String, String> {
  private final HashMap<String, String> hashMap = new HashMap<String, String>();

  @Override
  public int size() {
    return hashMap.size();
  }

  @Override
  public boolean isEmpty() {
    return hashMap.isEmpty();
  }

  @Override
  public boolean containsKey(final Object key) {
    return hashMap.containsKey(normalizeKey(key));
  }

  @Override
  public boolean containsValue(final Object value) {
    return hashMap.containsValue(value);
  }

  @Override
  public String get(final Object key) {
    return hashMap.get(normalizeKey(key));
  }

  @Override
  public String put(final String key, final String value) {
    return hashMap.put(normalizeKey(key), value);
  }

  @Override
  public String remove(final Object key) {
    return hashMap.remove(normalizeKey(key));
  }

  @Override
  public void putAll(final Map<? extends String, ? extends String> m) {
    for (final Map.Entry<? extends String, ? extends String> entry : m.entrySet()) {
      put(entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void clear() {
    hashMap.clear();
  }

  @Override
  public Set<String> keySet() {
    return hashMap.keySet();
  }

  @Override
  public Collection<String> values() {
    return hashMap.values();
  }

  @Override
  public Set<Entry<String, String>> entrySet() {
    return hashMap.entrySet();
  }

  private static String normalizeKey(final Object key) {
    return key != null ? key.toString().toUpperCase() : null;
  }
}
