package com.lexcv.repositories;

import com.lexcv.models.PagamentoSubscricao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PagamentoSubscricaoRepository extends JpaRepository<PagamentoSubscricao, UUID> {

    List<PagamentoSubscricao> findByAdquirenteTenantId(UUID adquirenteTenantId);

    Page<PagamentoSubscricao> findByAdquirenteTenantId(UUID adquirenteTenantId, Pageable pageable);

    Optional<PagamentoSubscricao> findByIdAndAdquirenteTenantId(UUID id, UUID adquirenteTenantId);
}
