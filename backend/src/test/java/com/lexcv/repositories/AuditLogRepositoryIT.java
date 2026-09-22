package com.lexcv.repositories;

import com.lexcv.models.AuditLog;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 128 (AUDT-03), Plano 07: prova ao nivel SQL, contra PostgreSQL real (Testcontainers),
 * de que {@link AuditLogRepository#buscarEventosRbac} isola por tenant, exclui eventos que nao
 * sao de RBAC (processo/parecer), e filtra correctamente por utilizador alvo e por papel (id),
 * incluindo o caso de um mesmo id de utilizador/papel a existir em dois tenants diferentes --
 * complementa {@code AuditoriaRbacControllerTest} (que prova o gate e o isolamento ao nivel de
 * controller com repositorios mockados) com a prova de que a propria query nativa aplica a
 * fronteira {@code tenant_id} correctamente, nao apenas o codigo Java a volta dela.
 *
 * <p>Segue exatamente o idioma de {@link NotificacaoRepositoryIT} ({@code @DataJpaTest} +
 * {@code @AutoConfigureTestDatabase(replace = NONE)} + {@code @Testcontainers} +
 * {@code @ServiceConnection}), a mesma infraestrutura ja usada por
 * {@link UserRepositoryContagemPapeisIT}.
 *
 * <p><b>Nao corre localmente.</b> Este ambiente de execucao tem um bloqueio conhecido de Docker
 * (canal npipe indisponivel no Windows local usado para este plano) que impede o Testcontainers
 * de arrancar um contentor. Esta classe compila (prova estrutural verificada por
 * {@code mvn test-compile}) mas nao foi executada neste ambiente -- corre onde o Testcontainers
 * tiver Docker disponivel (CI, ou uma maquina de desenvolvimento com Docker Desktop/Engine a
 * correr). Ver 128-07-SUMMARY.md para o registo explicito desta limitacao.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AuditLogRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AuditLogRepository auditLogRepository;

    private AuditLog papelCriado(UUID tenantId, UUID papelId, String papelNome) {
        String detalhe = "{\"autorNome\":\"Maria Silva\",\"papelId\":\"" + papelId
                + "\",\"papelNome\":\"" + papelNome + "\"}";
        return auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .acao("papel_criar")
                .entidadeTipo("papel_escritorio")
                .entidadeId(papelId.toString())
                .autorId(UUID.randomUUID())
                .detalhe(detalhe)
                .build());
    }

    private AuditLog atribuicao(UUID tenantId, String acao, UUID alvoId, UUID papelId, String papelNome) {
        String detalhe = "{\"autorNome\":\"Maria Silva\",\"alvoNome\":\"Alvo de Teste\",\"papelId\":\""
                + papelId + "\",\"papelNome\":\"" + papelNome + "\"}";
        return auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .acao(acao)
                .entidadeTipo("atribuicao_papel")
                .entidadeId(alvoId.toString())
                .autorId(UUID.randomUUID())
                .detalhe(detalhe)
                .build());
    }

    private AuditLog eventoDeProcesso(UUID tenantId, UUID processoId) {
        return auditLogRepository.save(AuditLog.builder()
                .tenantId(tenantId)
                .acao("transicao_estado")
                .entidadeTipo("processo")
                .entidadeId(processoId.toString())
                .autorId(UUID.randomUUID())
                .detalhe(null)
                .build());
    }

    @Test
    void buscarEventosRbac_isolaPorTenantEExcluiEventosDeProcesso_newestFirst() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        // Ordem de insercao deliberada: id crescente == ordem de insercao (IDENTITY), o que
        // desempata ORDER BY timestamp DESC, id DESC de forma deterministica quando os
        // timestamps caem no mesmo instante (comum em insercoes rapidas dentro do mesmo teste).
        AuditLog criado = papelCriado(tenantA, r1, "Recepcao");
        AuditLog atribuido = atribuicao(tenantA, "papel_atribuir", u1, r1, "Recepcao");
        AuditLog retirado = atribuicao(tenantA, "papel_retirar", u2, r2, "Financeiro");
        eventoDeProcesso(tenantA, UUID.randomUUID());
        // Ruido de outro tenant, com os MESMOS ids de utilizador e papel de tenantA -- prova que
        // a isolacao e por tenant_id, nunca pelos ids referenciados.
        atribuicao(tenantB, "papel_atribuir", u1, r1, "Recepcao");

        Page<AuditLog> pagina = auditLogRepository.buscarEventosRbac(tenantA, null, null, PageRequest.of(0, 20));

        assertEquals(3, pagina.getTotalElements());
        List<AuditLog> conteudo = pagina.getContent();
        assertEquals(3, conteudo.size());
        // newest first: o ultimo evento gravado (maior id) aparece primeiro.
        assertEquals(retirado.getId(), conteudo.get(0).getId());
        assertEquals(atribuido.getId(), conteudo.get(1).getId());
        assertEquals(criado.getId(), conteudo.get(2).getId());
    }

    @Test
    void buscarEventosRbac_filtraPorUtilizadorAlvo_devolveApenasAsSuasAtribuicoes() {
        UUID tenantA = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        papelCriado(tenantA, r1, "Recepcao");
        AuditLog atribuidoU1 = atribuicao(tenantA, "papel_atribuir", u1, r1, "Recepcao");
        atribuicao(tenantA, "papel_retirar", u2, r2, "Financeiro");

        Page<AuditLog> pagina = auditLogRepository.buscarEventosRbac(tenantA, u1.toString(), null, PageRequest.of(0, 20));

        assertEquals(1, pagina.getTotalElements());
        assertEquals(atribuidoU1.getId(), pagina.getContent().get(0).getId());
    }

    @Test
    void buscarEventosRbac_filtraPorPapel_devolvePapelEASuaAtribuicao() {
        UUID tenantA = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        AuditLog criadoR1 = papelCriado(tenantA, r1, "Recepcao");
        AuditLog atribuidoR1 = atribuicao(tenantA, "papel_atribuir", u1, r1, "Recepcao");
        atribuicao(tenantA, "papel_retirar", u2, r2, "Financeiro");

        Page<AuditLog> pagina = auditLogRepository.buscarEventosRbac(tenantA, null, r1.toString(), PageRequest.of(0, 20));

        assertEquals(2, pagina.getTotalElements());
        List<Long> ids = pagina.getContent().stream().map(AuditLog::getId).toList();
        assertEquals(List.of(atribuidoR1.getId(), criadoR1.getId()), ids);
    }

    @Test
    void buscarEventosRbac_tenantBSemFiltros_devolveApenasOSeuUnicoEvento() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();

        papelCriado(tenantA, r1, "Recepcao");
        AuditLog eventoTenantB = atribuicao(tenantB, "papel_atribuir", u1, r1, "Recepcao");

        Page<AuditLog> pagina = auditLogRepository.buscarEventosRbac(tenantB, null, null, PageRequest.of(0, 20));

        assertEquals(1, pagina.getTotalElements());
        assertEquals(eventoTenantB.getId(), pagina.getContent().get(0).getId());
        assertEquals(tenantB, pagina.getContent().get(0).getTenantId());
    }

    @Test
    void buscarEventosRbac_tamanhoDePaginaDoisSobreTresLinhas_devolveDoisTotalPages() {
        UUID tenantA = UUID.randomUUID();
        UUID r1 = UUID.randomUUID();
        UUID r2 = UUID.randomUUID();
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        papelCriado(tenantA, r1, "Recepcao");
        atribuicao(tenantA, "papel_atribuir", u1, r1, "Recepcao");
        atribuicao(tenantA, "papel_retirar", u2, r2, "Financeiro");

        Page<AuditLog> pagina = auditLogRepository.buscarEventosRbac(tenantA, null, null, PageRequest.of(0, 2));

        assertEquals(3, pagina.getTotalElements());
        assertEquals(2, pagina.getTotalPages());
        assertEquals(2, pagina.getContent().size());
    }
}
