package com.lexcv.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "t_cliente",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "documento_numero"}),
        @UniqueConstraint(columnNames = {"tenant_id", "numero_sequencial"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    private String tipo;

    @NotBlank(message = "Nome é obrigatório")
    @Column(nullable = false)
    private String nome;

    @NotBlank(message = "NIF é obrigatório")
    @Pattern(regexp = "^\\d{9}$", message = "NIF deve conter exatamente 9 dígitos numéricos")
    private String nif;
    private String email;
    private String telefone;
    private String morada;
    private String localidade;
    private Boolean ativo;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento_tipo")
    private DocumentoTipo documentoTipo;

    @Column(name = "documento_numero")
    private String documentoNumero;

    @Column(name = "ramo_atividade")
    private String ramoAtividade;

    @Column(name = "detalhes_adicionais", length = 255)
    private String detalhesAdicionais;

    @Column(name = "numero_sequencial")
    private Integer numeroSequencial;

    @Column(name = "numero_cliente", length = 20)
    @JsonProperty("numero_cliente")
    private String numeroCliente;

    @Column(name = "avencado")
    private Boolean avencado;

    @Column(name = "procuracao_key")
    @JsonProperty("procuracao_key")
    private String procuracaoKey;

    @Column(name = "descricao_caso", columnDefinition = "TEXT")
    @JsonProperty("descricao_caso")
    private String descricaoCaso;

    @Column(name = "documentos_entregues", columnDefinition = "TEXT")
    @Convert(converter = DocumentosEntreguesConverter.class)
    @JsonProperty("documentos_entregues")
    private List<DocumentoEntregue> documentosEntregues;

    @Column(name = "documentos_a_tratar", columnDefinition = "TEXT")
    @Convert(converter = DocumentosATratarConverter.class)
    @JsonProperty("documentos_a_tratar")
    private List<DocumentoATratar> documentosATratar;

    @Column(name = "deslocacoes", columnDefinition = "TEXT")
    @Convert(converter = DeslocacoesConverter.class)
    private List<Deslocacao> deslocacoes;

    @Column(name = "honorarios_propostos", columnDefinition = "TEXT")
    @Convert(converter = HonorariosPropostosConverter.class)
    @JsonProperty("honorarios_propostos")
    private HonorariosPropostos honorariosPropostos;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.ativo == null) {
            this.ativo = true;
        }
    }
}
