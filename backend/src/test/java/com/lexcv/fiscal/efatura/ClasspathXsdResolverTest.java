package com.lexcv.fiscal.efatura;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.ls.LSInput;

import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ClasspathXsdResolverTest {

    private static final String BASE = "classpath:xsd/efatura/EnvelopedSignature.xsd";
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema";

    private final ClasspathXsdResolver resolver = new ClasspathXsdResolver();

    @Test
    void resolveUmFicheiroDaListaBrancaComOsBytesDoRecurso() throws IOException {
        LSInput input = resolver.resolveResource(XSD_NS, null, null, "common/CV_EFatura_Types_v1.0.xsd", BASE);

        assertThat(input).isNotNull();
        assertThat(input.getSystemId()).isEqualTo("classpath:xsd/efatura/common/CV_EFatura_Types_v1.0.xsd");
        try (InputStream esperado = getClass().getClassLoader()
                .getResourceAsStream("xsd/efatura/common/CV_EFatura_Types_v1.0.xsd")) {
            assertThat(esperado).isNotNull();
            assertThat(input.getByteStream().readAllBytes()).isEqualTo(esperado.readAllBytes());
        }
    }

    @Test
    void resolveRelativoAoDiretorioDeQuemReferencia() {
        LSInput input = resolver.resolveResource(XSD_NS, null, null, "CV_EFatura_Elements_v1.0.xsd",
                "classpath:xsd/efatura/common/CV_EFatura_InvoiceReceipt_v1.0.xsd");

        assertThat(input).isNotNull();
        assertThat(input.getSystemId()).isEqualTo("classpath:xsd/efatura/common/CV_EFatura_Elements_v1.0.xsd");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../bindings/efatura.xjb",
            "../../application.yml",
            "file:///etc/passwd",
            "http://www.w3.org/TR/xmldsig-core/xmldsig-core-schema.xsd",
            "README.md",
            "common/../../x.xsd",
            "common/../README.md",
            "//evil.example/xsd/efatura/EnvelopedSignature.xsd",
            "jar:file:/tmp/x.jar!/xsd/efatura/EnvelopedSignature.xsd",
    })
    void recusaTudoOQueNaoEstaNaListaBranca(String systemId) {
        assertThat(resolver.resolveResource(XSD_NS, null, null, systemId, BASE)).isNull();
    }

    @Test
    void recusaSemBaseConhecidaOuSemSystemId() {
        assertThat(resolver.resolveResource(XSD_NS, null, null, null, BASE)).isNull();
        assertThat(resolver.resolveResource(XSD_NS, null, null, "common/W3C_XMLDSig.xsd", "file:///tmp/x.xsd")).isNull();
    }

    @Test
    void listaBrancaTemExatamenteOs22Xsd() {
        assertThat(ClasspathXsdResolver.LISTA_BRANCA).hasSize(22).allMatch(n -> n.endsWith(".xsd"));
    }
}
