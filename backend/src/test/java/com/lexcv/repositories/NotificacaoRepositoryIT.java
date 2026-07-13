package com.lexcv.repositories;

import com.lexcv.models.Notificacao;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Primeiro teste de integração com PostgreSQL real (Testcontainers) deste backend.
 * {@code NotificacaoRepository.buscarPorFiltros} é a primeira combinação nativeQuery=true +
 * Pageable do projeto, com o idioma {@code CAST(:param AS text/boolean) IS NULL} exigido pelo
 * Postgres para inferir o tipo de um bind nulo — um comportamento específico do motor que o H2
 * não reproduz com fidelidade suficiente (ver .planning/research/STACK.md).
 *
 * {@code @DataJpaTest} (não {@code @SpringBootTest}) contorna por construção o bloqueio
 * MINIO_ENDPOINT: esta fatia de contexto nunca instancia MinioConfig/SecurityConfig, logo os
 * respetivos placeholders de ambiente nunca são avaliados.
 *
 * {@code @AutoConfigureTestDatabase(replace = Replace.NONE)} é obrigatório a par de
 * {@code @ServiceConnection} — sem ele o {@code @DataJpaTest} troca silenciosamente a
 * DataSource por uma base embutida, ignorando o contentor Testcontainers.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class NotificacaoRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private NotificacaoRepository notificacaoRepository;

    @Autowired
    private EntityManager entityManager;

    /**
     * Persiste uma linha válida via o builder, variando sempre entidadeId para nunca colidir
     * com uk_notificacao_dedup (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria)
     * entre fixtures do mesmo teste.
     */
    private Notificacao persistir(UUID tenantId, UUID destinatarioId, String categoria, String entidadeId, boolean lida) {
        Notificacao notificacao = Notificacao.builder()
                .tenantId(tenantId)
                .destinatarioId(destinatarioId)
                .categoria(categoria)
                .entidadeTipo("processo")
                .entidadeId(entidadeId)
                .titulo("Título " + entidadeId)
                .mensagem("Mensagem " + entidadeId)
                .lida(lida)
                .build();
        return notificacaoRepository.save(notificacao);
    }

    @Test
    void buscarPorFiltros_escopaPorTenantEDestinatario_nuncaVazaOutroTenantOuOutroDestinatario() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID destinatarioA = UUID.randomUUID();
        UUID destinatarioB = UUID.randomUUID();

        persistir(tenantA, destinatarioA, "FASE_ENTRADA", "id-1", false);
        persistir(tenantA, destinatarioB, "FASE_ENTRADA", "id-2", false);
        persistir(tenantB, destinatarioA, "FASE_ENTRADA", "id-3", false);

        Page<Notificacao> page = notificacaoRepository.buscarPorFiltros(
                tenantA, destinatarioA, null, null, PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals(destinatarioA, page.getContent().get(0).getDestinatarioId());
        assertEquals(tenantA, page.getContent().get(0).getTenantId());
    }

    @Test
    void buscarPorFiltros_categoriaNula_devolveTodas_categoriaConcreta_filtraApenasEssa() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-10", false);
        persistir(tenantId, destinatarioId, "DOCUMENTO_NOVO", "id-11", false);

        // Bind nulo: prova CAST(:categoria AS text) IS NULL contra Postgres real.
        Page<Notificacao> todas = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, null, PageRequest.of(0, 10));
        assertEquals(2, todas.getTotalElements());

        Page<Notificacao> apenasFase = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, "FASE_ENTRADA", null, PageRequest.of(0, 10));
        assertEquals(1, apenasFase.getTotalElements());
        assertEquals("FASE_ENTRADA", apenasFase.getContent().get(0).getCategoria());
    }

    @Test
    void buscarPorFiltros_lidaNula_devolveTodas_lidaConcreta_filtraApenasEssaFlag() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-20", false);
        persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-21", true);

        // Bind nulo: prova CAST(:lida AS boolean) IS NULL contra Postgres real.
        Page<Notificacao> todas = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, null, PageRequest.of(0, 10));
        assertEquals(2, todas.getTotalElements());

        Page<Notificacao> naoLidas = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, false, PageRequest.of(0, 10));
        assertEquals(1, naoLidas.getTotalElements());
        assertFalse(naoLidas.getContent().get(0).getLida());

        Page<Notificacao> lidas = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, true, PageRequest.of(0, 10));
        assertEquals(1, lidas.getTotalElements());
        assertTrue(lidas.getContent().get(0).getLida());
    }

    @Test
    void buscarPorFiltros_paginacao_totalElementsCorretoETamanhoDePaginaRespeitado_ordemDescPorCreatedAt() {
        UUID tenantId = UUID.randomUUID();
        UUID destinatarioId = UUID.randomUUID();
        Notificacao n1 = persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-30", false);
        Notificacao n2 = persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-31", false);
        Notificacao n3 = persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-32", false);

        // WR-01 (Phase 91 code review): created_at é preenchido por @PrePersist com
        // LocalDateTime.now() e a coluna é updatable=false (a entidade não expõe setter), pelo
        // que não dá para pré-atribuir o valor antes do save(). Em vez de depender de
        // Thread.sleep() entre inserções -- um padrão de teste inerentemente instável, já que
        // duas inserções podem cair no mesmo milissegundo sob carga de CI/GC -- força-se aqui
        // timestamps estritamente crescentes via UPDATE nativo pós-persistência.
        entityManager.flush();
        LocalDateTime base = LocalDateTime.now();
        forcarCreatedAt(n1.getId(), base.minusSeconds(2));
        forcarCreatedAt(n2.getId(), base.minusSeconds(1));
        forcarCreatedAt(n3.getId(), base);
        // Descarta o 1º-level cache: sem isto, a query nativa abaixo devolveria as instâncias
        // já geridas (com os createdAt originais, quase simultâneos entre si) em vez dos valores
        // reais agora persistidos na BD.
        entityManager.clear();

        Page<Notificacao> page = notificacaoRepository.buscarPorFiltros(
                tenantId, destinatarioId, null, null, PageRequest.of(0, 2));

        assertEquals(3, page.getTotalElements());
        assertEquals(2, page.getContent().size());

        List<Notificacao> content = page.getContent();
        assertEquals(n3.getId(), content.get(0).getId());
        assertEquals(n2.getId(), content.get(1).getId());
        assertTrue(content.get(0).getCreatedAt().isAfter(content.get(1).getCreatedAt()));
    }

    private void forcarCreatedAt(UUID id, LocalDateTime createdAt) {
        entityManager.createNativeQuery("UPDATE t_notificacao SET created_at = :createdAt WHERE id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate();
    }
}
