package store;

import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {
    private final ConcurrentHashMap<String, String> mp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ex_mp = new ConcurrentHashMap<>();

    // Plain SET — always overwrites the value and clears any existing TTL.
    public void set(String key, String value) {
        mp.put(key, value);
        ex_mp.remove(key);
    }

    // SET ... EX/PX — overwrites the value and sets a new expiry (epoch millis).
    public void setWithExpiry(String key, String value, long exp_time) {
        mp.put(key, value);
        ex_mp.put(key, exp_time);
    }

    // SET ... NX — only sets if key is missing/expired. Returns true if it set.
    public boolean setIfAbsent(String key, String value) {
        if (get(key) != null)
            return false;
        mp.put(key, value);
        ex_mp.remove(key);
        return true;
    }

    // SET ... XX — only sets if key exists and isn't expired. Returns true if it
    // set.
    public boolean setIfPresent(String key, String value) {
        if (get(key) == null)
            return false;
        mp.put(key, value);
        ex_mp.remove(key);
        return true;
    }

    // Returns the value, or null if missing/expired. Lazily evicts expired keys.
    public String get(String key) {
        if (ex_mp.containsKey(key) && System.currentTimeMillis() > ex_mp.get(key)) {
            mp.remove(key);
            ex_mp.remove(key);
            return null;
        }
        return mp.get(key);
    }
}