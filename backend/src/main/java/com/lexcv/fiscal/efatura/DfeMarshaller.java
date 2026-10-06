package com.lexcv.fiscal.efatura;

import com.lexcv.fiscal.efatura.xsd.Dfe;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serializa um {@link Dfe} (modelo JAXB gerado do XSD oficial) para os bytes UTF-8 exatos que são
 * validados, guardados e enviados. O {@link JAXBContext} é criado uma vez (thread-safe); cada
 * chamada cria o seu {@link Marshaller} (não thread-safe). Sem saída formatada: os bytes
 * guardados são os bytes cujo hash é calculado.
 */
@Component
public final class DfeMarshaller {

    private static final byte[] DECLARACAO =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>".getBytes(StandardCharsets.UTF_8);

    private final JAXBContext contexto;

    public DfeMarshaller() {
        this.contexto = criarContexto();
    }

    private static JAXBContext criarContexto() {
        try {
            return JAXBContext.newInstance(Dfe.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("Modelo JAXB eFatura indisponível", e);
        }
    }

    /** Contexto partilhado (para testes que precisam de um Unmarshaller do mesmo modelo). */
    JAXBContext contexto() {
        return contexto;
    }

    /** Bytes UTF-8 do documento, com a declaração XML sem {@code standalone}; array novo por chamada. */
    public byte[] marshal(Dfe dfe) {
        if (dfe == null) {
            throw new IllegalArgumentException("Documento DFE em falta");
        }
        try {
            Marshaller marshaller = contexto.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_ENCODING, StandardCharsets.UTF_8.name());
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            saida.writeBytes(DECLARACAO);
            marshaller.marshal(dfe, saida);
            return saida.toByteArray();
        } catch (JAXBException e) {
            throw new IllegalStateException("Falha ao serializar o documento DFE", e);
        }
    }
}
