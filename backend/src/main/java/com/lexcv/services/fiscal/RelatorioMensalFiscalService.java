package com.lexcv.services.fiscal;

import com.lexcv.config.UserPrincipal;
import com.lexcv.fiscal.csv.CsvFiscal;
import com.lexcv.models.ComunicacaoFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.RegimeIva;
import com.lexcv.models.TipoDocumentoFiscal;
import com.lexcv.repositories.ComunicacaoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalRepository;
import com.lexcv.repositories.DocumentoFiscalXmlRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 137 (RELF-01): o CSV mensal para o contabilista -- as Faturas-Recibo e Notas de Crédito
 * emitidas no mês pelo escritório do chamador (137-UI-SPEC, superfície 4).
 *
 * <ul>
 *   <li><b>Tenant primeiro</b> (T-137-75): o finder do mês e as três leituras em lote (IUD, estado da
 *       comunicação, número da FR de origem) recebem o {@code tenantId}; sem N+1.</li>
 *   <li><b>Formato Excel-PT</b> ({@link CsvFiscal}): BOM UTF-8, {@code ;}, CRLF (também no fim),
 *       vírgula decimal, datas {@code dd/MM/yyyy}.</li>
 *   <li><b>Sinais:</b> nas NC, base, IVA, retenção e total saem negativos; a linha "Totais" soma os
 *       valores com sinal.</li>
 *   <li><b>Guarda de fórmulas só no texto livre</b> (T-137-76): {@link CsvFiscal#textoLivre} apenas no
 *       Cliente e no Motivo de isenção; NIF, IUD, série, número, datas e valores ficam intactos (um
 *       valor negativo nunca ganha apóstrofo).</li>
 *   <li><b>Auditoria</b> (T-137-78): cada exportação grava um evento (mês, número de documentos, nome
 *       do autor) na mesma transação -- por isso {@code @Transactional} de escrita, não readOnly. A
 *       construção do ficheiro em memória é só CPU, sem I/O externo dentro da transação.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RelatorioMensalFiscalService {

    static final List<String> CABECALHO = List.of("Data", "Tipo", "Série", "Número", "IUD", "Cliente",
            "NIF cliente", "Base", "IVA", "Motivo de isenção", "Retenção", "Total", "Documento de origem",
            "Estado da comunicação");
    static final String CONSUMIDOR_FINAL = "Consumidor final";
    static final String TOTAIS = "Totais";

    private static final Map<EstadoComunicacaoFiscal, String> ROTULOS_COMUNICACAO = rotulos();
    private static final Comparator<DocumentoFiscal> ORDEM = Comparator
            .comparing(DocumentoFiscal::getDataEmissao, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DocumentoFiscal::getEmitidoEm, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DocumentoFiscal::getTipo, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DocumentoFiscal::getAno, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(DocumentoFiscal::getNumero, Comparator.nullsLast(Comparator.naturalOrder()));

    private final DocumentoFiscalRepository documentoFiscalRepository;
    private final DocumentoFiscalXmlRepository documentoFiscalXmlRepository;
    private final ComunicacaoFiscalRepository comunicacaoFiscalRepository;
    private final AuditoriaFiscalService auditoriaFiscalService;

    /** O ficheiro pronto a descarregar: bytes, nome ({@link NomesFicheiroFiscal#csv}) e contagem; os bytes são copiados. */
    public record CsvMensal(byte[] conteudo, String nomeFicheiro, int numeroDocumentos) {

        public CsvMensal {
            Objects.requireNonNull(conteudo, "conteudo");
            conteudo = conteudo.clone();
        }

        @Override
        public byte[] conteudo() {
            return conteudo.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof CsvMensal c && Arrays.equals(conteudo, c.conteudo)
                    && Objects.equals(nomeFicheiro, c.nomeFicheiro) && numeroDocumentos == c.numeroDocumentos;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(conteudo), nomeFicheiro, numeroDocumentos);
        }

        @Override
        public String toString() {
            return "CsvMensal[" + conteudo.length + " bytes, nomeFicheiro=" + nomeFicheiro
                    + ", numeroDocumentos=" + numeroDocumentos + "]";
        }
    }

    @Transactional
    public CsvMensal exportar(UUID tenantId, UserPrincipal autor, YearMonth mes) {
        List<DocumentoFiscal> documentos = new ArrayList<>(documentoFiscalRepository
                .findByTenantIdAndDataEmissaoBetweenOrderByDataEmissaoAscAnoAscNumeroAsc(tenantId, mes.atDay(1),
                        mes.atEndOfMonth()));
        // FR e NC têm séries próprias: "FR n.º 1" e "NC n.º 1" do mesmo dia empatam em (data, ano, número).
        // Desempate determinístico: hora de emissão, depois o tipo (FR antes de NC), depois o número.
        documentos.sort(ORDEM);
        List<UUID> ids = documentos.stream().map(DocumentoFiscal::getId).toList();

        Map<UUID, String> iuds = ids.isEmpty() ? Map.of()
                : documentoFiscalXmlRepository.findByTenantIdAndDocumentoFiscalIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(DocumentoFiscalXml::getDocumentoFiscalId, DocumentoFiscalXml::getIud,
                        (a, b) -> a));
        Map<UUID, EstadoComunicacaoFiscal> estados = ids.isEmpty() ? Map.of()
                : comunicacaoFiscalRepository.findByTenantIdAndDocumentoFiscalIdIn(tenantId, ids).stream()
                .collect(Collectors.toMap(ComunicacaoFiscal::getDocumentoFiscalId, ComunicacaoFiscal::getEstado,
                        (a, b) -> a));
        Set<UUID> origens = documentos.stream().map(DocumentoFiscal::getDocumentoOrigemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> numerosOrigem = origens.isEmpty() ? Map.of()
                : documentoFiscalRepository.findByTenantIdAndIdIn(tenantId, origens).stream()
                .collect(Collectors.toMap(DocumentoFiscal::getId, DocumentoFiscal::getNumeroFormatado, (a, b) -> a));

        List<String> linhas = new ArrayList<>(documentos.size() + 2);
        linhas.add(CsvFiscal.linha(CABECALHO.stream().map(CsvFiscal::estruturado).toList()));
        BigDecimal somaBase = BigDecimal.ZERO;
        BigDecimal somaIva = BigDecimal.ZERO;
        BigDecimal somaRetencao = BigDecimal.ZERO;
        BigDecimal somaTotal = BigDecimal.ZERO;
        for (DocumentoFiscal d : documentos) {
            boolean nc = d.getTipo() == TipoDocumentoFiscal.NC;
            BigDecimal base = comSinal(d.getTotalBase(), nc);
            BigDecimal iva = comSinal(d.getTotalIva(), nc);
            BigDecimal retencao = comSinal(d.getTotalRetencao(), nc);
            BigDecimal total = comSinal(d.getTotalDocumento(), nc);
            somaBase = somaBase.add(base);
            somaIva = somaIva.add(iva);
            somaRetencao = somaRetencao.add(retencao);
            somaTotal = somaTotal.add(total);

            EstadoComunicacaoFiscal estado = estados.get(d.getId());
            linhas.add(CsvFiscal.linha(List.of(
                    CsvFiscal.data(d.getDataEmissao()),
                    CsvFiscal.estruturado(d.getTipo().name()),
                    CsvFiscal.estruturado(d.getSerieCodigo()),
                    CsvFiscal.estruturado(d.getNumeroFormatado()),
                    CsvFiscal.estruturado(iuds.getOrDefault(d.getId(), "")),
                    CsvFiscal.textoLivre(d.getAdquirenteNome()),
                    CsvFiscal.estruturado(nifCliente(d.getAdquirenteNif())),
                    CsvFiscal.valor(base),
                    CsvFiscal.valor(iva),
                    CsvFiscal.textoLivre(motivoIsencao(d)),
                    CsvFiscal.valor(retencao),
                    CsvFiscal.valor(total),
                    CsvFiscal.estruturado(nc && d.getDocumentoOrigemId() != null
                            ? numerosOrigem.getOrDefault(d.getDocumentoOrigemId(), "") : ""),
                    CsvFiscal.estruturado(estado == null ? "" : ROTULOS_COMUNICACAO.get(estado)))));
        }
        linhas.add(CsvFiscal.linha(List.of(TOTAIS, "", "", "", "", "", "",
                CsvFiscal.valor(somaBase), CsvFiscal.valor(somaIva), "",
                CsvFiscal.valor(somaRetencao), CsvFiscal.valor(somaTotal), "", "")));

        StringBuilder sb = new StringBuilder(CsvFiscal.BOM);
        for (String linha : linhas) {
            sb.append(linha).append(CsvFiscal.FIM_LINHA);
        }

        auditoriaFiscalService.registarExportacaoMensal(tenantId, autor, mes, documentos.size());
        return new CsvMensal(sb.toString().getBytes(StandardCharsets.UTF_8), NomesFicheiroFiscal.csv(mes),
                documentos.size());
    }

    private static BigDecimal comSinal(BigDecimal valor, boolean negar) {
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor;
        return negar ? v.negate() : v;
    }

    private static String nifCliente(String nif) {
        return nif == null || nif.isBlank() ? CONSUMIDOR_FINAL : nif;
    }

    /** "{código} — {descrição}" da fotografia do emitente quando o IVA é isento; senão vazio. */
    private static String motivoIsencao(DocumentoFiscal d) {
        if (d.getEmitenteRegimeIva() != RegimeIva.ISENTO || d.getEmitenteMotivoIsencaoCodigo() == null) {
            return "";
        }
        String descricao = d.getEmitenteMotivoIsencaoDescricao();
        return descricao == null || descricao.isBlank()
                ? d.getEmitenteMotivoIsencaoCodigo()
                : d.getEmitenteMotivoIsencaoCodigo() + " — " + descricao;
    }

    private static Map<EstadoComunicacaoFiscal, String> rotulos() {
        Map<EstadoComunicacaoFiscal, String> m = new EnumMap<>(EstadoComunicacaoFiscal.class);
        m.put(EstadoComunicacaoFiscal.PENDENTE, "Pendente");
        m.put(EstadoComunicacaoFiscal.ACEITE_SIMULADO, "Aceite (simulação)");
        m.put(EstadoComunicacaoFiscal.REJEITADO, "Rejeitado");
        m.put(EstadoComunicacaoFiscal.ERRO, "Erro");
        return Map.copyOf(m);
    }
}
