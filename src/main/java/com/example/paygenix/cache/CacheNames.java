package com.example.paygenix.cache;

public final class CacheNames {
    public static final String USER_BY_ID = "userById";
    public static final String REFRESH_TOKEN_BY_USER_ID = "refreshTokenByUserId";
    public static final String REFRESH_TOKEN_BY_TOKEN_HASH = "refreshTokenByTokenHash";

    public static final String CARD_BY_ID = "cardById";
    public static final String CARD_BY_NUMBER = "cardByNumber";
    public static final String CARD_ALL = "cardAll";

    public static final String EDIT_PROFILE_BY_ID = "editProfileById";
    public static final String EDIT_PROFILE_BY_EMAIL = "editProfileByEmail";
    public static final String EDIT_PROFILE_ALL = "editProfileAll";

    public static final String LEGAL_DOCUMENT_BY_TYPE = "legalDocumentByType";
    private CacheNames() {}
}
