package com.lexcv.fiscal.pdf;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Phase 137 (ENTR-01): entrada imutável do renderer do PDF fiscal, construída APENAS a partir da
 * fotografia guardada do documento (EMIS-08) -- nunca das linhas vivas de {@code Cliente} ou
 * {@code ConfiguracaoFiscal}. Não guarda referências a entidades.
 *
 * <p>{@code iud} é obrigatório: o PDF só é gerado depois de o XML (e o seu IUD) existir.
 * {@code origem} só existe numa Nota de Crédito. {@code simulado} é {@code true} para
 * {@link AmbienteFiscal#SIMULADO}, o único ambiente deste build.
 */
public record DadosPdfDocumentoFiscal(
        TipoDocumentoFiscal tipo,
        String numeroFormatado,
        String serieCodigo,
        LocalDate dataEmissao,
        Emitente emitente,
        Adquirente adquirente,
        List<Linha> linhas,
        Totais totais,
        String moeda,
        String iud,
        Origem origem,
        String motivoRotulo,
        String motivoTexto,
        boolean simulado
) {

    public DadosPdfDocumentoFiscal {
        Objects.requireNonNull(tipo, "tipo");
        if (numeroFormatado == null || numeroFormatado.isBlank()) {
            throw new IllegalArgumentException("Número do documento em falta");
        }
        if (iud == null || iud.isBlank()) {
            throw new IllegalArgumentException("IUD em falta");
        }
        Objects.requireNonNull(dataEmissao, "dataEmissao");
        Objects.requireNonNull(emitente, "emitente");
        Objects.requireNonNull(adquirente, "adquirente");
        Objects.requireNonNull(totais, "totais");
        linhas = linhas == null ? List.of() : List.copyOf(linhas);
    }

    /** Fotografia do escritório emitente. {@code isento} quando o regime é {@link RegimeIva#ISENTO}. */
    public record Emitente(String firma, String nif, String morada, String localidade, String regimeIvaRotulo,
                           boolean isento, String motivoIsencaoCodigo, String motivoIsencaoDescricao) {
    }

    /** Fotografia do cliente adquirente; {@code nif} em branco = "Consumidor final". */
    public record Adquirente(String nome, String nif, String morada, String localidade) {
    }

    public record Linha(String descricao, BigDecimal quantidade, BigDecimal precoUnitario, BigDecimal valorBase,
                        BigDecimal taxaIva, BigDecimal valorIva, String motivoIsencaoCodigo,
                        BigDecimal taxaRetencao, BigDecimal valorRetencao, BigDecimal totalLinha) {
    }

    /**
     * Taxas em percentagem (ex.: {@code 15.0000}). {@code totalDocumento} = base + IVA;
     * {@code valorLiquido} = total menos a retenção (o valor efetivamente recebido / creditado).
     */
    public record Totais(BigDecimal base, BigDecimal iva, BigDecimal retencao, BigDecimal totalDocumento,
                         BigDecimal taxaIva, BigDecimal taxaRetencao, BigDecimal valorLiquido) {
    }

    /** NC: a Fatura-Recibo corrigida. */
    public record Origem(String numeroFormatado, LocalDate dataEmissao) {
    }

    /**
     * Copia os valores da fotografia guardada.
     *
     * @param origemOuNulo NC: a FR de origem, do mesmo tenant (quem chama garante-o); nulo numa FR
     */
    public static DadosPdfDocumentoFiscal de(DocumentoFiscal d, List<DocumentoFiscalLinha> linhas, String iud,
                                             DocumentoFiscal origemOuNulo) {
        Objects.requireNonNull(d, "documento");
        List<Linha> copia = (linhas == null ? List.<DocumentoFiscalLinha>of() : linhas).stream()
                .sorted(Comparator.comparing(DocumentoFiscalLinha::getNumeroLinha,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(l -> new Linha(l.getDescricao(), l.getQuantidade(), l.getPrecoUnitario(), l.getValorBase(),
                        l.getTaxaIva(), l.getValorIva(), l.getMotivoIsencaoCodigo(), l.getTaxaRetencao(),
                        l.getValorRetencao(), l.getTotalLinha()))
                .toList();
        RegimeIva regime = d.getEmitenteRegimeIva();
        return new DadosPdfDocumentoFiscal(
                d.getTipo(),
                d.getNumeroFormatado(),
                d.getSerieCodigo(),
                d.getDataEmissao(),
                new Emitente(d.getEmitenteFirma(), d.getEmitenteNif(), d.getEmitenteMorada(),
                        d.getEmitenteLocalidade(), regime == null ? null : regime.rotulo(),
                        regime == RegimeIva.ISENTO, d.getEmitenteMotivoIsencaoCodigo(),
                        d.getEmitenteMotivoIsencaoDescricao()),
                new Adquirente(d.getAdquirenteNome(), d.getAdquirenteNif(), d.getAdquirenteMorada(),
                        d.getAdquirenteLocalidade()),
                copia,
                new Totais(d.getTotalBase(), d.getTotalIva(), d.getTotalRetencao(), d.getTotalDocumento(),
                        d.getTaxaIva(), d.getTaxaRetencao(), d.getValorLiquido()),
                d.getMoeda(),
                iud,
                origemOuNulo == null ? null
                        : new Origem(origemOuNulo.getNumeroFormatado(), origemOuNulo.getDataEmissao()),
                d.getMotivoCodigo() == null ? null : d.getMotivoCodigo().rotulo(),
                d.getMotivoTexto(),
                // Fail-safe (DFE-06): só um ambiente real conhecido tiraria a marca; hoje não existe nenhum.
                d.getAmbiente() == null || d.getAmbiente() == AmbienteFiscal.SIMULADO);
    }
}
