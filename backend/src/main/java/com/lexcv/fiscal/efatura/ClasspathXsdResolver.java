package com.lexcv.fiscal.efatura;

import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Resolve os {@code include}/{@code import} do pacote XSD eFatura apenas a partir do classpath e
 * apenas para os 22 ficheiros vendorizados (lista branca fixa). Qualquer outro {@code systemId}
 * -- {@code file:}, {@code http:}, {@code ../} para fora do pacote, o {@code .xjb} ou o README --
 * resolve para {@code null}; com {@code ACCESS_EXTERNAL_SCHEMA=""} no {@code SchemaFactory}, isso
 * faz falhar o carregamento do esquema em vez de ir buscar o recurso a outro lado (T-136-17).
 *
 * <p>A normalização usa {@link URI#resolve} sobre uma base sintética hierárquica, nunca a API de caminhos do
 * sistema de ficheiros (FindSecBugs PATH_TRAVERSAL_IN, observado na investigação).
 */
public final class ClasspathXsdResolver implements LSResourceResolver {

    /** Prefixo dos systemIds que este resolvedor emite e aceita como base. */
    public static final String PREFIXO = "classpath:xsd/efatura/";

    private static final String RAIZ_RECURSOS = "xsd/efatura/";
    private static final String ESQUEMA_SINTETICO = "classpath";
    private static final String CAMINHO_SINTETICO = "/xsd/efatura/";

    /** Os 22 XSD do pacote 2024-05-27, relativos a {@code xsd/efatura/} (ver XsdEfaturaIntegridadeTest). */
    static final Set<String> LISTA_BRANCA = Set.of(
            "EnvelopedSignature.xsd",
            "InternallyDetachedSignature.xsd",
            "common/CV_EFatura_CreditNote_v1.0.xsd",
            "common/CV_EFatura_DebitNote_v1.0.xsd",
            "common/CV_EFatura_Elements_v1.0.xsd",
            "common/CV_EFatura_InvoiceReceipt_v1.0.xsd",
            "common/CV_EFatura_Invoice_v1.0.xsd",
            "common/CV_EFatura_MainElements_v1.0.xsd",
            "common/CV_EFatura_MainTypes_v1.0.xsd",
            "common/CV_EFatura_Receipt_v1.0.xsd",
            "common/CV_EFatura_RegistrationNote_v1.0.xsd",
            "common/CV_EFatura_ReturnNote_v1.0.xsd",
            "common/CV_EFatura_SalesReceipt_v1.0.xsd",
            "common/CV_EFatura_TaxExemptionReason_v1.0.xsd",
            "common/CV_EFatura_Transport_v1.0.xsd",
            "common/CV_EFatura_Types_v1.0.xsd",
            "common/ETSI_XAdESv132.xsd",
            "common/ETSI_XAdESv141.xsd",
            "common/ISO_ISO3AlphaCurrencyCode_2012-08-31.xsd",
            "common/ISO_ISOTwo-letterCountryCode_SecondEdition2006.xsd",
            "common/UNECE_PaymentMeansCode_D19B.xsd",
            "common/W3C_XMLDSig.xsd");

    @Override
    public LSInput resolveResource(String type, String namespaceURI, String publicId, String systemId,
                                   String baseURI) {
        String relativo = resolverRelativo(systemId, baseURI);
        if (relativo == null) {
            return null;
        }
        byte[] bytes = lerRecurso(relativo);
        return bytes == null ? null : new EntradaXsd(PREFIXO + relativo, publicId, bytes);
    }

    /**
     * Caminho relativo a {@code xsd/efatura/} do recurso pedido, ou {@code null} se não for um dos
     * 22 ficheiros da lista branca depois de normalizado.
     */
    static String resolverRelativo(String systemId, String baseURI) {
        if (systemId == null || baseURI == null || !baseURI.startsWith(PREFIXO)) {
            return null;
        }
        try {
            URI base = new URI(ESQUEMA_SINTETICO, null, CAMINHO_SINTETICO + baseURI.substring(PREFIXO.length()), null);
            URI pedido = systemId.startsWith(PREFIXO)
                    ? new URI(ESQUEMA_SINTETICO, null, CAMINHO_SINTETICO + systemId.substring(PREFIXO.length()), null)
                    : new URI(systemId);
            URI resolvido = base.resolve(pedido).normalize();
            if (!ESQUEMA_SINTETICO.equals(resolvido.getScheme()) || resolvido.getRawAuthority() != null
                    || resolvido.getRawQuery() != null || resolvido.getRawFragment() != null) {
                return null;
            }
            String caminho = resolvido.getPath();
            if (caminho == null || !caminho.startsWith(CAMINHO_SINTETICO)) {
                return null;
            }
            String relativo = caminho.substring(CAMINHO_SINTETICO.length());
            return LISTA_BRANCA.contains(relativo) ? relativo : null;
        } catch (URISyntaxException | IllegalArgumentException e) {
            return null;
        }
    }

    /** Bytes de um recurso da lista branca, ou {@code null} se não existir no classpath. */
    static byte[] lerRecurso(String relativo) {
        if (!LISTA_BRANCA.contains(relativo)) {
            return null;
        }
        ClassLoader cl = ClasspathXsdResolver.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(RAIZ_RECURSOS + relativo)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** LSInput mínimo: só bytes e systemId (o encoding vem da declaração XML do XSD). */
    private static final class EntradaXsd implements LSInput {
        private final String systemId;
        private final String publicId;
        private final byte[] bytes;

        EntradaXsd(String systemId, String publicId, byte[] bytes) {
            this.systemId = systemId;
            this.publicId = publicId;
            this.bytes = bytes;
        }

        @Override public Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(Reader characterStream) { throw new UnsupportedOperationException(); }
        @Override public InputStream getByteStream() { return new ByteArrayInputStream(bytes); }
        @Override public void setByteStream(InputStream byteStream) { throw new UnsupportedOperationException(); }
        @Override public String getStringData() { return null; }
        @Override public void setStringData(String stringData) { throw new UnsupportedOperationException(); }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String systemId) { throw new UnsupportedOperationException(); }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String publicId) { throw new UnsupportedOperationException(); }
        @Override public String getBaseURI() { return systemId; }
        @Override public void setBaseURI(String baseURI) { throw new UnsupportedOperationException(); }
        @Override public String getEncoding() { return null; }
        @Override public void setEncoding(String encoding) { throw new UnsupportedOperationException(); }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean certifiedText) { throw new UnsupportedOperationException(); }
    }
}
