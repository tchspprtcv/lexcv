package com.lexcv.controllers;

import com.lexcv.dtos.MoldeCreateRequest;
import com.lexcv.dtos.MoldeProvisionResponse;
import com.lexcv.dtos.MoldesConsolaResponse;
import com.lexcv.dtos.MoldesUpdateRequest;
import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.dtos.TenantAdminSummaryResponse;
import com.lexcv.dtos.TenantProvisionResponse;
import com.lexcv.dtos.TenantUpdateRequest;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Superfície de administração de plataforma (Phase 119/120, PROV-01/PROV-03/PROV-04/PROV-05/
 * PROV-06). Gated a {@code PLATAFORMA_ADMIN} ao nível da classe -- nunca acessível a um
 * {@code ADMIN} de um escritório normal (tenant comum); o gate de classe cobre automaticamente
 * qualquer handler acrescentado aqui. A Phase 120 (Plan 02) acrescentou 3 handlers novos --
 * listagem com utilização, ajuste de plano/limite e alternância de suspenso/ativo -- todos
 * cobertos pelo mesmo gate de classe herdado, sem nenhum {@code @PreAuthorize} adicional por
 * método.
 *
 * <p>Ao contrário de {@link AdminController}, este controller NÃO é tenant-scoped: nenhum
 * handler lê o contexto de segurança nem o principal autenticado do chamador -- a criação
 * provisiona um tenant NOVO, e a listagem/ajuste/suspensão operam deliberadamente sobre TODOS os
 * tenants, nunca apenas sobre o tenant de quem chama. Não há {@code getTenantId()} nenhum para
 * ler.
 *
 * <p>{@code POST /api/v1/setup/initialize} ({@link SetupController}) continua a existir,
 * público e com o seu gate singleton intacto; este controller é um caminho totalmente
 * distinto -- nunca lhe chama, nunca reutiliza {@code initializeSystem}, e nunca consulta
 * {@code isInitialized()}.
 */
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")
@RequiredArgsConstructor
public class PlatformAdminController {

    // Phase 120 (Plan 02): nome literal da tenant reservada de plataforma, seedada
    // incondicionalmente por DatabaseSeeder.seedTenantPlataforma() (Phase 119). Usado apenas para
    // recusar a suspensão desta tenant específica em setTenantAtivo -- suspendê-la trancaria fora
    // o único PLATAFORMA_ADMIN existente, sem via de recuperação pela aplicação.
    private static final String TENANT_RESERVADO = "ALCv";

    // Phase 125 (MOLD-02/03/04): continuacao das guardas das Phases 119/121 -- este papel nunca
    // e instanciavel (Role.instanciavel semeado false, Plan 01), nunca aparece como molde
    // editavel na consola de plataforma, e nunca e atribuivel a partir de superficie de
    // escritorio. Mesmo registo de TENANT_RESERVADO acima, nao uma reinvencao.
    private static final String PAPEL_RESERVADO = "PLATAFORMA_ADMIN";

    private final SetupService setupService;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TenantRoleRepository tenantRoleRepository;

    @PostMapping("/tenants")
    public ResponseEntity<?> createTenant(@RequestBody SetupInitializeRequest request) {
        try {
            Tenant tenant = setupService.provisionTenant(request);
            TenantProvisionResponse response = TenantProvisionResponse.builder()
                    .id(tenant.getId())
                    .nome(tenant.getNome())
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            // WR-02 (119-REVIEW.md): TOCTOU entre o pre-check findByEmail(...).isPresent() de
            // provisionTenant e o commit real da transacao. User.id usa
            // GenerationType.UUID (gerado em memoria, sem round-trip a BD), pelo que o
            // Hibernate tipicamente adia o INSERT ate ao flush/commit da transacao
            // @Transactional -- ou seja, DEPOIS de provisionTenant ja ter corrido o seu proprio
            // pre-check e devolvido sem erro. O commit acontece dentro desta mesma chamada
            // (fronteira do proxy transacional de SetupService), por isso so este catch aqui --
            // nao um try/catch dentro do proprio metodo do servico -- consegue apanhar esta
            // excecao para o pedido perdedor de uma corrida concorrente com o mesmo adminEmail.
            // Traduzida para a mesma mensagem/400 do caso nao-concorrente (pre-check), para o
            // comportamento visivel ao cliente nao depender de timing.
            return ResponseEntity.badRequest().body(Map.of("message", "Já existe um utilizador com este email."));
        }
    }

    /**
     * {@code GET /api/v1/platform/tenants} (PROV-03): devolve todos os tenants existentes, cada
     * um com o número de utilizadores ativos calculado ao vivo via
     * {@link UserRepository#countByTenantIdAndAtivoTrue(UUID)} -- a única contagem deste tipo em
     * todo o codebase (ver o doc-comment do próprio método). Deliberadamente sem filtro de tenant:
     * esta listagem é cross-tenant por desenho, e o gate de classe
     * {@code hasRole('PLATAFORMA_ADMIN')} é a única fronteira de autorização que decide quem a
     * pode alcançar. A lista é ordenada por nome (case-insensitive) para a consola ter uma ordem
     * estável entre chamadas -- {@code findAll()} não garante nenhuma ordem própria.
     */
    @GetMapping("/tenants")
    public ResponseEntity<?> listTenants() {
        List<TenantAdminSummaryResponse> tenants = tenantRepository.findAll().stream()
                .map(this::toSummary)
                .sorted(Comparator.comparing(TenantAdminSummaryResponse::getNome, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        return ResponseEntity.ok(tenants);
    }

    /**
     * {@code PUT /api/v1/platform/tenants/{id}} (PROV-04): ajusta {@code plano} e
     * {@code limiteUtilizadores} de um tenant já existente. {@code plano} é obrigatório -- um
     * corpo que o omita desserializa para {@code null} e é recusado aqui (um valor de enum
     * inválido já é recusado antes disso, pelo Jackson, traduzido em {@code 400} pelo handler de
     * {@code HttpMessageNotReadableException} acrescentado por esta mesma fase em
     * {@code GlobalExceptionHandler}). {@code limiteUtilizadores} continua a aceitar {@code null}
     * como "sem limite"; um valor presente tem de ser maior ou igual a 1. Nunca toca em
     * {@code ativo} -- essa alternância tem o seu próprio endpoint dedicado, ver
     * {@link #setTenantAtivo(UUID, Map)}.
     */
    @PutMapping("/tenants/{id}")
    public ResponseEntity<?> updateTenant(@PathVariable UUID id, @RequestBody TenantUpdateRequest request) {
        Tenant tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Tenant não encontrado."));
        }

        if (request.getPlano() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "O plano é obrigatório."));
        }

        if (request.getLimiteUtilizadores() != null && request.getLimiteUtilizadores() < 1) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "O limite de utilizadores deve ser um número inteiro maior ou igual a 1, ou ficar vazio para não aplicar limite."));
        }

        tenant.setPlano(request.getPlano());
        tenant.setLimiteUtilizadores(request.getLimiteUtilizadores());
        Tenant tenantGravado = tenantRepository.save(tenant);

        return ResponseEntity.ok(toSummary(tenantGravado));
    }

    /**
     * {@code PATCH /api/v1/platform/tenants/{id}/ativo} (PROV-05): alterna o estado
     * suspenso/ativo de um tenant. Esta é a camada autoritativa -- a desativação do botão
     * equivalente no frontend é apenas espelho de UX, não a fronteira de segurança real (ver
     * {@code JwtAuthenticationFilter}, que já recusa todos os pedidos autenticados de um tenant
     * suspenso, mesmo em sessões já ativas).
     *
     * <p><b>Guarda da tenant reservada:</b> suspender a tenant reservada (constante
     * {@code TENANT_RESERVADO}, hoje com o nome {@code ALCv}) trancaria fora o único
     * {@code PLATAFORMA_ADMIN} existente, sem nenhuma via de recuperação pela aplicação -- só por
     * SQL manual. A guarda usa comparação literal de {@code nome} porque {@code t_tenant.nome}
     * não tem constraint {@code unique} (ver {@code TenantRepository#findFirstByNome}, WR-01 da
     * Phase 119): um segundo tenant com o mesmo nome ficaria também não-suspensível, o que é o
     * lado seguro do erro. A guarda só bloqueia a suspensão -- reativar uma tenant reservada é
     * inofensivo e nunca falha.
     */
    @PatchMapping("/tenants/{id}/ativo")
    public ResponseEntity<?> setTenantAtivo(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        if (!(body.get("ativo") instanceof Boolean novoAtivo)) {
            return ResponseEntity.badRequest().body(Map.of("message", "O campo ativo deve ser um valor booleano."));
        }

        Tenant tenant = tenantRepository.findById(id).orElse(null);
        if (tenant == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Tenant não encontrado."));
        }

        if (TENANT_RESERVADO.equals(tenant.getNome()) && !novoAtivo) {
            return ResponseEntity.badRequest().body(Map.of("message", "Não é possível suspender o tenant da plataforma (ALCv)."));
        }

        tenant.setAtivo(novoAtivo);
        Tenant tenantGravado = tenantRepository.save(tenant);

        return ResponseEntity.ok(toSummary(tenantGravado));
    }

    /**
     * {@code GET /api/v1/platform/moldes} (Phase 125, MOLD-02): payload único da consola de
     * moldes -- catálogo de permissões elegíveis mais a lista de moldes instanciáveis, cada um
     * com o número de escritórios que já o instanciaram. Ver o doc-comment de
     * {@link MoldesConsolaResponse} para a razão do payload único (um só pedido sob o gate de
     * classe exclusivo de {@code PLATAFORMA_ADMIN}, em vez de reutilizar {@code GET /admin/rbac},
     * cujo gate é mais largo).
     *
     * <p>O catálogo exclui permissões reservadas à plataforma ao nível de SQL
     * ({@link PermissionRepository#findAllByReservadaPlataformaFalse()}) e, em Java, qualquer
     * permissão sem rótulo utilizável -- o {@code aria-label} de cada checkbox do UI-SPEC
     * (secção 5) é {@code "${permissao.rotulo} — ${molde.nome}"} e fica sem sentido com um rótulo
     * em branco. Ordenado por {@code ordem} (nulos no fim) com desempate por nome, tolerando uma
     * base de dados onde a migração correu mas o seeder ainda não populou {@code ordem}.
     *
     * <p>Os moldes vêm de {@link RoleRepository#findAllByInstanciavelTrue()}, com uma exclusão
     * defensiva adicional por nome literal de {@link #PAPEL_RESERVADO} -- defesa em profundidade
     * sobre o filtro SQL, mesma disciplina de três camadas independentes já aplicada em
     * {@code SetupService.instanciarMoldes} (Plan 02).
     */
    @GetMapping("/moldes")
    public ResponseEntity<?> listMoldes() {
        return ResponseEntity.ok(montarConsolaResponse());
    }

    /**
     * {@code PUT /api/v1/platform/moldes} (Phase 125, MOLD-02/MOLD-03): grava em lote o conjunto
     * de permissões de um ou mais moldes -- validate-then-write, nenhuma entrada é gravada antes
     * de todas as entradas do pedido passarem todas as guardas, para que um pedido inválido nunca
     * deixe metade dos moldes alterados. As chaves de permissão são resolvidas exclusivamente
     * pela mesma leitura filtrada de {@link #listMoldes()}
     * ({@link PermissionRepository#findAllByReservadaPlataformaFalse()}) -- nunca por
     * {@code findById} sobre um id cru -- para que uma permissão reservada à plataforma nunca
     * possa entrar num molde por via de um corpo de pedido (T-125-20).
     *
     * <p><b>Este handler nunca lê nem escreve {@link TenantRoleRepository}</b>, a não ser a
     * contagem de leitura dentro de {@link #toMoldeDto(Role)} usada para reprojectar a resposta.
     * O papel instanciado é um snapshot (MOLD-03, decisão bloqueada da fase) -- propagar aqui
     * contradiria essa decisão e invalidaria o aviso de não-propagação que o ecrã promete ao
     * operador (T-125-19).
     */
    @Transactional
    @PutMapping("/moldes")
    public ResponseEntity<?> updateMoldes(@RequestBody MoldesUpdateRequest request) {
        if (request == null || request.getMoldes() == null || request.getMoldes().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "A lista de moldes é obrigatória."));
        }

        Map<String, Permission> catalogoPorChave = permissionRepository.findAllByReservadaPlataformaFalse().stream()
                .collect(Collectors.toMap(Permission::getNome, permissao -> permissao));

        // Validate-then-write (T-125-21): resolve e valida TODAS as entradas antes de gravar
        // qualquer uma. resolvido preserva a ordem de chegada apenas por clareza -- a gravacao em
        // si nao depende de ordem.
        Map<Role, Set<Permission>> resolvido = new java.util.LinkedHashMap<>();
        for (MoldesUpdateRequest.MoldePermissoesEntry entrada : request.getMoldes()) {
            if (entrada.getId() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "O id do molde é obrigatório."));
            }

            Role role = roleRepository.findById(entrada.getId()).orElse(null);
            if (role == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Molde não encontrado."));
            }

            if (!Boolean.TRUE.equals(role.getInstanciavel())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Este papel não é um molde da plataforma."));
            }

            if (PAPEL_RESERVADO.equals(role.getNome())) {
                return ResponseEntity.badRequest().body(Map.of("message", "O papel PLATAFORMA_ADMIN não pode ser editado como molde."));
            }

            if (entrada.getPermissoes() == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "A lista de permissões é obrigatória."));
            }

            Set<Permission> permissoesResolvidas = new HashSet<>();
            for (String chave : entrada.getPermissoes()) {
                Permission permissao = catalogoPorChave.get(chave);
                if (permissao == null) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Permissão desconhecida: " + chave));
                }
                permissoesResolvidas.add(permissao);
            }

            resolvido.put(role, permissoesResolvidas);
        }

        for (Map.Entry<Role, Set<Permission>> entry : resolvido.entrySet()) {
            Role role = entry.getKey();
            role.setPermissions(new HashSet<>(entry.getValue()));
            roleRepository.save(role);
        }

        return ResponseEntity.ok(montarConsolaResponse());
    }

    /**
     * {@code POST /api/v1/platform/moldes} (Phase 125, MOLD-04): cria um novo molde já marcado
     * como instanciável ({@code instanciavel = true}), disponível para escritórios provisionados
     * a partir daí. O nome é normalizado para maiúsculas (coerente com os 4 moldes semeados) antes
     * de qualquer comparação ou gravação.
     */
    @PostMapping("/moldes")
    public ResponseEntity<?> createMolde(@RequestBody MoldeCreateRequest request) {
        if (request == null || request.getNome() == null || request.getNome().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "O nome do molde é obrigatório."));
        }

        String nomeNormalizado = request.getNome().trim().toUpperCase(Locale.ROOT);

        if (PAPEL_RESERVADO.equals(nomeNormalizado)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Este nome está reservado à plataforma e não pode ser usado como molde."));
        }

        if (roleRepository.findByNome(nomeNormalizado).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Já existe um papel com este nome."));
        }

        Map<String, Permission> catalogoPorChave = permissionRepository.findAllByReservadaPlataformaFalse().stream()
                .collect(Collectors.toMap(Permission::getNome, permissao -> permissao));

        Set<Permission> permissoesResolvidas = new HashSet<>();
        if (request.getPermissoes() != null) {
            for (String chave : request.getPermissoes()) {
                Permission permissao = catalogoPorChave.get(chave);
                if (permissao == null) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Permissão desconhecida: " + chave));
                }
                permissoesResolvidas.add(permissao);
            }
        }

        try {
            Role role = Role.builder()
                    .nome(nomeNormalizado)
                    .instanciavel(true)
                    .permissions(permissoesResolvidas)
                    .build();
            Role roleGravado = roleRepository.save(role);
            MoldeProvisionResponse response = MoldeProvisionResponse.builder()
                    .id(roleGravado.getId())
                    .nome(roleGravado.getNome())
                    .build();
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (DataIntegrityViolationException ex) {
            // Duplicacao concorrente: a constraint unica de Role.nome apanha na base de dados uma
            // corrida entre dois pedidos com o mesmo nome que passaram ambos o pre-check acima --
            // mesmo idioma de createTenant/DataIntegrityViolationException para email duplicado.
            return ResponseEntity.badRequest().body(Map.of("message", "Já existe um papel com este nome."));
        }
    }

    /**
     * Monta o payload único de {@link #listMoldes()} e reutilizado por {@link #updateMoldes} para
     * devolver as contagens {@code escritoriosInstanciados} já atualizadas sem um segundo pedido.
     */
    private MoldesConsolaResponse montarConsolaResponse() {
        List<MoldesConsolaResponse.PermissaoDto> permissoes = permissionRepository.findAllByReservadaPlataformaFalse().stream()
                .filter(p -> p.getRotulo() != null && !p.getRotulo().trim().isEmpty())
                .sorted(Comparator
                        .comparing(Permission::getOrdem, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Permission::getNome))
                .map(this::toPermissaoDto)
                .collect(Collectors.toList());

        List<MoldesConsolaResponse.MoldeDto> moldes = roleRepository.findAllByInstanciavelTrue().stream()
                .filter(role -> !PAPEL_RESERVADO.equals(role.getNome()))
                .sorted(Comparator.comparing(Role::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(this::toMoldeDto)
                .collect(Collectors.toList());

        return MoldesConsolaResponse.builder()
                .permissoes(permissoes)
                .moldes(moldes)
                .build();
    }

    /**
     * Projeta um {@link Tenant} para o contrato de resposta partilhado pelos 3 handlers acima --
     * ver o doc-comment de {@link TenantAdminSummaryResponse} para o que fica deliberadamente de
     * fora.
     */
    private TenantAdminSummaryResponse toSummary(Tenant tenant) {
        return TenantAdminSummaryResponse.builder()
                .id(tenant.getId())
                .nome(tenant.getNome())
                .plano(tenant.getPlano())
                .limiteUtilizadores(tenant.getLimiteUtilizadores())
                .ativo(tenant.getAtivo())
                .utilizadoresAtivos(userRepository.countByTenantIdAndAtivoTrue(tenant.getId()))
                .build();
    }

    /**
     * Mapeia uma entidade {@link Permission} para {@link MoldesConsolaResponse.PermissaoDto} com
     * campos nomeados via {@code builder()} -- {@code key} vem de {@code nome} (a chave técnica),
     * {@code nome} vem de {@code rotulo} (o rótulo legível). Nunca um construtor posicional: os
     * dois campos chamam-se "nome" em sítios diferentes, e uma troca silenciosa faria a consola
     * mostrar a chave técnica como rótulo (armadilha identificada na Phase 124, ver
     * {@code AdminController#toPermissionDef}).
     */
    private MoldesConsolaResponse.PermissaoDto toPermissaoDto(Permission permission) {
        return MoldesConsolaResponse.PermissaoDto.builder()
                .key(permission.getNome())
                .nome(permission.getRotulo())
                .descricao(permission.getDescricao())
                .modulo(permission.getModulo())
                .build();
    }

    /**
     * Mapeia uma entidade {@link Role} (molde) para {@link MoldesConsolaResponse.MoldeDto},
     * incluindo a contagem viva de {@code escritoriosInstanciados} via
     * {@link TenantRoleRepository#countByMoldeId(Integer)} -- calculada por molde, exatamente
     * como {@link #toSummary(Tenant)} já faz com
     * {@code userRepository.countByTenantIdAndAtivoTrue}.
     */
    private MoldesConsolaResponse.MoldeDto toMoldeDto(Role role) {
        List<String> chaves = role.getPermissions().stream()
                .map(Permission::getNome)
                .sorted()
                .collect(Collectors.toList());
        return MoldesConsolaResponse.MoldeDto.builder()
                .id(role.getId())
                .nome(role.getNome())
                .permissoes(chaves)
                .escritoriosInstanciados(tenantRoleRepository.countByMoldeId(role.getId()))
                .build();
    }
}
