package com.lexcv.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Phase 133: recusa de uma regra fiscal (ex.: ativar a faturação com dados incompletos,
 * alterar o NIF depois do primeiro documento).
 *
 * <p>Os serviços lançam-na DENTRO de {@code @Transactional}: por ser uma
 * {@link RuntimeException}, a transação faz rollback automático e nenhum evento de auditoria
 * fica gravado -- o mesmo objetivo de {@code RecusaTransacional}, mas para recusas na camada de
 * serviço (que não devolve {@code ResponseEntity}). {@code GlobalExceptionHandler} converte-a
 * em {@code {message, code, campo?}} com o {@link #getStatus()} indicado.
 *
 * <p>A mensagem tem de ser SEMPRE texto em português, seguro para o cliente e escrito pelo
 * LexCV -- nunca a mensagem de uma exceção de biblioteca.
 */
public class RecusaFiscalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final HttpStatus status;
    private final String codigo;
    private final String campo;

    public RecusaFiscalException(HttpStatus status, String codigo, String mensagem) {
        this(status, codigo, mensagem, null);
    }

    public RecusaFiscalException(HttpStatus status, String codigo, String mensagem, String campo) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.campo = campo;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }

    /** Campo do pedido a que a recusa diz respeito, ou {@code null}. */
    public String getCampo() {
        return campo;
    }
}
