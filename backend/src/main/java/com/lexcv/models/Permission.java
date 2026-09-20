package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "t_permission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "nome")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String nome;

    // Phase 124 (CATL-01): rotulo/descricao/modulo/ordem sao as colunas descritivas do
    // catalogo de permissoes -- antes desta fase existiam apenas como uma lista Java
    // hardcoded em AdminController.getRbac(). Ficam nullable de proposito: t_permission ja
    // tem 20 linhas reais em bases de dados existentes (com t_role_permission/
    // t_user_permission a referenciar o seu id) e DatabaseSeeder.seedRbac() -- que corre
    // incondicionalmente na primeira instrucao de run(), antes do gate seedEnabled, logo
    // tambem em producao -- povoa-as no mesmo arranque em que as colunas aparecem. Um
    // ALTER TABLE ... NOT NULL sem default falharia contra as linhas existentes (mesma
    // armadilha documentada em Tenant.java:40-49/63-74 para plano/ativo).
    @Column(name = "rotulo", length = 255)
    private String rotulo;

    @Column(name = "descricao", length = 500)
    private String descricao;

    // Categoria/modulo de agrupamento no ecra de Definicoes (RBAC); RbacTab deriva a ordem
    // dos separadores da ordem de chegada do array systemPermissions.
    @Column(name = "modulo", length = 255)
    private String modulo;

    // Ordem de apresentacao dentro do catalogo. Existe porque
    // web/src/app/(dashboard)/settings/page.tsx deriva a ordem dos modulos de
    // Array.from(new Set(systemPermissions.map(p => p.modulo))), isto e, da ordem de
    // chegada do array -- sem esta coluna a matriz RBAC reordenava-se entre pedidos.
    @Column(name = "ordem")
    private Integer ordem;

    // Falha fechada (CATL-03): uma permissao que chegue a t_permission sem ser declarada
    // pelo catalogo da plataforma (codigo futuro, linha manual, script de terceiros) nasce
    // marcada reservada e nunca e oferecida a um escritorio. columnDefinition garante que
    // um ambiente novo (ddl-auto=update contra uma tabela ainda sem esta coluna) a cria ja
    // com o default certo, tal como Tenant.ativo (Tenant.java:63-74). @Builder.Default e
    // obrigatorio: sem ele, qualquer Permission.builder()...build() que nao fixe este campo
    // explicitamente (incluindo os 20 pontos de construcao dentro do proprio
    // DatabaseSeeder.seedRbac()) receberia null em vez do default fechado. DatabaseSeeder
    // poe explicitamente false nas 20 entradas do catalogo em cada arranque -- a reserva do
    // papel PLATAFORMA_ADMIN (Phase 119) continua a ser feita por colecao de permissoes
    // vazia (upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList())), nao por
    // esta marca; hoje nenhuma das 20 permissoes do catalogo e reservada.
    @Column(name = "reservada_plataforma", nullable = false, columnDefinition = "boolean not null default true")
    @Builder.Default
    private Boolean reservadaPlataforma = true;
}
