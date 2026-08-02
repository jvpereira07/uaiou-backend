package com.uaiou.uploads.repository;

import com.uaiou.uploads.entity.Upload;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UploadRepository extends JpaRepository<Upload, UUID> {}
