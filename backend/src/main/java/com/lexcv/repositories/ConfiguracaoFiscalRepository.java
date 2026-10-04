package com.lexcv.repositories;

import com.lexcv.models.ConfiguracaoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Phase 133 (CFG-01): configuração fiscal 1:1 com o tenant; todo o finder recebe tenantId. */
@Repository
public interface ConfiguracaoFiscalRepository extends JpaRepository<ConfiguracaoFiscal, UUID> {

    Optional<ConfiguracaoFiscal> findByTenantId(UUID tenantId);

    // PITFALLS P-25: o mesmo NIF não pode ser emitente em dois escritórios. Verificação
    // deliberadamente cross-tenant (devolve só um booleano, nunca dados de outro tenant).
    boolean existsByNifAndTenantIdNot(String nif, UUID tenantId);
}
