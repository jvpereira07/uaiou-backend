package com.uaiou.uploads.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uaiou.shared.pagination.LinkRef;
import com.uaiou.uploads.Purpose;
import com.uaiou.uploads.UploadStatus;
import java.util.Map;
import java.util.UUID;

/**
 * RF-05.5: {@code fileUrl} é gerado a cada chamada, nunca persistido — nulo quando o upload ainda
 * não está {@code ready}.
 */
public record UploadMetadataResponse(
    UUID id,
    UploadStatus status,
    Purpose purpose,
    String contentType,
    long sizeBytes,
    String fileUrl,
    @JsonProperty("_links") Map<String, LinkRef> links) {}
