package com.lexcv.dtos;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Phase 136 (DFE-04, DFE-06): resumo da comunicação no detalhe e as suas regras de visibilidade. */
class ComunicacaoFiscalResumoTest {

    private static final Instant ULTIMA = Instant.parse("2026-06-15T13:00:00Z");
    private static final Instant PROXIMA = Instant.parse("2026-06-15T13:02:00Z");
    private static final String IUD = "CV326061551234567999999020000000011234567890" + "5";

    private static ComunicacaoFiscal comunicacao(EstadoComunicacaoFiscal estado, String erro, Instant proxima) {
        return ComunicacaoFiscal.builder()
                .tenantId(UUID.randomUUID()).documentoFiscalId(UUID.randomUUID())
                .ambiente(AmbienteFiscal.SIMULADO).estado(estado).tentativas(2)
                .ultimaTentativaEm(ULTIMA).ultimoErro(erro).ultimoErroCodigo(erro == null ? null : "XSD_INVALIDO")
                .proximaTentativaEm(proxima).build();
    }

    @Test
    void pendenteComTentativasMostraProximaTentativaEOUltimoErro() {
        ComunicacaoFiscalResumo r = ComunicacaoFiscalResumo.de(
                comunicacao(EstadoComunicacaoFiscal.PENDENTE, "Serviço indisponível", PROXIMA), null);

        assertEquals("PENDENTE", r.estado());
        assertEquals("SIMULADO", r.ambiente());
        assertNull(r.iud());
        assertEquals(2, r.tentativas());
        assertEquals(ULTIMA, r.ultimaTentativaEm());
        // IN-04: a razão das novas tentativas fica visível enquanto a linha está pendente.
        assertEquals("Serviço indisponível", r.ultimoErro());
        assertEquals(PROXIMA, r.proximaTentativaEm());
    }

    @Test
    void pendenteSemTentativasNaoMostraErro() {
        ComunicacaoFiscal c = comunicacao(EstadoComunicacaoFiscal.PENDENTE, "Antigo", PROXIMA);
        c.setTentativas(0);
        assertNull(ComunicacaoFiscalResumo.de(c, null).ultimoErro());
    }

    @Test
    void erroMostraOUltimoErroESemProximaTentativa() {
        ComunicacaoFiscalResumo r = ComunicacaoFiscalResumo.de(
                comunicacao(EstadoComunicacaoFiscal.ERRO, "Tentativas esgotadas.", PROXIMA), IUD);

        assertEquals("ERRO", r.estado());
        assertEquals(IUD, r.iud());
        assertEquals("Tentativas esgotadas.", r.ultimoErro());
        assertNull(r.proximaTentativaEm());
    }

    @Test
    void rejeitadoMostraOUltimoErro() {
        ComunicacaoFiscalResumo r = ComunicacaoFiscalResumo.de(
                comunicacao(EstadoComunicacaoFiscal.REJEITADO, "Documento recusado pela validação.", null), IUD);

        assertEquals("Documento recusado pela validação.", r.ultimoErro());
    }

    @Test
    void aceiteSimuladoEscondeUmErroAntigo() {
        ComunicacaoFiscalResumo r = ComunicacaoFiscalResumo.de(
                comunicacao(EstadoComunicacaoFiscal.ACEITE_SIMULADO, "Erro de uma tentativa anterior", PROXIMA), IUD);

        assertEquals("ACEITE_SIMULADO", r.estado());
        assertNull(r.ultimoErro());
        assertNull(r.proximaTentativaEm());
        assertEquals(IUD, r.iud());
    }

    @Test
    void semLinhaDeComunicacaoNaoHaResumo() {
        assertNull(ComunicacaoFiscalResumo.de(null, IUD));
    }

    @Test
    void detalheExpoeOResumoEOsOverloadsAntigosDaoNulo() {
        DocumentoFiscal fr = DocumentoFiscal.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).tipo(TipoDocumentoFiscal.FR)
                .ambiente(AmbienteFiscal.SIMULADO).serieId(UUID.randomUUID()).serieCodigo("SIM-FR-2026").ano(2026)
                .numero(1L).numeroFormatado("SIM-FR-2026/1").dataEmissao(LocalDate.of(2026, 6, 15))
                .emitidoEm(ULTIMA).emitenteNif("512345679").emitenteFirma("Firma").emitenteMorada("Morada")
                .emitenteRegimeIva(RegimeIva.NORMAL).adquirenteNif("234567891").adquirenteNome("Maria")
                .adquirenteMorada("Rua 2").clienteId(UUID.randomUUID()).processoId(UUID.randomUUID()).honorarioId(1)
                .pagamentoId(1).metodoPagamento("DINHEIRO").meioPagamentoCodigo("10").moeda("CVE")
                .taxaIva(new BigDecimal("15")).totalBase(new BigDecimal("100.00")).totalIva(new BigDecimal("15.00"))
                .totalRetencao(BigDecimal.ZERO).totalDocumento(new BigDecimal("115.00"))
                .valorLiquido(new BigDecimal("115.00")).chaveIdempotencia(UUID.randomUUID()).build();
        ComunicacaoFiscalResumo resumo = ComunicacaoFiscalResumo.de(
                comunicacao(EstadoComunicacaoFiscal.PENDENTE, null, PROXIMA), null);

        DocumentoFiscalDetalheResponse comResumo = DocumentoFiscalDetalheResponse.de(fr, List.of(),
                EstadoComunicacaoFiscal.PENDENTE, null, List.of(), resumo);

        assertSame(resumo, comResumo.comunicacao());
        assertEquals("PENDENTE", comResumo.estadoComunicacao());
        assertNull(DocumentoFiscalDetalheResponse.de(fr, List.of(), EstadoComunicacaoFiscal.PENDENTE).comunicacao());
        assertNull(DocumentoFiscalDetalheResponse.de(fr, List.of(), null, null, List.of()).comunicacao());
    }
}
