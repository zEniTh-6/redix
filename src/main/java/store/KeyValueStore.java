package store;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;

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

    public int setExpiry(String key, long sec) {
        int result = 0;
        long exp_time = System.currentTimeMillis() + sec * 1000;
        if (mp.containsKey(key)) {
            ex_mp.put(key, exp_time);
            result = 1;
        }
        return result;
    }

    public long getTime(String key) {
        long time = 0;
        long curr_time = System.currentTimeMillis();
        if (!ex_mp.containsKey(key)) {
            time = -1;
        } else {
            if (ex_mp.containsKey(key) && curr_time < ex_mp.get(key)) {
                time = (ex_mp.get(key) - curr_time) / 1000;
            } else if (ex_mp.containsKey(key) && curr_time >= ex_mp.get(key)) {
                mp.remove(key);
                ex_mp.remove(key);
                time = -2;
            }
        }
        return time;
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

    public int persist(String Key) {
        int result = 0;
        if (ex_mp.containsKey(Key)) {
            ex_mp.remove(Key);
            result = 1;
        }
        return result;
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

    public Set<String> keys() {
        Set<String> result = new HashSet<>();
        for (String key : mp.keySet()) {
            if (GET(key) != null) {
                result.add(key);
            }
        }
        return result;
    }
}