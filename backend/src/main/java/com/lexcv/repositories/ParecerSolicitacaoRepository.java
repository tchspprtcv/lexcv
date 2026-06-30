package com.lexcv.repositories;

import com.lexcv.models.ParecerSolicitacao;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ParecerSolicitacaoRepository extends JpaRepository<ParecerSolicitacao, UUID> {
    List<ParecerSolicitacao> findByTenantId(UUID tenantId);
}
