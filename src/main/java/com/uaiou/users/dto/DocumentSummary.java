package com.uaiou.users.dto;

import com.uaiou.uploads.Purpose;
import com.uaiou.users.DocumentApprovalStatus;
import java.time.Instant;
import java.util.UUID;

public record DocumentSummary(
    UUID id,
    Purpose type,
    DocumentApprovalStatus status,
    String rejectionReason,
    Instant createdAt) {}
