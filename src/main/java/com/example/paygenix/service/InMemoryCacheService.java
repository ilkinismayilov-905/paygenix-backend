package com.example.paygenix.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryCacheService implements CacheService {

    private static class Entry {
        String value;
        long expiresAtEpochMs;

        Entry(String value, long expiresAtEpochMs) {
            this.value = value;
            this.expiresAtEpochMs = expiresAtEpochMs;
        }
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void save(String key, String value, long ttlMs) {
        long expires = Instant.now().toEpochMilli() + ttlMs;
        store.put(key, new Entry(value, expires));
    }

    @Override
    public String getAndDelete(String key) {
        Entry e = store.remove(key);
        if (e == null) return null;
        if (Instant.now().toEpochMilli() > e.expiresAtEpochMs) return null;
        return e.value;
    }
}
