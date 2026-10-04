package com.lexcv.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda de regressao CFG-03 (Phase 133, Plano 05, ROADMAP criterio 3, T-133-25), EVOLUIDA de
 * proposito na Phase 134 (plano 08, D-11, T-134-34).
 *
 * <p><b>O que mudou e porque.</b> Na Phase 133 este teste proibia qualquer simbolo fiscal em
 * {@code ResourceController}: o pagamento nao tinha caminho fiscal. A Phase 134 liga o registo de
 * um pagamento a emissao da Fatura-Recibo quando a faturacao esta ativa, por isso o controlador
 * passa a ter UM ponto de decisao: {@code pagamentoFaturadoService.faturacaoAtiva(tenantId)} no
 * corpo de {@code createPagamento}, que delega no {@code PagamentoFaturadoService} ou, com a
 * faturacao desligada, chama {@code registarPagamentoLegado}.
 *
 * <p><b>O que continua provado.</b>
 * <ul>
 *   <li>{@code Pagamento.java} continua sem nenhum simbolo fiscal (proibicao alargada).</li>
 *   <li>{@code ResourceController} nunca refere a configuracao fiscal, a numeracao, as series, o
 *       repositorio de documentos fiscais nem a comunicacao: o acesso a dados fiscais passa so pelos
 *       servicos (sem estado da configuracao no contexto OSIV, T-134-35).</li>
 *   <li>Ha exatamente uma verificacao da faturacao, e fica dentro de {@code createPagamento}.</li>
 *   <li>O ramo desligado e o MESMO texto de antes: o SHA-256 do corpo normalizado (espacos
 *       colapsados) de {@code registarPagamentoLegado} e igual ao do corpo de
 *       {@code createPagamento} no commit 09aa999 (antes da mudanca). Qualquer alteracao, mesmo de
 *       um comentario, faz falhar este teste.</li>
 *   <li>Nenhum dos dois metodos e {@code @Transactional} (P-02: a falha engolida da conta corrente
 *       tornaria a transacao rollback-only), e {@code createPagamento} mantem o mesmo gate.</li>
 * </ul>
 * O comportamento do ramo desligado e provado tambem por Mockito em
 * {@code ResourceControllerPagamentoTest}.
 */
class FaturacaoDesligadaPagamentoInalteradoTest {

    /**
     * SHA-256 do corpo normalizado de {@code createPagamento(@RequestBody Pagamento pag)} em
     * {@code git show 09aa999:backend/src/main/java/com/lexcv/controllers/ResourceController.java}.
     */
    static final String HASH_CORPO_LEGADO = "3f84b99944c79d0056c0a801d2da18659c3bd6cd09ff8935901499d343041908";

    private static final Path CONTROLADOR = Path.of("src/main/java/com/lexcv/controllers/ResourceController.java");
    private static final Path PAGAMENTO = Path.of("src/main/java/com/lexcv/models/Pagamento.java");

    private static final List<String> TOKENS_PROIBIDOS_PAGAMENTO = List.of(
            "ConfiguracaoFiscal", "NumeracaoService", "SerieFiscal", "faturacao", "Faturacao", "Fiscal",
            "chave", "retencao");

    private static final List<String> TOKENS_PROIBIDOS_CONTROLADOR = List.of(
            "ConfiguracaoFiscal", "NumeracaoService", "SerieFiscal", "SerieFiscalRepository",
            "DocumentoFiscalRepository", "ComunicacaoFiscal");

    private static final String UNICA_VERIFICACAO = "pagamentoFaturadoService.faturacaoAtiva(";
    private static final String ASSINATURA_HANDLER = "public ResponseEntity<?> createPagamento(";
    private static final String ASSINATURA_LEGADO = "private ResponseEntity<?> registarPagamentoLegado(Pagamento pag)";

    private static String ler(Path p) throws IOException {
        assertTrue(Files.isRegularFile(p), "Ficheiro fonte em falta: " + p);
        return Files.readString(p, StandardCharsets.UTF_8);
    }

    /**
     * Texto estritamente entre a chaveta de abertura do metodo com esta assinatura e a chaveta que
     * a fecha (contagem de chavetas), ou {@code null} se a assinatura nao existir.
     */
    static String corpoDoMetodo(String fonte, String assinatura) {
        int inicio = fonte.indexOf(assinatura);
        if (inicio < 0) {
            return null;
        }
        int abre = fonte.indexOf('{', inicio);
        int profundidade = 0;
        for (int i = abre; i < fonte.length(); i++) {
            char c = fonte.charAt(i);
            if (c == '{') {
                profundidade++;
            } else if (c == '}') {
                profundidade--;
                if (profundidade == 0) {
                    return fonte.substring(abre + 1, i);
                }
            }
        }
        throw new IllegalStateException("Chavetas desequilibradas depois de " + assinatura);
    }

    static String sha256Normalizado(String corpo) throws NoSuchAlgorithmException {
        String normalizado = corpo.replaceAll("\\s+", " ").trim();
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalizado.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }

    private static int ocorrencias(String texto, String token) {
        int n = 0;
        for (int i = texto.indexOf(token); i >= 0; i = texto.indexOf(token, i + 1)) {
            n++;
        }
        return n;
    }

    @Test
    void pagamentoContinuaSemSimbolosFiscais() throws IOException {
        String fonte = ler(PAGAMENTO);
        for (String token : TOKENS_PROIBIDOS_PAGAMENTO) {
            assertFalse(fonte.contains(token), PAGAMENTO + " contem '" + token + "' (CFG-03)");
        }
    }

    @Test
    void controladorNuncaRefereConfiguracaoNumeracaoSeriesNemDocumentosFiscais() throws IOException {
        String fonte = ler(CONTROLADOR);
        for (String token : TOKENS_PROIBIDOS_CONTROLADOR) {
            assertFalse(fonte.contains(token), CONTROLADOR + " contem '" + token + "' (CFG-03 / T-134-35)");
        }
    }

    @Test
    void existeUmaSoVerificacaoDaFaturacaoDentroDeCreatePagamento() throws IOException {
        String fonte = ler(CONTROLADOR);
        assertEquals(1, ocorrencias(fonte, "faturacao"), "exatamente uma ocorrencia de 'faturacao'");
        assertEquals(0, ocorrencias(fonte, "Faturacao"), "nenhuma ocorrencia de 'Faturacao'");
        assertEquals(1, ocorrencias(fonte, UNICA_VERIFICACAO));

        String handler = corpoDoMetodo(fonte, ASSINATURA_HANDLER);
        assertNotNull(handler, "createPagamento em falta");
        assertTrue(handler.contains(UNICA_VERIFICACAO), "a verificacao tem de estar no corpo de createPagamento");
        assertTrue(handler.contains("registarPagamentoLegado("), "o ramo desligado tem de chamar o metodo legado");
    }

    @Test
    void ramoDesligadoEhOTextoDeAntes() throws Exception {
        String corpo = corpoDoMetodo(ler(CONTROLADOR), ASSINATURA_LEGADO);
        assertNotNull(corpo, "registarPagamentoLegado(Pagamento pag) em falta");
        assertEquals(HASH_CORPO_LEGADO, sha256Normalizado(corpo),
                "o corpo de registarPagamentoLegado mudou -- o ramo com a faturacao desligada tem de ficar "
                        + "EXATAMENTE como estava (CFG-03)");
    }

    @Test
    void nenhumDosMetodosEhTransacionalEOGateSeMantem() {
        List<Method> metodos = Arrays.stream(ResourceController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("createPagamento") || m.getName().equals("registarPagamentoLegado"))
                .toList();
        assertEquals(2, metodos.size(), "esperados createPagamento e registarPagamentoLegado: " + metodos);
        for (Method m : metodos) {
            assertNull(m.getAnnotation(Transactional.class), m.getName() + " nao pode ser @Transactional (P-02)");
        }
        Method handler = metodos.stream().filter(m -> m.getName().equals("createPagamento")).findFirst().orElseThrow();
        PreAuthorize gate = handler.getAnnotation(PreAuthorize.class);
        assertNotNull(gate);
        assertEquals("hasAuthority('financeiro:edit')", gate.value());
        assertFalse(ResourceController.class.isAnnotationPresent(Transactional.class));
    }
}
