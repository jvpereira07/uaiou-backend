package com.uaiou.notifications.repository;

import com.uaiou.notifications.entity.PreferenciaNotificacao;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferenciaNotificacaoRepository
    extends JpaRepository<PreferenciaNotificacao, UUID> {}
