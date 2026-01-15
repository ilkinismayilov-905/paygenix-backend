package com.example.paygenix.service;

public interface CacheService {
    void save(String key, String value, long ttlMs);
    String getAndDelete(String key);
}
