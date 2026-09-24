package com.lexcv.repositories;

import com.lexcv.models.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    /**
     * Lookup idempotente da tenant reservada de plataforma (nome literal {@code "LexCV"}),
     * usado por {@code DatabaseSeeder} para garantir find-or-create sem duplicar a tenant
     * em arranques sucessivos da aplicação (Phase 119, PROV-01).
     *
     * <p><strong>WR-01 (119-REVIEW.md):</strong> deliberadamente {@code findFirstByNome} (que
     * devolve o 1º resultado, via {@code LIMIT 1}), não {@code findByNome} (que lançaria
     * {@code IncorrectResultSizeDataAccessException} perante mais de uma linha). Não há
     * constraint {@code unique} em {@code t_tenant.nome}, e {@code seedTenantPlataforma()} corre,
     * sem lock, em todo o arranque -- um arranque concorrente de >1 instância contra a mesma base
     * de dados vazia pode inserir duas linhas "LexCV". Usar {@code findFirst} garante que essa
     * corrida transitória nunca se transforma num crash-loop permanente em todos os arranques
     * seguintes; apenas ignora a linha extra.
     */
    Optional<Tenant> findFirstByNome(String nome);
}
