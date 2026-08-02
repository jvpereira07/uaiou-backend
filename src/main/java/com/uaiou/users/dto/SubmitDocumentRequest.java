package com.uaiou.users.dto;

import com.uaiou.uploads.Purpose;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitDocumentRequest(@NotNull Purpose type, @NotNull UUID uploadId) {}
