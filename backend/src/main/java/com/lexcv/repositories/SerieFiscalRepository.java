package com.lexcv.repositories;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.SerieFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 133 (CFG-05): séries de numeração fiscal; todo o finder recebe tenantId.
 *
 * <ul>
 *   <li>{@link #criarSeNaoExiste} é a ÚNICA forma de criar uma série -- nunca findFirst + save,
 *       que corre o risco de duas transações concorrentes criarem a mesma série.</li>
 *   <li>{@link #bloquear} tem de ser chamado dentro da transação do chamador: o lock
 *       {@code PESSIMISTIC_WRITE} ({@code SELECT ... FOR UPDATE}) dura até ao commit/rollback
 *       (idioma de {@code ParecerSolicitacaoRepository.findByIdForUpdate}).</li>
 *   <li>{@link #definirLockTimeoutLocal} equivale a {@code SET LOCAL lock_timeout = '5s'}: o hint
 *       JPA de lock timeout não é fiável no PostgreSQL (ARCHITECTURE §4.2).</li>
 *   <li>{@code existsByTenantIdAndUltimoNumeroGreaterThan(tenantId, 0L)} é o substituto, na
 *       Phase 133, de "já foi emitido um documento fiscal": na Phase 134 emitir um documento
 *       implica {@code ultimo_numero > 0} na mesma transação.</li>
 * </ul>
 */
@Repository
public interface SerieFiscalRepository extends JpaRepository<SerieFiscal, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SerieFiscal s where s.tenantId = :tenantId and s.tipoDocumento = :tipo "
            + "and s.ano = :ano and s.ambiente = :ambiente")
    Optional<SerieFiscal> bloquear(@Param("tenantId") UUID tenantId,
                                   @Param("tipo") TipoDocumentoFiscal tipo,
                                   @Param("ano") Integer ano,
                                   @Param("ambiente") AmbienteFiscal ambiente);

    @Modifying
    @Query(nativeQuery = true, value = "INSERT INTO t_serie_fiscal "
            + "(id, tenant_id, tipo_documento, ano, ambiente, codigo, ultimo_numero, created_at) "
            + "VALUES (:id, :tenantId, :tipo, :ano, :ambiente, :codigo, 0, now()) "
            + "ON CONFLICT (tenant_id, tipo_documento, ano, ambiente) DO NOTHING")
    int criarSeNaoExiste(@Param("id") UUID id,
                         @Param("tenantId") UUID tenantId,
                         @Param("tipo") String tipo,
                         @Param("ano") Integer ano,
                         @Param("ambiente") String ambiente,
                         @Param("codigo") String codigo);

    @Query(nativeQuery = true, value = "SELECT set_config('lock_timeout', '5s', true)")
    String definirLockTimeoutLocal();

    List<SerieFiscal> findByTenantIdOrderByAnoDescTipoDocumentoAsc(UUID tenantId);

    boolean existsByTenantIdAndUltimoNumeroGreaterThan(UUID tenantId, Long numero);
}
