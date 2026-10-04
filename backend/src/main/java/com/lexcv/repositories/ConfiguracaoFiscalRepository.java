package com.lexcv.repositories;

import com.lexcv.models.ConfiguracaoFiscal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** Phase 133 (CFG-01): configuração fiscal 1:1 com o tenant; todo o finder recebe tenantId. */
@Repository
public interface ConfiguracaoFiscalRepository extends JpaRepository<ConfiguracaoFiscal, UUID> {

    /** Só leitura (GET). As mutações usam {@link #bloquearPorTenant}. */
    Optional<ConfiguracaoFiscal> findByTenantId(UUID tenantId);

    /**
     * {@code SELECT ... FOR UPDATE} da configuração do tenant (CR-01 da revisão da Phase 133):
     * todas as mutações da configuração fiscal (guardar, ativar, desativar, email) leem a linha
     * com este lock, para que duas mutações concorrentes se serializem em vez de uma reescrever a
     * linha inteira a partir de uma cópia desatualizada. Tem de ser chamado dentro da transação
     * do chamador; o lock dura até ao commit/rollback.
     *
     * <p><b>Ordem de locks:</b> configuração fiscal PRIMEIRO, série fiscal
     * ({@link SerieFiscalRepository#bloquear}) por último. A emissão da Phase 134 tem de tomar
     * este lock antes do lock da série, para que "já foi emitido um documento" (desativar, NIF
     * bloqueado) não possa mudar entre a verificação e o commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConfiguracaoFiscal c where c.tenantId = :tenantId")
    Optional<ConfiguracaoFiscal> bloquearPorTenant(@Param("tenantId") UUID tenantId);

    /**
     * Phase 134 (WR-02): só o valor de {@code ativa}, como escalar. O controlador decide se
     * delega na emissão fiscal sem pôr uma instância de ConfiguracaoFiscal no contexto de
     * persistência (OSIV) antes de {@link #bloquearPorTenant}, que tem de continuar a ser a
     * primeira leitura da linha (ordem R-01: configuração → cliente → processo → conta corrente
     * → série). Vazio quando o tenant não tem configuração.
     */
    @Query("select c.ativa from ConfiguracaoFiscal c where c.tenantId = :tenantId")
    Optional<Boolean> ativaPorTenant(@Param("tenantId") UUID tenantId);
}
