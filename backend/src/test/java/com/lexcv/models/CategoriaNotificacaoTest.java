package com.lexcv.models;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 136 (DFE-07): {@code COMUNICACAO_FISCAL_FALHOU} é uma categoria NÃO silenciável (obrigação
 * fiscal, como {@code PRAZO_VENCIDO}); as restantes categorias não mudam de comportamento.
 * Phase 137 (ENTR-05): {@code EMAIL_FISCAL_FALHOU} também não é silenciável.
 */
class CategoriaNotificacaoTest {

    @Test
    void temExatamenteOnzeCategorias() {
        assertEquals(11, CategoriaNotificacao.values().length);
        assertEquals(List.of("FASE_ENTRADA", "DOCUMENTO_NOVO", "PROCESSO_ATRIBUIDO", "PARECER_ATRIBUIDO",
                        "PRAZO_PROXIMO", "PRAZO_VENCIDO", "EVENTO_PROXIMO", "EVENTO_VENCIDO", "HONORARIO_ATRASADO",
                        "COMUNICACAO_FISCAL_FALHOU", "EMAIL_FISCAL_FALHOU"),
                Arrays.stream(CategoriaNotificacao.values()).map(Enum::name).toList());
    }

    @Test
    void comunicacaoFiscalFalhouExisteENaoESilenciavel() {
        assertEquals(CategoriaNotificacao.COMUNICACAO_FISCAL_FALHOU,
                CategoriaNotificacao.fromString("COMUNICACAO_FISCAL_FALHOU").orElseThrow());
        assertFalse(CategoriaNotificacao.COMUNICACAO_FISCAL_FALHOU.isSilenciavel());
        assertFalse(CategoriaNotificacao.isSilenciavelCategoria("COMUNICACAO_FISCAL_FALHOU"));
    }

    @Test
    void emailFiscalFalhouEAUltimaENaoESilenciavel() {
        CategoriaNotificacao[] todas = CategoriaNotificacao.values();
        assertEquals(CategoriaNotificacao.EMAIL_FISCAL_FALHOU, todas[todas.length - 1]);
        assertEquals(CategoriaNotificacao.EMAIL_FISCAL_FALHOU,
                CategoriaNotificacao.fromString("EMAIL_FISCAL_FALHOU").orElseThrow());
        assertFalse(CategoriaNotificacao.EMAIL_FISCAL_FALHOU.isSilenciavel());
        assertFalse(CategoriaNotificacao.isSilenciavelCategoria("EMAIL_FISCAL_FALHOU"));
    }

    @Test
    void soPrazoVencidoEAsFalhasFiscaisNaoSaoSilenciaveis() {
        Set<CategoriaNotificacao> naoSilenciaveis = Arrays.stream(CategoriaNotificacao.values())
                .filter(c -> !c.isSilenciavel())
                .collect(Collectors.toSet());
        assertEquals(Set.of(CategoriaNotificacao.PRAZO_VENCIDO, CategoriaNotificacao.COMUNICACAO_FISCAL_FALHOU,
                        CategoriaNotificacao.EMAIL_FISCAL_FALHOU),
                naoSilenciaveis);
        for (CategoriaNotificacao c : CategoriaNotificacao.values()) {
            if (!naoSilenciaveis.contains(c)) {
                assertTrue(CategoriaNotificacao.isSilenciavelCategoria(c.name()), c.name());
            }
        }
    }

    @Test
    void fromStringNuncaLanca() {
        assertTrue(CategoriaNotificacao.fromString(null).isEmpty());
        assertTrue(CategoriaNotificacao.fromString("").isEmpty());
        assertTrue(CategoriaNotificacao.fromString("comunicacao_fiscal_falhou").isEmpty());
        assertFalse(CategoriaNotificacao.isSilenciavelCategoria("DESCONHECIDA"));
    }
}
