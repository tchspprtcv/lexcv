package com.lexcv.models;

import java.util.Arrays;
import java.util.Optional;

/**
 * Phase 133 (CFG-02): lista oficial {@code TaxExemptionReasonCode} da eFatura de Cabo Verde,
 * transcrita de {@code CV_EFatura_TaxExemptionReason_v1.0.xsd} (21 códigos; {@code descricao} =
 * Description do XSD, {@code mencao} = Mention do XSD).
 *
 * <p>Este enum é a ÚNICA fonte da lista: o frontend lê-a de
 * {@code GET /api/v1/faturacao/motivos-isencao} (Plan 05) em vez de manter uma cópia própria.
 * O valor guardado em {@code t_configuracao_fiscal.motivo_isencao_codigo} é {@link #codigo()}.
 */
public enum MotivoIsencaoIva {
    M1("1", "Regime da margem de lucro - Bens em segunda mão, objetos de arte, de coleção ou antiguidades", "Isento Regime da Margem de Lucro"),
    M2("2", "Bens da Lista Anexa", "Isento Bens da Lista Anexa Art.º 9.º CIVA"),
    M3("3", "Isenções nas operações internas - Transmissões de bens e prestações de serviços isentas", "Isento Art.º 9.º CIVA"),
    M4("4", "Isenções na exportação, operações assimiladas e transportes internacionais", "Isento Art.º 13.º CIVA"),
    M5("5", "Outras isenções", "Isento Art.º 14.º CIVA"),
    M6("6", "Quantias excluídas do valor tributável", "Não Sujeito Art.º 15.º n.º 6 CIVA"),
    M7("7", "Estado e demais pessoas colectivas de direito público - poder de autoridade e operações a favor das populações, sem contrapartida directa", "Não Sujeito Art.º 2.º n.º 3 CIVA"),
    M8("8", "Amostras e ofertas de pequenos valores", "Não Sujeito Amostras e Ofertas de Pequenos Valores"),
    M9("9", "Mercadorias Transmitidas unicamente para demonstração", "Não Sujeito Mercadorias Para Demonstração"),
    M10("10", "Mercadorias Enviadas à Consignação", "Não Sujeito Mercadorias Enviadas à Consignação"),
    M11("11", "Operações efetuadas no território nacional por não residentes sem representante legal, quando o adquirente não seja sujeito passivo de IVA", "Não Sujeito Art.º 26 n.º 3 CIVA a Contrario Sensu"),
    M12("12", "Autoconsumos e operações gratuitas, que não tenham havido dedução total ou parcial do imposto", "Não Sujeito Autoconsumos e Op. Gratuitas"),
    M13("13", "Prestação de serviços localizados fora do território nacional", "Não Sujeito P. Serviços Localiz. Fora do T. Nacional"),
    M14("14", "IVA – autoliquidação - nº 12 do art.º 32º do CIVA", "IVA – Autoliquidação"),
    M15("15", "Cessão a título oneroso ou gratuito de um estabelecimento comercial, ou de um património", "IVA - Não Devido Nem Exigível"),
    M16("16", "Regime da margem de lucro - Bens em segunda mão", "IVA - Bens em Segunda Mão"),
    M17("17", "Regime da margem de lucro - Objetos de arte, de coleção ou antiguidades", "IVA - Objetos de Arte de Coleção ou Antiguidades"),
    M18("18", "Exigibilidade de caixa - Empreitadas ao Estado", "IVA Exigível e Dedutível no Pagamento"),
    M19("19", "Regime da margem de lucro - Agências operadoras turísticas", "IVA Incluído - Não Confere Direito a Dedução"),
    M20("20", "IVA - não confere direito a dedução (REMPE)", "Tributo Especial Unificado"),
    M21("21", "Isenções do Orçamento do Estado", "Isento OE");

    private final String codigo;
    private final String descricao;
    private final String mencao;

    MotivoIsencaoIva(String codigo, String descricao, String mencao) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.mencao = mencao;
    }

    public String codigo() {
        return codigo;
    }

    public String descricao() {
        return descricao;
    }

    public String mencao() {
        return mencao;
    }

    /** Devolve o motivo com este código oficial, ou vazio para null/em branco/desconhecido. */
    public static Optional<MotivoIsencaoIva> porCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values()).filter(m -> m.codigo.equals(codigo)).findFirst();
    }
}
