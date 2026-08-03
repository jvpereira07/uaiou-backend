package com.uaiou.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** {@code POST /me/devices} (api/notificacoes.md). */
public record RegisterDeviceRequest(
    @NotBlank @Pattern(regexp = "android|ios|web", message = "must be android, ios or web")
        String platform,
    @NotBlank @Size(max = 255) String pushToken,
    @Size(max = 20) String appVersion) {}
