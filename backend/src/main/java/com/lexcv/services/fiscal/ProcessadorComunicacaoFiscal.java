package com.lexcv.services.fiscal;

import com.lexcv.fiscal.efatura.DfeMarshaller;
import com.lexcv.fiscal.efatura.DfeValidador;
import com.lexcv.fiscal.efatura.DfeXmlBuilder;
import com.lexcv.fiscal.efatura.DocumentoComunicavel;
import com.lexcv.fiscal.efatura.EfaturaGateway;
import com.lexcv.fiscal.efatura.IudGerador;
import com.lexcv.fiscal.efatura.MapeamentoEfatura;
import com.lexcv.fiscal.efatura.PedidoComunicacao;
import com.lexcv.fiscal.efatura.RecusaFormatoEfatura;
import com.lexcv.fiscal.efatura.ResultadoComunicacao;
import com.lexcv.fiscal.efatura.ResultadoValidacao;
import com.lexcv.fiscal.efatura.TransmissaoEfatura;
import com.lexcv.fiscal.efatura.xsd.Dfe;
import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.DocumentoFiscal;
import com.lexcv.models.DocumentoFiscalXml;
import com.lexcv.models.EstadoComunicacaoFiscal;
import com.lexcv.models.TipoDocumentoFiscal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Phase 136 (DFE-01, DFE-02, DFE-04, DFE-06, DFE-07): processa UMA linha reclamada da fila de
 * comunicação fiscal.
 *
 * <p>Passos: snapshot (tx só de leitura, tenant da linha) -> reutiliza a linha XML existente, ou
 * gera IUD + DFE, serializa e valida contra o XSD (fora de transação) -> grava o XML (insert-only,
 * só se for válido) -> envia pela única porta {@link EfaturaGateway} (fora de transação) -> mapeia
 * o resultado com {@link EstadoComunicacaoMapper#estadoPara} -> regista o resultado (tx curta,
 * guardada pela versão) -> se a linha passou a {@code ERRO}, notifica os titulares de
 * {@code financeiro:manage} uma vez por episódio, DEPOIS do commit do resultado.
 *
 * <p>Sem anotações de transação: todas as transações vivem em {@link ComunicacaoFiscalTransacoes}
 * (chamadas pelo proxy), para que nenhuma envolva o XML ou o gateway.
 *
 * <p>Isolamento (T-136-51): {@link #processar} nunca lança. Há um {@code catch (Throwable)} à
 * volta do cálculo (vira {@code FALHA_INTERNA}, transitório), outro à volta do registo do
 * resultado e outro à volta da notificação. Só códigos e mensagens FIXOS chegam à base de dados
 * (T-136-48): o texto de uma exceção nunca é gravado; vai só para o log do servidor.
 */
@Slf4j
@Service
public class ProcessadorComunicacaoFiscal {

    static final String ORIGEM_SEM_IUD = "ORIGEM_SEM_IUD";
    static final String MSG_ORIGEM_SEM_IUD = "A fatura-recibo de origem ainda não foi comunicada.";
    static final String DOCUMENTO_INEXISTENTE = "DOCUMENTO_INEXISTENTE";
    static final String MSG_DOCUMENTO_INEXISTENTE = "O documento fiscal não foi encontrado.";
    static final String IUD_COLISAO = "IUD_COLISAO";
    static final String MSG_IUD_COLISAO = "Identificador repetido; nova tentativa automática.";
    static final String FALHA_INTERNA = "FALHA_INTERNA";
    static final String MSG_FALHA_INTERNA = "Falha interna ao comunicar o documento.";

    private final ComunicacaoFiscalTransacoes transacoes;
    private final DfeXmlBuilder builder;
    private final DfeMarshaller marshaller;
    private final DfeValidador validador;
    private final IudGerador iudGerador;
    private final EfaturaGateway gateway;
    private final TransmissaoEfatura transmissao;
    private final NotificacaoComunicacaoFiscal notificacao;
    private final Clock clock;

    public ProcessadorComunicacaoFiscal(ComunicacaoFiscalTransacoes transacoes, DfeXmlBuilder builder,
                                        DfeMarshaller marshaller, DfeValidador validador, IudGerador iudGerador,
                                        EfaturaGateway gateway, TransmissaoEfatura transmissao,
                                        NotificacaoComunicacaoFiscal notificacao, Clock clock) {
        this.transacoes = transacoes;
        this.builder = builder;
        this.marshaller = marshaller;
        this.validador = validador;
        this.iudGerador = iudGerador;
        this.gateway = gateway;
        this.transmissao = transmissao;
        this.notificacao = notificacao;
        this.clock = clock;
    }

    /** Processa um item. Nunca lança. */
    public void processar(ComunicacaoReclamada item) {
        String[] numeroFormatado = new String[1];
        ResultadoComunicacao resultado;
        try {
            resultado = comunicar(item, numeroFormatado);
        } catch (Throwable e) {
            log.error("Falha interna ao comunicar o documento fiscal {} (tentativa {})",
                    item.documentoFiscalId(), item.tentativas(), e);
            resultado = new ResultadoComunicacao.ErroTransitorio(FALHA_INTERNA, MSG_FALHA_INTERNA);
        }

        EstadoComunicacaoFiscal estado;
        int linhas;
        try {
            estado = EstadoComunicacaoMapper.estadoPara(resultado, item.ambiente(), item.tentativas());
            Instant proxima = estado == EstadoComunicacaoFiscal.PENDENTE
                    ? clock.instant().plus(BackoffComunicacao.atraso(Math.max(1, item.tentativas())))
                    : null;
            linhas = transacoes.registarResultado(item, estado, codigo(resultado), mensagem(resultado), proxima);
        } catch (Throwable e) {
            log.error("Resultado da comunicação do documento fiscal {} não registado", item.documentoFiscalId(), e);
            return;
        }
        if (linhas != 1) {
            log.warn("Lease perdido na comunicação do documento fiscal {}: resultado ignorado",
                    item.documentoFiscalId());
            return;
        }
        if (estado == EstadoComunicacaoFiscal.ERRO) {
            notificar(item, numeroFormatado[0]);
        }
    }

    private ResultadoComunicacao comunicar(ComunicacaoReclamada item, String[] numeroFormatado) {
        Optional<SnapshotComunicacao> carregado =
                transacoes.carregarSnapshot(item.tenantId(), item.documentoFiscalId());
        if (carregado.isEmpty()) {
            return new ResultadoComunicacao.Rejeitado(DOCUMENTO_INEXISTENTE, MSG_DOCUMENTO_INEXISTENTE);
        }
        SnapshotComunicacao snapshot = carregado.get();
        DocumentoFiscal documento = snapshot.documento();
        numeroFormatado[0] = documento.getNumeroFormatado();

        DocumentoFiscalXml linhaXml;
        if (snapshot.xmlExistente().isPresent()) {
            linhaXml = snapshot.xmlExistente().get();
        } else {
            boolean nc = documento.getTipo() == TipoDocumentoFiscal.NC;
            if (nc && snapshot.iudOrigem().isEmpty()) {
                return new ResultadoComunicacao.ErroTransitorio(ORIGEM_SEM_IUD, MSG_ORIGEM_SEM_IUD);
            }
            AmbienteFiscal ambiente = documento.getAmbiente();
            byte[] xml;
            String iud;
            try {
                DocumentoComunicavel doc = DocumentoComunicavel.de(documento, snapshot.linha(),
                        snapshot.iudOrigem().orElse(null), nc ? snapshot.numeroFormatadoOrigem().orElse(null) : null);
                iud = iudGerador.gerar(MapeamentoEfatura.repositorioPara(ambiente), doc.dataEmissao(),
                        doc.emitenteNif(), MapeamentoEfatura.ledPara(ambiente),
                        MapeamentoEfatura.codigoTipoIud(doc.tipo()), doc.numero());
                Dfe dfe = builder.construir(doc, iud, transmissao);
                xml = marshaller.marshal(dfe);
            } catch (RecusaFormatoEfatura recusa) {
                if (recusa.tipo() == RecusaFormatoEfatura.Codigo.ORIGEM_SEM_IUD) {
                    return new ResultadoComunicacao.ErroTransitorio(recusa.codigo(), recusa.mensagem());
                }
                return new ResultadoComunicacao.Rejeitado(recusa.codigo(), recusa.mensagem());
            }
            ResultadoValidacao validacao = validador.validar(xml);
            if (!validacao.valido()) {
                String codigo = validacao.codigo() != null ? validacao.codigo() : ResultadoValidacao.XSD_INVALIDO;
                return new ResultadoComunicacao.Rejeitado(codigo, mensagemFormato(validacao.linha()));
            }
            Optional<DocumentoFiscalXml> gravada = transacoes.gravarXml(item.tenantId(), item.documentoFiscalId(),
                    iud, ambiente, MapeamentoEfatura.repositorioPara(ambiente), MapeamentoEfatura.ledPara(ambiente),
                    MapeamentoEfatura.VERSAO_FORMATO, new String(xml, StandardCharsets.UTF_8), sha256Hex(xml));
            if (gravada.isEmpty()) {
                return new ResultadoComunicacao.ErroTransitorio(IUD_COLISAO, MSG_IUD_COLISAO);
            }
            linhaXml = gravada.get();
        }

        return gateway.comunicar(new PedidoComunicacao(item.tenantId(), item.documentoFiscalId(), item.ambiente(),
                linhaXml.getIud(), linhaXml.getXml()));
    }

    private void notificar(ComunicacaoReclamada item, String numeroFormatado) {
        try {
            String numero = numeroFormatado;
            if (numero == null) {
                numero = transacoes.carregarSnapshot(item.tenantId(), item.documentoFiscalId())
                        .map(s -> s.documento().getNumeroFormatado())
                        .orElse(item.documentoFiscalId().toString());
            }
            notificacao.notificarFalhaPersistente(item.tenantId(), item.documentoFiscalId(), numero,
                    item.reprocessamentos());
        } catch (Throwable e) {
            log.error("Notificação da falha de comunicação do documento fiscal {} não criada",
                    item.documentoFiscalId(), e);
        }
    }

    private static String codigo(ResultadoComunicacao resultado) {
        return switch (resultado) {
            case ResultadoComunicacao.AceiteSimulado aceite -> null;
            case ResultadoComunicacao.Rejeitado r -> r.codigo();
            case ResultadoComunicacao.ErroTransitorio e -> e.codigo();
        };
    }

    private static String mensagem(ResultadoComunicacao resultado) {
        return switch (resultado) {
            case ResultadoComunicacao.AceiteSimulado aceite -> null;
            case ResultadoComunicacao.Rejeitado r -> r.mensagem();
            case ResultadoComunicacao.ErroTransitorio e -> e.mensagem();
        };
    }

    /** Mesma mensagem fixa do adaptador simulado: só a linha do erro, nunca o texto do parser. */
    static String mensagemFormato(int linha) {
        return linha > 0
                ? "O documento não cumpre o formato eFatura (linha " + linha + ")."
                : "O documento não cumpre o formato eFatura.";
    }

    static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
