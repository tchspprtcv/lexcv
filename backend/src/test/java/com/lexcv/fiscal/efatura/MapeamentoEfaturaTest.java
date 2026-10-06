package com.lexcv.fiscal.efatura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.MetodoPagamento;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import jakarta.persistence.Entity;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Phase 136 (DFE-01, DFE-06): a tabela única de escolhas de formato, a projeção imutável do
 * snapshot e o registo da transmissão.
 */
class MapeamentoEfaturaTest {

    private static String xsd(String nome) throws IOException {
        try (InputStream in = MapeamentoEfaturaTest.class.getClassLoader()
                .getResourceAsStream("xsd/efatura/common/" + nome)) {
            assertThat(in).as(nome).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---- MapeamentoEfatura ----

    @Test
    void codigosDeTipo() {
        assertThat(MapeamentoEfatura.codigoTipo(TipoDocumentoFiscal.FR)).isEqualTo(2);
        assertThat(MapeamentoEfatura.codigoTipo(TipoDocumentoFiscal.NC)).isEqualTo(5);
        assertThat(MapeamentoEfatura.codigoTipoIud(TipoDocumentoFiscal.FR)).isEqualTo(2);
        assertThat(MapeamentoEfatura.codigoTipoIud(TipoDocumentoFiscal.NC)).isEqualTo(5);
    }

    @Test
    void repositorioELedDoAmbienteSimulado() {
        assertThat(MapeamentoEfatura.repositorioPara(AmbienteFiscal.SIMULADO)).isEqualTo(3);
        assertThat(MapeamentoEfatura.ledPara(AmbienteFiscal.SIMULADO)).isEqualTo(99999);
        assertThat(MapeamentoEfatura.LED_SIMULADO).isEqualTo(99999);
        assertThat(MapeamentoEfatura.REPOSITORIO_TESTE).isEqualTo(3);
    }

    @Test
    void constantesDeFormato() {
        assertThat(MapeamentoEfatura.UNIT_CODE).isEqualTo("EA");
        assertThat(MapeamentoEfatura.EMITTER_ID_FR).isEqualTo("HONORARIOS");
        assertThat(MapeamentoEfatura.EMITTER_ID_NC).isEqualTo("NOTACREDITO");
        assertThat(MapeamentoEfatura.emitterIdentification(TipoDocumentoFiscal.FR)).isEqualTo("HONORARIOS");
        assertThat(MapeamentoEfatura.emitterIdentification(TipoDocumentoFiscal.NC)).isEqualTo("NOTACREDITO");
        assertThat(MapeamentoEfatura.VERSAO_FORMATO).isEqualTo("2024-05-27");
        assertThat(MapeamentoEfatura.VERSAO_DFE).isEqualTo("1.0");
        assertThat(MapeamentoEfatura.MAX_NOME).isEqualTo(150);
        assertThat(MapeamentoEfatura.MAX_NUMERO).isEqualTo(999_999_999L);
        assertThat(MapeamentoEfatura.ISSUE_MODE_ONLINE).isEqualTo(1);
    }

    @Test
    void issueReasonCodeEDoisParaTodosOsMotivosEExisteNoXsd() throws IOException {
        for (MotivoNotaCredito m : MotivoNotaCredito.values()) {
            assertThat(MapeamentoEfatura.issueReasonCode(m)).as(m.name()).isEqualTo("2");
        }
        String tipos = xsd("CV_EFatura_Types_v1.0.xsd");
        int inicio = tipos.indexOf("<x:simpleType name=\"stIssueReasonCode\">");
        int fim = tipos.indexOf("</x:simpleType>", inicio);
        assertThat(inicio).isPositive();
        assertThat(tipos.substring(inicio, fim)).contains("<x:enumeration value=\"2\" />");
    }

    @Test
    void codigosDeMeioDePagamentoExistemNaListaD19B() throws IOException {
        String lista = xsd("UNECE_PaymentMeansCode_D19B.xsd");
        for (MetodoPagamento m : MetodoPagamento.values()) {
            assertThat(lista).as(m.name())
                    .contains("<xsd:enumeration value=\"" + m.codigoMeioPagamento() + "\">");
        }
    }

    @Test
    void notaDaNotaDeCreditoEControlada() {
        String nota = MapeamentoEfatura.notaNotaCredito(MotivoNotaCredito.CORRECAO_VALOR, "SIM-FR-2026/1");
        assertThat(nota).isEqualTo("Nota de crédito: Correção de valor — SIM-FR-2026/1");
        assertThat(nota.length()).isGreaterThanOrEqualTo(10);
    }

    // ---- DocumentoComunicavel ----

    static DocumentoFiscal fr() {
        return DocumentoFiscal.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID())
                .tipo(TipoDocumentoFiscal.FR).ambiente(AmbienteFiscal.SIMULADO)
                .serieId(UUID.randomUUID()).serieCodigo("SIM-FR-2026").ano(2026).numero(1L)
                .numeroFormatado("SIM-FR-2026/1").dataEmissao(LocalDate.of(2026, 6, 15))
                .emitidoEm(Instant.parse("2026-06-15T12:34:56.789Z"))
                .emitenteNif("512345679").emitenteFirma("Silva & Associados").emitenteMorada("Rua A")
                .emitenteLocalidade("Praia").emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("123456789").adquirenteNome("Ana Lopes").adquirenteMorada("Rua B")
                .adquirenteLocalidade("Mindelo")
                .clienteId(UUID.randomUUID()).processoId(UUID.randomUUID()).honorarioId(1).pagamentoId(1)
                .metodoPagamento("DINHEIRO").meioPagamentoCodigo("10").moeda("CVE")
                .taxaIva(new BigDecimal("15.0000")).totalBase(new BigDecimal("100.00"))
                .totalIva(new BigDecimal("15.00")).totalRetencao(new BigDecimal("0.00"))
                .totalDocumento(new BigDecimal("115.00")).valorLiquido(new BigDecimal("115.00"))
                .chaveIdempotencia(UUID.randomUUID())
                .build();
    }

    static DocumentoFiscal nc() {
        return DocumentoFiscal.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID())
                .tipo(TipoDocumentoFiscal.NC).ambiente(AmbienteFiscal.SIMULADO)
                .serieId(UUID.randomUUID()).serieCodigo("SIM-NC-2026").ano(2026).numero(1L)
                .numeroFormatado("SIM-NC-2026/1").dataEmissao(LocalDate.of(2026, 6, 16))
                .emitidoEm(Instant.parse("2026-06-16T09:00:00Z"))
                .emitenteNif("512345679").emitenteFirma("Silva & Associados").emitenteMorada("Rua A")
                .emitenteRegimeIva(RegimeIva.NORMAL)
                .adquirenteNif("123456789").adquirenteNome("Ana Lopes").adquirenteMorada("Rua B")
                .clienteId(UUID.randomUUID()).processoId(UUID.randomUUID()).honorarioId(1).pagamentoId(2)
                .documentoOrigemId(UUID.randomUUID()).motivoCodigo(MotivoNotaCredito.CORRECAO_VALOR)
                .motivoTexto("texto livre")
                .metodoPagamento("DINHEIRO").meioPagamentoCodigo("10").moeda("CVE")
                .taxaIva(new BigDecimal("15.0000")).totalBase(new BigDecimal("50.00"))
                .totalIva(new BigDecimal("7.50")).totalRetencao(new BigDecimal("0.00"))
                .totalDocumento(new BigDecimal("57.50")).valorLiquido(new BigDecimal("57.50"))
                .chaveIdempotencia(UUID.randomUUID())
                .build();
    }

    static DocumentoFiscalLinha linha(BigDecimal base) {
        return DocumentoFiscalLinha.builder()
                .id(UUID.randomUUID()).tenantId(UUID.randomUUID()).documentoFiscalId(UUID.randomUUID())
                .numeroLinha(1).descricao("Honorários").quantidade(new BigDecimal("1.0000"))
                .precoUnitario(base).valorBase(base).taxaIva(new BigDecimal("15.0000"))
                .valorIva(new BigDecimal("15.00")).valorRetencao(new BigDecimal("0.00"))
                .totalLinha(new BigDecimal("115.00"))
                .build();
    }

    @Test
    void deFrCarregaTodosOsCampos() {
        DocumentoFiscal d = fr();
        DocumentoComunicavel c = DocumentoComunicavel.de(d, linha(new BigDecimal("100.00")), null, null);

        assertThat(c.tipo()).isEqualTo(TipoDocumentoFiscal.FR);
        assertThat(c.ambiente()).isEqualTo(AmbienteFiscal.SIMULADO);
        assertThat(c.serieCodigo()).isEqualTo("SIM-FR-2026");
        assertThat(c.numero()).isEqualTo(1L);
        assertThat(c.numeroFormatado()).isEqualTo("SIM-FR-2026/1");
        assertThat(c.dataEmissao()).isEqualTo(LocalDate.of(2026, 6, 15));
        // 12:34:56Z em Cabo Verde (UTC-1), truncado aos segundos.
        assertThat(c.horaEmissao()).isEqualTo(LocalTime.of(11, 34, 56));
        assertThat(c.emitenteNif()).isEqualTo("512345679");
        assertThat(c.emitenteFirma()).isEqualTo("Silva & Associados");
        assertThat(c.emitenteMorada()).isEqualTo("Rua A");
        assertThat(c.emitenteLocalidade()).isEqualTo("Praia");
        assertThat(c.emitenteRegimeIva()).isEqualTo(RegimeIva.NORMAL);
        assertThat(c.adquirenteNif()).isEqualTo("123456789");
        assertThat(c.adquirenteNome()).isEqualTo("Ana Lopes");
        assertThat(c.adquirenteMorada()).isEqualTo("Rua B");
        assertThat(c.adquirenteLocalidade()).isEqualTo("Mindelo");
        assertThat(c.meioPagamentoCodigo()).isEqualTo("10");
        assertThat(c.totalBase()).isEqualByComparingTo("100.00");
        assertThat(c.totalIva()).isEqualByComparingTo("15.00");
        assertThat(c.totalRetencao()).isEqualByComparingTo("0");
        assertThat(c.totalDocumento()).isEqualByComparingTo("115.00");
        assertThat(c.valorLiquido()).isEqualByComparingTo("115.00");
        assertThat(c.motivoCodigo()).isNull();
        assertThat(c.iudOrigem()).isNull();
        assertThat(c.numeroFormatadoOrigem()).isNull();
        assertThat(c.linha().numero()).isEqualTo(1);
        assertThat(c.linha().descricao()).isEqualTo("Honorários");
        assertThat(c.linha().quantidade()).isEqualByComparingTo("1");
        assertThat(c.linha().precoUnitario()).isEqualByComparingTo("100.00");
        assertThat(c.linha().valorBase()).isEqualByComparingTo("100.00");
        assertThat(c.linha().taxaIva()).isEqualByComparingTo("15");
        assertThat(c.linha().valorRetencao()).isEqualByComparingTo("0");
    }

    @Test
    void deNcCarregaAOrigem() {
        DocumentoComunicavel c = DocumentoComunicavel.de(nc(), linha(new BigDecimal("50.00")),
                "CV3260615512345679999990200000000112345678904", "SIM-FR-2026/1");
        assertThat(c.tipo()).isEqualTo(TipoDocumentoFiscal.NC);
        assertThat(c.motivoCodigo()).isEqualTo(MotivoNotaCredito.CORRECAO_VALOR);
        assertThat(c.iudOrigem()).isEqualTo("CV3260615512345679999990200000000112345678904");
        assertThat(c.numeroFormatadoOrigem()).isEqualTo("SIM-FR-2026/1");
    }

    @Test
    void ncSemIudDeOrigemERecusada() {
        assertThatThrownBy(() -> DocumentoComunicavel.de(nc(), linha(new BigDecimal("50.00")), null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DocumentoComunicavel.de(nc(), linha(new BigDecimal("50.00")), " ", "SIM-FR-2026/1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void frComOrigemERecusada() {
        assertThatThrownBy(() -> DocumentoComunicavel.de(fr(), linha(new BigDecimal("100.00")), "CV3", "SIM-FR-2026/1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nenhumComponenteEUmaEntidadeJpa() {
        for (Class<?> tipo : new Class<?>[]{DocumentoComunicavel.class, DocumentoComunicavel.Linha.class}) {
            assertThat(tipo.isRecord()).isTrue();
            for (RecordComponent rc : tipo.getRecordComponents()) {
                assertThat(rc.getType().isAnnotationPresent(Entity.class))
                        .as(tipo.getSimpleName() + "." + rc.getName()).isFalse();
            }
        }
    }

    // ---- TransmissaoEfatura ----

    @Test
    void transmissaoValida() {
        TransmissaoEfatura t = new TransmissaoEfatura("512345679", "LEXCVSIM", "LexCV", "3.0.0");
        assertThat(t.softwareCodigo()).isEqualTo("LEXCVSIM");
    }

    @Test
    void transmissaoInvalidaERecusada() {
        assertThatThrownBy(() -> new TransmissaoEfatura("012345679", "LEXCVSIM", "LexCV", "3.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransmissaoEfatura(null, "LEXCVSIM", "LexCV", "3.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransmissaoEfatura("512345679", "lexcv", "LexCV", "3.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransmissaoEfatura("512345679", "ABCDEFGHIJK", "LexCV", "3.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransmissaoEfatura("512345679", "LEXCVSIM", "Lx", "3.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransmissaoEfatura("512345679", "LEXCVSIM", "L".repeat(151), "3.0.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransmissaoEfatura("512345679", "LEXCVSIM", "LexCV", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TransmissaoEfatura("512345679", "LEXCVSIM", "LexCV", "1".repeat(51)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- RecusaFormatoEfatura ----

    @Test
    void recusaTemCodigoEMensagemFixa() {
        RecusaFormatoEfatura r = new RecusaFormatoEfatura(RecusaFormatoEfatura.Codigo.FIRMA_EXCEDE_150);
        assertThat(r.codigo()).isEqualTo("FIRMA_EXCEDE_150");
        assertThat(r.getMessage()).isEqualTo(r.mensagem()).isNotBlank();
        assertThat(r.getCause()).isNull();
        for (RecusaFormatoEfatura.Codigo c : RecusaFormatoEfatura.Codigo.values()) {
            assertThat(c.mensagem()).as(c.name()).isNotBlank().hasSizeLessThanOrEqualTo(500);
        }
        assertThat(RecusaFormatoEfatura.Codigo.values()).extracting(Enum::name)
                .contains("FIRMA_EXCEDE_150", "NUMERO_FORA_DO_LIMITE", "TEXTO_INVALIDO", "ORIGEM_SEM_IUD");
    }
}
