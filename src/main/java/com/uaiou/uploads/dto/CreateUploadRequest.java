package com.uaiou.uploads.dto;

import com.uaiou.uploads.Purpose;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateUploadRequest(
    @NotNull Purpose purpose, @NotBlank String contentType, @NotNull @Positive Long sizeBytes) {}
