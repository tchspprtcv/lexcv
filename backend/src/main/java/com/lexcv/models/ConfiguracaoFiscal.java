package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Phase 133 (CFG-01..06): dados fiscais do escritório emitente, 1:1 com o tenant
 * ({@code tenant_id} UNIQUE). Tabela própria -- {@code t_tenant} não é alterada.
 *
 * <p>{@code ativa} e {@code envioEmailAutomatico} começam desligados: um escritório que não ative
 * a faturação não vê nenhuma mudança de comportamento. Esquema manual equivalente:
 * {@code backend/migrations/133-create-fiscal-foundation-tables.sql}.
 */
@Entity
@Table(name = "t_configuracao_fiscal",
       uniqueConstraints = @UniqueConstraint(
               name = "uk_configuracao_fiscal_tenant",
               columnNames = {"tenant_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfiguracaoFiscal {

    /**
     * NIF fiscal do emitente: 9 dígitos com o primeiro de 1 a 9 (mais apertado do que o NIF de
     * cliente, {@code ^\d{9}$}). Reutilizado pelo DTO de pedido (Plan 04).
     */
    public static final String NIF_FISCAL_REGEX = "^[1-9]\\d{8}$";

    private static final Pattern NIF_FISCAL = Pattern.compile(NIF_FISCAL_REGEX);

    public static final int MORADA_MAX = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "nif", length = 9)
    private String nif;

    @Column(name = "firma", length = 200)
    private String firma;

    @Column(name = "morada", length = MORADA_MAX)
    private String morada;

    @Column(name = "localidade", length = 100)
    private String localidade;

    // O serviço escreve sempre "CV" (país fixo Cabo Verde).
    @Column(name = "pais_codigo", length = 2)
    private String paisCodigo;

    @Column(name = "email_contacto", length = 254)
    private String emailContacto;

    @Column(name = "telefone_contacto", length = 32)
    private String telefoneContacto;

    // Sem CHECK na coluna (varchar simples): novos valores de enum não exigem DROP CONSTRAINT.
    @Enumerated(EnumType.STRING)
    @Column(name = "regime_iva", columnDefinition = "varchar(32)")
    private RegimeIva regimeIva;

    // Código oficial MotivoIsencaoIva.codigo() ("1".."21"); obrigatório quando ISENTO.
    @Column(name = "motivo_isencao_codigo", length = 2)
    private String motivoIsencaoCodigo;

    @Column(name = "ativa", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean ativa = false;

    @Column(name = "envio_email_automatico", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean envioEmailAutomatico = false;

    @Column(name = "envio_email_aceite_por")
    private UUID envioEmailAceitePor;

    @Column(name = "envio_email_aceite_em")
    private Instant envioEmailAceiteEm;

    // Sem @PrePersist com Instant.now(): o serviço define createdAt a partir do Clock injetado.
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    /**
     * Dados fiscais suficientes para ativar a faturação: NIF fiscal válido, firma, morada
     * (no máximo 100 caracteres), localidade, email e telefone preenchidos, regime de IVA
     * definido e, quando ISENTO, um motivo de isenção oficial.
     */
    public boolean completa() {
        if (nif == null || !NIF_FISCAL.matcher(nif).matches()) {
            return false;
        }
        if (vazio(firma) || vazio(morada) || vazio(localidade)
                || vazio(emailContacto) || vazio(telefoneContacto)) {
            return false;
        }
        if (morada.length() > MORADA_MAX) {
            return false;
        }
        if (regimeIva == null) {
            return false;
        }
        return regimeIva == RegimeIva.NORMAL || MotivoIsencaoIva.porCodigo(motivoIsencaoCodigo).isPresent();
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
