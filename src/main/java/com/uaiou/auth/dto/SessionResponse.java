package com.uaiou.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import java.util.Map;

public record SessionResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    SessionUser user,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
