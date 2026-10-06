package com.lexcv.fiscal.efatura;

import com.lexcv.models.AmbienteFiscal;
import com.lexcv.models.MotivoNotaCredito;
import com.lexcv.models.TipoDocumentoFiscal;

/**
 * Phase 136 (DFE-01, DFE-06): a ÚNICA tabela das escolhas de formato eFatura que o XSD não fixa.
 *
 * <p>Cada constante cita o item do portão das fontes primárias (G1–G15, 136-RESEARCH.md) a que
 * responde. A decisão do fim da fase (136-16) altera só este ficheiro: nenhum código de formato
 * fica espalhado pelo builder, pelo processador ou pelo adaptador.
 *
 * <p>Os {@code switch} sobre {@link AmbienteFiscal} e {@link TipoDocumentoFiscal} são exaustivos e
 * sem ramo {@code default}: acrescentar um ambiente (ex.: produção) ou um tipo de documento é um
 * erro de compilação aqui, o que obriga a decidir o repositório e o LED de propósito (T-136-34).
 */
public final class MapeamentoEfatura {

    /** G1: versão do pacote XSD vendorizado; guardada por linha de XML. */
    public static final String VERSAO_FORMATO = "2024-05-27";

    /** Atributo {@code Dfe/@Version} ({@code stDocVersion}). */
    public static final String VERSAO_DFE = "1.0";

    /** {@code CountryCode} do emitente, do adquirente e do transmissor. */
    public static final String PAIS = "CV";

    /** G4: LED sintético do ambiente simulado ({@code stLedCode} 1–99999); nunca usado fora de SIMULADO. */
    public static final int LED_SIMULADO = 99999;

    /** {@code RepositoryCode} 3 = repositório de teste. */
    public static final int REPOSITORIO_TESTE = 3;

    /** G9: {@code Transmission/IssueMode} 1 = Online. */
    public static final int ISSUE_MODE_ONLINE = 1;

    /** G13: {@code Quantity/@UnitCode} para serviços (como nas amostras oficiais). */
    public static final String UNIT_CODE = "EA";

    /** {@code Item/EmitterIdentification} da linha de uma Fatura-Recibo de honorários. */
    public static final String EMITTER_ID_FR = "HONORARIOS";

    /** {@code Item/EmitterIdentification} da linha de uma Nota de Crédito. */
    public static final String EMITTER_ID_NC = "NOTACREDITO";

    /** Gap firma: {@code Name} tem 3–150 caracteres. */
    public static final int MAX_NOME = 150;

    /** {@code Name} mínimo. */
    public static final int MIN_NOME = 3;

    /** {@code AddressDetail} e {@code City}: até 100 caracteres. */
    public static final int MAX_MORADA = 100;

    /** {@code Item/Description}: 1–300 caracteres. */
    public static final int MAX_DESCRICAO = 300;

    /** {@code Note}: 10–500 caracteres. */
    public static final int MIN_NOTA = 10;

    /** {@code Note}: 10–500 caracteres. */
    public static final int MAX_NOTA = 500;

    /** {@code DocumentNumber}: 1–999 999 999. */
    public static final long MAX_NUMERO = 999_999_999L;

    /*
     * Escolhas omitidas de propósito (sem constante, documentadas aqui):
     * G10 Tax/TaxTotal não é enviado (totais vêm do snapshot); G11 PaymentAmount = valor_liquido;
     * G12 Party/Contacts omitido (não há colunas no snapshot); G15 IsSpecimen omitido
     * (RepositoryCode 3 já marca o teste).
     */

    private MapeamentoEfatura() {
    }

    /** {@code Dfe/@DocumentTypeCode}: FR = 2, NC = 5. */
    public static int codigoTipo(TipoDocumentoFiscal tipo) {
        return switch (tipo) {
            case FR -> 2;
            case NC -> 5;
        };
    }

    /** Código de tipo dentro do IUD (G2): o mesmo valor, formatado com 2 dígitos pelo {@link IudGerador}. */
    public static int codigoTipoIud(TipoDocumentoFiscal tipo) {
        return codigoTipo(tipo);
    }

    /** {@code RepositoryCode}: só SIMULADO existe e vai para o repositório de teste (DFE-06). */
    public static int repositorioPara(AmbienteFiscal ambiente) {
        return switch (ambiente) {
            case SIMULADO -> REPOSITORIO_TESTE;
        };
    }

    /** G4: LED por ambiente; só SIMULADO, com o LED sintético. */
    public static int ledPara(AmbienteFiscal ambiente) {
        return switch (ambiente) {
            case SIMULADO -> LED_SIMULADO;
        };
    }

    /**
     * G6: {@code IssueReasonCode} da NC. Todos os motivos mapeiam para "2" (Art.º 65, n.º 2 CIVA:
     * anulação ou redução do valor tributável), a confirmar pelo contabilista no portão.
     */
    public static String issueReasonCode(MotivoNotaCredito motivo) {
        return switch (motivo) {
            case ANULACAO_TOTAL, CORRECAO_VALOR, ERRO_DADOS_CLIENTE, OUTRO -> "2";
        };
    }

    /** {@code Item/EmitterIdentification} por tipo de documento. */
    public static String emitterIdentification(TipoDocumentoFiscal tipo) {
        return switch (tipo) {
            case FR -> EMITTER_ID_FR;
            case NC -> EMITTER_ID_NC;
        };
    }

    /**
     * {@code Note} controlada da NC: rótulo do motivo + número da FR. Nunca o texto livre
     * {@code motivo_texto} (sigilo, pode ter 1 caractere ou quebras de linha; T-136-32).
     */
    public static String notaNotaCredito(MotivoNotaCredito motivo, String numeroFormatadoOrigem) {
        return "Nota de crédito: " + motivo.rotulo() + " — " + numeroFormatadoOrigem;
    }
}
