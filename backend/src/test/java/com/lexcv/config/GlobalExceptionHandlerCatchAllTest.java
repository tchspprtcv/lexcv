package com.lexcv.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WR-06 da revisão da Phase 133: o catch-all nunca devolve ao cliente a mensagem nem a classe da
 * exceção; devolve um texto fixo e uma referência que o servidor regista com a exceção completa.
 */
class GlobalExceptionHandlerCatchAllTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void ligarAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void desligarAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void naoEcoaMensagemNemClasseDaExcecao() {
        List<Exception> excecoes = List.of(
                new IllegalArgumentException("No enum constant com.lexcv.models.AmbienteFiscal.REAL"),
                new IllegalStateException("Série fiscal inexistente depois do INSERT ON CONFLICT: SIM-FR-2026"),
                new DataIntegrityViolationException(
                        "could not execute statement [ERROR: duplicate key value violates unique constraint "
                                + "\"uk_configuracao_fiscal_tenant\"] [update t_configuracao_fiscal set ...]"));

        for (Exception ex : excecoes) {
            ResponseEntity<Map<String, String>> resposta = handler.handleAllExceptions(ex);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, resposta.getStatusCode());
            Map<String, String> corpo = resposta.getBody();
            assertEquals(Map.of("message", GlobalExceptionHandler.MENSAGEM_ERRO_INTERNO,
                    "referencia", corpo.get("referencia")), corpo);
            assertDoesNotThrow(() -> UUID.fromString(corpo.get("referencia")));
            assertFalse(corpo.containsKey("error"));
            for (String valor : corpo.values()) {
                assertFalse(valor.contains(ex.getClass().getSimpleName()), valor);
                assertFalse(valor.contains("com.lexcv"), valor);
                assertFalse(valor.contains("SIM-FR"), valor);
                assertFalse(valor.contains("uk_configuracao_fiscal"), valor);
            }
        }
    }

    @Test
    void registaAReferenciaEAExcecaoNoLogDoServidor() {
        IllegalStateException ex = new IllegalStateException("detalhe interno");

        String referencia = handler.handleAllExceptions(ex).getBody().get("referencia");

        ILoggingEvent evento = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains(referencia))
                .findFirst()
                .orElseThrow(() -> new AssertionError("referência não registada no log"));
        assertEquals("ERROR", evento.getLevel().toString());
        assertTrue(evento.getThrowableProxy() != null, "a exceção tem de ir para o log");
        assertEquals(IllegalStateException.class.getName(), evento.getThrowableProxy().getClassName());
    }

    @Test
    void cadaErroTemUmaReferenciaPropria() {
        String a = handler.handleAllExceptions(new RuntimeException("x")).getBody().get("referencia");
        String b = handler.handleAllExceptions(new RuntimeException("x")).getBody().get("referencia");
        assertFalse(a.equals(b));
    }
}
