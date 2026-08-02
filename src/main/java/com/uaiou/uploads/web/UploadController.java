package com.uaiou.uploads.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.uploads.dto.ConfirmUploadResponse;
import com.uaiou.uploads.dto.CreateUploadRequest;
import com.uaiou.uploads.dto.CreateUploadResponse;
import com.uaiou.uploads.dto.UploadMetadataResponse;
import com.uaiou.uploads.service.UploadService;
import com.uaiou.users.Role;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/uploads.md} (T-05). */
@RestController
@RequestMapping("/uploads")
public class UploadController {

  private final UploadService uploadService;
  private final CurrentUserHolder currentUserHolder;

  public UploadController(UploadService uploadService, CurrentUserHolder currentUserHolder) {
    this.uploadService = uploadService;
    this.currentUserHolder = currentUserHolder;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreateUploadResponse create(@Valid @RequestBody CreateUploadRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    return uploadService.create(user.userId(), request);
  }

  @PutMapping("/{id}")
  public ConfirmUploadResponse confirm(@PathVariable UUID id) {
    AuthenticatedUser user = currentUserHolder.require();
    return uploadService.confirm(user.userId(), id);
  }

  @GetMapping("/{id}")
  public UploadMetadataResponse read(@PathVariable UUID id) {
    AuthenticatedUser user = currentUserHolder.require();
    return uploadService.readMetadata(user.userId(), user.role() == Role.ADMIN, id);
  }
}
