package com.example.paygenix.admin;

import java.util.List;

// ask for page + 1 while hasNext is true
public record AdminPage<T>(List<T> items, int page, int size, boolean hasNext) {
}
