package com.lexcv.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.AuditoriaRbacEntradaDto;
import com.lexcv.models.AuditLog;
import com.lexcv.models.Permission;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 128 (AUDT-01/AUDT-02), Plan 02: prova comportamental de {@link AuditoriaRbacService} --
 * cada bullet de comportamento do plano tem pelo menos um teste dedicado aqui, incluindo a regra
 * de privacidade (Decisao 2 revista): nenhum {@code detalhe} gravado pode conter o email do
 * autor ou do alvo, provado com um email distintivo que nao aparece em nenhum outro lugar.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaRbacServiceTest {

    private static final String EMAIL_DISTINTIVO = "nao-deve-aparecer@exemplo.cv";

    @Mock
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Construido em @BeforeEach, nao como inicializador de campo: o inicializador de campo
    // corre durante a construcao da instancia de teste, ANTES de o MockitoExtension injectar
    // @Mock auditLogRepository (que so acontece em postProcessTestInstance) -- construir aqui
    // capturaria um auditLogRepository ainda nulo.
    private AuditoriaRbacService service;

    @BeforeEach
    void configurar() {
        service = new AuditoriaRbacService(auditLogRepository, objectMapper);
    }

    // -----------------------------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------------------------

    private UserPrincipal autorFixture(String nome) {
        return UserPrincipal.create(UUID.randomUUID(), UUID.randomUUID(), nome, EMAIL_DISTINTIVO,
                Set.of(), Set.of(), Set.of());
    }

    private User alvoFixture(String nome) {
        return User.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .nome(nome)
                .email(EMAIL_DISTINTIVO)
                .build();
    }

    private TenantRole papelFixture(String nome, Permission... permissoes) {
        return TenantRole.builder()
                .id(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .nome(nome)
                .permissions(Set.of(permissoes))
                .build();
    }

    private Permission permissao(String nome) {
        return Permission.builder().id((int) (Math.random() * 100000)).nome(nome).build();
    }

    private JsonNode detalheDe(AuditLog auditLog) throws Exception {
        return objectMapper.readTree(auditLog.getDetalhe());
    }

    // -----------------------------------------------------------------------------------------
    // registarPapelCriado
    // -----------------------------------------------------------------------------------------

    @Test
    void registarPapelCriado_gravaEventoComPermissoesOrdenadas() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UserPrincipal autor = autorFixture("Autora Criadora");
        TenantRole papel = papelFixture("Advogado", permissao("financeiro:view"), permissao("clientes:view"));

        service.registarPapelCriado(tenantId, autor, papel);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        AuditLog salvo = captor.getValue();

        assertEquals(tenantId, salvo.getTenantId());
        assertNull(salvo.getProcessoId());
        assertEquals("papel_criar", salvo.getAcao());
        assertEquals("papel_escritorio", salvo.getEntidadeTipo());
        assertEquals(papel.getId().toString(), salvo.getEntidadeId());
        assertEquals(autor.getUserId(), salvo.getAutorId());

        JsonNode detalhe = detalheDe(salvo);
        assertEquals("Autora Criadora", detalhe.get("autorNome").asText());
        assertEquals(papel.getId().toString(), detalhe.get("papelId").asText());
        assertEquals("Advogado", detalhe.get("papelNome").asText());
        assertEquals(2, detalhe.get("permissoesAdicionadas").size());
        assertEquals("clientes:view", detalhe.get("permissoesAdicionadas").get(0).asText());
        assertEquals("financeiro:view", detalhe.get("permissoesAdicionadas").get(1).asText());
    }

    // -----------------------------------------------------------------------------------------
    // registarPapelRenomeado
    // -----------------------------------------------------------------------------------------

    @Test
    void registarPapelRenomeado_gravaNomeAntigoENovo() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID papelId = UUID.randomUUID();
        UserPrincipal autor = autorFixture("Autora Renomeadora");

        service.registarPapelRenomeado(tenantId, autor, papelId, "Advogado Junior", "Advogado Senior");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        AuditLog salvo = captor.getValue();

        assertEquals("papel_renomear", salvo.getAcao());
        assertEquals("papel_escritorio", salvo.getEntidadeTipo());
        assertEquals(papelId.toString(), salvo.getEntidadeId());

        JsonNode detalhe = detalheDe(salvo);
        assertEquals("Advogado Junior", detalhe.get("nomeAntigo").asText());
        assertEquals("Advogado Senior", detalhe.get("nomeNovo").asText());
        assertEquals("Advogado Senior", detalhe.get("papelNome").asText());
    }

    // -----------------------------------------------------------------------------------------
    // registarPapelApagado
    // -----------------------------------------------------------------------------------------

    @Test
    void registarPapelApagado_gravaPermissoesRemovidas() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UserPrincipal autor = autorFixture("Autora Apagadora");
        TenantRole papel = papelFixture("Tecnico", permissao("agenda:view"));

        service.registarPapelApagado(tenantId, autor, papel);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        AuditLog salvo = captor.getValue();

        assertEquals("papel_apagar", salvo.getAcao());
        JsonNode detalhe = detalheDe(salvo);
        assertEquals(1, detalhe.get("permissoesRemovidas").size());
        assertEquals("agenda:view", detalhe.get("permissoesRemovidas").get(0).asText());
    }

    // -----------------------------------------------------------------------------------------
    // registarPermissoesAlteradas
    // -----------------------------------------------------------------------------------------

    @Test
    void registarPermissoesAlteradas_gravaAdicionadasERemovidasOrdenadas() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UserPrincipal autor = autorFixture("Autora Permissoes");
        TenantRole papel = papelFixture("Assistente");

        service.registarPermissoesAlteradas(tenantId, autor, papel,
                Set.of("financeiro:view", "clientes:view"), Set.of("agenda:edit"));

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        AuditLog salvo = captor.getValue();

        assertEquals("papel_permissoes_alterar", salvo.getAcao());
        JsonNode detalhe = detalheDe(salvo);
        assertEquals(2, detalhe.get("permissoesAdicionadas").size());
        assertEquals("clientes:view", detalhe.get("permissoesAdicionadas").get(0).asText());
        assertEquals("financeiro:view", detalhe.get("permissoesAdicionadas").get(1).asText());
        assertEquals(1, detalhe.get("permissoesRemovidas").size());
        assertEquals("agenda:edit", detalhe.get("permissoesRemovidas").get(0).asText());
    }

    @Test
    void registarPermissoesAlteradas_semAlteracaoNaoGravaNada() {
        UUID tenantId = UUID.randomUUID();
        UserPrincipal autor = autorFixture("Autora Sem Alteracao");
        TenantRole papel = papelFixture("Assistente");

        service.registarPermissoesAlteradas(tenantId, autor, papel, Set.of(), Set.of());

        verify(auditLogRepository, never()).save(any());
    }

    // -----------------------------------------------------------------------------------------
    // registarAtribuicoes
    // -----------------------------------------------------------------------------------------

    @Test
    void registarAtribuicoes_diffGeraAtribuirERetirar() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UserPrincipal autor = autorFixture("Autora Atribuidora");
        User alvo = alvoFixture("Alvo Diff");
        TenantRole papelA = papelFixture("A");
        TenantRole papelB = papelFixture("B");
        TenantRole papelC = papelFixture("C");

        service.registarAtribuicoes(tenantId, autor, alvo, Set.of(papelA, papelB), Set.of(papelB, papelC), null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(2)).save(captor.capture());
        List<AuditLog> salvos = captor.getAllValues();

        AuditLog atribuir = salvos.stream().filter(a -> "papel_atribuir".equals(a.getAcao())).findFirst().orElseThrow();
        AuditLog retirar = salvos.stream().filter(a -> "papel_retirar".equals(a.getAcao())).findFirst().orElseThrow();

        assertEquals("atribuicao_papel", atribuir.getEntidadeTipo());
        assertEquals(alvo.getId().toString(), atribuir.getEntidadeId());
        assertEquals(papelC.getId().toString(), detalheDe(atribuir).get("papelId").asText());

        assertEquals("atribuicao_papel", retirar.getEntidadeTipo());
        assertEquals(alvo.getId().toString(), retirar.getEntidadeId());
        assertEquals(papelA.getId().toString(), detalheDe(retirar).get("papelId").asText());
    }

    @Test
    void registarAtribuicoes_semDiferencaNaoGravaNada() {
        UUID tenantId = UUID.randomUUID();
        UserPrincipal autor = autorFixture("Autora Sem Diferenca");
        User alvo = alvoFixture("Alvo Sem Diferenca");
        UUID idPartilhado = UUID.randomUUID();
        TenantRole instanciaUm = TenantRole.builder().id(idPartilhado).tenantId(tenantId).nome("A").permissions(Set.of()).build();
        TenantRole instanciaDois = TenantRole.builder().id(idPartilhado).tenantId(tenantId).nome("A").permissions(Set.of()).build();

        service.registarAtribuicoes(tenantId, autor, alvo, Set.of(instanciaUm), Set.of(instanciaDois), null);

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void registarAtribuicoes_comMotivoUtilizadorEliminadoGravaMotivoNaRetirada() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UserPrincipal autor = autorFixture("Autora Eliminacao");
        User alvo = alvoFixture("Alvo Eliminado");
        TenantRole papelA = papelFixture("A");

        service.registarAtribuicoes(tenantId, autor, alvo, Set.of(papelA), Set.of(),
                AuditoriaRbacService.MOTIVO_UTILIZADOR_ELIMINADO);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        AuditLog retirar = captor.getValue();

        assertEquals("papel_retirar", retirar.getAcao());
        assertEquals("utilizador_eliminado", detalheDe(retirar).get("motivo").asText());
    }

    @Test
    void registarAtribuicoes_comAutorNuloNaoGravaAutorId_nemChaveAutorNome() throws Exception {
        UUID tenantId = UUID.randomUUID();
        User alvo = alvoFixture("Alvo Sistema");
        TenantRole papelA = papelFixture("A");

        service.registarAtribuicoes(tenantId, null, alvo, Set.of(), Set.of(papelA), null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        AuditLog salvo = captor.getValue();

        assertNull(salvo.getAutorId());
        assertFalse(detalheDe(salvo).has("autorNome"));
    }

    // -----------------------------------------------------------------------------------------
    // Privacidade (Decisao 2 revista) -- nenhum detalhe gravado contem o email
    // -----------------------------------------------------------------------------------------

    @Test
    void nenhumDetalheGravadoContemOEmailDoAutorOuDoAlvo() {
        UUID tenantId = UUID.randomUUID();
        UserPrincipal autor = autorFixture("Autora Privacidade");
        User alvo = alvoFixture("Alvo Privacidade");
        TenantRole papel = papelFixture("Papel Privacidade", permissao("financeiro:view"));

        service.registarPapelCriado(tenantId, autor, papel);
        service.registarPapelRenomeado(tenantId, autor, papel.getId(), "Antigo", "Novo");
        service.registarPapelApagado(tenantId, autor, papel);
        service.registarPermissoesAlteradas(tenantId, autor, papel, Set.of("agenda:view"), Set.of());
        service.registarAtribuicoes(tenantId, autor, alvo, Set.of(), Set.of(papel), null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(5)).save(captor.capture());
        for (AuditLog salvo : captor.getAllValues()) {
            assertFalse(salvo.getDetalhe().contains(EMAIL_DISTINTIVO),
                    "detalhe nao deve conter o email: " + salvo.getDetalhe());
        }
    }

    // -----------------------------------------------------------------------------------------
    // listar
    // -----------------------------------------------------------------------------------------

    @Test
    void listar_passaFiltrosComoTextoEMapeiaCategoriaEDetalhe() {
        UUID tenantId = UUID.randomUUID();
        UUID utilizadorAlvoId = UUID.randomUUID();
        UUID papelId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);

        AuditLog rowPapel = AuditLog.builder()
                .id(1L).tenantId(tenantId).entidadeTipo("papel_escritorio").entidadeId(papelId.toString())
                .acao("papel_criar").autorId(UUID.randomUUID()).timestamp(LocalDateTime.now())
                .detalhe("{\"papelId\":\"" + papelId + "\",\"papelNome\":\"Advogado\",\"autorNome\":\"Autor Um\"}")
                .build();
        AuditLog rowAtribuicao = AuditLog.builder()
                .id(2L).tenantId(tenantId).entidadeTipo("atribuicao_papel").entidadeId(utilizadorAlvoId.toString())
                .acao("papel_atribuir").autorId(UUID.randomUUID()).timestamp(LocalDateTime.now())
                .detalhe("{\"papelId\":\"" + papelId + "\",\"alvoNome\":\"Alvo Dois\"}")
                .build();
        AuditLog rowDetalheNulo = AuditLog.builder()
                .id(3L).tenantId(tenantId).entidadeTipo("papel_escritorio").entidadeId(UUID.randomUUID().toString())
                .acao("papel_apagar").autorId(UUID.randomUUID()).timestamp(LocalDateTime.now())
                .detalhe(null)
                .build();
        AuditLog rowDetalheInvalido = AuditLog.builder()
                .id(4L).tenantId(tenantId).entidadeTipo("atribuicao_papel").entidadeId(utilizadorAlvoId.toString())
                .acao("papel_retirar").autorId(UUID.randomUUID()).timestamp(LocalDateTime.now())
                .detalhe("{nao json")
                .build();

        when(auditLogRepository.buscarEventosRbac(eq(tenantId), eq(utilizadorAlvoId.toString()), eq(papelId.toString()), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(rowPapel, rowAtribuicao, rowDetalheNulo, rowDetalheInvalido), pageable, 4));

        Page<AuditoriaRbacEntradaDto> pagina = service.listar(tenantId, utilizadorAlvoId, papelId, pageable);

        List<AuditoriaRbacEntradaDto> conteudo = pagina.getContent();
        assertEquals(4, conteudo.size());

        AuditoriaRbacEntradaDto dtoPapel = conteudo.get(0);
        assertEquals("papel", dtoPapel.getCategoria());
        assertNull(dtoPapel.getAlvoId());
        assertEquals("Advogado", dtoPapel.getPapelNome());
        assertEquals("Autor Um", dtoPapel.getAutorNome());

        AuditoriaRbacEntradaDto dtoAtribuicao = conteudo.get(1);
        assertEquals("atribuicao", dtoAtribuicao.getCategoria());
        assertEquals(utilizadorAlvoId.toString(), dtoAtribuicao.getAlvoId());
        assertEquals("Alvo Dois", dtoAtribuicao.getAlvoNome());

        AuditoriaRbacEntradaDto dtoNulo = conteudo.get(2);
        assertEquals("papel", dtoNulo.getCategoria());
        assertNull(dtoNulo.getAlvoId());
        assertNull(dtoNulo.getPapelNome());
        assertNull(dtoNulo.getAutorNome());

        AuditoriaRbacEntradaDto dtoInvalido = conteudo.get(3);
        assertEquals("atribuicao", dtoInvalido.getCategoria());
        assertEquals(utilizadorAlvoId.toString(), dtoInvalido.getAlvoId());
        assertNull(dtoInvalido.getAlvoNome());
    }

    @Test
    void listar_comFiltrosNulosPassaNullParaORepositorio() {
        UUID tenantId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        when(auditLogRepository.buscarEventosRbac(eq(tenantId), isNull(), isNull(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<AuditoriaRbacEntradaDto> pagina = service.listar(tenantId, null, null, pageable);

        assertTrue(pagina.getContent().isEmpty());
        verify(auditLogRepository, times(1)).buscarEventosRbac(tenantId, null, null, pageable);
    }
}
