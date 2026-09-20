package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "t_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "nome")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String nome;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "t_role_permission",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();

    // Phase 125 (MOLD-01): marca se este papel global e um molde instanciavel por escritorio
    // -- a fonte de t_tenant_role. Falha fechada (default false): um papel novo so se torna
    // molde se alguem o declarar explicitamente, e DatabaseSeeder.seedRbac() (upsertRolePermissions
    // com terceiro argumento booleano) e o unico ponto onde isso acontece hoje, convergindo em
    // cada arranque. PLATAFORMA_ADMIN e o caso que este default protege: nasce e permanece
    // instanciavel = false em todo arranque, para nunca poder ser instanciado, editado como
    // molde nem atribuido a partir de superficie de escritorio (continuacao das guardas das
    // Phases 119 e 121). columnDefinition garante que um ambiente que ainda nao tem esta coluna
    // (ddl-auto=update) a cria ja com o default fechado; @Builder.Default e obrigatorio, senao
    // qualquer Role.builder()...build() que nao fixe o campo recebe null em vez de false.
    @Column(name = "instanciavel", nullable = false, columnDefinition = "boolean not null default false")
    @Builder.Default
    private Boolean instanciavel = false;
}
