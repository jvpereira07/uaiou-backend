package com.uaiou.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RegisterResponse(
    UUID id,
    Role role,
    UserStatus status,
    List<String> requiredDocuments,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
