package com.uaiou.users.web;

import com.uaiou.auth.web.AuthenticatedUser;
import com.uaiou.auth.web.CurrentUserHolder;
import com.uaiou.users.dto.DocumentSummary;
import com.uaiou.users.dto.DocumentsResponse;
import com.uaiou.users.dto.SubmitDocumentRequest;
import com.uaiou.users.service.DocumentoCadastroService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints de {@code system-documentation/api/usuarios.md} cobertos por T-06. */
@RestController
@RequestMapping("/me/documents")
public class DocumentsController {

  private final DocumentoCadastroService documentoCadastroService;
  private final CurrentUserHolder currentUserHolder;

  public DocumentsController(
      DocumentoCadastroService documentoCadastroService, CurrentUserHolder currentUserHolder) {
    this.documentoCadastroService = documentoCadastroService;
    this.currentUserHolder = currentUserHolder;
  }

  @GetMapping
  public DocumentsResponse list() {
    AuthenticatedUser user = currentUserHolder.require();
    return documentoCadastroService.list(user.userId());
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public DocumentSummary submit(@Valid @RequestBody SubmitDocumentRequest request) {
    AuthenticatedUser user = currentUserHolder.require();
    return documentoCadastroService.submit(user.userId(), request);
  }
}
