package com.lexcv.dtos;

/**
 * Phase 137 (ENTR-04): resposta do reenvio manual do email de um documento fiscal -- a entrega
 * volta a {@code PENDENTE} com o contador de tentativas a zero; o job envia na próxima execução.
 */
public record ReenviarEmailResponse(String estado, int tentativas) {
}
