package net.kibblelands.patcher;

import java.util.*;

/**
 * @author Fox2Code
 * This map put alls modifications to a secondary Map
 * The goal is to get all modifications in a separate Map to apply them later
 */
@SuppressWarnings("SuspiciousMethodCalls") /* Intellij IDEA think hes smart by saying I code like shit but no */
public class PatchMap<K,V> extends AbstractMap<K,V> implements Map<K,V> {
    private final Map<K,V> orig, patch;
    private final TransformSet transformSet;

    public PatchMap(Map<K,V> orig,Map<K,V> patch) {
        this.orig = orig;
        this.patch = patch;
        this.transformSet = new TransformSet();
    }

    public Map<K, V> getOrigMap() {
        return orig;
    }

    public Map<K, V> getPatchMap() {
        return patch;
    }

    @Override
    public Set<Entry<K, V>> entrySet() {
        return this.transformSet;
    }

    @Override
    public V get(Object key) {
        return this.patch.getOrDefault(key, orig.get(key));
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        if (this.patch.containsKey(key)) {
            V value = this.patch.get(key);
            if (value == null) {
                return defaultValue;
            } else {
                return value;
            }
        } else {
            return this.orig.getOrDefault(key, defaultValue);
        }
    }

    @Override
    public V put(K key, V value) {
        return this.patch.put(key, value);
    }

    @Override
    public V remove(Object key) {
        //noinspection unchecked
        return this.patch.put((K) key, null);
    }

    @Override
    public boolean containsKey(Object key) {
        return this.orig.containsKey(key);
    }

    @Override
    public int size() {
        return this.orig.size();
    }

    @Override
    public boolean isEmpty() {
        return this.orig.isEmpty();
    }

    private class TransformSet extends AbstractSet<Entry<K, V>> {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            Iterator<Entry<K, V>> iterator = orig.entrySet().iterator();
            return new Iterator<Entry<K, V>>() {
                EntryPatch latest;

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Entry<K, V> next() {
                    return latest = new EntryPatch(iterator.next());
                }

                @Override
                public void remove() {
                    latest.setValue(null);
                }
            };
        }

        @Override
        public int size() {
            return orig.size();
        }

        @Override
        public boolean isEmpty() {
            return orig.isEmpty();
        }
    }

    private class EntryPatch implements Entry<K, V> {
        private final Entry<K, V> origEntry;
        private V cachedValue;
        private boolean removed;
        private EntryPatch(Entry<K, V> origEntry) {
            this.origEntry = origEntry;
        }

        @Override
        public K getKey() {
            return origEntry.getKey();
        }

        @Override
        public V getValue() {
            if (cachedValue == null) {
                cachedValue = get(origEntry.getKey());
            }
            return cachedValue;
        }

        @Override
        public V setValue(V value) {
            V oldValue;
            if (cachedValue == null && !removed) {
                oldValue = get(origEntry.getKey());
            } else {
                oldValue = cachedValue;
            }
            removed = value != null;
            cachedValue = value;
            patch.put(origEntry.getKey(), value);
            return oldValue;
        }

        public int hashCode() {
            K key = origEntry.getKey();
            if (cachedValue == null) {
                cachedValue = get(origEntry.getKey());
            }
            int keyHash = (key==null ? 0 : key.hashCode());
            int valueHash = (cachedValue==null ? 0 : cachedValue.hashCode());
            return keyHash ^ valueHash;
        }

        public String toString() {
            if (cachedValue == null) {
                cachedValue = get(origEntry.getKey());
            }
            return origEntry.getKey() + "=" + cachedValue;
        }
    }
}
