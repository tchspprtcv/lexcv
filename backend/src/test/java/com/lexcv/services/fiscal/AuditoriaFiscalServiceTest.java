package com.lexcv.services.fiscal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lexcv.config.UserPrincipal;
import com.lexcv.models.AuditLog;
import com.lexcv.repositories.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 133 (CFG-01/02/06), Plan 04: prova comportamental de {@link AuditoriaFiscalService} --
 * eventos append-only, na transação do chamador (MANDATORY), com apenas o nome do autor e os
 * NOMES dos campos alterados (nunca valores, nunca o email do autor).
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaFiscalServiceTest {

    private static final String EMAIL_DISTINTIVO = "fiscal-privacidade@example.cv";

    @Mock
    private AuditLogRepository auditLogRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AuditoriaFiscalService service;
    private UUID tenantId;
    private UUID configId;
    private UserPrincipal autor;

    @BeforeEach
    void configurar() {
        service = new AuditoriaFiscalService(auditLogRepository, objectMapper);
        tenantId = UUID.randomUUID();
        configId = UUID.randomUUID();
        autor = UserPrincipal.create(UUID.randomUUID(), tenantId, "Ana", EMAIL_DISTINTIVO,
                Set.of(), Set.of(), Set.of());
    }

    private AuditLog unicoGravado() {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    private JsonNode detalhe(AuditLog log) throws Exception {
        return objectMapper.readTree(log.getDetalhe());
    }

    private void assertEstrutura(AuditLog log, String acao) {
        assertEquals(tenantId, log.getTenantId());
        assertNull(log.getProcessoId());
        assertEquals(acao, log.getAcao());
        assertEquals("configuracao_fiscal", log.getEntidadeTipo());
        assertEquals(configId.toString(), log.getEntidadeId());
        assertEquals(autor.getUserId(), log.getAutorId());
        assertNull(log.getId());
    }

    @Test
    void todosOsMetodosRegistarSaoMandatory() throws Exception {
        List<Method> registar = java.util.Arrays.stream(AuditoriaFiscalService.class.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                .filter(m -> m.getName().startsWith("registar"))
                .toList();
        assertEquals(10, registar.size(), "esperados 10 métodos registar*: " + registar);
        for (Method m : registar) {
            Transactional tx = m.getAnnotation(Transactional.class);
            assertNotNull(tx, m.getName() + " sem @Transactional");
            assertEquals(Propagation.MANDATORY, tx.propagation(), m.getName());
        }
    }

    @Test
    void registarDadosAlterados_gravaNomesDosCamposOrdenados() throws Exception {
        service.registarDadosAlterados(tenantId, autor, configId, Set.of("nif", "morada"));

        AuditLog log = unicoGravado();
        assertEstrutura(log, AuditoriaFiscalService.ACAO_DADOS_ALTERAR);
        assertEquals("faturacao_dados_alterar", AuditoriaFiscalService.ACAO_DADOS_ALTERAR);
        JsonNode d = detalhe(log);
        assertEquals("Ana", d.get("autorNome").asText());
        assertEquals(2, d.get("camposAlterados").size());
        assertEquals("morada", d.get("camposAlterados").get(0).asText());
        assertEquals("nif", d.get("camposAlterados").get(1).asText());
        assertEquals(2, d.size());
    }

    @Test
    void registarAtivacao_gravaSoAutorNome() throws Exception {
        service.registarAtivacao(tenantId, autor, configId);

        AuditLog log = unicoGravado();
        assertEstrutura(log, "faturacao_ativar");
        JsonNode d = detalhe(log);
        assertEquals("Ana", d.get("autorNome").asText());
        assertEquals(1, d.size());
    }

    @Test
    void registarDesativacao_comEmailLigadoIncluiEnvioEmailDesligado() throws Exception {
        service.registarDesativacao(tenantId, autor, configId, true);

        AuditLog log = unicoGravado();
        assertEstrutura(log, "faturacao_desativar");
        JsonNode d = detalhe(log);
        assertTrue(d.get("envioEmailDesligado").asBoolean());
    }

    @Test
    void registarDesativacao_semEmailLigadoOmiteAChave() throws Exception {
        service.registarDesativacao(tenantId, autor, configId, false);

        JsonNode d = detalhe(unicoGravado());
        assertFalse(d.has("envioEmailDesligado"));
        assertEquals("Ana", d.get("autorNome").asText());
    }

    @Test
    void registarEmailLigado_incluiDeclaracaoAceite() throws Exception {
        service.registarEmailLigado(tenantId, autor, configId);

        AuditLog log = unicoGravado();
        assertEstrutura(log, "faturacao_email_ligar");
        assertTrue(detalhe(log).get("declaracaoAceite").asBoolean());
    }

    @Test
    void registarEmailDesligado_gravaSoAutorNome() throws Exception {
        service.registarEmailDesligado(tenantId, autor, configId);

        AuditLog log = unicoGravado();
        assertEstrutura(log, "faturacao_email_desligar");
        assertEquals(1, detalhe(log).size());
    }

    @Test
    void nenhumDetalheGravadoContemOEmailDoAutor() {
        service.registarDadosAlterados(tenantId, autor, configId, Set.of("emailContacto"));
        service.registarAtivacao(tenantId, autor, configId);
        service.registarDesativacao(tenantId, autor, configId, true);
        service.registarEmailLigado(tenantId, autor, configId);
        service.registarEmailDesligado(tenantId, autor, configId);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(5)).save(captor.capture());
        for (AuditLog log : captor.getAllValues()) {
            assertFalse(log.getDetalhe().contains(EMAIL_DISTINTIVO), log.getDetalhe());
            assertTrue(log.getDetalhe().contains("Ana"));
        }
    }

    @Test
    void codigoFonteNaoUsaOGetterDeEmail() throws Exception {
        String fonte = Files.readString(
                Path.of("src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java"),
                StandardCharsets.UTF_8);
        String proibido = "get" + "Email";
        assertFalse(fonte.contains(proibido), "AuditoriaFiscalService não pode ler o email do autor");
    }

    @Test
    void registarEmissao_gravaEventoDoDocumentoFiscal() throws Exception {
        UUID documentoId = UUID.randomUUID();

        service.registarEmissao(tenantId, autor, documentoId, "SIM-FR-2026/7");

        AuditLog log = unicoGravado();
        assertEquals(tenantId, log.getTenantId());
        assertNull(log.getProcessoId());
        assertNull(log.getId());
        assertEquals("documento_fiscal_emitir", log.getAcao());
        assertEquals("documento_fiscal_emitir", AuditoriaFiscalService.ACAO_EMITIR);
        assertEquals("documento_fiscal", log.getEntidadeTipo());
        assertEquals("documento_fiscal", AuditoriaFiscalService.ENTIDADE_TIPO_DOCUMENTO);
        assertEquals(documentoId.toString(), log.getEntidadeId());
        assertEquals(autor.getUserId(), log.getAutorId());
        JsonNode d = detalhe(log);
        assertEquals(2, d.size(), log.getDetalhe());
        assertEquals("Ana", d.get("autorNome").asText());
        assertEquals("SIM-FR-2026/7", d.get("numeroFormatado").asText());
    }

    @Test
    void registarEmissao_detalheSemValoresNifsNemEmail() throws Exception {
        service.registarEmissao(tenantId, autor, UUID.randomUUID(), "SIM-FR-2026/1");

        AuditLog log = unicoGravado();
        assertFalse(log.getDetalhe().contains(EMAIL_DISTINTIVO), log.getDetalhe());
        JsonNode d = detalhe(log);
        java.util.Set<String> chaves = new java.util.HashSet<>();
        d.fieldNames().forEachRemaining(chaves::add);
        assertEquals(Set.of("autorNome", "numeroFormatado"), chaves);
    }

    @Test
    void registarEmissao_ehMandatory() throws Exception {
        Method m = AuditoriaFiscalService.class.getMethod("registarEmissao",
                UUID.class, UserPrincipal.class, UUID.class, String.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.MANDATORY, tx.propagation());
    }

    @Test
    void registarEmissaoNotaCredito_gravaEventoDaNc() throws Exception {
        UUID ncId = UUID.randomUUID();

        service.registarEmissaoNotaCredito(tenantId, autor, ncId, "SIM-NC-2026/3", "SIM-FR-2026/7");

        AuditLog log = unicoGravado();
        assertEquals(tenantId, log.getTenantId());
        assertNull(log.getProcessoId());
        assertNull(log.getId());
        assertEquals("documento_fiscal_emitir_nc", log.getAcao());
        assertEquals("documento_fiscal_emitir_nc", AuditoriaFiscalService.ACAO_EMITIR_NC);
        assertEquals("documento_fiscal", log.getEntidadeTipo());
        assertEquals(ncId.toString(), log.getEntidadeId());
        assertEquals(autor.getUserId(), log.getAutorId());
        JsonNode d = detalhe(log);
        java.util.Set<String> chaves = new java.util.HashSet<>();
        d.fieldNames().forEachRemaining(chaves::add);
        assertEquals(Set.of("autorNome", "numeroFormatado", "documentoOrigem"), chaves);
        assertEquals("Ana", d.get("autorNome").asText());
        assertEquals("SIM-NC-2026/3", d.get("numeroFormatado").asText());
        assertEquals("SIM-FR-2026/7", d.get("documentoOrigem").asText());
    }

    @Test
    void registarEmissaoNotaCredito_detalheSemMotivoNifMoradaNemEmail() throws Exception {
        // O método nem recebe o texto livre do motivo (pode conter dados do cliente): um texto
        // distintivo usado no pedido nunca pode aparecer no detalhe gravado.
        String motivoDistintivo = "MOTIVO-PRIVADO-XYZ morada Rua 5 NIF 123456789";

        service.registarEmissaoNotaCredito(tenantId, autor, UUID.randomUUID(), "SIM-NC-2026/1", "SIM-FR-2026/1");

        AuditLog log = unicoGravado();
        assertFalse(log.getDetalhe().contains(motivoDistintivo), log.getDetalhe());
        assertFalse(log.getDetalhe().contains("MOTIVO-PRIVADO"), log.getDetalhe());
        assertFalse(log.getDetalhe().contains("123456789"), log.getDetalhe());
        assertFalse(log.getDetalhe().contains(EMAIL_DISTINTIVO), log.getDetalhe());
        assertFalse(log.getDetalhe().toLowerCase().contains("motivo"), log.getDetalhe());
        boolean temParametroTexto = java.util.Arrays.stream(AuditoriaFiscalService.class.getMethods())
                .filter(m -> m.getName().equals("registarEmissaoNotaCredito"))
                .allMatch(m -> m.getParameterCount() == 5);
        assertTrue(temParametroTexto, "registarEmissaoNotaCredito recebe apenas 5 parâmetros");
    }

    @Test
    void registarEmissaoNotaCredito_ehMandatory() throws Exception {
        Method m = AuditoriaFiscalService.class.getMethod("registarEmissaoNotaCredito",
                UUID.class, UserPrincipal.class, UUID.class, String.class, String.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.MANDATORY, tx.propagation());
    }

    @Test
    void eventosDaConfiguracaoMantemEntidadeConfiguracaoFiscal() {
        service.registarDadosAlterados(tenantId, autor, configId, Set.of("nif"));
        service.registarAtivacao(tenantId, autor, configId);
        service.registarDesativacao(tenantId, autor, configId, false);
        service.registarEmailLigado(tenantId, autor, configId);
        service.registarEmailDesligado(tenantId, autor, configId);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository, times(5)).save(captor.capture());
        for (AuditLog log : captor.getAllValues()) {
            assertEquals("configuracao_fiscal", log.getEntidadeTipo());
            assertEquals(configId.toString(), log.getEntidadeId());
        }
    }

    @Test
    void falhaDeSerializacaoLancaIllegalStateException() throws Exception {
        ObjectMapper falhado = mock(ObjectMapper.class);
        when(falhado.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") { });
        AuditoriaFiscalService comFalha = new AuditoriaFiscalService(auditLogRepository, falhado);

        assertThrows(IllegalStateException.class, () -> comFalha.registarAtivacao(tenantId, autor, configId));
        verify(auditLogRepository, never()).save(any());
    }

    @Test
    void registarReprocessamentoComunicacao_gravaSoAutorNumeroEEstadoAnterior() throws Exception {
        UUID documentoId = UUID.randomUUID();

        service.registarReprocessamentoComunicacao(tenantId, autor, documentoId, "SIM-FR-2026/7", "ERRO");

        AuditLog log = unicoGravado();
        assertEquals(tenantId, log.getTenantId());
        assertNull(log.getProcessoId());
        assertNull(log.getId());
        assertEquals("documento_fiscal_reprocessar_comunicacao", log.getAcao());
        assertEquals("documento_fiscal_reprocessar_comunicacao", AuditoriaFiscalService.ACAO_REPROCESSAR_COMUNICACAO);
        assertEquals("documento_fiscal", log.getEntidadeTipo());
        assertEquals(documentoId.toString(), log.getEntidadeId());
        assertEquals(autor.getUserId(), log.getAutorId());
        assertFalse(log.getDetalhe().contains(EMAIL_DISTINTIVO), log.getDetalhe());
        JsonNode d = detalhe(log);
        java.util.Set<String> chaves = new java.util.HashSet<>();
        d.fieldNames().forEachRemaining(chaves::add);
        assertEquals(Set.of("autorNome", "numeroFormatado", "estadoAnterior"), chaves);
        assertEquals("Ana", d.get("autorNome").asText());
        assertEquals("SIM-FR-2026/7", d.get("numeroFormatado").asText());
        assertEquals("ERRO", d.get("estadoAnterior").asText());
    }

    @Test
    void registarReprocessamentoComunicacao_ehMandatory() throws Exception {
        Method m = AuditoriaFiscalService.class.getMethod("registarReprocessamentoComunicacao",
                UUID.class, UserPrincipal.class, UUID.class, String.class, String.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.MANDATORY, tx.propagation());
    }

    // ------------------------------------------------------------------ Phase 137: reenvio de email

    @Test
    void registarReenvioEmail_gravaSoAutorNumeroEEstadoAnteriorSemEndereco() throws Exception {
        UUID documentoId = UUID.randomUUID();

        service.registarReenvioEmail(tenantId, autor, documentoId, "SIM-FR-2026/7", "FALHOU");

        AuditLog log = unicoGravado();
        assertEquals(tenantId, log.getTenantId());
        assertNull(log.getProcessoId());
        assertNull(log.getId());
        assertEquals("documento_fiscal_reenviar_email", log.getAcao());
        assertEquals("documento_fiscal_reenviar_email", AuditoriaFiscalService.ACAO_REENVIAR_EMAIL);
        assertEquals("documento_fiscal", log.getEntidadeTipo());
        assertEquals(documentoId.toString(), log.getEntidadeId());
        assertEquals(autor.getUserId(), log.getAutorId());
        assertFalse(log.getDetalhe().contains("@"), log.getDetalhe());
        JsonNode d = detalhe(log);
        java.util.Set<String> chaves = new java.util.HashSet<>();
        d.fieldNames().forEachRemaining(chaves::add);
        assertEquals(Set.of("autorNome", "numeroFormatado", "estadoAnterior"), chaves);
        assertEquals("Ana", d.get("autorNome").asText());
        assertEquals("SIM-FR-2026/7", d.get("numeroFormatado").asText());
        assertEquals("FALHOU", d.get("estadoAnterior").asText());
    }

    @Test
    void registarReenvioEmail_ehMandatory() throws Exception {
        Method m = AuditoriaFiscalService.class.getMethod("registarReenvioEmail",
                UUID.class, UserPrincipal.class, UUID.class, String.class, String.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.MANDATORY, tx.propagation());
    }

    // ------------------------------------------------------------------ Phase 137: descarga PDF/XML

    @Test
    void registarDescarga_gravaAutorNumeroEFormatoSemEndereco() throws Exception {
        UUID documentoId = UUID.randomUUID();

        service.registarDescarga(tenantId, autor, documentoId, "SIM-FR-2026/7", "PDF");

        AuditLog log = unicoGravado();
        assertEquals(tenantId, log.getTenantId());
        assertNull(log.getProcessoId());
        assertNull(log.getId());
        assertEquals("documento_fiscal_descarregar", log.getAcao());
        assertEquals("documento_fiscal_descarregar", AuditoriaFiscalService.ACAO_DESCARREGAR);
        assertEquals("documento_fiscal", log.getEntidadeTipo());
        assertEquals(documentoId.toString(), log.getEntidadeId());
        assertEquals(autor.getUserId(), log.getAutorId());
        assertFalse(log.getDetalhe().contains("@"), log.getDetalhe());
        JsonNode d = detalhe(log);
        java.util.Set<String> chaves = new java.util.HashSet<>();
        d.fieldNames().forEachRemaining(chaves::add);
        assertEquals(Set.of("autorNome", "numeroFormatado", "formato"), chaves);
        assertEquals("Ana", d.get("autorNome").asText());
        assertEquals("SIM-FR-2026/7", d.get("numeroFormatado").asText());
        assertEquals("PDF", d.get("formato").asText());
    }

    @Test
    void registarDescarga_ehMandatory() throws Exception {
        Method m = AuditoriaFiscalService.class.getMethod("registarDescarga",
                UUID.class, UserPrincipal.class, UUID.class, String.class, String.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.MANDATORY, tx.propagation());
    }

    // ------------------------------------------------------------------ Phase 137: exportação mensal (RELF-01)

    @Test
    void registarExportacaoMensal_gravaAutorMesEContagemSemEndereco() throws Exception {
        service.registarExportacaoMensal(tenantId, autor, java.time.YearMonth.of(2026, 9), 2);

        AuditLog log = unicoGravado();
        assertEquals(tenantId, log.getTenantId());
        assertNull(log.getProcessoId());
        assertNull(log.getId());
        assertEquals("documento_fiscal_exportar_mes", log.getAcao());
        assertEquals("documento_fiscal_exportar_mes", AuditoriaFiscalService.ACAO_EXPORTAR_MES);
        assertEquals("relatorio_fiscal", log.getEntidadeTipo());
        assertEquals("relatorio_fiscal", AuditoriaFiscalService.ENTIDADE_TIPO_RELATORIO);
        assertEquals("2026-09", log.getEntidadeId());
        assertEquals(autor.getUserId(), log.getAutorId());
        assertFalse(log.getDetalhe().contains("@"), log.getDetalhe());
        JsonNode d = detalhe(log);
        java.util.Set<String> chaves = new java.util.HashSet<>();
        d.fieldNames().forEachRemaining(chaves::add);
        assertEquals(Set.of("autorNome", "mes", "numeroDocumentos"), chaves);
        assertEquals("Ana", d.get("autorNome").asText());
        assertEquals("2026-09", d.get("mes").asText());
        assertEquals(2, d.get("numeroDocumentos").asInt());
    }

    @Test
    void registarExportacaoMensal_mesVazioGravaZeroDocumentos() throws Exception {
        service.registarExportacaoMensal(tenantId, autor, java.time.YearMonth.of(2026, 1), 0);

        JsonNode d = detalhe(unicoGravado());
        assertEquals(0, d.get("numeroDocumentos").asInt());
        assertEquals("2026-01", d.get("mes").asText());
    }

    @Test
    void registarExportacaoMensal_ehMandatory() throws Exception {
        Method m = AuditoriaFiscalService.class.getMethod("registarExportacaoMensal",
                UUID.class, UserPrincipal.class, java.time.YearMonth.class, int.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx);
        assertEquals(Propagation.MANDATORY, tx.propagation());
    }
}
