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
        assertEquals(6, registar.size(), "esperados 6 métodos registar*: " + registar);
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
}
