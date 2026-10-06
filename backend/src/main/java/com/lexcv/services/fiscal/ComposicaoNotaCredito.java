package com.lexcv.services.fiscal;

import com.lexcv.dtos.NotaCreditoRequest;
import com.lexcv.exceptions.RecusaFiscalException;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.TipoDocumentoFiscal;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Phase 135 (NCRD-01, NCRD-02, P-13): a ÚNICA função que decide os montantes de uma Nota de
 * Crédito. A pré-visualização e a emissão chamam-na com os mesmos argumentos, por isso recusam com
 * os mesmos códigos e calculam os mesmos valores ("frontend burro", CONTEXT).
 *
 * <p>Pura: sem Spring beans, sem base de dados, sem relógio. {@code hoje} (Cabo Verde) é calculado
 * pelo chamador; as NC anteriores da FR são lidas pelo chamador sob os locks da emissão.
 *
 * <p>Ordem das verificações (a primeira falha ganha): a origem é uma FR ({@code NC_SOBRE_NC}) →
 * firma da FR ≤ 150 ({@code FIRMA_EXCEDE_150}, WR-04 da 136) →
 * tipo → motivo → texto do motivo → valor (TOTAL sem valor; PARCIAL com valor válido) → teto
 * cumulativo ({@code NC_EXCEDE_ORIGINAL}, 409).
 *
 * <p>Montantes:
 * <ul>
 *   <li>Remanescente por coluna = coluna da FR menos a soma dessa coluna nas NC anteriores.</li>
 *   <li>TOTAL, ou PARCIAL igual ao total remanescente: credita exatamente os remanescentes, sem
 *       recalcular -- a soma das NC fecha cada coluna da FR ao cêntimo.</li>
 *   <li>PARCIAL inferior ao remanescente: {@link CalculoFiscal#calcular} com o regime, a taxa de IVA
 *       e a taxa de retenção FOTOGRAFADOS na FR original (nunca a taxa vigente hoje) e o mesmo
 *       arredondamento HALF_UP.</li>
 *   <li>Clamp (P-13): os arredondamentos de vários créditos parciais podem somar mais do que a FR
 *       numa coluna. A base é limitada ao remanescente da base (o excedente passa para o IVA), o IVA
 *       ao remanescente do IVA (o excedente volta para a base). A retenção é recalculada sobre a
 *       base já limitada ({@code round(base * taxaRetencao / 100, HALF_UP)}) e limitada ao
 *       remanescente da retenção. Como {@code valor <= remBase + remIva}, as duas primeiras limitações nunca entram
 *       em conflito; nenhuma coluna acumulada passa a da FR.</li>
 * </ul>
 * Os montantes da NC são magnitudes positivas; o sinal negativo vive só no estorno.
 */
public final class ComposicaoNotaCredito {

    static final String MSG_EXCEDE = "O valor indicado excede o que ainda pode ser creditado nesta fatura-recibo. "
            + "Reduza o valor ou escolha crédito total.";
    static final String MSG_TOTALMENTE_CREDITADA = "Esta fatura-recibo já foi totalmente creditada.";

    private static final BigDecimal CEM = new BigDecimal("100");

    private ComposicaoNotaCredito() {
    }

    /**
     * @param origem          a Fatura-Recibo a creditar (já carregada no tenant do chamador)
     * @param notasAnteriores NC já emitidas sobre {@code origem} (documentos de outro tipo são ignorados)
     * @param req             pedido do utilizador
     * @param hoje            data de emissão (Cabo Verde), calculada pelo chamador
     * @throws RecusaFiscalException 422 (pedido inválido, NC sobre NC) ou 409 (teto excedido)
     */
    public static ProjetoNotaCredito compor(DocumentoFiscal origem, List<DocumentoFiscal> notasAnteriores,
                                            NotaCreditoRequest req, LocalDate hoje) {
        ValidacaoNotaCredito.exigirFaturaRecibo(origem);
        // WR-04 (136): a NC copia a firma da FR; acima de 150 caracteres nunca seria comunicada.
        ValidacaoEmissao.validarFirmaEmitente(origem.getEmitenteFirma(), true);
        TipoCredito tipo = ValidacaoNotaCredito.validarTipo(req.tipo());
        MotivoNotaCredito motivo = ValidacaoNotaCredito.validarMotivo(req.motivoCodigo());
        String motivoTexto = ValidacaoNotaCredito.validarMotivoTexto(req.motivoTexto());
        BigDecimal valor = null;
        if (tipo == TipoCredito.TOTAL) {
            if (req.valor() != null) {
                throw ValidacaoNotaCredito.recusaValor();
            }
        } else {
            valor = ValidacaoNotaCredito.validarValorParcial(req.valor());
        }

        BigDecimal creditadoBase = BigDecimal.ZERO;
        BigDecimal creditadoIva = BigDecimal.ZERO;
        BigDecimal creditadoRetencao = BigDecimal.ZERO;
        for (DocumentoFiscal nc : notasAnteriores) {
            if (nc.getTipo() != TipoDocumentoFiscal.NC) {
                continue;
            }
            creditadoBase = creditadoBase.add(nc.getTotalBase());
            creditadoIva = creditadoIva.add(nc.getTotalIva());
            creditadoRetencao = creditadoRetencao.add(nc.getTotalRetencao());
        }
        BigDecimal creditadoTotal = totalCreditado(notasAnteriores);
        BigDecimal remBase = origem.getTotalBase().subtract(creditadoBase).setScale(2);
        BigDecimal remIva = origem.getTotalIva().subtract(creditadoIva).setScale(2);
        BigDecimal remRetencao = origem.getTotalRetencao().subtract(creditadoRetencao).setScale(2);
        BigDecimal remTotal = valorCreditavelRestante(origem, notasAnteriores);

        if (remTotal.signum() <= 0) {
            throw excede(MSG_TOTALMENTE_CREDITADA);
        }
        if (valor != null && valor.compareTo(remTotal) > 0) {
            throw excede(MSG_EXCEDE);
        }

        CalculoFiscal.ResultadoCalculo calculo;
        if (valor == null || valor.compareTo(remTotal) == 0) {
            calculo = new CalculoFiscal.ResultadoCalculo(remBase, remIva, origem.getTaxaIva(), remRetencao,
                    origem.getTaxaRetencao(), remTotal, remTotal.subtract(remRetencao));
        } else {
            CalculoFiscal.ResultadoCalculo c = CalculoFiscal.calcular(valor, origem.getEmitenteRegimeIva(),
                    origem.getTaxaIva(), origem.getTaxaRetencao());
            BigDecimal base = c.base().min(remBase);
            BigDecimal iva = valor.subtract(base);
            if (iva.compareTo(remIva) > 0) {
                iva = remIva;
                base = valor.subtract(iva);
            }
            // WR-04 da revisão: a retenção é proporcional à base DESTA NC, por isso é recalculada
            // sobre a base já limitada (a mesma regra HALF_UP do CalculoFiscal) e só depois limitada
            // ao remanescente da retenção.
            BigDecimal retencao = c.taxaRetencao() == null
                    ? BigDecimal.ZERO.setScale(2)
                    : base.multiply(c.taxaRetencao()).divide(CEM, 2, RoundingMode.HALF_UP).min(remRetencao);
            calculo = new CalculoFiscal.ResultadoCalculo(base, iva, c.taxaIva(), retencao, c.taxaRetencao(),
                    valor, valor.subtract(retencao));
        }

        return new ProjetoNotaCredito(
                origem.getId(),
                origem.getNumeroFormatado(),
                tipo,
                motivo,
                motivoTexto,
                hoje,
                TextoDocumentoFiscal.descricaoLinhaNotaCredito(origem.getNumeroFormatado()),
                origem.getEmitenteRegimeIva(),
                calculo,
                origem.getTotalDocumento(),
                creditadoTotal,
                remTotal,
                remTotal.subtract(calculo.total()));
    }

    /**
     * IN-03 da revisão: a ÚNICA definição de "total já creditado" de uma FR -- soma dos totais das
     * NC (documentos de outro tipo e totais nulos são ignorados), escala 2. Usada pela composição,
     * pela repetição da emissão e pelo detalhe da FR.
     */
    public static BigDecimal totalCreditado(List<DocumentoFiscal> notas) {
        BigDecimal creditado = BigDecimal.ZERO;
        if (notas != null) {
            for (DocumentoFiscal n : notas) {
                if (n.getTipo() == TipoDocumentoFiscal.NC && n.getTotalDocumento() != null) {
                    creditado = creditado.add(n.getTotalDocumento());
                }
            }
        }
        return creditado.setScale(2, RoundingMode.UNNECESSARY);
    }

    /**
     * IN-03 da revisão: a ÚNICA definição de "valor ainda creditável" -- total da FR menos
     * {@link #totalCreditado}. Sem clamp silencioso: um valor negativo torna visível um problema de dados.
     */
    public static BigDecimal valorCreditavelRestante(DocumentoFiscal origem, List<DocumentoFiscal> notas) {
        return origem.getTotalDocumento().subtract(totalCreditado(notas)).setScale(2, RoundingMode.UNNECESSARY);
    }

    private static RecusaFiscalException excede(String mensagem) {
        return new RecusaFiscalException(HttpStatus.CONFLICT, "NC_EXCEDE_ORIGINAL", mensagem, "valor");
    }
}
