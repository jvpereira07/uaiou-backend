package com.uaiou.uploads.repository;

import com.uaiou.uploads.UploadStatus;
import com.uaiou.uploads.entity.Upload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UploadRepository extends JpaRepository<Upload, UUID> {

  /**
   * RF-05.7: candidatos à coleta de órfãos — nunca inclui {@code ready}, que é vida útil normal do
   * objeto.
   */
  @Query("select u from Upload u where u.status = :status and u.expiraEm < :now")
  List<Upload> findExpired(@Param("status") UploadStatus status, @Param("now") Instant now);
}
