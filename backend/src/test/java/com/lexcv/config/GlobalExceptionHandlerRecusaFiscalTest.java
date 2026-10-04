package com.lexcv.config;

import com.lexcv.exceptions.RecusaFiscalException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 133 (T-133-01): a fiscal refusal becomes {message, code, campo?} with the refusal's own
 * status -- never the catch-all 500 that echoes the exception class name.
 */
class GlobalExceptionHandlerRecusaFiscalTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void recusaSemCampoDevolveMessageECode() {
        ResponseEntity<Map<String, String>> resposta = handler.handleRecusaFiscal(new RecusaFiscalException(
                HttpStatus.UNPROCESSABLE_ENTITY, "CONFIGURACAO_FISCAL_INCOMPLETA",
                "Os dados fiscais estão incompletos."));

        assertEquals(422, resposta.getStatusCode().value());
        Map<String, String> corpo = resposta.getBody();
        assertNotNull(corpo);
        assertEquals("Os dados fiscais estão incompletos.", corpo.get("message"));
        assertEquals("CONFIGURACAO_FISCAL_INCOMPLETA", corpo.get("code"));
        assertFalse(corpo.containsKey("campo"));
        assertFalse(corpo.containsKey("error"));
        assertEquals(2, corpo.size());
    }

    @Test
    void recusaComCampoIncluiCampo() {
        ResponseEntity<Map<String, String>> resposta = handler.handleRecusaFiscal(new RecusaFiscalException(
                HttpStatus.CONFLICT, "NIF_BLOQUEADO", "O NIF já não pode ser alterado.", "nif"));

        assertEquals(409, resposta.getStatusCode().value());
        Map<String, String> corpo = resposta.getBody();
        assertNotNull(corpo);
        assertEquals("nif", corpo.get("campo"));
        assertEquals("NIF_BLOQUEADO", corpo.get("code"));
        assertEquals("O NIF já não pode ser alterado.", corpo.get("message"));
        assertFalse(corpo.containsKey("error"));
        assertFalse(corpo.values().stream().anyMatch(v -> v.contains("RecusaFiscalException")));
    }

    @Test
    void excecaoExpoeGetters() {
        RecusaFiscalException ex = new RecusaFiscalException(HttpStatus.CONFLICT, "X", "Mensagem.");
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("X", ex.getCodigo());
        assertNull(ex.getCampo());
        assertEquals("Mensagem.", ex.getMessage());
    }
}
