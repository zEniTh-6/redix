package store;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class KeyValueStore {
    private final ConcurrentHashMap<String, String> mp = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> ex_mp = new ConcurrentHashMap<>();

    public void set(String key, String value) {
        mp.put(key, value);
        ex_mp.remove(key);
    }

    public void setWithExpiry(String key, String value, long exp_time) {
        mp.put(key, value);
        ex_mp.put(key, exp_time);
    }

    public boolean setIfAbsent(String key, String value) {
        if (GET(key) != null)
            return false;
        mp.put(key, value);
        ex_mp.remove(key);
        return true;
    }

    public boolean setIfPresent(String key, String value) {
        if (GET(key) == null)
            return false;
        mp.put(key, value);
        ex_mp.remove(key);
        return true;
    }

    public String GET(String key) {
        if (ex_mp.containsKey(key) && System.currentTimeMillis() > ex_mp.get(key)) {
            mp.remove(key);
            ex_mp.remove(key);
            return null;
        }
        return mp.get(key);
    }

    public int del(String key) {
        int rm = 0;
        if (GET(key) != null) {
            mp.remove(key);
            ex_mp.remove(key);
            rm++;
        }
        return rm;
    }

    public int exists(String key) {
        int exist = 0;
        if (GET(key) != null)
            exist = 1;
        return exist;
    }

    public boolean type(String key) {
        boolean str = false;
        if (GET(key) != null)
            str = true;
        return str;
    }
}