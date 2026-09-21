package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.RbacResponse;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.MapeamentoParcialPapeisException;
import com.lexcv.services.ResolucaoPapeisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
// Phase 127 (Plano 02, PAPEL-08/PAPEL-09): todo o handler governado por este gate de classe e
// gestao de utilizadores -- gatear pelo nome literal do papel ADMIN deixou de ser seguro no
// exacto momento em que esta fase torna TenantRole.nome editavel (PAPEL-04):
// UserPrincipal.create deriva "ROLE_<nome efectivo do papel>", pelo que um escritorio que
// renomeie o seu proprio papel de administrador deixa de satisfazer hasRole('ADMIN') e fica
// trancado fora de TODA a superficie /api/v1/admin -- gestao de utilizadores incluida, e do
// proprio ecra RBAC que lhe permitiria desfazer a renomeacao. Esse e exactamente o
// autotrancamento silencioso (403 sem explicacao) que PAPEL-08 proibe. hasAuthority('users:manage')
// sobrevive a renomeacao porque a permissao esta agregada ao TenantRole, nao ao seu nome. O
// invariante que torna este gate seguro: a regra de piso do plano 03 garante que o papel de
// administrador do escritorio nunca pode perder users:manage/rbac:manage.
@PreAuthorize("hasAuthority('users:manage')")
@RequiredArgsConstructor
@Slf4j
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
    // A Phase 121 (ISOL-03) fechou o endpoint PUT /rbac por inteiro a papeis de plataforma --
    // ver o comentario acima do handler updateRbac para o mecanismo exato.
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
    private final ResolucaoPapeisService resolucaoPapeisService;

    @GetMapping("/users")
    public ResponseEntity<?> listUsers() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        List<User> users = userRepository.findByTenantId(principal.getTenantId());
        // Map to UserResponse
        // Phase 126 (Plan 04): papeis+permissoes (parcelas 1+2) pelo resolvedor unico --
        // papeis de escritorio se existirem, senao globais. Ver ResolucaoPapeisService.
        List<UserResponse> responses = users.stream().map(u -> {
            Set<String> nomesPapeis = resolucaoPapeisService.resolverNomesPapeis(u);
            Set<String> permissions = resolucaoPapeisService.resolverPermissoesEfectivas(u);

            return UserResponse.builder()
                    .id(u.getId())
                    .tenant_id(u.getTenantId())
                    .nome(u.getNome())
                    .email(u.getEmail())
                    .telefone(u.getTelefone())
                    .avatar_url(u.getAvatarUrl())
                    .roles(nomesPapeis)
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

    // CR-01/WR-04 (126-REVIEW.md): par de valores devolvido pelo wrapper abaixo -- exactamente um
    // dos dois campos e nao-nulo. Java nao tem tipo soma leve para isto sem puxar uma dependencia
    // nova, e um par assim, usado so internamente, e mais simples e mais legivel aqui do que um
    // Either genérico.
    private record ResolucaoTenantRolesOuErro(Set<TenantRole> tenantRoles, ResponseEntity<?> erro) {
        static ResolucaoTenantRolesOuErro sucesso(Set<TenantRole> tenantRoles) {
            return new ResolucaoTenantRolesOuErro(tenantRoles, null);
        }

        static ResolucaoTenantRolesOuErro erro(ResponseEntity<?> erro) {
            return new ResolucaoTenantRolesOuErro(null, erro);
        }
    }

    // CR-01/WR-04 (126-REVIEW.md): ponto UNICO onde createUser/updateUser chamam
    // ResolucaoPapeisService.resolverPapeisDeEscritorio -- escolha deliberada (a) do fix do
    // achado: falhar o pedido em vez de (b) instanciar o TenantRole em falta na hora. Precedente
    // ja estabelecido neste ficheiro para "pre-requisito em falta": SetupService faz
    // roleRepository.findByNome("ADMIN").orElseThrow(...) em vez de criar o papel ADMIN sobre a
    // marcha; a convergencia do catalogo de moldes (instanciarMoldes) e responsabilidade exclusiva
    // de SetupService.provisionTenant/initializeSystem e de MigracaoPapeisEscritorioService, nunca
    // um efeito lateral de um pedido de CRUD de utilizadores -- misturar as duas coisas aqui
    // obrigaria este controller a reimplementar a logica de instanciacao (snapshot de permissoes,
    // moldeId, sistema=true) ou a injectar SetupService so para isto, e ainda deixaria por
    // resolver que transaccao rebobina se a instanciacao falhar a meio de um update de utilizador.
    // Falhar com 409, nomeando o(s) papel(eis) sem correspondencia, empurra a correcao para onde
    // ela pertence -- o catalogo de moldes -- e nunca escreve o subconjunto parcial (o que NAO e
    // aceitavel, ver CR-01).
    private ResolucaoTenantRolesOuErro resolverTenantRolesOuErro(UUID tenantId, Set<Role> roles) {
        try {
            return ResolucaoTenantRolesOuErro.sucesso(
                    resolucaoPapeisService.resolverPapeisDeEscritorio(tenantId, roles));
        } catch (MapeamentoParcialPapeisException e) {
            log.warn("Mapeamento PARCIAL de papeis de escritorio recusado para o tenant {} -- "
                    + "papeis sem TenantRole homonimo: {}. Pedido recusado antes de qualquer "
                    + "escrita -- nunca grava um subconjunto silencioso (CR-01/WR-04, "
                    + "126-REVIEW.md).", tenantId, e.getPapeisSemCorrespondencia());
            return ResolucaoTenantRolesOuErro.erro(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Os seguintes papéis ainda não têm um papel de escritório correspondente "
                            + "neste tenant: " + String.join(", ", e.getPapeisSemCorrespondencia())
                            + ". Contacte o administrador de plataforma para atualizar o catálogo "
                            + "de moldes antes de atribuir este papel.")));
        }
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

        // Phase 126 (Plan 04): o caminho de escrita que tem de acompanhar o cutover de leitura --
        // sem isto, a autoridade efectiva do utilizador criado (resolvida pelo lado de escritorio
        // assim que o tenant tiver algum papel convertido) nunca refletiria os papeis globais
        // escritos abaixo. O tenant usado e sempre o do principal autenticado -- nunca um valor
        // vindo do corpo do pedido -- a mesma fronteira de isolamento multi-tenant que o resto
        // deste controller ja respeita; o metodo do resolvedor filtra por esse tenant, logo um
        // TenantRole homonimo de outro escritorio e inalcancavel por construcao.
        //
        // CR-01/WR-04 (126-REVIEW.md): correspondencia PARCIAL (alguns dos "roles" pedidos tem
        // TenantRole homonimo, outros nao) e recusada aqui, ANTES de qualquer escrita -- ver
        // resolverTenantRolesOuErro. Sem isto, o subconjunto encontrado seria gravado em silencio
        // e a resposta 201 esconderia que a autoridade efectiva do utilizador criado ficaria
        // incompleta.
        ResolucaoTenantRolesOuErro resolucao = resolverTenantRolesOuErro(principal.getTenantId(), roles);
        if (resolucao.erro() != null) {
            return resolucao.erro();
        }
        Set<TenantRole> tenantRoles = resolucao.tenantRoles();

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
                .tenantRoles(tenantRoles)
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
                .roles(resolucaoPapeisService.resolverNomesPapeis(user))
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
                // CR-01/WR-04 (126-REVIEW.md): resolver o mapeamento de escritorio ANTES de
                // mutar `user` -- se a correspondencia for parcial, o pedido tem de ser recusado
                // sem que roles/tenantRoles fiquem inconsistentes em memoria (mesmo que nada
                // ainda tivesse sido persistido, ver o comentario de resolverTenantRolesOuErro).
                ResolucaoTenantRolesOuErro resolucao =
                        resolverTenantRolesOuErro(principal.getTenantId(), roles);
                if (resolucao.erro() != null) {
                    return resolucao.erro();
                }

                user.setRoles(roles);
                // Phase 126 (Plan 04): mesmo caminho de escrita de createUser -- ver o comentario
                // la. Sem esta linha, um admin que mudasse o papel de um utilizador aqui veria a
                // operacao devolver 200 sem nenhum efeito na autoridade real dessa pessoa, porque
                // o resolvedor le o lado de escritorio quando o utilizador ja o tem. Copia
                // (HashSet novo), nunca a colecao devolvida pelo resolvedor.
                user.setTenantRoles(new HashSet<>(resolucao.tenantRoles()));
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

    // Phase 127 (Plano 02, PAPEL-09): getRbac passa a gatear por hasAuthority('rbac:manage'),
    // deixando de aceitar hasRole('PLATAFORMA_ADMIN'). O CR-01 original (121-REVIEW.md) tinha
    // aceitado essa segunda autoridade porque PLATAFORMA_ADMIN se tinha tornado o UNICO escritor
    // desta matriz (ver o gate historico de updateRbac abaixo) e ficava, por isso, incapaz de a
    // ler primeiro -- um alargamento so de LEITURA, necessario apenas enquanto a escrita
    // pertencia a plataforma. O plano 03 desta fase devolve a escrita ao proprio escritorio
    // (rbac:manage tenant-scoped); PLATAFORMA_ADMIN passa a gerir moldes de papel em
    // /platform/moldes, nunca a matriz de um escritorio individual, e nao detem nenhuma
    // permissao (DatabaseSeeder.upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList(),
    // false)) -- retirar aqui o acesso de PLATAFORMA_ADMIN fecha uma leitura de plataforma sobre
    // uma superficie de escritorio, nunca quebra um chamador legitimo (a tensao que PAPEL-09 pede
    // para apertar). O corpo do metodo (incluindo a exclusao deliberada de PAPEL_PLATAFORMA da
    // resposta, abaixo) fica inalterado neste plano -- so quem pode chamar muda; o corpo em si
    // (ainda global, nao tenant-scoped) e reescrito pelo plano 03 em conjunto com o gate de
    // updateRbac.
    @PreAuthorize("hasAuthority('rbac:manage')")
    @GetMapping("/rbac")
    public ResponseEntity<?> getRbac() {
        List<Role> roles = roleRepository.findAll();
        Map<String, List<String>> rolePermissions = new HashMap<>();

        // WR-03 (124-REVIEW.md): rolePermissions abaixo e deliberadamente NAO filtrado por
        // reservadaPlataforma/rotulo -- ao contrario de systemPermissions mais abaixo, que
        // ja aplica os dois filtros. Hoje isto e inofensivo porque as 20 entradas do catalogo
        // (DatabaseSeeder.CATALOGO_PERMISSOES) sao todas reservadaPlataforma=false e com
        // rotulo preenchido, logo os dois conjuntos de chaves coincidem sempre. Deixa de ser
        // verdade no exacto momento em que uma fase futura (125/127, ver o comentario CATL-03
        // em Permission.java) marcar alguma permissao do catalogo como
        // reservadaPlataforma=true, ou a deixar sem rotulo: se um papel alguma vez detiver
        // essa chave (SQL directo, endpoint futuro, ou bug em updateRbac), rolePermissions
        // passa a nomear uma chave sem coluna correspondente em systemPermissions -- forma que
        // o RbacTab do frontend (que deriva as colunas da matriz de systemPermissions) nao tem
        // comportamento definido para. Nao corrigido aqui de proposito: filtrar
        // rolePermissions mudaria o que este endpoint expoe, e a Phase 124 nao muda nenhuma
        // autoridade nem nenhum gate (124-CONTEXT.md) -- a autorizacao real
        // (JwtAuthenticationFilter, UserPrincipal.create) continua a ler
        // Role.getPermissions() directamente e e completamente alheia a
        // reservadaPlataforma/rotulo.
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

        // Phase 124 (Plan 02): a lista hardcoded de 17 entradas foi removida --
        // DatabaseSeeder.seedRbac() e agora a fonte de verdade do catalogo (CATL-01). O filtro
        // de reservadas a plataforma fica na query derivada do repositorio abaixo, de proposito,
        // nao aqui (CATL-03). A ordenacao por "ordem" existe porque o RbacTab do ecra de
        // Definicoes deriva a ordem dos modulos da ordem de chegada deste array.
        List<Permission> permissoesCatalogo = permissionRepository.findAllByReservadaPlataformaFalse();
        List<RbacResponse.PermissionDefDto> systemPermissions = permissoesCatalogo.stream()
                .filter(p -> {
                    boolean temRotulo = p.getRotulo() != null && !p.getRotulo().isBlank();
                    if (!temRotulo) {
                        log.warn("RBAC_CATALOGO: permissão '{}' sem rótulo, excluída de systemPermissions", p.getNome());
                    }
                    return temRotulo;
                })
                .sorted(Comparator.comparing(Permission::getOrdem, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Permission::getNome))
                .map(this::toPermissionDef)
                .collect(Collectors.toList());

        RbacResponse response = RbacResponse.builder()
                .rolePermissions(rolePermissions)
                .systemPermissions(systemPermissions)
                .build();

        return ResponseEntity.ok(response);
    }

    // Phase 124 (Plan 02): mapper entidade -> DTO para getRbac() (analog: PlatformAdminController
    // .toSummary). key <- Permission.nome (chave tecnica), nome <- Permission.rotulo (rotulo
    // legivel) -- os dois campos chamam-se "nome" em sitios diferentes e trocar a atribuicao
    // faria a matriz RBAC mostrar a chave tecnica como rotulo.
    private RbacResponse.PermissionDefDto toPermissionDef(Permission p) {
        return RbacResponse.PermissionDefDto.builder()
                .key(p.getNome())
                .nome(p.getRotulo())
                .descricao(p.getDescricao())
                .modulo(p.getModulo())
                .build();
    }

    // ISOL-03 (Phase 121): so este handler ganha um gate de metodo mais especifico -- Role e
    // Permission sao tabelas globais de plataforma, sem coluna tenant_id (ver PAPEL_PLATAFORMA
    // acima), pelo que um ADMIN de um escritorio a gravar esta matriz reescreveria exatamente as
    // mesmas linhas de que dependem os utilizadores TECNICO/ADVOGADO/ASSISTENTE de todos os
    // outros escritorios. A anotacao de metodo abaixo substitui -- nunca soma a -- o gate de
    // classe desta controller (a mais especifica ganha, nunca sao combinadas com E logico); todos
    // os restantes handlers continuam governados apenas pelo gate de classe.
    //
    // Phase 127 (Plano 02, 127-CONTEXT.md Decisao 1): este gate fica DELIBERADAMENTE por mexer
    // neste plano, ao contrario do de getRbac acima. O plano 03 desta fase muda os dois gates
    // (este e o de getRbac) e o CORPO deste handler NA MESMA alteracao -- nunca em sequencia --
    // porque abrir este gate a hasAuthority('rbac:manage') antes do corpo passar a escrever
    // TenantRole (tenant-scoped) em vez de Role (global) reabriria, mesmo que so por um plano de
    // duracao, exactamente a escrita cross-tenant que o ISOL-03 acima fechou: um ADMIN de
    // escritorio conseguiria de novo gravar a mesma matriz global partilhada por todos os
    // outros escritorios.
    @PreAuthorize("hasRole('PLATAFORMA_ADMIN')")
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
