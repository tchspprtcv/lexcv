package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.ConfiguracaoFiscalRequest;
import com.lexcv.dtos.ConfiguracaoFiscalResponse;
import com.lexcv.dtos.EmailAutomaticoRequest;
import com.lexcv.dtos.MotivoIsencaoResponse;
import com.lexcv.dtos.SerieFiscalResponse;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.ConfiguracaoFiscal;
import com.lexcv.models.MotivoIsencaoIva;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.SerieFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.models.User;
import com.lexcv.repositories.ConfiguracaoFiscalRepository;
import com.lexcv.repositories.SerieFiscalRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Phase 133 (CFG-01/02/03/06), Plan 04: contrato unitário do {@link ConfiguracaoFiscalService}. */
class ConfiguracaoFiscalServiceTest {

    private static final Instant AGORA = Instant.parse("2026-06-15T12:00:00Z");
    private static final Clock RELOGIO = Clock.fixed(AGORA, ZoneOffset.UTC);

    private ConfiguracaoFiscalRepository configRepo;
    private SerieFiscalRepository serieRepo;
    private UserRepository userRepo;
    private AuditoriaFiscalService auditoria;
    private ConfiguracaoFiscalService service;

    private UUID tenantId;
    private UserPrincipal autor;

    @BeforeEach
    void setUp() {
        configRepo = mock(ConfiguracaoFiscalRepository.class);
        serieRepo = mock(SerieFiscalRepository.class);
        userRepo = mock(UserRepository.class);
        auditoria = mock(AuditoriaFiscalService.class);
        service = new ConfiguracaoFiscalService(configRepo, serieRepo, userRepo, auditoria, RELOGIO);
        tenantId = UUID.randomUUID();
        autor = UserPrincipal.create(UUID.randomUUID(), tenantId, "Ana", "ana@example.cv",
                Set.of(), Set.of(), Set.of());

        when(configRepo.findByTenantId(any())).thenReturn(Optional.empty());
        when(configRepo.bloquearPorTenant(any())).thenReturn(Optional.empty());
        when(configRepo.saveAndFlush(any(ConfiguracaoFiscal.class))).thenAnswer(inv -> {
            ConfiguracaoFiscal c = inv.getArgument(0);
            if (c.getId() == null) {
                c.setId(UUID.randomUUID());
            }
            return c;
        });
        when(configRepo.save(any(ConfiguracaoFiscal.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // -----------------------------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------------------------

    private static ConfiguracaoFiscalRequest pedido() {
        return new ConfiguracaoFiscalRequest("512345678", "Escritório Silva", "Rua 5 de Julho, 12",
                "Praia", "geral@silva.cv", "+238 260 00 00", RegimeIva.NORMAL, null);
    }

    private static ConfiguracaoFiscalRequest pedido(String nif, String morada, RegimeIva regime, String motivo) {
        ConfiguracaoFiscalRequest p = pedido();
        return new ConfiguracaoFiscalRequest(nif, p.firma(), morada, p.localidade(), p.emailContacto(),
                p.telefoneContacto(), regime, motivo);
    }

    private ConfiguracaoFiscal existente(boolean ativa, boolean email) {
        ConfiguracaoFiscalRequest p = pedido();
        ConfiguracaoFiscal c = ConfiguracaoFiscal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .nif(p.nif())
                .firma(p.firma())
                .morada(p.morada())
                .localidade(p.localidade())
                .paisCodigo("CV")
                .emailContacto(p.emailContacto())
                .telefoneContacto(p.telefoneContacto())
                .regimeIva(RegimeIva.NORMAL)
                .ativa(ativa)
                .envioEmailAutomatico(email)
                .createdAt(Instant.parse("2026-01-01T00:00:00Z"))
                .build();
        when(configRepo.findByTenantId(tenantId)).thenReturn(Optional.of(c));
        when(configRepo.bloquearPorTenant(tenantId)).thenReturn(Optional.of(c));
        return c;
    }

    private void comDocumentosEmitidos(boolean emitidos) {
        when(serieRepo.existsByTenantIdAndUltimoNumeroGreaterThan(tenantId, 0L)).thenReturn(emitidos);
    }

    private static RecusaFiscalException recusa(HttpStatus status, String codigo, org.junit.jupiter.api.function.Executable e) {
        RecusaFiscalException ex = assertThrows(RecusaFiscalException.class, e);
        assertEquals(status, ex.getStatus());
        assertEquals(codigo, ex.getCodigo());
        return ex;
    }

    @SuppressWarnings("unchecked")
    private Set<String> camposAuditados() {
        ArgumentCaptor<Set<String>> captor = ArgumentCaptor.forClass(Set.class);
        verify(auditoria).registarDadosAlterados(eq(tenantId), eq(autor), any(UUID.class), captor.capture());
        return captor.getValue();
    }

    // -----------------------------------------------------------------------------------------
    // Anotações transacionais
    // -----------------------------------------------------------------------------------------

    @Test
    void mutacoesSaoTransactionalRequiredELeiturasReadOnly() {
        Set<String> leituras = Set.of("obter", "listarSeries");
        Set<String> mutacoes = Set.of("guardar", "ativar", "desativar", "definirEmailAutomatico");
        List<Method> publicos = Arrays.stream(ConfiguracaoFiscalService.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .toList();
        for (Method m : publicos) {
            Transactional tx = m.getAnnotation(Transactional.class);
            assertNotNull(tx, m.getName() + " sem @Transactional");
            if (leituras.contains(m.getName())) {
                assertTrue(tx.readOnly(), m.getName());
            } else {
                assertTrue(mutacoes.contains(m.getName()), "método público inesperado: " + m.getName());
                assertFalse(tx.readOnly(), m.getName());
                assertEquals(Propagation.REQUIRED, tx.propagation(), m.getName());
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // obter (CFG-03)
    // -----------------------------------------------------------------------------------------

    // -----------------------------------------------------------------------------------------
    // Lock da configuração (CR-01 da revisão)
    // -----------------------------------------------------------------------------------------

    /**
     * Cada mutação lê a configuração com {@code bloquearPorTenant} (PESSIMISTIC_WRITE) e fá-lo
     * ANTES de consultar as séries (ordem de locks: configuração -> série). Nunca usa o finder
     * sem lock. A prova contra PostgreSQL real é {@code ConfiguracaoFiscalConcorrenciaIT}.
     */
    @Test
    void mutacoesBloqueiamAConfiguracaoAntesDasSeries() {
        List<org.junit.jupiter.api.function.Executable> mutacoes = List.of(
                () -> service.guardar(tenantId, autor, pedido("512345678", "Avenida Nova, 1", RegimeIva.NORMAL, null)),
                () -> service.ativar(tenantId, autor),
                () -> service.desativar(tenantId, autor),
                () -> service.definirEmailAutomatico(tenantId, autor, new EmailAutomaticoRequest(true, true)));
        for (org.junit.jupiter.api.function.Executable mutacao : mutacoes) {
            org.mockito.Mockito.clearInvocations(configRepo, serieRepo);
            existente(true, false);
            comDocumentosEmitidos(false);
            assertDoesNotThrow(mutacao);

            org.mockito.InOrder ordem = org.mockito.Mockito.inOrder(configRepo, serieRepo);
            ordem.verify(configRepo).bloquearPorTenant(tenantId);
            ordem.verify(serieRepo).existsByTenantIdAndUltimoNumeroGreaterThan(tenantId, 0L);
            verify(configRepo, never()).findByTenantId(any());
        }
    }

    @Test
    void obterNaoBloqueia() {
        existente(true, false);
        service.obter(tenantId);
        verify(configRepo, never()).bloquearPorTenant(any());
    }

    @Test
    void obterSemLinhaDevolveDesligadoENaoCriaNada() {
        ConfiguracaoFiscalResponse r = service.obter(tenantId);

        assertFalse(r.configurada());
        assertFalse(r.ativa());
        assertFalse(r.envioEmailAutomatico());
        assertFalse(r.documentosEmitidos());
        assertFalse(r.nifBloqueado());
        assertFalse(r.podeDesativar());
        assertFalse(r.completa());
        assertEquals("Cabo Verde", r.pais());
        verify(configRepo, never()).save(any());
        verify(configRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void obterComDocumentosBloqueiaNifEDesativacao() {
        existente(true, false);
        comDocumentosEmitidos(true);

        ConfiguracaoFiscalResponse r = service.obter(tenantId);

        assertTrue(r.configurada());
        assertTrue(r.ativa());
        assertTrue(r.documentosEmitidos());
        assertTrue(r.nifBloqueado());
        assertFalse(r.podeDesativar());
    }

    @Test
    void obterAtivaSemDocumentosPodeDesativar() {
        existente(true, false);
        assertTrue(service.obter(tenantId).podeDesativar());
    }

    // -----------------------------------------------------------------------------------------
    // guardar (CFG-01)
    // -----------------------------------------------------------------------------------------

    @Test
    void guardarPrimeiraVezCriaLinhaDoTenantEAuditaOsOitoCampos() {
        ConfiguracaoFiscalResponse r = service.guardar(tenantId, autor, pedido());

        ArgumentCaptor<ConfiguracaoFiscal> captor = ArgumentCaptor.forClass(ConfiguracaoFiscal.class);
        verify(configRepo).saveAndFlush(captor.capture());
        ConfiguracaoFiscal salvo = captor.getValue();
        assertEquals(tenantId, salvo.getTenantId());
        assertEquals("CV", salvo.getPaisCodigo());
        assertFalse(salvo.getAtiva());
        assertFalse(salvo.getEnvioEmailAutomatico());
        assertEquals(AGORA, salvo.getCreatedAt());
        assertEquals(AGORA, salvo.getUpdatedAt());
        assertEquals(autor.getUserId(), salvo.getUpdatedBy());

        assertEquals(Set.of("nif", "firma", "morada", "localidade", "emailContacto", "telefoneContacto",
                "regimeIva", "motivoIsencaoCodigo"), camposAuditados());
        assertTrue(r.completa());
        assertTrue(r.configurada());
        assertFalse(r.ativa());
    }

    @Test
    void guardarAparaEspacos() {
        ConfiguracaoFiscalRequest p = new ConfiguracaoFiscalRequest(" 512345678 ", "  Firma  ", " Rua ",
                " Praia ", " a@b.cv ", " 123 ", RegimeIva.NORMAL, null);
        service.guardar(tenantId, autor, p);

        ArgumentCaptor<ConfiguracaoFiscal> captor = ArgumentCaptor.forClass(ConfiguracaoFiscal.class);
        verify(configRepo).saveAndFlush(captor.capture());
        assertEquals("512345678", captor.getValue().getNif());
        assertEquals("Firma", captor.getValue().getFirma());
        assertEquals("a@b.cv", captor.getValue().getEmailContacto());
    }

    @Test
    void guardarNormalDescartaMotivo() {
        service.guardar(tenantId, autor, pedido("512345678", "Rua", RegimeIva.NORMAL, "5"));

        ArgumentCaptor<ConfiguracaoFiscal> captor = ArgumentCaptor.forClass(ConfiguracaoFiscal.class);
        verify(configRepo).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getMotivoIsencaoCodigo());
    }

    @Test
    void guardarIsentoComMotivoOficialGuardaOCodigo() {
        service.guardar(tenantId, autor, pedido("512345678", "Rua", RegimeIva.ISENTO, "5"));

        ArgumentCaptor<ConfiguracaoFiscal> captor = ArgumentCaptor.forClass(ConfiguracaoFiscal.class);
        verify(configRepo).saveAndFlush(captor.capture());
        assertEquals("5", captor.getValue().getMotivoIsencaoCodigo());
        assertEquals(RegimeIva.ISENTO, captor.getValue().getRegimeIva());
    }

    @Test
    void guardarIsentoSemMotivoOuComMotivoDesconhecidoERecusado() {
        for (String motivo : new String[]{null, "99", " "}) {
            RecusaFiscalException ex = recusa(HttpStatus.BAD_REQUEST, "MOTIVO_ISENCAO_INVALIDO",
                    () -> service.guardar(tenantId, autor, pedido("512345678", "Rua", RegimeIva.ISENTO, motivo)));
            assertEquals("motivoIsencaoCodigo", ex.getCampo());
            assertEquals("Escolha o motivo de isenção.", ex.getMessage());
        }
        verify(configRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void guardarSemAlteracoesNaoAuditaNemGrava() {
        existente(false, false);

        ConfiguracaoFiscalResponse r = service.guardar(tenantId, autor, pedido());

        verify(configRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
        assertTrue(r.configurada());
    }

    @Test
    void guardarMudandoNifComDocumentosEmitidosERecusado() {
        ConfiguracaoFiscal c = existente(true, false);
        comDocumentosEmitidos(true);

        RecusaFiscalException ex = recusa(HttpStatus.CONFLICT, "NIF_BLOQUEADO",
                () -> service.guardar(tenantId, autor, pedido("612345678", c.getMorada(), RegimeIva.NORMAL, null)));
        assertEquals("nif", ex.getCampo());
        assertEquals("O NIF não pode ser alterado depois de emitido o primeiro documento.", ex.getMessage());
        assertEquals("512345678", c.getNif());
        verify(configRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void guardarMudandoSoMoradaComDocumentosEmitidosEAceite() {
        existente(true, false);
        comDocumentosEmitidos(true);

        service.guardar(tenantId, autor, pedido("512345678", "Avenida Nova, 1", RegimeIva.NORMAL, null));

        assertEquals(Set.of("morada"), camposAuditados());
        verify(configRepo).saveAndFlush(any());
    }

    @Test
    void guardarComNifDeOutroTenantERecusado() {
        when(configRepo.existsByNifAndTenantIdNot("512345678", tenantId)).thenReturn(true);

        RecusaFiscalException ex = recusa(HttpStatus.CONFLICT, "NIF_JA_REGISTADO",
                () -> service.guardar(tenantId, autor, pedido()));
        assertEquals("nif", ex.getCampo());
        assertEquals("Este NIF já está registado noutro escritório.", ex.getMessage());
        verify(configRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditoria);
    }

    @Test
    void guardarConcorrenteDevolveConflito() {
        when(configRepo.saveAndFlush(any(ConfiguracaoFiscal.class)))
                .thenThrow(new DataIntegrityViolationException("uk_configuracao_fiscal_tenant"));

        RecusaFiscalException ex = recusa(HttpStatus.CONFLICT, "CONFIGURACAO_FISCAL_CONCORRENTE",
                () -> service.guardar(tenantId, autor, pedido()));
        assertEquals("Os dados fiscais foram alterados ao mesmo tempo noutro pedido. Atualize a página e tente de novo.",
                ex.getMessage());
        verifyNoInteractions(auditoria);
    }

    // -----------------------------------------------------------------------------------------
    // ativar / desativar (CFG-02)
    // -----------------------------------------------------------------------------------------

    @Test
    void ativarSemLinhaERecusado() {
        RecusaFiscalException ex = recusa(HttpStatus.UNPROCESSABLE_ENTITY, "CONFIGURACAO_FISCAL_INCOMPLETA",
                () -> service.ativar(tenantId, autor));
        assertEquals("Não foi possível ativar a faturação: os dados fiscais estão incompletos. "
                + "Preencha e guarde todos os campos e tente de novo.", ex.getMessage());
        verifyNoInteractions(auditoria);
    }

    @Test
    void ativarComDadosIncompletosERecusado() {
        ConfiguracaoFiscal c = existente(false, false);
        c.setTelefoneContacto(null);

        recusa(HttpStatus.UNPROCESSABLE_ENTITY, "CONFIGURACAO_FISCAL_INCOMPLETA", () -> service.ativar(tenantId, autor));
        assertFalse(c.getAtiva());
        verifyNoInteractions(auditoria);
    }

    @Test
    void ativarCompletaLigaEAudita() {
        ConfiguracaoFiscal c = existente(false, false);

        ConfiguracaoFiscalResponse r = service.ativar(tenantId, autor);

        assertTrue(c.getAtiva());
        assertEquals(AGORA, c.getUpdatedAt());
        assertEquals(autor.getUserId(), c.getUpdatedBy());
        assertTrue(r.ativa());
        verify(auditoria).registarAtivacao(tenantId, autor, c.getId());
    }

    @Test
    void ativarJaAtivaNaoAudita() {
        existente(true, false);

        assertTrue(service.ativar(tenantId, autor).ativa());
        verifyNoInteractions(auditoria);
    }

    @Test
    void desativarComDocumentosEmitidosERecusado() {
        ConfiguracaoFiscal c = existente(true, false);
        comDocumentosEmitidos(true);

        RecusaFiscalException ex = recusa(HttpStatus.CONFLICT, "FATURACAO_JA_EMITIU",
                () -> service.desativar(tenantId, autor));
        assertEquals("Não é possível desativar a faturação porque já foram emitidos documentos.", ex.getMessage());
        assertTrue(c.getAtiva());
        verifyNoInteractions(auditoria);
    }

    @Test
    void desativarSemDocumentosDesligaTambemOEmail() {
        ConfiguracaoFiscal c = existente(true, true);

        ConfiguracaoFiscalResponse r = service.desativar(tenantId, autor);

        assertFalse(c.getAtiva());
        assertFalse(c.getEnvioEmailAutomatico());
        assertFalse(r.ativa());
        assertFalse(r.envioEmailAutomatico());
        verify(auditoria).registarDesativacao(tenantId, autor, c.getId(), true);
    }

    @Test
    void desativarComEmailDesligadoAuditaFalse() {
        ConfiguracaoFiscal c = existente(true, false);

        service.desativar(tenantId, autor);

        verify(auditoria).registarDesativacao(tenantId, autor, c.getId(), false);
    }

    @Test
    void desativarJaInativaOuSemLinhaNaoAudita() {
        service.desativar(tenantId, autor);
        existente(false, false);
        service.desativar(tenantId, autor);
        verify(auditoria, never()).registarDesativacao(any(), any(), any(), anyBoolean());
    }

    // -----------------------------------------------------------------------------------------
    // definirEmailAutomatico (CFG-06)
    // -----------------------------------------------------------------------------------------

    @Test
    void ligarEmailComFaturacaoDesligadaERecusado() {
        existente(false, false);

        RecusaFiscalException ex = recusa(HttpStatus.CONFLICT, "FATURACAO_DESLIGADA",
                () -> service.definirEmailAutomatico(tenantId, autor, new EmailAutomaticoRequest(true, true)));
        assertEquals("Ative a faturação para poder ligar o envio automático.", ex.getMessage());
        verifyNoInteractions(auditoria);
    }

    @Test
    void ligarEmailSemLinhaERecusado() {
        recusa(HttpStatus.CONFLICT, "FATURACAO_DESLIGADA",
                () -> service.definirEmailAutomatico(tenantId, autor, new EmailAutomaticoRequest(true, true)));
        verifyNoInteractions(auditoria);
    }

    @Test
    void ligarEmailSemDeclaracaoERecusado() {
        ConfiguracaoFiscal c = existente(true, false);

        for (Boolean aceite : new Boolean[]{null, false}) {
            RecusaFiscalException ex = recusa(HttpStatus.UNPROCESSABLE_ENTITY, "DECLARACAO_NAO_ACEITE",
                    () -> service.definirEmailAutomatico(tenantId, autor, new EmailAutomaticoRequest(true, aceite)));
            assertEquals("aceiteDeclaracao", ex.getCampo());
            assertEquals("É necessário confirmar que compreende que os documentos simulados não têm validade fiscal.",
                    ex.getMessage());
        }
        assertFalse(c.getEnvioEmailAutomatico());
        verifyNoInteractions(auditoria);
    }

    @Test
    void ligarEmailValidoRegistaQuemAceitouEQuando() {
        ConfiguracaoFiscal c = existente(true, false);
        User ana = User.builder().id(autor.getUserId()).tenantId(tenantId).nome("Ana Silva").build();
        when(userRepo.findById(autor.getUserId())).thenReturn(Optional.of(ana));

        ConfiguracaoFiscalResponse r = service.definirEmailAutomatico(tenantId, autor,
                new EmailAutomaticoRequest(true, true));

        assertTrue(c.getEnvioEmailAutomatico());
        assertEquals(autor.getUserId(), c.getEnvioEmailAceitePor());
        assertEquals(AGORA, c.getEnvioEmailAceiteEm());
        assertTrue(r.envioEmailAutomatico());
        assertEquals("Ana Silva", r.envioEmailAceitePorNome());
        assertEquals(AGORA, r.envioEmailAceiteEm());
        verify(auditoria).registarEmailLigado(tenantId, autor, c.getId());
    }

    @Test
    void nomeDeQuemAceitouNuncaVemDeOutroTenant() {
        ConfiguracaoFiscal c = existente(true, true);
        UUID outro = UUID.randomUUID();
        c.setEnvioEmailAceitePor(outro);
        User deFora = User.builder().id(outro).tenantId(UUID.randomUUID()).nome("Intruso").build();
        when(userRepo.findById(outro)).thenReturn(Optional.of(deFora));

        assertNull(service.obter(tenantId).envioEmailAceitePorNome());
    }

    @Test
    void ligarEmailJaLigadoNaoAudita() {
        existente(true, true);

        service.definirEmailAutomatico(tenantId, autor, new EmailAutomaticoRequest(true, true));
        verifyNoInteractions(auditoria);
    }

    @Test
    void desligarEmailNaoPedeDeclaracaoEMantemHistorico() {
        ConfiguracaoFiscal c = existente(true, true);
        UUID quem = UUID.randomUUID();
        Instant quando = Instant.parse("2026-03-01T10:00:00Z");
        c.setEnvioEmailAceitePor(quem);
        c.setEnvioEmailAceiteEm(quando);

        ConfiguracaoFiscalResponse r = service.definirEmailAutomatico(tenantId, autor,
                new EmailAutomaticoRequest(false, null));

        assertFalse(c.getEnvioEmailAutomatico());
        assertEquals(quem, c.getEnvioEmailAceitePor());
        assertEquals(quando, c.getEnvioEmailAceiteEm());
        assertFalse(r.envioEmailAutomatico());
        assertNull(r.envioEmailAceitePorNome());
        verify(auditoria).registarEmailDesligado(tenantId, autor, c.getId());
    }

    @Test
    void desligarEmailJaDesligadoNaoAudita() {
        existente(true, false);
        service.definirEmailAutomatico(tenantId, autor, new EmailAutomaticoRequest(false, null));
        service.definirEmailAutomatico(tenantId, autor, new EmailAutomaticoRequest(false, false));
        verifyNoInteractions(auditoria);
    }

    // -----------------------------------------------------------------------------------------
    // listarSeries / motivos
    // -----------------------------------------------------------------------------------------

    @Test
    void listarSeriesMapeiaRotulos() {
        SerieFiscal s = SerieFiscal.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .tipoDocumento(TipoDocumentoFiscal.FR)
                .ano(2026)
                .ambiente(AmbienteFiscal.SIMULADO)
                .codigo("SIM-FR-2026")
                .ultimoNumero(7L)
                .build();
        when(serieRepo.findByTenantIdOrderByAnoDescTipoDocumentoAsc(tenantId)).thenReturn(List.of(s));

        List<SerieFiscalResponse> r = service.listarSeries(tenantId);

        assertEquals(1, r.size());
        SerieFiscalResponse sr = r.get(0);
        assertEquals(TipoDocumentoFiscal.FR, sr.tipoDocumento());
        assertEquals("Fatura-Recibo", sr.tipoDocumentoRotulo());
        assertEquals(2026, sr.ano());
        assertEquals("SIM-FR-2026", sr.codigo());
        assertEquals(7L, sr.ultimoNumero());
        assertEquals(AmbienteFiscal.SIMULADO, sr.ambiente());
        assertEquals("Simulado", sr.ambienteRotulo());
    }

    @Test
    void motivosIsencaoSeguemAOrdemDoEnum() {
        List<MotivoIsencaoResponse> todos = MotivoIsencaoResponse.todos();
        assertEquals(MotivoIsencaoIva.values().length, todos.size());
        assertEquals("1", todos.get(0).codigo());
        assertEquals(MotivoIsencaoIva.M1.descricao(), todos.get(0).descricao());
        assertEquals(MotivoIsencaoIva.M21.mencao(), todos.get(todos.size() - 1).mencao());
    }
}
