package com.uaiou.auth.service;

public record IssuedAccessToken(String token, long expiresInSeconds) {}
