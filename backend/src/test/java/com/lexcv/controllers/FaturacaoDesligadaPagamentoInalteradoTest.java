package com.lexcv.controllers;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda de regressao CFG-03 (Phase 133, Plano 05, ROADMAP criterio 3, T-133-25): o registo de um
 * pagamento de honorario NAO ganha nenhum caminho fiscal nesta fase. Prova por leitura do
 * ficheiro fonte -- {@code ResourceController} e {@code Pagamento} nao referem nenhum simbolo da
 * fundacao fiscal.
 *
 * <p>Complementa o default de dados: {@code ConfiguracaoFiscal.ativa} e {@code false} por
 * omissao, por isso nenhum escritorio muda de comportamento ate um administrador ativar a
 * faturacao -- e, mesmo ativada, na Phase 133 a ativacao nao toca no fluxo de pagamento.
 *
 * <p><b>Phase 134</b> (emissao da Fatura-Recibo a partir do pagamento) vai, de proposito, alterar
 * este teste quando ligar o pagamento a emissao fiscal -- falhar aqui antes disso e uma regressao.
 */
class FaturacaoDesligadaPagamentoInalteradoTest {

    private static final List<String> TOKENS_FISCAIS = List.of(
            "ConfiguracaoFiscal", "NumeracaoService", "SerieFiscal", "faturacao", "Faturacao");

    private static final List<Path> FICHEIROS = List.of(
            Path.of("src/main/java/com/lexcv/controllers/ResourceController.java"),
            Path.of("src/main/java/com/lexcv/models/Pagamento.java"));

    @Test
    void registoDePagamentoNaoTemCaminhoFiscalNestaFase() throws IOException {
        for (Path ficheiro : FICHEIROS) {
            assertTrue(Files.isRegularFile(ficheiro), "Ficheiro fonte em falta: " + ficheiro);
            String fonte = Files.readString(ficheiro, StandardCharsets.UTF_8);
            for (String token : TOKENS_FISCAIS) {
                assertFalse(fonte.contains(token),
                        ficheiro + " contem '" + token + "' -- a Phase 133 nao liga o pagamento a faturacao (CFG-03)");
            }
        }
    }
}
