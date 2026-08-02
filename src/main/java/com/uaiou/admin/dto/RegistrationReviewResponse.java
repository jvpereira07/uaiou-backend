package com.uaiou.admin.dto;

import com.uaiou.users.UserStatus;
import java.util.UUID;

public record RegistrationReviewResponse(UUID userId, UserStatus status) {}
