package com.uaiou.auth.dto;

import com.uaiou.users.Role;
import com.uaiou.users.UserStatus;
import java.util.UUID;

public record SessionUser(UUID id, Role role, UserStatus status, String displayName) {}
