package com.lexcv.fiscal.pdf;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fixa byte a byte as três fontes DejaVu 2.37 vendorizadas em {@code pdf/fonts/} e a presença da
 * sua licença. São as únicas fontes que o renderer de PDF fiscal carrega (137-06); qualquer
 * alteração a um destes ficheiros falha o build. Ver {@code src/main/resources/pdf/README.md}.
 */
class FontesPdfIntegridadeTest {

    private static final String RAIZ_FONTES = "pdf/fonts/";

    /** Nome do ficheiro relativo a {@code pdf/fonts/} -> SHA-256 (ver README.md e 137-SPIKE.md E). */
    static final Map<String, String> MANIFESTO_FONTES = new LinkedHashMap<>();

    static {
        MANIFESTO_FONTES.put("DejaVuSans.ttf", "7da195a74c55bef988d0d48f9508bd5d849425c1770dba5d7bfc6ce9ed848954");
        MANIFESTO_FONTES.put("DejaVuSans-Bold.ttf", "e6476c1b80502924294eed40894c5b18e06c181444ca953e5334262df9c27724");
        MANIFESTO_FONTES.put("DejaVuSansMono.ttf", "b4a6c3e4faab8773f4ff761d56451646409f29abedd68f05d38c2df667d3c582");
    }

    @Test
    void manifestoTemExatamenteTresFontes() {
        assertThat(MANIFESTO_FONTES).hasSize(3);
    }

    @Test
    void cadaFonteVendorizadaCorrespondeAoHashDoManifesto() throws Exception {
        for (Map.Entry<String, String> entrada : MANIFESTO_FONTES.entrySet()) {
            assertThat(sha256DoRecurso(RAIZ_FONTES + entrada.getKey()))
                    .as("SHA-256 de %s%s", RAIZ_FONTES, entrada.getKey())
                    .isEqualTo(entrada.getValue());
        }
    }

    @Test
    void licencaDejaVuEstaPresenteEMencionaBitstreamVera() throws Exception {
        byte[] licenca = lerRecurso(RAIZ_FONTES + "LICENSE-DejaVu.txt");
        assertThat(licenca).as("LICENSE-DejaVu.txt").isNotEmpty();
        assertThat(new String(licenca, StandardCharsets.UTF_8)).contains("Bitstream Vera");
    }

    @Test
    void readmeDeProvenienciaListaCadaHash() throws Exception {
        String readme = new String(lerRecurso("pdf/README.md"), StandardCharsets.UTF_8);
        assertThat(readme).contains("SHA-256");
        for (Map.Entry<String, String> entrada : MANIFESTO_FONTES.entrySet()) {
            assertThat(readme).as("README lista %s", entrada.getKey())
                    .contains(entrada.getKey()).contains(entrada.getValue());
        }
    }

    private static byte[] lerRecurso(String caminho) throws IOException {
        try (InputStream in = FontesPdfIntegridadeTest.class.getClassLoader().getResourceAsStream(caminho)) {
            assertThat(in).as("recurso %s no classpath", caminho).isNotNull();
            return in.readAllBytes();
        }
    }

    private static String sha256DoRecurso(String caminho) throws IOException, NoSuchAlgorithmException {
        try (InputStream in = FontesPdfIntegridadeTest.class.getClassLoader().getResourceAsStream(caminho)) {
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
