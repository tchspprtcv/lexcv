package com.lexcv.fiscal.efatura;

import com.lexcv.fiscal.efatura.xsd.Dfe;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class DfeMarshallerTest {

    private final DfeMarshaller marshaller = new DfeMarshaller();
    private final DfeValidador validador = new DfeValidador();

    private Dfe lerExemploFre() throws Exception {
        Unmarshaller unmarshaller = marshaller.contexto().createUnmarshaller();
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("efatura/2024-05-27/2 InvoiceReceipt.xml")) {
            assertThat(in).isNotNull();
            return (Dfe) unmarshaller.unmarshal(in);
        }
    }

    @Test
    void idaEVoltaDoExemploFreContinuaValido() throws Exception {
        Dfe dfe = lerExemploFre();

        byte[] bytes = marshaller.marshal(dfe);

        assertThat(validador.validar(bytes)).isEqualTo(ResultadoValidacao.sucesso());
        String xml = new String(bytes, StandardCharsets.UTF_8);
        assertThat(xml).contains("CV1200520123456789000112345678901112345678904");
        assertThat(xml).contains("Nome do Destinatário");
    }

    @Test
    void saidaEUtf8SemStandaloneNemIndentacao() throws Exception {
        String xml = new String(marshaller.marshal(lerExemploFre()), StandardCharsets.UTF_8);

        assertThat(xml).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        assertThat(xml).doesNotContain("standalone");
        assertThat(xml).doesNotContainPattern("\\n\\s+<InvoiceReceipt");
        assertThat(xml).doesNotContainPattern(">\\n\\s+<LedCode");
    }

    @Test
    void cadaChamadaDevolveUmArrayNovo() throws Exception {
        Dfe dfe = lerExemploFre();

        byte[] primeiro = marshaller.marshal(dfe);
        byte[] segundo = marshaller.marshal(dfe);

        assertThat(primeiro).isEqualTo(segundo).isNotSameAs(segundo);
    }
}
