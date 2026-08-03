package com.uaiou.notifications.dto;

import java.time.Instant;
import java.util.UUID;

public record DeviceResponse(UUID id, String platform, String appVersion, Instant lastUsedAt) {}
