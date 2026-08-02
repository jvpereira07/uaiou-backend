package com.uaiou.uploads.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.uploads.UploadStatus;
import java.util.Map;
import java.util.UUID;

public record ConfirmUploadResponse(
    UUID id,
    UploadStatus status,
    String fileUrl,
    String checksum,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
