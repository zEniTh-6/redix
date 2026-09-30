package store;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;

public class ListStore {
    private final ConcurrentHashMap<String, LinkedList<String>> list_mp = new ConcurrentHashMap<>();

    public int rpush(String l_name, List<String> values) {
        LinkedList<String> list = list_mp.computeIfAbsent(l_name, k -> new LinkedList<>());
        int size;
        synchronized (list) {
            for (int i = 0; i < values.size(); i++) {
                list.addLast(values.get(i));
            }
            size = list.size();
        }
        return size;
    }

    public int lpush(String l_name, List<String> values) {
        LinkedList<String> list = list_mp.computeIfAbsent(l_name, k -> new LinkedList<>());
        int size;
        synchronized (list) {
            for (int i = 0; i < values.size(); i++) {
                list.addFirst(values.get(i));
            }
            size = list.size();
        }
        return size;
    }

    public int llen(String l_name) {
        List<String> list = list_mp.get(l_name);
        return list != null ? list.size() : 0;
    }

    public List<String> lrange(String l_name, int st, int end) {
        List<String> value = list_mp.get(l_name);
        if (value == null)
            return new ArrayList<>();

        synchronized (value) {
            if (st < 0)
                st = value.size() + st;
            if (end < 0)
                end = value.size() + end;
            if (st < 0)
                st = 0;

            if (st > value.size() || st > end)
                return new ArrayList<>();

            if (end >= value.size())
                end = value.size() - 1;

            return new ArrayList<>(value.subList(st, end + 1));
        }
    }

    public List<String> lpop(String l_name, int num) {
        LinkedList<String> list = list_mp.get(l_name);
        List<String> popped = new ArrayList<>();
        if (list == null || num <= 0)
            return popped;

        synchronized (list) {
            int actual = Math.min(num, list.size());
            while (actual != 0) {
                popped.add(list.removeFirst());
                actual--;
            }
        }
        return popped;
    }

    public List<String> rpop(String l_name, int num) {
        LinkedList<String> list = list_mp.get(l_name);
        List<String> popped = new ArrayList<>();
        if (list == null || num <= 0)
            return popped;

        synchronized (list) {
            int actual = Math.min(num, list.size());
            while (actual != 0) {
                popped.add(list.removeLast());
                actual--;
            }
        }
        return popped;
    }

    public int del(String key) {
        int rm = 0;
        if (list_mp.containsKey(key)) {
            list_mp.remove(key);
            rm++;
        }
        return rm;
    }

    public int exists(String key) {
        int exist = 0;
        if (list_mp.containsKey(key))
            exist = 1;
        return exist;
    }

    public boolean type(String key) {
        boolean list = false;
        if (list_mp.containsKey(key))
            list = true;
        return list;
    }

    public Set<String> keys() {
        return new HashSet<>(list_mp.keySet());
    }
}