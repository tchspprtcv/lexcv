package com.lexcv.services;

import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
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
public class ResolucaoPapeisService {

    private final TenantRoleRepository tenantRoleRepository;
    private final RoleRepository roleRepository;

    /**
     * A UNICA avaliacao desta condicao em todo o servico -- todos os metodos publicos passam por
     * aqui, nunca replicam a condicao inline. Um utilizador sem nenhum papel de escritorio (o
     * administrador de plataforma, permanentemente -- ver 126-CONTEXT.md Decisao 3) cai
     * silenciosamente para os papeis globais, seguindo o mesmo idioma defensivo de
     * JwtAuthenticationFilter:45-48 (orElse(null) mais guarda por null, nunca controlo de fluxo
     * por excepcao).
     */
    private boolean usaPapeisDeEscritorio(User user) {
        return user.getTenantRoles() != null && !user.getTenantRoles().isEmpty();
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
     * (conversao de dados) e pelo Plano 04 (caminho de escrita do AdminController). Devolve
     * conjunto vazio quando nenhum corresponde -- nunca nulo, nunca excepcao.
     */
    public Set<TenantRole> resolverPapeisDeEscritorio(UUID tenantId, Collection<Role> papeisGlobais) {
        return papeisGlobais.stream()
                .map(role -> tenantRoleRepository.findByTenantIdAndNome(tenantId, role.getNome()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }
}
