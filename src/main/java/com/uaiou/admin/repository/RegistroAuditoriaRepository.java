package com.uaiou.admin.repository;

import com.uaiou.admin.entity.RegistroAuditoria;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/**
 * RF-07.2: expõe só {@code save} e consulta (via {@link JpaSpecificationExecutor}) — estender
 * {@code Repository}, não {@code JpaRepository}, é o que garante que nenhum {@code delete}/{@code
 * update} chega a existir nesta interface. Critério de aceite 8 (nenhuma rota altera ou apaga
 * {@code registro_auditoria}) começa aqui: não dá pra expor o que a interface não tem.
 */
public interface RegistroAuditoriaRepository
    extends Repository<RegistroAuditoria, UUID>, JpaSpecificationExecutor<RegistroAuditoria> {

  RegistroAuditoria save(RegistroAuditoria registro);
}
