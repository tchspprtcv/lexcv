package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Phase 138 (SUBS-02): Pagamento de subscrição de um escritório cliente à plataforma LexCV.
 * Regista o recebimento da anuidade/mensalidade e serve de base à emissão da Fatura-Recibo de
 * subscrição correspondente.
 */
@Entity
@Table(name = "t_pagamento_subscricao",
       indexes = {
               @Index(name = "idx_pagamento_subscricao_adquirente", columnList = "adquirente_tenant_id"),
               @Index(name = "idx_pagamento_subscricao_data", columnList = "data_pagamento")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagamentoSubscricao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "adquirente_tenant_id", nullable = false)
    private UUID adquirenteTenantId;

    @Column(name = "valor_pago", precision = 19, scale = 2, nullable = false)
    private BigDecimal valorPago;

    @Column(name = "data_pagamento", nullable = false)
    private LocalDate dataPagamento;

    @Column(name = "metodo", length = 32, nullable = false)
    private String metodo;

    @Column(name = "periodo_inicio")
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim")
    private LocalDate periodoFim;

    @Column(name = "plano", length = 32)
    private String plano;

    @Column(name = "criado_por_id")
    private UUID criadoPorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
