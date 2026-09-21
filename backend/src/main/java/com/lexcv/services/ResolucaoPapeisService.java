package com.lexcv.services;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 126 (MIGR-02 prep): ponto UNICO de decisao do caminho de resolucao de autoridade --
 * papeis de escritorio (TenantRole) quando existem para o utilizador, papeis globais (Role)
 * quando nao existem. Antes deste servico, a mesma uniao de nomes/permissoes estava triplicada
 * em JwtAuthenticationFilter, AuthController.updateMe e AdminController.listUsers -- tres copias
 * a mudar em lockstep e exactamente a classe de bug que esta fase existe para eliminar: uma
 * alteracao futura pode acertar em duas copias e falhar na terceira, e a terceira e um ecra, nao
 * um erro.
 *
 * <p>Nao introduz nenhuma query nova por pedido: opera sobre um {@link User} ja carregado, cujo
 * campo {@code tenantRoles} e {@code FetchType.EAGER} (Phase 126 Plan 01) -- o
 * {@code userRepository.findById} que o filtro ja faz continua a ser a unica query, preservando
 * a invariante documentada em {@code JwtAuthenticationFilter:50-58} ("imediato, nao no proximo
 * login", deliberadamente sem memorizacao).
 *
 * <p>Nenhum call site chama este servico ainda neste plano -- o cutover e dos Planos 04 e 05.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResolucaoPapeisService {

    // WR-01 (126-REVIEW.md): o discriminador REAL do administrador de plataforma -- ver
    // usaPapeisDeEscritorio. Mesmo nome literal usado em AdminController.PAPEL_PLATAFORMA e
    // MigracaoPapeisEscritorioService.TENANT_RESERVADO; declarado aqui em vez de importado porque
    // ambas as constantes de origem sao private nas respectivas classes. Verificar por papel
    // global (user.getRoles(), sempre EAGER, sem query extra) em vez de tenant reservado evita
    // introduzir uma segunda query por pedido em JwtAuthenticationFilter.
    private static final String NOME_PAPEL_PLATAFORMA = "PLATAFORMA_ADMIN";

    private final TenantRoleRepository tenantRoleRepository;
    private final RoleRepository roleRepository;

    /**
     * A UNICA avaliacao desta condicao em todo o servico -- todos os metodos publicos passam por
     * aqui, nunca replicam a condicao inline.
     *
     * <p>WR-01 (126-REVIEW.md): "tenantRoles vazio" DEIXOU de ser, por si so, o sinal de "e o
     * administrador de plataforma". Essa era a afirmacao original deste comentario, e era falsa
     * na pratica: tambem descrevia o administrador fundador de qualquer escritorio acabado de
     * provisionar, ate ao proximo arranque que corresse MigracaoPapeisEscritorioService --
     * SetupService.provisionTenant/initializeSystem passaram a atribuir o TenantRole ADMIN ao
     * fundador de imediato precisamente para fechar essa janela (ver o comentario la), mas esta
     * classe nao pode continuar a assumir "vazio implica plataforma" como se fosse garantido por
     * outro lado -- um dado ausente e indistinguivel de uma conversao falhada ou de uma corrida
     * de arranque (WR-03). O discriminador real agora e {@link #ehAdministradorDePlataforma},
     * baseado no papel global {@code PLATAFORMA_ADMIN} (Decisao 3, 126-CONTEXT.md), nunca na
     * ausencia de dados. Continua, tal como antes, a falhar para o lado seguro (papeis globais)
     * quando tenantRoles esta vazio por qualquer motivo -- nunca tranca ninguem fora -- mas agora
     * regista um aviso quando quem cai nesse ramo NAO e o administrador de plataforma, para que o
     * gap fique observavel em vez de silencioso.
     */
    private boolean usaPapeisDeEscritorio(User user) {
        if (user.getTenantRoles() != null && !user.getTenantRoles().isEmpty()) {
            return true;
        }
        if (!ehAdministradorDePlataforma(user)) {
            log.warn("Utilizador {} (tenant {}) resolve por papeis globais com tenantRoles vazio "
                    + "sem ser o administrador de plataforma -- verificar se "
                    + "MigracaoPapeisEscritorioService ja convergiu este tenant, ou se o "
                    + "provisionamento do administrador fundador ainda nao lhe atribuiu o "
                    + "TenantRole ADMIN (WR-01, 126-REVIEW.md). Continua a resolver por papeis "
                    + "globais -- falha para o lado seguro, nunca tranca o utilizador fora.",
                    user.getEmail(), user.getTenantId());
        }
        return false;
    }

    private boolean ehAdministradorDePlataforma(User user) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> NOME_PAPEL_PLATAFORMA.equals(r.getNome()));
    }

    /**
     * Nomes dos papeis efectivos do utilizador -- papeis de escritorio se existirem, senao
     * papeis globais. Devolve sempre um {@link Set} mutavel-independente, nunca uma vista sobre a
     * colecao da entidade.
     */
    public Set<String> resolverNomesPapeis(User user) {
        if (usaPapeisDeEscritorio(user)) {
            return user.getTenantRoles().stream()
                    .map(TenantRole::getNome)
                    .collect(Collectors.toSet());
        }
        return user.getRoles().stream()
                .map(Role::getNome)
                .collect(Collectors.toSet());
    }

    /**
     * Parcelas 1 (permissoes dos papeis) e 2 (permissoes directas do utilizador) apenas --
     * deliberadamente SEM a parcela 3 (o bloco ADMIN acrescentado por codigo continua a viver em
     * UserPrincipal.create). E assim que cada call site preserva exactamente o comportamento de
     * hoje: o filtro de autenticacao ganha a parcela 3 porque passa o resultado por
     * UserPrincipal.create; a actualizacao de perfil e a listagem de utilizadores nao a ganham
     * porque nunca passaram por la. Nao "corrigir" esta ausencia num refactor futuro -- e
     * intencional, documentada aqui para que nao seja lida como descuido.
     *
     * <p>A parcela 1 vem sempre do MESMO lado que {@link #resolverNomesPapeis} escolheu -- nunca
     * uma mistura dos dois -- porque ambos passam pela mesma {@link #usaPapeisDeEscritorio}.
     */
    public Set<String> resolverPermissoesEfectivas(User user) {
        Set<String> permissoes;
        if (usaPapeisDeEscritorio(user)) {
            permissoes = user.getTenantRoles().stream()
                    .flatMap(tr -> tr.getPermissions().stream())
                    .map(Permission::getNome)
                    .collect(Collectors.toSet());
        } else {
            permissoes = user.getRoles().stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(Permission::getNome)
                    .collect(Collectors.toSet());
        }
        permissoes.addAll(user.getPermissions());
        return permissoes;
    }

    /**
     * Predicado de proveniencia para os tres sitios de logica de negocio que hoje comparam por
     * nome literal ({@code ParecerController}, {@code ResourceController}): "este utilizador tem
     * um papel instanciado a partir do molde chamado {@code nomeMolde}?". Ao contrario da
     * comparacao por nome, sobrevive a uma renomeacao do papel de escritorio (Phase 127,
     * PAPEL-04).
     *
     * <p>Dois limites deliberados, preservados de proposito e nao um descuido: (a) um papel
     * criado de raiz por um escritorio tem {@code moldeId} nulo e NUNCA satisfaz esta verificacao
     * -- igual a hoje, em que um papel inventado por um escritorio tambem nao corresponde ao nome
     * literal; (b) isto e proveniencia, nao permissao -- converter para verificacao por permissao
     * mudaria comportamento observavel e esta diferido no 126-CONTEXT.md, a espera de decisao de
     * produto.
     */
    public boolean temPapelDeMolde(User user, String nomeMolde) {
        if (usaPapeisDeEscritorio(user)) {
            Integer moldeId = roleRepository.findByNome(nomeMolde)
                    .map(Role::getId)
                    .orElse(null);
            if (moldeId == null) {
                return false;
            }
            return user.getTenantRoles().stream()
                    .anyMatch(tr -> moldeId.equals(tr.getMoldeId()));
        }
        return user.getRoles().stream()
                .anyMatch(r -> nomeMolde.equals(r.getNome()));
    }

    /**
     * Para cada papel global dado, procura o {@link TenantRole} homonimo do tenant. E o seam que
     * o comentario IN-01 de {@code TenantRoleRepository} antecipava -- usado pelo Plano 03
     * (conversao de dados) e pelo Plano 04 (caminho de escrita do AdminController).
     *
     * <p>CR-01/WR-04 (126-REVIEW.md): distingue as TRES saidas possiveis em vez de confundir duas
     * delas num unico {@code Set} ambiguo -- a ambiguidade original era precisamente a causa raiz
     * de CR-01. (1) Todos os papeis pedidos mapeiam -- devolve o conjunto completo. (2) Nenhum
     * papel pedido mapeia (incluindo o caso de {@code papeisGlobais} vazio) -- devolve
     * {@link Set#of()}, exactamente como antes; todos os chamadores actuais (AdminController,
     * MigracaoPapeisEscritorioService) ja tratam este caso como "sem correspondencia, ficar em
     * papeis globais" e continuam a fazê-lo sem alteracao. (3) ALGUNS papeis mapeiam e outros nao
     * -- esta e a saida que era anteriormente devolvida como um {@code Set} parcial silencioso
     * (CR-01: escrito directamente em {@code user.tenantRoles}, um {@code 200}/{@code 201} a
     * esconder que a autoridade efectiva do utilizador ficaria incompleta). Passa a lancar
     * {@link MapeamentoParcialPapeisException}, nomeando os papeis sem correspondencia, para que
     * cada chamador decida explicitamente o que fazer -- nunca mais escrever esse subconjunto sem
     * mais.
     */
    public Set<TenantRole> resolverPapeisDeEscritorio(UUID tenantId, Collection<Role> papeisGlobais) {
        Set<TenantRole> encontrados = new HashSet<>();
        Set<String> semCorrespondencia = new HashSet<>();
        for (Role role : papeisGlobais) {
            Optional<TenantRole> tenantRole = tenantRoleRepository.findByTenantIdAndNome(tenantId, role.getNome());
            if (tenantRole.isPresent()) {
                encontrados.add(tenantRole.get());
            } else {
                semCorrespondencia.add(role.getNome());
            }
        }
        if (!encontrados.isEmpty() && !semCorrespondencia.isEmpty()) {
            throw new MapeamentoParcialPapeisException(semCorrespondencia);
        }
        return encontrados;
    }
}
