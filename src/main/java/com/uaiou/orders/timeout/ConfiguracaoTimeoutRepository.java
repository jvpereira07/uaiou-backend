package com.uaiou.orders.timeout;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracaoTimeoutRepository extends JpaRepository<ConfiguracaoTimeout, String> {

  List<ConfiguracaoTimeout> findByAtivoTrue();
}
