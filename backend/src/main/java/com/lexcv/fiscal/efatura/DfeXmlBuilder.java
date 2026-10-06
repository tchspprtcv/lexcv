package com.lexcv.fiscal.efatura;

import com.lexcv.fiscal.efatura.xsd.CreditNote;
import com.lexcv.fiscal.efatura.xsd.CtAddressBase;
import com.lexcv.fiscal.efatura.xsd.CtFiscalDocument;
import com.lexcv.fiscal.efatura.xsd.CtParty;
import com.lexcv.fiscal.efatura.xsd.CtPaymentsPayment;
import com.lexcv.fiscal.efatura.xsd.CtQuantity;
import com.lexcv.fiscal.efatura.xsd.Dfe;
import com.lexcv.fiscal.efatura.xsd.InvoiceReceipt;
import com.lexcv.fiscal.efatura.xsd.Item;
import com.lexcv.fiscal.efatura.xsd.Lines;
import com.lexcv.fiscal.efatura.xsd.Payment;
import com.lexcv.fiscal.efatura.xsd.References;
import com.lexcv.fiscal.efatura.xsd.Software;
import com.lexcv.fiscal.efatura.xsd.StTaxId;
import com.lexcv.fiscal.efatura.xsd.StTaxTypeCode;
import com.lexcv.fiscal.efatura.xsd.Tax;
import com.lexcv.fiscal.efatura.xsd.Totals;
import com.lexcv.fiscal.efatura.xsd.Transmission;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import org.springframework.stereotype.Component;
import un.unece.uncefact.identifierlist.standard.iso.isotwo_lettercountrycode.secondedition2006.ISOTwoletterCountryCodeContentType;

/**
 * Phase 136 (DFE-01, DFE-02, DFE-06): constrói o modelo JAXB {@link Dfe} de uma Fatura-Recibo ou
 * de uma Nota de Crédito a partir do snapshot imutável ({@link DocumentoComunicavel}), seguindo
 * linha a linha as tabelas "XSD -> Snapshot Mapping" de 136-RESEARCH.md.
 *
 * <p>Puro: sem repositórios, sem relógio, sem rede. Nenhum XML é escrito à mão; a ordem dos
 * elementos e o escape do texto são do JAXB (T-136-33). Todos os códigos e constantes vêm de
 * {@link MapeamentoEfatura}. Os textos ({@code Name}, {@code AddressDetail}, {@code City},
 * {@code Description}, {@code Note}) são normalizados (espaços em branco seguidos -> um espaço,
 * aparados) por causa do {@code stNoExtraSpaces}; o que não se pode corrigir é recusado com
 * {@link RecusaFormatoEfatura}. A {@code Note} da NC é texto controlado; o texto livre
 * {@code motivo_texto} nunca é mapeado (T-136-32).
 */
@Component
public final class DfeXmlBuilder {

    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    private final DatatypeFactory datas;

    public DfeXmlBuilder() {
        try {
            this.datas = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException e) {
            throw new IllegalStateException("DatatypeFactory indisponível", e);
        }
    }

    /**
     * @param doc         snapshot projetado
     * @param iud         IUD do documento ({@code Dfe/@Id})
     * @param transmissao dados do bloco {@code Transmission}
     * @throws RecusaFormatoEfatura quando o snapshot não cabe no formato
     */
    public Dfe construir(DocumentoComunicavel doc, String iud, TransmissaoEfatura transmissao) {
        Objects.requireNonNull(doc, "doc");
        Objects.requireNonNull(iud, "iud");
        Objects.requireNonNull(transmissao, "transmissao");

        verificarNumero(doc.numero());
        String firma = norm(doc.emitenteFirma());
        if (firma != null && firma.length() > MapeamentoEfatura.MAX_NOME) {
            throw new RecusaFormatoEfatura(RecusaFormatoEfatura.Codigo.FIRMA_EXCEDE_150);
        }

        CtParty emitente = parte(doc.emitenteNif(), firma, doc.emitenteMorada(), doc.emitenteLocalidade());
        CtParty adquirente = parte(doc.adquirenteNif(), norm(doc.adquirenteNome()), doc.adquirenteMorada(),
                doc.adquirenteLocalidade());
        int led = MapeamentoEfatura.ledPara(doc.ambiente());
        int numero = Math.toIntExact(doc.numero());
        XMLGregorianCalendar data = data(doc.dataEmissao());
        XMLGregorianCalendar hora = hora(doc.horaEmissao());
        Lines linhas = linhas(doc);
        Totals totais = totais(doc);

        Dfe dfe = new Dfe();
        dfe.setVersion(MapeamentoEfatura.VERSAO_DFE);
        dfe.setId(iud);
        dfe.setDocumentTypeCode(BigInteger.valueOf(MapeamentoEfatura.codigoTipo(doc.tipo())));
        switch (doc.tipo()) {
            case FR -> {
                InvoiceReceipt fr = new InvoiceReceipt();
                fr.setLedCode(led);
                fr.setSerie(doc.serieCodigo());
                fr.setDocumentNumber(numero);
                fr.setIssueDate(data);
                fr.setIssueTime(hora);
                fr.setEmitterParty(emitente);
                fr.setReceiverParty(adquirente);
                fr.setLines(linhas);
                fr.setTotals(totais);
                fr.setPayments(pagamentos(doc));
                dfe.setInvoiceReceipt(fr);
            }
            case NC -> {
                // Pré-condição da API pública (IN-01): no caminho do job, o processador já trata a
                // falta do IUD de origem e DocumentoComunicavel.de recusa a NC sem motivo/origem.
                if (doc.iudOrigem() == null || doc.iudOrigem().isBlank()
                        || doc.numeroFormatadoOrigem() == null || doc.motivoCodigo() == null) {
                    throw new RecusaFormatoEfatura(RecusaFormatoEfatura.Codigo.ORIGEM_SEM_IUD);
                }
                CreditNote nc = new CreditNote();
                nc.setLedCode(led);
                nc.setSerie(doc.serieCodigo());
                nc.setDocumentNumber(numero);
                nc.setIssueDate(data);
                nc.setIssueTime(hora);
                nc.setIssueReasonCode(MapeamentoEfatura.issueReasonCode(doc.motivoCodigo()));
                nc.setEmitterParty(emitente);
                nc.setReceiverParty(adquirente);
                nc.setLines(linhas);
                nc.setTotals(totais);
                nc.setReferences(referencias(doc.iudOrigem()));
                nc.setNote(nota(doc));
                dfe.setCreditNote(nc);
            }
        }
        dfe.setTransmission(transmissao(transmissao));
        dfe.setRepositoryCode(BigInteger.valueOf(MapeamentoEfatura.repositorioPara(doc.ambiente())));
        return dfe;
    }

    /**
     * WR-03: recusa um número fora de {@code 1..MAX_NUMERO}. Público porque o processador o chama
     * ANTES de gerar o IUD (o gerador também recusaria o número, mas como erro genérico).
     *
     * @throws RecusaFormatoEfatura {@code NUMERO_FORA_DO_LIMITE}
     */
    public static void verificarNumero(long numero) {
        if (numero < 1 || numero > MapeamentoEfatura.MAX_NUMERO) {
            throw new RecusaFormatoEfatura(RecusaFormatoEfatura.Codigo.NUMERO_FORA_DO_LIMITE);
        }
    }

    // ---- partes ----

    private static CtParty parte(String nif, String nomeNormalizado, String morada, String localidade) {
        if (nomeNormalizado == null || nomeNormalizado.length() < MapeamentoEfatura.MIN_NOME
                || nomeNormalizado.length() > MapeamentoEfatura.MAX_NOME) {
            throw textoInvalido();
        }
        String detalhe = norm(morada);
        if (detalhe == null || detalhe.length() > MapeamentoEfatura.MAX_MORADA) {
            throw textoInvalido();
        }
        String cidade = norm(localidade);
        if (cidade != null && cidade.length() > MapeamentoEfatura.MAX_MORADA) {
            throw textoInvalido();
        }
        CtAddressBase endereco = new CtAddressBase();
        endereco.setCountryCode(pais());
        endereco.setCity(cidade);
        endereco.setAddressDetail(detalhe);

        CtParty parte = new CtParty();
        parte.setTaxId(nif(nif));
        parte.setName(nomeNormalizado);
        parte.setAddress(endereco);
        return parte;
    }

    private static StTaxId nif(String nif) {
        StTaxId id = new StTaxId();
        id.setValue(nif);
        id.setCountryCode(pais());
        return id;
    }

    private static ISOTwoletterCountryCodeContentType pais() {
        return ISOTwoletterCountryCodeContentType.fromValue(MapeamentoEfatura.PAIS);
    }

    // ---- linhas ----

    private static Lines linhas(DocumentoComunicavel doc) {
        DocumentoComunicavel.Linha l = doc.linha();
        String descricao = norm(l.descricao());
        if (descricao == null || descricao.length() > MapeamentoEfatura.MAX_DESCRICAO) {
            throw textoInvalido();
        }

        CtQuantity quantidade = new CtQuantity();
        quantidade.setValue(decimal(l.quantidade()));
        quantidade.setUnitCode(MapeamentoEfatura.UNIT_CODE);

        Item item = new Item();
        item.setDescription(descricao);
        item.setEmitterIdentification(MapeamentoEfatura.emitterIdentification(doc.tipo()));

        Lines.Line linha = new Lines.Line();
        linha.setId(String.valueOf(l.numero()));
        linha.setQuantity(quantidade);
        linha.setPrice(decimal(l.precoUnitario()));
        linha.setPriceExtension(decimal(l.valorBase()));
        linha.setNetTotal(decimal(l.valorBase()));
        linha.getTax().add(impostoIva(doc));
        if (l.valorRetencao().signum() > 0) {
            if (l.taxaRetencao() == null || l.taxaRetencao().signum() <= 0) {
                throw textoInvalido();
            }
            Tax ir = new Tax();
            ir.setTaxTypeCode(StTaxTypeCode.IR);
            ir.setTaxPercentage(decimal(l.taxaRetencao()));
            linha.getTax().add(ir);
        }
        linha.setItem(item);

        Lines linhas = new Lines();
        linhas.getLine().add(linha);
        return linhas;
    }

    private static Tax impostoIva(DocumentoComunicavel doc) {
        DocumentoComunicavel.Linha l = doc.linha();
        Tax iva = new Tax();
        switch (doc.emitenteRegimeIva()) {
            case NORMAL -> {
                iva.setTaxTypeCode(StTaxTypeCode.IVA);
                iva.setTaxPercentage(decimal(l.taxaIva()));
            }
            case ISENTO -> {
                String motivo = norm(l.motivoIsencaoCodigo());
                if (motivo == null) {
                    throw textoInvalido();
                }
                iva.setTaxTypeCode(StTaxTypeCode.NA);
                iva.setTaxExemptionReasonCode(motivo);
            }
        }
        return iva;
    }

    // ---- totais, pagamentos, referências, nota ----

    private static Totals totais(DocumentoComunicavel doc) {
        Totals t = new Totals();
        t.setPriceExtensionTotalAmount(decimal(doc.totalBase()));
        t.setChargeTotalAmount(BigDecimal.ZERO);
        t.setDiscountTotalAmount(BigDecimal.ZERO);
        t.setNetTotalAmount(decimal(doc.totalBase()));
        t.setTaxTotalAmount(decimal(doc.totalIva()));
        if (doc.totalRetencao().signum() > 0) {
            t.setWithholdingTaxTotalAmount(decimal(doc.totalRetencao()));
        }
        t.setPayableAmount(decimal(doc.valorLiquido()));
        return t;
    }

    private CtPaymentsPayment pagamentos(DocumentoComunicavel doc) {
        Payment p = new Payment();
        p.setPaymentMeansCode(doc.meioPagamentoCodigo());
        p.setPaymentDate(data(doc.dataEmissao()));
        // G11: PaymentAmount = valor líquido, igual ao PayableAmount.
        p.setPaymentAmount(decimal(doc.valorLiquido()));
        CtPaymentsPayment pagamentos = new CtPaymentsPayment();
        pagamentos.getPayment().add(p);
        return pagamentos;
    }

    private static References referencias(String iudOrigem) {
        CtFiscalDocument fd = new CtFiscalDocument();
        fd.setValue(iudOrigem);
        References.Reference r = new References.Reference();
        r.setFiscalDocument(fd);
        References refs = new References();
        refs.getReference().add(r);
        return refs;
    }

    private static String nota(DocumentoComunicavel doc) {
        String nota = norm(MapeamentoEfatura.notaNotaCredito(doc.motivoCodigo(), norm(doc.numeroFormatadoOrigem())));
        if (nota == null || nota.length() < MapeamentoEfatura.MIN_NOTA || nota.length() > MapeamentoEfatura.MAX_NOTA) {
            throw textoInvalido();
        }
        return nota;
    }

    private static Transmission transmissao(TransmissaoEfatura t) {
        Software software = new Software();
        software.setCode(t.softwareCodigo());
        software.setName(t.softwareNome());
        software.setVersion(t.softwareVersao());
        Transmission tr = new Transmission();
        tr.setIssueMode(BigInteger.valueOf(MapeamentoEfatura.ISSUE_MODE_ONLINE));
        tr.setTransmitterTaxId(nif(t.nifTransmissor()));
        tr.setSoftware(software);
        return tr;
    }

    // ---- valores ----

    /** Espaços em branco seguidos (incl. tab e quebra de linha) -> um espaço; aparado; vazio -> null. */
    static String norm(String s) {
        if (s == null) {
            return null;
        }
        String r = ESPACOS.matcher(s).replaceAll(" ").trim();
        return r.isEmpty() ? null : r;
    }

    /** Sem zeros à direita e nunca em notação científica (escala mínima 0). */
    static BigDecimal decimal(BigDecimal v) {
        BigDecimal r = v.stripTrailingZeros();
        return r.scale() < 0 ? r.setScale(0) : r;
    }

    private XMLGregorianCalendar data(LocalDate d) {
        return datas.newXMLGregorianCalendarDate(d.getYear(), d.getMonthValue(), d.getDayOfMonth(),
                DatatypeConstants.FIELD_UNDEFINED);
    }

    private XMLGregorianCalendar hora(LocalTime t) {
        return datas.newXMLGregorianCalendarTime(t.getHour(), t.getMinute(), t.getSecond(),
                DatatypeConstants.FIELD_UNDEFINED);
    }

    private static RecusaFormatoEfatura textoInvalido() {
        return new RecusaFormatoEfatura(RecusaFormatoEfatura.Codigo.TEXTO_INVALIDO);
    }
}
