package com.lexcv.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        logger.warn("Validation failed: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> handleConstraintViolationException(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath().toString();
            errors.put(propertyPath, violation.getMessage());
        });
        logger.warn("Constraint violation: {}", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    /**
     * Phase 119 (Plan 04): sem este handler, uma recusa de {@code @PreAuthorize} (method
     * security, {@code @EnableMethodSecurity} em {@code SecurityConfig}) propaga-se como
     * exceção até este {@code @RestControllerAdvice} e cai no catch-all {@link Exception}
     * abaixo, que devolve {@code 500}. Isso (a) faz o Success Criterion 4 da Phase 119 falhar
     * literalmente (exige {@code 403} numa recusa de autorização) e (b) é exatamente o sintoma
     * já registado em STATE.md para {@code GET /api/v1/admin/users} ("For any non-ADMIN role
     * this returns 500 (should be 403)"). Captura deliberadamente a classe pai
     * {@link AccessDeniedException} -- a subclasse concreta que o Spring Security 6.4 lança
     * numa recusa de method security estende esta classe pai; capturar a pai cobre essa
     * subclasse e mantém-se estável se o tipo concreto mudar de versão. A mensagem devolvida ao
     * cliente é genérica e nunca ecoa {@code ex.getMessage()} -- a mensagem interna do Spring
     * Security pode revelar a expressão {@code @PreAuthorize} avaliada; esse detalhe fica só no
     * log do servidor.
     *
     * <p><b>Efeito lateral deliberado (global):</b> este handler aplica-se a todos os endpoints
     * já gated por {@code @PreAuthorize} em todo o backend, por isso qualquer recusa de
     * autorização que hoje devolve {@code 500} passa a devolver {@code 403}. Isto corrige o
     * comportamento incorreto, não o introduz -- ver o SUMMARY desta fase para a análise de
     * impacto no cliente ({@code web/src/lib/api.ts}, linha 43, já trata {@code 403} sem toast
     * e sem redirect).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(AccessDeniedException ex) {
        logger.warn("Acesso negado numa recusa de autorização", ex);
        Map<String, String> body = new HashMap<>();
        body.put("message", "Acesso negado.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Phase 120 (Plan 02): sem este handler, um corpo JSON inválido -- por exemplo um valor de
     * {@code plano} que não corresponde a nenhuma constante de
     * {@link com.lexcv.models.TenantPlano} no corpo do novo {@code PUT /platform/tenants/{id}},
     * ou qualquer JSON malformado em qualquer outro endpoint deste backend -- propaga-se como
     * esta exceção até este {@code @RestControllerAdvice} e cai no catch-all {@link Exception}
     * abaixo, que devolve {@code 500} com o nome da classe da exceção no corpo. Com este handler,
     * a mesma situação devolve {@code 400} com a forma {@code {"message": ...}} idêntica à de
     * todos os outros endpoints deste backend, e deixa de ecoar ao cliente detalhes internos de
     * desserialização.
     *
     * <p><b>Efeito lateral deliberado (global):</b> tal como o handler de
     * {@link AccessDeniedException} acima, este handler aplica-se a todo o backend, não apenas ao
     * endpoint que motivou a sua introdução -- corrige um comportamento já incorreto (um
     * {@code 500} onde deveria haver um {@code 400}), não introduz um novo.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        logger.warn("Corpo do pedido inválido", ex);
        Map<String, String> body = new HashMap<>();
        body.put("message", "Corpo do pedido inválido.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllExceptions(Exception ex) {
        logger.error("Unhandled Exception caught by GlobalExceptionHandler", ex);
        Map<String, String> body = new HashMap<>();
        body.put("error", ex.getClass().getSimpleName());
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
