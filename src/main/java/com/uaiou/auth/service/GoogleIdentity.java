package com.uaiou.auth.service;

public record GoogleIdentity(String googleId, String email, boolean emailVerified) {}
