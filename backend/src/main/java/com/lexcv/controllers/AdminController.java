package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.RbacResponse;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    // Phase 119 (Plan 03): "PLATAFORMA_ADMIN" e um papel reservado, seedado incondicionalmente a
    // partir desta fase (DatabaseSeeder.seedRbac(), Plan 01) para servir exclusivamente o novo
    // PlatformAdminController (Plan 04), gated por @PreAuthorize("hasRole('PLATAFORMA_ADMIN')").
    // UserPrincipal deriva autoridades ROLE_* genericamente a partir de qualquer papel guardado na
    // base de dados (ver UserPrincipal.create), sem allowlist -- por isso, sem as guardas abaixo,
    // um ADMIN de um escritorio normal poderia atribuir-se (ou a outro utilizador) este papel via
    // createUser/updateUser, satisfazer o hasRole('PLATAFORMA_ADMIN') do Plan 04, e alcancar
    // POST /api/v1/platform/tenants -- criacao arbitraria de tenants por um cliente qualquer. As
    // guardas de getRbac/updateRbac fecham, respetivamente, a visibilidade e a alterabilidade das
    // permissoes deste papel a partir do ecra de Definicoes (RBAC) de qualquer escritorio.
    //
    // CR-01 (119-REVIEW.md): createUser/updateUser originalmente so guardavam o campo "roles" --
    // o campo irmao "permissions" (free-form, sem catalogo) e virado diretamente em
    // GrantedAuthority por UserPrincipal.create, sem qualquer prefixagem "ROLE_" propria da app,
    // pelo que um "ROLE_PLATAFORMA_ADMIN" colocado ali bypassava por completo as guardas
    // originais (que so olhavam para "roles"). As guardas de "permissions" abaixo, que usam
    // PAPEL_PLATAFORMA_AUTORIDADE, fecham esse caminho.
    //
    // A Phase 121 (ISOL-03) ira depois fechar o endpoint PUT /rbac por inteiro a papeis de
    // plataforma -- esta fase apenas protege o papel novo, sem antecipar esse bloqueio.
    private static final String PAPEL_PLATAFORMA = "PLATAFORMA_ADMIN";

    // CR-01 (119-REVIEW.md): forma que a mesma reserva assume quando chega via "permissions" em
    // vez de "roles". UserPrincipal.create NAO acrescenta o prefixo "ROLE_" a permissions (ao
    // contrario do que faz para roles, ver o metodo), por isso e esta string ja-prefixada --
    // nao PAPEL_PLATAFORMA sozinho -- que realmente satisfaz hasRole('PLATAFORMA_ADMIN') quando
    // colocada em User.permissions. Bloqueamos as duas formas (crua e prefixada) por defesa em
    // profundidade, mesmo a crua nao bastando por si so para o bypass.
    private static final String PAPEL_PLATAFORMA_AUTORIDADE = "ROLE_" + PAPEL_PLATAFORMA;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenantRepository;

    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        List<User> users = userRepository.findByTenantId(principal.getTenantId());
        // Map to UserResponse
        List<UserResponse> responses = users.stream().map(u -> {
            Set<String> roles = u.getRoles().stream().map(Role::getNome).collect(Collectors.toSet());
            Set<String> permissions = u.getRoles().stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(Permission::getNome)
                    .collect(Collectors.toSet());
            u.getPermissions().forEach(permissions::add);

            return UserResponse.builder()
                    .id(u.getId())
                    .tenant_id(u.getTenantId())
                    .nome(u.getNome())
                    .email(u.getEmail())
                    .telefone(u.getTelefone())
                    .avatar_url(u.getAvatarUrl())
                    .roles(roles)
                    .permissions(permissions)
                    .ativo(u.getAtivo())
                    .build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    // Phase 117 (PLAN-02/PLAN-04): limite de utilizadores ativos por tenant. Tenant.limiteUtilizadores
    // == null significa sem limite (plano Enterprise "por acordo"); um valor numérico bloqueia a
    // operação quando os utilizadores já ativos do tenant do chamador (nunca de um tenant forjado a
    // partir do corpo do pedido) já o igualam. A contagem é sempre lida ao vivo nesta query — nunca em
    // cache — pelo que desativar um utilizador liberta a vaga de imediato no pedido seguinte.
    //
    // CR-01 (117-REVIEW.md): único ponto de verificação do limite no AdminController — chamado tanto
    // por createUser (o novo utilizador ainda não conta para si próprio, por isso a comparação é >=)
    // como por updateUser (apenas quando `ativo` transita de false para true — ver updateUser). Antes
    // desta extração, o limite só era verificado em createUser, o que tornava a regra totalmente
    // contornável via reativação por PUT /api/v1/admin/users/{id}. Não duplicar esta comparação inline.
    //
    // WR-01 (117-REVIEW.md): a contagem-depois-comparação abaixo não é atómica (sem lock, sem
    // @Version, sem constraint de BD) — risco conscientemente aceite em 117-02-PLAN.md (T-117-07).
    // A framing original ali só descreve 2 pedidos concorrentes ("pior caso é 1 utilizador acima do
    // limite"); na prática, N pedidos concorrentes que observem o mesmo count == limite-1 podem todos
    // passar a verificação, produzindo até limite-1+N utilizadores ativos, não apenas limite+1. Aceite
    // tal e qual por ser um endpoint só-ADMIN, de baixo volume e faturação manual, sem alterar a decisão
    // do plano de não acrescentar @Transactional/locks/constraints — reconfirmar quando a Phase 119/120
    // tornarem o aprovisionamento/limites self-service.
    private Optional<ResponseEntity<?>> limiteUtilizadoresExcedido(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
        if (tenant != null && tenant.getLimiteUtilizadores() != null) {
            long utilizadoresAtivos = userRepository.countByTenantIdAndAtivoTrue(tenantId);
            if (utilizadoresAtivos >= tenant.getLimiteUtilizadores()) {
                return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Limite de utilizadores atingido para o vosso plano.")));
            }
        }
        return Optional.empty();
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("nome") || !body.containsKey("email") || !body.containsKey("password") || !body.containsKey("roles")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nome, email, password e roles são obrigatórios."));
        }

        String password = (String) body.get("password");
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "A password deve ter no mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial."));
        }

        String email = (String) body.get("email");
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Já existe um utilizador registado com este endereço de email."));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        List<?> rolesList = (List<?>) body.get("roles");

        // Phase 119 (Plan 03): recusar antes do lookup -- ver o comentario de PAPEL_PLATAFORMA.
        for (Object rObj : rolesList) {
            if (PAPEL_PLATAFORMA.equals(rObj)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                        "O papel de administrador de plataforma é reservado e não pode ser atribuído a partir da gestão de utilizadores do escritório."));
            }
        }

        Set<Role> roles = new HashSet<>();
        for (Object rObj : rolesList) {
            String roleName = (String) rObj;
            roleRepository.findByNome(roleName).ifPresent(roles::add);
        }

        if (roles.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Pelo menos uma role válida é obrigatória."));
        }

        // Phase 117 (PLAN-02/PLAN-04): limite de utilizadores ativos por tenant, aplicado através do
        // helper partilhado limiteUtilizadoresExcedido (ver o seu comentário para o contrato completo e
        // para a nota CR-01 sobre porque este helper existe e é chamado a partir de dois sítios).
        // WR-03 (117-REVIEW.md): resolver primeiro o valor final de `ativo` e só invocar o helper
        // quando esse valor é true -- um pedido com "ativo": false nunca aumenta a contagem de
        // utilizadores ativos, por isso não pode ser bloqueado pelo limite.
        boolean ativoInicial = body.get("ativo") == null || (Boolean) body.get("ativo");
        if (ativoInicial) {
            Optional<ResponseEntity<?>> limiteExcedido = limiteUtilizadoresExcedido(principal.getTenantId());
            if (limiteExcedido.isPresent()) {
                return limiteExcedido.get();
            }
        }

        List<?> permsList = body.containsKey("permissions") ? (List<?>) body.get("permissions") : Collections.emptyList();

        // CR-01 (119-REVIEW.md): mesma recusa aplicada acima a "roles" -- ver o comentario de
        // PAPEL_PLATAFORMA_AUTORIDADE. Bloqueia tanto a forma crua do papel como a forma
        // ja-prefixada que realmente satisfaz hasRole('PLATAFORMA_ADMIN') quando vinda deste campo.
        for (Object pObj : permsList) {
            if (PAPEL_PLATAFORMA.equals(pObj) || PAPEL_PLATAFORMA_AUTORIDADE.equals(pObj)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                        "O papel de administrador de plataforma é reservado e não pode ser atribuído a partir da gestão de utilizadores do escritório."));
            }
        }

        Set<String> permissions = new HashSet<>();
        for (Object pObj : permsList) {
            permissions.add((String) pObj);
        }

        User user = User.builder()
                .tenantId(principal.getTenantId())
                .nome((String) body.get("nome"))
                .email(email)
                .passwordHash(passwordEncoder.encode((String) body.get("password")))
                .roles(roles)
                .permissions(permissions)
                .ativo(ativoInicial)
                .telefone(body.containsKey("telefone") ? (String) body.get("telefone") : "")
                .avatarUrl(body.containsKey("avatar_url") ? (String) body.get("avatar_url") : "")
                .build();

        user = userRepository.save(user);

        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .tenant_id(user.getTenantId())
                .nome(user.getNome())
                .email(user.getEmail())
                .roles(user.getRoles().stream().map(Role::getNome).collect(Collectors.toSet()))
                .permissions(permissions)
                .ativo(user.getAtivo())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        User user = userRepository.findById(id).orElse(null);
        if (user == null || !user.getTenantId().equals(principal.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilizador não encontrado"));
        }

        if (body.containsKey("email")) {
            String email = (String) body.get("email");
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Já existe outro utilizador com este email."));
            }
            user.setEmail(email);
        }

        if (body.containsKey("nome")) user.setNome((String) body.get("nome"));
        if (body.containsKey("telefone")) user.setTelefone((String) body.get("telefone"));
        if (body.containsKey("avatar_url")) user.setAvatarUrl((String) body.get("avatar_url"));
        if (body.containsKey("ativo")) {
            // IN-04 (117-REVIEW.md): validar que "ativo" é mesmo um Boolean antes de desembrulhar
            // para primitivo -- um "ativo": null explícito (JSON válido; a chave fica presente no
            // Map com valor null) não pode rebentar com NullPointerException.
            if (!(body.get("ativo") instanceof Boolean novoAtivo)) {
                return ResponseEntity.badRequest().body(Map.of("message", "O campo ativo deve ser um valor booleano."));
            }
            // CR-01 (117-REVIEW.md): reativar um utilizador (false -> true) é o segundo caminho capaz
            // de tornar um utilizador ativo, além de createUser — sem esta verificação o limite era
            // totalmente contornável (criar com ativo=false, que nunca sobe a contagem, e reativar
            // depois sem qualquer controlo). Só a transição false -> true paga o custo da verificação;
            // true -> false, ou qualquer update que não mexa em `ativo`, nunca chamam o helper.
            if (novoAtivo && !Boolean.TRUE.equals(user.getAtivo())) {
                Optional<ResponseEntity<?>> limiteExcedido = limiteUtilizadoresExcedido(principal.getTenantId());
                if (limiteExcedido.isPresent()) {
                    return limiteExcedido.get();
                }
            }
            user.setAtivo(novoAtivo);
        }

        if (body.containsKey("password") && ((String) body.get("password")).trim().length() > 0) {
            String password = (String) body.get("password");
            if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
                return ResponseEntity.badRequest().body(Map.of("message", "A password deve ter no mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial."));
            }
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        if (body.containsKey("roles")) {
            List<?> rolesList = (List<?>) body.get("roles");

            // Phase 119 (Plan 03): mesma recusa de createUser -- ver o comentario de
            // PAPEL_PLATAFORMA. O return acontece antes de qualquer userRepository.save(user), por
            // isso nenhuma mutacao ja aplicada em memoria (nome/email/telefone/etc.) e persistida.
            for (Object rObj : rolesList) {
                if (PAPEL_PLATAFORMA.equals(rObj)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                            "O papel de administrador de plataforma é reservado e não pode ser atribuído a partir da gestão de utilizadores do escritório."));
                }
            }

            Set<Role> roles = new HashSet<>();
            for (Object rObj : rolesList) {
                String roleName = (String) rObj;
                roleRepository.findByNome(roleName).ifPresent(roles::add);
            }
            if (!roles.isEmpty()) {
                user.setRoles(roles);
            }
        }

        if (body.containsKey("permissions")) {
            List<?> permsList = (List<?>) body.get("permissions");

            // CR-01 (119-REVIEW.md): mesma recusa de createUser -- ver o comentario de
            // PAPEL_PLATAFORMA_AUTORIDADE. O return acontece antes de qualquer
            // userRepository.save(user), por isso nenhuma mutacao ja aplicada em memoria
            // (nome/email/telefone/roles/etc.) e persistida.
            for (Object pObj : permsList) {
                if (PAPEL_PLATAFORMA.equals(pObj) || PAPEL_PLATAFORMA_AUTORIDADE.equals(pObj)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                            "O papel de administrador de plataforma é reservado e não pode ser atribuído a partir da gestão de utilizadores do escritório."));
                }
            }

            Set<String> permissions = new HashSet<>();
            for (Object pObj : permsList) {
                permissions.add((String) pObj);
            }
            user.setPermissions(permissions);
        }

        user = userRepository.save(user);

        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        if (principal.getUserId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Não é permitido apagar a sua própria conta de utilizador administrador."));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null || !user.getTenantId().equals(principal.getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilizador não encontrado"));
        }

        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Utilizador removido com sucesso!"));
    }

    @GetMapping("/rbac")
    public ResponseEntity<?> getRbac() {
        List<Role> roles = roleRepository.findAll();
        Map<String, List<String>> rolePermissions = new HashMap<>();

        for (Role role : roles) {
            // Phase 119 (Plan 03): o ecra de Definicoes (RBAC) de um escritorio nao deve sequer
            // saber que o papel de plataforma existe -- ver o comentario de PAPEL_PLATAFORMA.
            if (PAPEL_PLATAFORMA.equals(role.getNome())) {
                continue;
            }
            List<String> perms = role.getPermissions().stream()
                    .map(Permission::getNome)
                    .collect(Collectors.toList());
            rolePermissions.put(role.getNome(), perms);
        }

        List<RbacResponse.PermissionDefDto> systemPermissions = Arrays.asList(
                new RbacResponse.PermissionDefDto("clientes:view", "Visualizar Clientes", "Ver lista e detalhes de clientes", "Clientes"),
                new RbacResponse.PermissionDefDto("clientes:edit", "Gerir Clientes", "Criar, editar e apagar clientes", "Clientes"),
                new RbacResponse.PermissionDefDto("processos:view", "Visualizar Processos", "Ver lista e detalhes de processos judiciais", "Processos"),
                new RbacResponse.PermissionDefDto("processos:edit", "Gerir Processos", "Criar, editar, alterar fases e apagar processos", "Processos"),
                new RbacResponse.PermissionDefDto("agenda:view", "Visualizar Agenda", "Ver calendário e prazos/eventos", "Agenda"),
                new RbacResponse.PermissionDefDto("agenda:edit", "Gerir Agenda", "Criar, editar e concluir eventos/prazos", "Agenda"),
                new RbacResponse.PermissionDefDto("documentos:view", "Visualizar Documentos", "Ver e descarregar documentos", "Documentos"),
                new RbacResponse.PermissionDefDto("documentos:edit", "Gerir Documentos", "Carregar e apagar documentos", "Documentos"),
                new RbacResponse.PermissionDefDto("financeiro:view", "Visualizar Financeiro", "Ver honorários, pagamentos e conta corrente", "Financeiro"),
                new RbacResponse.PermissionDefDto("financeiro:edit", "Gerir Financeiro", "Lançar honorários, pagamentos e gerir conta corrente", "Financeiro"),
                new RbacResponse.PermissionDefDto("pareceres:view", "Visualizar Pareceres", "Ver lista, detalhe e pesquisa de pareceres jurídicos", "Pareceres"),
                new RbacResponse.PermissionDefDto("pareceres:create", "Criar Solicitações", "Criar novas solicitações de parecer jurídico", "Pareceres"),
                new RbacResponse.PermissionDefDto("pareceres:edit", "Elaborar e Entregar Pareceres", "Criar versões, elaborar conteúdo e entregar pareceres", "Pareceres"),
                new RbacResponse.PermissionDefDto("pareceres:manage", "Aprovar Pareceres", "Aprovação interna de pareceres jurídicos", "Pareceres"),
                new RbacResponse.PermissionDefDto("notificacoes:view", "Visualizar Notificações", "Ver e marcar como lidas as notificações próprias", "Notificações"),
                new RbacResponse.PermissionDefDto("rbac:manage", "Gerir Permissões (RBAC)", "Alterar regras de acesso globais por função", "Administração"),
                new RbacResponse.PermissionDefDto("users:manage", "Gerir Utilizadores", "Criar, ativar/desativar, e configurar utilizadores", "Administração")
        );

        RbacResponse response = RbacResponse.builder()
                .rolePermissions(rolePermissions)
                .systemPermissions(systemPermissions)
                .build();

        return ResponseEntity.ok(response);
    }

    @PutMapping("/rbac")
    public ResponseEntity<?> updateRbac(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("rolePermissions")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mapeamento rolePermissions é obrigatório"));
        }

        Map<?, ?> newRolePermissions = (Map<?, ?>) body.get("rolePermissions");

        for (Map.Entry<?, ?> entry : newRolePermissions.entrySet()) {
            String roleName = (String) entry.getKey();
            // Protection: Admin is immutable.
            // Phase 119 (Plan 03): PLATAFORMA_ADMIN e igualmente imutavel por este caminho --
            // DatabaseSeeder.upsertRolePermissions so faz addAll e nunca remove, pelo que uma
            // injecao de permissoes aqui persistiria para sempre, sem reparacao no arranque
            // seguinte. Ver o comentario de PAPEL_PLATAFORMA.
            if ("ADMIN".equals(roleName) || PAPEL_PLATAFORMA.equals(roleName)) {
                continue;
            }

            Role role = roleRepository.findByNome(roleName).orElse(null);
            if (role != null) {
                List<?> permsList = (List<?>) entry.getValue();
                Set<Permission> permissions = new HashSet<>();
                for (Object pObj : permsList) {
                    String pName = (String) pObj;
                    permissionRepository.findByNome(pName).ifPresent(permissions::add);
                }
                role.setPermissions(permissions);
                roleRepository.save(role);
            }
        }

        return ResponseEntity.ok(Map.of("message", "Permissões de perfis (RBAC) atualizadas com sucesso!"));
    }
}
