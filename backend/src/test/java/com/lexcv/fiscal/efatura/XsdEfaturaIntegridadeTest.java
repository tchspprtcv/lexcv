package com.lexcv.fiscal.efatura;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixa byte a byte o pacote XSD eFatura 2024-05-27 vendorizado (22 XSD) e os 4 ficheiros de
 * documentação/exemplo de teste. Qualquer alteração a um ficheiro vendorizado falha o build;
 * quando a fonte oficial (efatura.cv) estiver acessível, a verificação do portão é comparar
 * estes hashes. Ver {@code src/main/resources/xsd/efatura/README.md}.
 */
class XsdEfaturaIntegridadeTest {

    private static final String RAIZ_XSD = "xsd/efatura/";
    private static final String RAIZ_EXEMPLOS = "efatura/2024-05-27/";

    private static final Map<String, String> MANIFESTO_XSD = new LinkedHashMap<>();
    private static final Map<String, String> MANIFESTO_EXEMPLOS = new LinkedHashMap<>();

    static {
        MANIFESTO_XSD.put("EnvelopedSignature.xsd", "3366a38ee5632818da0ac830b608e8449aea27e744ea28bc200a68129ffc2fa0");
        MANIFESTO_XSD.put("InternallyDetachedSignature.xsd", "b2c669415dede8cf9e6a6c67578db1952c40d064a48d5743e301ec394df40aa6");
        MANIFESTO_XSD.put("common/CV_EFatura_CreditNote_v1.0.xsd", "6eab59302f2dd0cad0cbba0e5b640a79a8d99e5eed511ed390095f27f05cfe27");
        MANIFESTO_XSD.put("common/CV_EFatura_DebitNote_v1.0.xsd", "aea1fcea8c214204089bfcfec2e21c23b51bc7c59334662f8213b748ef413dd1");
        MANIFESTO_XSD.put("common/CV_EFatura_Elements_v1.0.xsd", "8ac4cfa0d2e8125335f3fdbb9fd23bf4b1a6897a7aba81f4ead5311d9c61667f");
        MANIFESTO_XSD.put("common/CV_EFatura_InvoiceReceipt_v1.0.xsd", "d5bdf74d5baad6a7794f7923f5e2f73f70d2d1ce05f1c805f5b51fc63a05c523");
        MANIFESTO_XSD.put("common/CV_EFatura_Invoice_v1.0.xsd", "c2be64f05f2dcc78f5d270ab1620217428935a16958dbc5caa037f1173aa8c01");
        MANIFESTO_XSD.put("common/CV_EFatura_MainElements_v1.0.xsd", "8c0ef2a8d3fdefa78b3e55234fa991a7398d434df10f3e0aae3d1be2bb63f15d");
        MANIFESTO_XSD.put("common/CV_EFatura_MainTypes_v1.0.xsd", "e1ac68ae79d36c13a0354f7b405e64841060a7eeeec5da4478b04569078d0fb0");
        MANIFESTO_XSD.put("common/CV_EFatura_Receipt_v1.0.xsd", "6ce312ba730ec76ade4c8c2c5a8d84b54409b69f0f3fa6e5628a3853a85a7afd");
        MANIFESTO_XSD.put("common/CV_EFatura_RegistrationNote_v1.0.xsd", "7f8435b918d5577017302f016dd1e73d1af25bff8642f84b33f8bc26e87f1ce1");
        MANIFESTO_XSD.put("common/CV_EFatura_ReturnNote_v1.0.xsd", "9b0886b5770745f66ca4e4da99ce2eac1e6beb84fdbbbdd69b5da766f01ba48b");
        MANIFESTO_XSD.put("common/CV_EFatura_SalesReceipt_v1.0.xsd", "aa2231dd32ad2aec44627eb48ac35e7fbfc17ed8f32057a6ddd06ab6bb9787ec");
        MANIFESTO_XSD.put("common/CV_EFatura_TaxExemptionReason_v1.0.xsd", "188c74e95e166159d2ec8e8cd74f568dcaed6f5bb64ba3639d78448aefd6cadf");
        MANIFESTO_XSD.put("common/CV_EFatura_Transport_v1.0.xsd", "9154f0286f5c79851c0fe7a9ff2d52302aa3218a345ba63e35fc51ff0b7be57a");
        MANIFESTO_XSD.put("common/CV_EFatura_Types_v1.0.xsd", "c0f34a7115f48d395566a05b7331194def4940df3e4374cb029a611d7975f487");
        MANIFESTO_XSD.put("common/ETSI_XAdESv132.xsd", "dcec1fae271c8b1d7a92acc79cf3504e6fab0aa235d8cb2f4d412e889809660b");
        MANIFESTO_XSD.put("common/ETSI_XAdESv141.xsd", "b202675d8ef478ea74ed1f4bde9b2d661236d9dfb7546d9c7fd7c7894c8d0630");
        MANIFESTO_XSD.put("common/ISO_ISO3AlphaCurrencyCode_2012-08-31.xsd", "1f19cb4196d9a4b533de3f1df7cd317cdd6af38fdfe98297b8a929a5018197d2");
        MANIFESTO_XSD.put("common/ISO_ISOTwo-letterCountryCode_SecondEdition2006.xsd", "e0d1162144f1126d292ec5adcc3f3fbb83398e527a00e8f3bcdfd8dbb1e0d462");
        MANIFESTO_XSD.put("common/UNECE_PaymentMeansCode_D19B.xsd", "56b1100a7f14d1d68abd73108d12947c62a0b44ad44eefa9ba1a33e454c786ae");
        MANIFESTO_XSD.put("common/W3C_XMLDSig.xsd", "b4716e1d9ad185b43bdc3464aa584fa5a24b33617361d73240d79858a550474c");

        MANIFESTO_EXEMPLOS.put("Read Me.txt", "6f31ac6a67cc77fb2ec90d6079ff7d9ed6ab3bf35bdb39d8154824f8e1be4492");
        MANIFESTO_EXEMPLOS.put("XML Fields Map.txt", "95a1aede7d29b25afc04d36a98241b1e1057a2dc150ea573ac0e0914116b48ba");
        MANIFESTO_EXEMPLOS.put("2 InvoiceReceipt.xml", "667e592d33e66f3bdcfd983ea94712705327299476e32ec6b0b05337fc63832b");
        MANIFESTO_EXEMPLOS.put("5 CreditNote.xml", "3b6a080fdb8af5fe45c27248c587364a1a1aa1742b627c703501e38c8ea00b13");
    }

    @Test
    void manifestoTemExatamente22XsdE4Exemplos() {
        assertThat(MANIFESTO_XSD).hasSize(22);
        assertThat(MANIFESTO_EXEMPLOS).hasSize(4);
    }

    @Test
    void cadaXsdVendorizadoCorrespondeAoHashDoManifesto() throws Exception {
        for (Map.Entry<String, String> entrada : MANIFESTO_XSD.entrySet()) {
            assertThat(sha256DoRecurso(RAIZ_XSD + entrada.getKey()))
                    .as("SHA-256 de %s%s", RAIZ_XSD, entrada.getKey())
                    .isEqualTo(entrada.getValue());
        }
    }

    @Test
    void cadaExemploDeTesteCorrespondeAoHashDoManifesto() throws Exception {
        for (Map.Entry<String, String> entrada : MANIFESTO_EXEMPLOS.entrySet()) {
            assertThat(sha256DoRecurso(RAIZ_EXEMPLOS + entrada.getKey()))
                    .as("SHA-256 de %s%s", RAIZ_EXEMPLOS, entrada.getKey())
                    .isEqualTo(entrada.getValue());
        }
    }

    @Test
    void pastaNaoTemXsdForaDoManifestoETemReadmeDeProveniencia() throws Exception {
        assertThat(recurso(RAIZ_XSD + "README.md")).as("README.md de proveniência").isNotNull();

        URL raiz = recurso(RAIZ_XSD);
        assertThat(raiz).as("pasta %s no classpath", RAIZ_XSD).isNotNull();
        assertThat(raiz.getProtocol()).isEqualTo("file");
        Path pasta = Paths.get(raiz.toURI());
        Set<String> encontrados;
        try (Stream<Path> caminhos = Files.walk(pasta)) {
            encontrados = caminhos
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".xsd"))
                    .map(p -> pasta.relativize(p).toString().replace('\\', '/'))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        assertThat(encontrados).isEqualTo(new TreeSet<>(MANIFESTO_XSD.keySet()));
    }

    private static URL recurso(String caminho) {
        return XsdEfaturaIntegridadeTest.class.getClassLoader().getResource(caminho);
    }

    private static String sha256DoRecurso(String caminho)
            throws IOException, NoSuchAlgorithmException, URISyntaxException {
        try (InputStream in = XsdEfaturaIntegridadeTest.class.getClassLoader().getResourceAsStream(caminho)) {
            assertThat(in).as("recurso %s no classpath", caminho).isNotNull();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int lidos;
            while ((lidos = in.read(buffer)) != -1) {
                digest.update(buffer, 0, lidos);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
    }
}
