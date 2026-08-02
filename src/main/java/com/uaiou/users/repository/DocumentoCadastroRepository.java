package com.uaiou.users.repository;

import com.uaiou.users.entity.DocumentoCadastro;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentoCadastroRepository extends JpaRepository<DocumentoCadastro, UUID> {

  boolean existsByUploadId(UUID uploadId);
}
