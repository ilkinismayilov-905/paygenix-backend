package com.example.paygenix.cache;

public final class CacheNames {
    public static final String EDIT_PROFILE_ALL = "editProfileAll";

    public static final String LEGAL_DOCUMENT_BY_TYPE = "legalDocument"; // renamed in T-121 so deploy drops entries cached before V14; publishing a version: SQL + DEL legalDocument::*
    private CacheNames() {}
}
