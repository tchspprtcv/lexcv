package com.lexcv.services.fiscal;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 137 (ENTR-03, ENTR-01, T-137-70): o email e o PDF nunca entram na transação do registo do
 * pagamento, da Nota de Crédito ou do caminho legado.
 *
 * <p>O único ponto de enfileiramento é {@link EnfileiramentoEntregaEmail#enfileirarAposAceite},
 * chamado pela transação que grava {@code ACEITE_SIMULADO} no job de comunicação. Esta guarda de
 * código-fonte prova que {@code PagamentoFaturadoService}, {@code NotaCreditoService} e
 * {@code ResourceController} não referem nenhuma classe da entrega por email nem do PDF, e que o
 * enfileiramento exige uma transação já aberta ({@code MANDATORY}).
 */
class EntregaForaDaTransacaoDoPagamentoTest {

    private static final Path RAIZ = Path.of("src/main/java/com/lexcv");

    private static final List<Path> CAMINHOS_DE_PAGAMENTO = List.of(
            RAIZ.resolve("services/fiscal/PagamentoFaturadoService.java"),
            RAIZ.resolve("services/fiscal/NotaCreditoService.java"),
            RAIZ.resolve("controllers/ResourceController.java"));

    private static final List<String> PROIBIDOS = List.of(
            "EnfileiramentoEntregaEmail",
            "FilaEntregaEmail",
            "EntregaEmailGateway",
            "PdfDocumentoFiscalService",
            "ProcessadorEntregaEmail");

    @Test
    void caminhosDePagamentoNaoReferemAEntregaNemOPdf() throws Exception {
        List<String> violacoes = new ArrayList<>();
        for (Path fonte : CAMINHOS_DE_PAGAMENTO) {
            assertTrue(Files.exists(fonte), "Fonte não encontrada: " + fonte.toAbsolutePath());
            String conteudo = Files.readString(fonte);
            for (String token : PROIBIDOS) {
                if (conteudo.contains(token)) {
                    violacoes.add(fonte.getFileName() + " refere " + token);
                }
            }
        }
        assertTrue(violacoes.isEmpty(), String.join("\n", violacoes));
    }

    @Test
    void enfileiramentoExigeATransacaoDeQuemChama() throws Exception {
        Method m = EnfileiramentoEntregaEmail.class.getMethod("enfileirarAposAceite", UUID.class, UUID.class,
                Instant.class);
        Transactional tx = m.getAnnotation(Transactional.class);
        assertNotNull(tx, "enfileirarAposAceite sem @Transactional");
        assertEquals(Propagation.MANDATORY, tx.propagation());
    }
}
