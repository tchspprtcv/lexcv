package com.lexcv.fiscal.efatura;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DfeValidadorTest {

    private static final String FRE = "efatura/2024-05-27/2 InvoiceReceipt.xml";
    private static final String NCE = "efatura/2024-05-27/5 CreditNote.xml";

    private static DfeValidador validador;

    @BeforeAll
    static void construir() {
        validador = new DfeValidador();
    }

    static String exemplo(String recurso) throws IOException {
        try (InputStream in = DfeValidadorTest.class.getClassLoader().getResourceAsStream(recurso)) {
            assertThat(in).as(recurso).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ResultadoValidacao validar(String xml) {
        return validador.validar(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void exemplosOficiaisSaoValidos() throws IOException {
        assertThat(validar(exemplo(FRE))).isEqualTo(ResultadoValidacao.sucesso());
        assertThat(validar(exemplo(NCE)).valido()).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "PaymentMeansCode fora da D19B | <PaymentMeansCode>31</PaymentMeansCode> | <PaymentMeansCode>999</PaymentMeansCode> | 999",
            "RepositoryCode 4 | <RepositoryCode>1</RepositoryCode> | <RepositoryCode>4</RepositoryCode> | <RepositoryCode>4",
            "espaço duplo num nome | <Name>Nome do Emissor</Name> | <Name>Nome  do Emissor</Name> | Nome  do",
            "seis casas decimais | <PriceExtension>30000</PriceExtension> | <PriceExtension>30000.123456</PriceExtension> | 123456",
    })
    void mutacoesDoExemploFreSaoRecusadasSemExporOValor(String caso, String de, String para, String valorInjetado)
            throws IOException {
        String original = exemplo(FRE);
        assertThat(original).contains(de);

        ResultadoValidacao resultado = validar(original.replace(de, para));

        assertThat(resultado.valido()).as(caso).isFalse();
        assertThat(resultado.codigo()).isEqualTo(ResultadoValidacao.XSD_INVALIDO);
        assertThat(resultado.linha()).isPositive();
        assertThat(resultado.toString()).doesNotContain(valorInjetado);
    }

    @Test
    void notaComMenosDe10CaracteresERecusada() throws IOException {
        String nce = exemplo(NCE).replaceFirst("<Note>[^<]*</Note>", "<Note>Curta</Note>");
        assertThat(nce).contains("<Note>Curta</Note>");

        ResultadoValidacao resultado = validar(nce);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.codigo()).isEqualTo(ResultadoValidacao.XSD_INVALIDO);
        assertThat(resultado.toString()).doesNotContain("Curta");
    }

    @Test
    void doctypeComEntidadeExternaERecusadoSemLerOFicheiro() throws IOException {
        String xxe = exemplo(FRE)
                .replaceFirst("<Dfe ", "<!DOCTYPE Dfe [<!ENTITY segredo SYSTEM \"file:///etc/hostname\">]>\n<Dfe ")
                .replace("<Name>Nome do Emissor</Name>", "<Name>&segredo;</Name>");

        ResultadoValidacao resultado = validar(xxe);

        assertThat(resultado.valido()).isFalse();
        assertThat(resultado.codigo()).isEqualTo(ResultadoValidacao.XML_PROIBIDO);
        assertThat(resultado.toString()).doesNotContain("hostname").doesNotContain("segredo");
    }

    @Test
    void doctypeSemEntidadesTambemERecusado() throws IOException {
        String comDoctype = exemplo(FRE).replaceFirst("<Dfe ", "<!DOCTYPE Dfe>\n<Dfe ");

        assertThat(validar(comDoctype).codigo()).isEqualTo(ResultadoValidacao.XML_PROIBIDO);
    }

    @Test
    void xmlMalFormadoOuVazioNuncaLancaExcecao() {
        assertThat(validador.validar(new byte[0]).valido()).isFalse();
        assertThat(validador.validar(null).codigo()).isEqualTo(ResultadoValidacao.XML_ILEGIVEL);
        assertThat(validar("<Dfe><nao-fecha>").valido()).isFalse();
    }

    @Test
    void esquemaDeEntradaEmFaltaFalhaNoArranque() {
        assertThatThrownBy(() -> new DfeValidador("xsd/efatura/NaoExiste.xsd"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void esquemaForaDaListaBrancaFalhaNoArranque() {
        // Só um XSD da lista branca pode ser o esquema de entrada.
        assertThatThrownBy(() -> new DfeValidador("xsd/bindings/efatura.xjb"))
                .isInstanceOf(IllegalStateException.class);
    }
}
