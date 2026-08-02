package com.uaiou.uploads.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.uploads.UploadStatus;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CreateUploadResponse(
    UUID id,
    UploadStatus status,
    String uploadUrl,
    Instant expiresAt,
    long maxSizeBytes,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
