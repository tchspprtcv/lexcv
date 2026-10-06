package com.lexcv.fiscal.efatura;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalLinha;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Phase 136 (DFE-01): projeção imutável do snapshot de um documento fiscal com tudo o que o
 * {@link DfeXmlBuilder} precisa. Só valores simples: nenhuma entidade JPA atravessa a fronteira
 * do formato (o builder é puro e não toca na base de dados).
 *
 * <p>{@code horaEmissao} é {@code emitido_em} em {@code Atlantic/Cape_Verde}, truncado aos
 * segundos ({@code IssueTime} sem fuso). Numa NC, {@code iudOrigem} é o IUD da FR corrigida e
 * {@code numeroFormatadoOrigem} o seu número, usado na {@code Note} controlada. O texto livre
 * {@code motivo_texto} NÃO faz parte da projeção (T-136-32).
 */
public record DocumentoComunicavel(
        TipoDocumentoFiscal tipo,
        AmbienteFiscal ambiente,
        String serieCodigo,
        long numero,
        String numeroFormatado,
        LocalDate dataEmissao,
        LocalTime horaEmissao,
        String emitenteNif,
        String emitenteFirma,
        String emitenteMorada,
        String emitenteLocalidade,
        RegimeIva emitenteRegimeIva,
        String adquirenteNif,
        String adquirenteNome,
        String adquirenteMorada,
        String adquirenteLocalidade,
        String meioPagamentoCodigo,
        BigDecimal totalBase,
        BigDecimal totalIva,
        BigDecimal totalRetencao,
        BigDecimal totalDocumento,
        BigDecimal valorLiquido,
        MotivoNotaCredito motivoCodigo,
        String numeroFormatadoOrigem,
        String iudOrigem,
        Linha linha) {

    /** Fuso da hora de emissão. */
    public static final ZoneId FUSO = ZoneId.of("Atlantic/Cape_Verde");

    /** A linha única do documento. */
    public record Linha(
            int numero,
            String descricao,
            BigDecimal quantidade,
            BigDecimal precoUnitario,
            BigDecimal valorBase,
            BigDecimal taxaIva,
            String motivoIsencaoCodigo,
            BigDecimal taxaRetencao,
            BigDecimal valorRetencao) {

        public Linha {
            Objects.requireNonNull(descricao, "descricao");
            Objects.requireNonNull(quantidade, "quantidade");
            Objects.requireNonNull(precoUnitario, "precoUnitario");
            Objects.requireNonNull(valorBase, "valorBase");
            Objects.requireNonNull(taxaIva, "taxaIva");
            Objects.requireNonNull(valorRetencao, "valorRetencao");
        }
    }

    public DocumentoComunicavel {
        Objects.requireNonNull(tipo, "tipo");
        Objects.requireNonNull(ambiente, "ambiente");
        Objects.requireNonNull(serieCodigo, "serieCodigo");
        Objects.requireNonNull(dataEmissao, "dataEmissao");
        Objects.requireNonNull(horaEmissao, "horaEmissao");
        Objects.requireNonNull(emitenteRegimeIva, "emitenteRegimeIva");
        Objects.requireNonNull(meioPagamentoCodigo, "meioPagamentoCodigo");
        Objects.requireNonNull(totalBase, "totalBase");
        Objects.requireNonNull(totalIva, "totalIva");
        Objects.requireNonNull(totalRetencao, "totalRetencao");
        Objects.requireNonNull(totalDocumento, "totalDocumento");
        Objects.requireNonNull(valorLiquido, "valorLiquido");
        Objects.requireNonNull(linha, "linha");
    }

    /**
     * Projeta o snapshot. Uma NC exige o IUD da FR de origem e o seu número; uma FR não aceita
     * origem.
     *
     * @throws IllegalArgumentException NC sem IUD/número de origem, ou FR com origem
     */
    public static DocumentoComunicavel de(DocumentoFiscal d, DocumentoFiscalLinha l,
                                          String iudOrigemOuNulo, String numeroFormatadoOrigemOuNulo) {
        Objects.requireNonNull(d, "documento");
        Objects.requireNonNull(l, "linha");
        boolean temOrigem = iudOrigemOuNulo != null || numeroFormatadoOrigemOuNulo != null;
        switch (d.getTipo()) {
            case NC -> {
                if (iudOrigemOuNulo == null || iudOrigemOuNulo.isBlank()
                        || numeroFormatadoOrigemOuNulo == null || numeroFormatadoOrigemOuNulo.isBlank()) {
                    throw new IllegalArgumentException("Uma nota de crédito exige o IUD e o número da fatura-recibo de origem.");
                }
                if (d.getMotivoCodigo() == null) {
                    throw new IllegalArgumentException("Uma nota de crédito exige o motivo.");
                }
            }
            case FR -> {
                if (temOrigem) {
                    throw new IllegalArgumentException("Uma fatura-recibo não tem documento de origem.");
                }
            }
        }
        Linha linha = new Linha(
                l.getNumeroLinha(),
                l.getDescricao(),
                l.getQuantidade(),
                l.getPrecoUnitario(),
                l.getValorBase(),
                l.getTaxaIva(),
                l.getMotivoIsencaoCodigo(),
                l.getTaxaRetencao(),
                l.getValorRetencao());
        return new DocumentoComunicavel(
                d.getTipo(),
                d.getAmbiente(),
                d.getSerieCodigo(),
                d.getNumero(),
                d.getNumeroFormatado(),
                d.getDataEmissao(),
                d.getEmitidoEm().atZone(FUSO).toLocalTime().truncatedTo(ChronoUnit.SECONDS),
                d.getEmitenteNif(),
                d.getEmitenteFirma(),
                d.getEmitenteMorada(),
                d.getEmitenteLocalidade(),
                d.getEmitenteRegimeIva(),
                d.getAdquirenteNif(),
                d.getAdquirenteNome(),
                d.getAdquirenteMorada(),
                d.getAdquirenteLocalidade(),
                d.getMeioPagamentoCodigo(),
                d.getTotalBase(),
                d.getTotalIva(),
                d.getTotalRetencao(),
                d.getTotalDocumento(),
                d.getValorLiquido(),
                d.getMotivoCodigo(),
                numeroFormatadoOrigemOuNulo,
                iudOrigemOuNulo,
                linha);
    }
}
