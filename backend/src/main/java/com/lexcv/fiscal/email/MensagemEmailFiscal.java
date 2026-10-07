package com.lexcv.fiscal.email;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Phase 137 (ENTR-03): a mensagem a enviar ao cliente, preenchida pelo compositor (137-14).
 * Multipart (texto simples + HTML simples com o mesmo texto) com exatamente dois anexos: o PDF e o
 * XML do documento.
 *
 * <p>{@link #toString()} nunca mostra o destinatário (dado pessoal) nem o conteúdo.
 *
 * @param replyTo endereço de contacto do escritório, quando existe
 */
public record MensagemEmailFiscal(String destinatario, Optional<String> replyTo, String assunto,
                                  String textoSimples, String html, List<Anexo> anexos) {

    public MensagemEmailFiscal {
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("Destinatário em falta");
        }
        if (assunto == null || assunto.isBlank()) {
            throw new IllegalArgumentException("Assunto em falta");
        }
        Objects.requireNonNull(textoSimples, "textoSimples");
        Objects.requireNonNull(html, "html");
        replyTo = replyTo == null ? Optional.empty() : replyTo;
        if (anexos == null || anexos.size() != 2) {
            throw new IllegalArgumentException("A mensagem fiscal tem exatamente dois anexos");
        }
        anexos = List.copyOf(anexos);
    }

    @Override
    public String toString() {
        return "MensagemEmailFiscal[anexos=" + anexos.stream().map(Anexo::nome).toList() + "]";
    }

    /** Um anexo; os bytes são copiados na entrada e na saída. */
    public record Anexo(String nome, String contentType, byte[] conteudo) {

        public Anexo {
            if (nome == null || nome.isBlank()) {
                throw new IllegalArgumentException("Nome do anexo em falta");
            }
            if (contentType == null || contentType.isBlank()) {
                throw new IllegalArgumentException("Tipo do anexo em falta");
            }
            Objects.requireNonNull(conteudo, "conteudo");
            conteudo = conteudo.clone();
        }

        @Override
        public byte[] conteudo() {
            return conteudo.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Anexo a && nome.equals(a.nome) && contentType.equals(a.contentType)
                    && java.util.Arrays.equals(conteudo, a.conteudo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nome, contentType, java.util.Arrays.hashCode(conteudo));
        }

        @Override
        public String toString() {
            return "Anexo[nome=" + nome + ", contentType=" + contentType + ", bytes=" + conteudo.length + "]";
        }
    }
}
