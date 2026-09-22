package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.PapelCreateRequest;
import com.lexcv.dtos.PapelRenameRequest;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.TenantRole;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.AuditoriaRbacService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * {@code /api/v1/admin/rbac/roles} (Phase 127, Plano 04, PAPEL-02/PAPEL-04/PAPEL-05/PAPEL-07/
 * PAPEL-08/PAPEL-09): CRUD dos papéis PRÓPRIOS do escritório do chamador -- criar, renomear,
 * apagar. Estas três operações não cabem na matriz de {@link AdminController#getRbac()}/
 * {@link AdminController#updateRbac}, que só lê/escreve o conjunto de permissões de papéis já
 * existentes.
 *
 * <p><b>Porque é uma classe NOVA, e não mais três handlers em {@link AdminController}:</b> desde
 * o Plano 02 desta fase, o gate de CLASSE de {@link AdminController} é
 * {@code hasAuthority('users:manage')}. Uma anotação de método mais específica SUBSTITUI -- nunca
 * soma a -- a anotação de classe (documentado no próprio {@link AdminController}, e provado por
 * proxy real em {@code AdminControllerRbacAutorizacaoTest}); se um handler de CRUD de papéis
 * fosse acrescentado ali e alguém se esquecesse do {@code @PreAuthorize} de método, esse handler
 * herdaria silenciosamente {@code users:manage} em vez de {@code rbac:manage} -- um alargamento
 * de gate por omissão, não por decisão. Uma classe dedicada com um gate de CLASSE
 * {@code hasAuthority('rbac:manage')} não pode ser esquecida desta forma: QUALQUER handler
 * acrescentado aqui fica automaticamente coberto, sem anotação de método nenhuma. Mesma disciplina
 * que {@link PlatformAdminController} já segue -- uma classe, uma autoridade.
 *
 * <p>{@code /api/v1/admin/rbac} (em {@link AdminController}) e {@code /api/v1/admin/rbac/roles}
 * (aqui) são mapeamentos distintos e não-ambíguos -- o primeiro lê/escreve o CONJUNTO DE
 * PERMISSÕES de papéis existentes; este cria/renomeia/apaga papéis em si.
 *
 * <p>Cada handler aqui resolve o tenant exclusivamente a partir do principal autenticado
 * ({@code SecurityContextHolder} -> {@code principal.getTenantId()}), nunca de um valor do corpo
 * ou do path do pedido -- a mesma fronteira de isolamento multi-tenant que
 * {@link AdminController#listUsers()} já usa (PAPEL-07).
 *
 * <p><b>Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 03:</b> {@code createRole}, {@code
 * renameRole} e {@code deleteRole} sao {@code @Transactional} porque o evento de auditoria
 * ({@link AuditoriaRbacService}) e a mudanca que ele descreve tem de fazer commit ou rollback
 * juntos -- ou ficam ambos, ou nenhum. Cada escrita e forcada a base de dados DENTRO do seu
 * {@code try} (com {@code saveAndFlush} em vez de {@code save}, ou com {@code flush()} explicito
 * apos {@code deleteById}) porque, sem esse flush, uma violacao de unicidade ou de FK so
 * apareceria no COMMIT da transacao, ja fora do {@code catch}, e chegaria ao chamador como 500 nao
 * tratado em vez do 409 estruturado que estes handlers sempre devolveram. Toda a resposta
 * nao-2xx (400/404/409) passa por {@link RecusaTransacional#recusar}, que marca a transacao como
 * rollback-only -- um pedido recusado nao grava evento nenhum, porque nao houve mudanca.
 */
@RestController
@RequestMapping("/api/v1/admin/rbac/roles")
@PreAuthorize("hasAuthority('rbac:manage')")
@RequiredArgsConstructor
@Slf4j
public class OfficeRolesController {

    // Redeclarado aqui (não importado) porque os originais em AdminController são private --
    // mesma disciplina que ResolucaoPapeisService redeclara NOME_PAPEL_PLATAFORMA. Ver
    // AdminController para o histórico completo (Phase 119/CR-01, 119-REVIEW.md) de porque as
    // duas formas (crua e prefixada ROLE_) são bloqueadas: UserPrincipal.create não prefixa
    // "permissions" com "ROLE_", por isso a string já-prefixada é a que realmente satisfaria
    // hasRole('PLATAFORMA_ADMIN') se chegasse a uma lista de autoridades por essa via.
    private static final String PAPEL_PLATAFORMA = "PLATAFORMA_ADMIN";
    private static final String PAPEL_PLATAFORMA_AUTORIDADE = "ROLE_" + PAPEL_PLATAFORMA;

    private final TenantRoleRepository tenantRoleRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    // ULTIMO campo deliberadamente (Plano 03): @RequiredArgsConstructor gera a construcao
    // posicional pela ordem de declaracao, por isso acrescentar este campo aqui so acrescenta um
    // argumento no FIM da lista -- nao reordena os quatro existentes.
    private final AuditoriaRbacService auditoriaRbacService;

    /**
     * O {@link UserPrincipal} autenticado -- fonte unica do tenant E do autor de cada evento de
     * auditoria (Decisao 3, 128-CONTEXT.md: "o autor e o tenant vem do principal autenticado,
     * nunca do corpo do pedido").
     */
    private UserPrincipal getPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (UserPrincipal) auth.getPrincipal();
    }

    private UUID getTenantId() {
        return getPrincipal().getTenantId();
    }

    /**
     * Valida e normaliza um nome submetido para {@link #createRole}/{@link #renameRole}: rejeita
     * nulo/vazio, retira espaços nas pontas SEM alterar maiúsculas/minúsculas (UI-SPEC §7/§8 --
     * ao contrário de {@code PlatformAdminController.createMolde}, que normaliza moldes para
     * maiúsculas, um papel de escritório é nomeado livremente pelo próprio administrador -- "
     * Recepção", "Financeiro Sénior" --, não existe convenção de maiúsculas de plataforma a
     * respeitar aqui) e recusa, de forma case-insensitive, qualquer uma das duas formas
     * reservadas.
     */
    private ResponseEntity<?> validarNome(String nomeSubmetido) {
        if (nomeSubmetido == null || nomeSubmetido.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "O nome do papel é obrigatório."));
        }

        String nome = nomeSubmetido.trim();
        String nomeParaComparacao = nome.toUpperCase(Locale.ROOT);
        if (PAPEL_PLATAFORMA.equals(nomeParaComparacao) || PAPEL_PLATAFORMA_AUTORIDADE.equals(nomeParaComparacao)) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Este nome está reservado à plataforma e não pode ser usado."));
        }

        return null;
    }

    /**
     * {@code POST /api/v1/admin/rbac/roles} (PAPEL-02): cria um novo papel próprio do escritório
     * do chamador, com o nome e o conjunto inicial de permissões escolhidos pelo administrador.
     */
    @PostMapping("")
    @Transactional
    public ResponseEntity<?> createRole(@RequestBody PapelCreateRequest request) {
        UUID tenantId = getTenantId();
        UserPrincipal principal = getPrincipal();

        String nomeSubmetido = request == null ? null : request.getNome();
        ResponseEntity<?> erroNome = validarNome(nomeSubmetido);
        if (erroNome != null) {
            return RecusaTransacional.recusar(erroNome);
        }
        String nome = nomeSubmetido.trim();

        if (tenantRoleRepository.findByTenantIdAndNome(tenantId, nome).isPresent()) {
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Já existe um papel com este nome neste escritório.")));
        }

        Map<String, Permission> catalogoPorChave = permissionRepository.findAllByReservadaPlataformaFalse().stream()
                .collect(Collectors.toMap(Permission::getNome, p -> p));

        Set<Permission> permissoesResolvidas = new HashSet<>();
        if (request.getPermissoes() != null) {
            for (String chave : request.getPermissoes()) {
                Permission permissao = catalogoPorChave.get(chave);
                if (permissao == null) {
                    return RecusaTransacional.recusar(
                            ResponseEntity.badRequest().body(Map.of("message", "Permissão desconhecida: " + chave)));
                }
                permissoesResolvidas.add(permissao);
            }
        }

        // moldeId(null) + sistema(false): um papel criado de raiz por um administrador não tem
        // nenhuma proveniência de molde. Consequência deliberada (Phase 126, documentada aqui
        // de novo por clareza): este papel nunca pode ser "protegido" -- não é o papel de
        // administrador do escritório nem nenhum outro papel instanciado -- e
        // ResolucaoPapeisService.temPapelDeMolde nunca o vai corresponder a nenhum molde. É o
        // limite já assumido pela Fase 126, não uma lacuna nova desta fase.
        TenantRole novoPapel = TenantRole.builder()
                .tenantId(tenantId)
                .nome(nome)
                .moldeId(null)
                .sistema(false)
                .permissions(permissoesResolvidas)
                .build();

        TenantRole papelGravado;
        try {
            // saveAndFlush (Plano 03), nao save: forca a escrita a base de dados AQUI, dentro
            // deste try, para que uma violacao concorrente da constraint unica (tenant_id, nome)
            // caia neste catch como 409 -- em vez de so aparecer no commit da transacao, ja fora
            // do catch, como 500 nao tratado.
            papelGravado = tenantRoleRepository.saveAndFlush(novoPapel);
        } catch (DataIntegrityViolationException ex) {
            // Duplicacao concorrente: a constraint unica (tenant_id, nome) de t_tenant_role apanha
            // na base de dados uma corrida entre dois pedidos com o mesmo nome que passaram ambos
            // o pre-check acima -- mesmo idioma de PlatformAdminController.createMolde para nome
            // de molde duplicado.
            //
            // WR-01 (128-REVIEW.md): este catch apanha SO a escrita de tenantRoleRepository, NUNCA
            // a chamada de auditoria abaixo -- a mesma disciplina que deleteRole (mais abaixo neste
            // ficheiro) ja segue, e que WR-01 (127-REVIEW.md) ja tinha corrigido la para o mesmo
            // motivo. Antes desta correcao, uma DataIntegrityViolationException lancada pela
            // PROPRIA escrita de auditoria (auditLogRepository.save dentro de
            // registarPapelCriado, ex.: uma futura constraint em t_audit_log) era apanhada aqui e
            // devolvida ao cliente como "nome duplicado" -- uma mensagem factualmente errada que
            // mandaria quem esta a diagnosticar a olhar para a causa errada. O rollback continua
            // correcto de qualquer forma (RecusaTransacional.recusar marca a transaccao inteira
            // como rollback-only); so a MENSAGEM devolvida estava errada.
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Já existe um papel com este nome neste escritório.")));
        }
        auditoriaRbacService.registarPapelCriado(tenantId, principal, papelGravado);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", papelGravado.getId(), "nome", papelGravado.getNome()));
    }

    /**
     * {@code PUT /api/v1/admin/rbac/roles/{id}} (PAPEL-04): renomeia um papel próprio já
     * existente do escritório do chamador. Alterar o nome está deliberadamente disponível também
     * para o papel de administrador do escritório protegido (PAPEL-08 proíbe APAGAR ou DESPOJAR
     * de permissões esse papel, nunca renomeá-lo) -- é precisamente porque o nome deste papel
     * passa a ser editável que o resto desta fase discrimina o papel protegido por proveniência
     * ({@code moldeId}), nunca por nome. Este handler nunca toca em {@code permissions},
     * {@code moldeId}, {@code sistema} nem em nenhuma atribuição de utilizador -- exatamente o
     * que o diálogo de renomear promete ao operador (UI-SPEC §7).
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<?> renameRole(@PathVariable UUID id, @RequestBody PapelRenameRequest request) {
        UUID tenantId = getTenantId();
        UserPrincipal principal = getPrincipal();

        TenantRole tenantRole = tenantRoleRepository.findById(id).orElse(null);
        if (tenantRole == null || !tenantRole.getTenantId().equals(tenantId)) {
            return RecusaTransacional.recusar(
                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Papel não encontrado")));
        }

        String nomeSubmetido = request == null ? null : request.getNome();
        ResponseEntity<?> erroNome = validarNome(nomeSubmetido);
        if (erroNome != null) {
            return RecusaTransacional.recusar(erroNome);
        }
        String nome = nomeSubmetido.trim();

        boolean duplicadoNoutroId = tenantRoleRepository.findByTenantIdAndNome(tenantId, nome)
                .filter(outro -> !outro.getId().equals(id))
                .isPresent();
        if (duplicadoNoutroId) {
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Já existe um papel com este nome neste escritório.")));
        }

        // Capturado ANTES do setNome (Plano 03): e o valor que registarPapelRenomeado precisa
        // para o "nome antigo" do evento -- depois do setNome, tenantRole.getNome() ja seria o
        // novo nome nos dois lados.
        String nomeAntigo = tenantRole.getNome();
        tenantRole.setNome(nome);

        TenantRole papelGravado;
        try {
            // saveAndFlush (Plano 03): mesma razao de createRole -- forca a violacao de
            // unicidade concorrente a aparecer aqui, dentro do catch, em vez de so no commit.
            papelGravado = tenantRoleRepository.saveAndFlush(tenantRole);
        } catch (DataIntegrityViolationException ex) {
            // Mesmo idioma de createRole: corrida concorrente apanhada pela constraint unica
            // (tenant_id, nome), nao pelo pre-check acima.
            //
            // WR-01 (128-REVIEW.md): mesma correcao de createRole -- este catch apanha SO a
            // escrita de tenantRoleRepository, NUNCA a chamada de auditoria abaixo, para que uma
            // falha na PROPRIA escrita de auditoria nunca seja mal-reportada como nome duplicado.
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Já existe um papel com este nome neste escritório.")));
        }
        if (!nomeAntigo.equals(nome)) {
            // Uma renomeacao para o MESMO nome (nome submetido == nome actual) nao e uma
            // mudanca -- nao ha o que descrever no historico, por isso nenhum evento.
            auditoriaRbacService.registarPapelRenomeado(
                    tenantId, principal, papelGravado.getId(), nomeAntigo, papelGravado.getNome());
        }
        return ResponseEntity.ok(Map.of("id", papelGravado.getId(), "nome", papelGravado.getNome()));
    }

    /**
     * {@code DELETE /api/v1/admin/rbac/roles/{id}} (PAPEL-05/PAPEL-08/PAPEL-09): apaga um papel
     * próprio do escritório do chamador. Ordem das guardas, da mais barata para a mais cara, e
     * nada é apagado antes de TODAS passarem:
     * <ol>
     *   <li>Fronteira cross-tenant (T-127-18): {@code findById} ausente OU {@code tenantId}
     *   diferente do principal -> 404, nunca 403 -- devolver 403 permitiria a um escritório
     *   distinguir "id inexistente" de "id de outro tenant", uma sonda de enumeração que 404
     *   fecha por construção.</li>
     *   <li>Proveniência (PAPEL-08/T-127-19): o papel de administrador do escritório nunca pode
     *   ser apagado. O discriminador é {@code moldeId} contra o id do molde global "ADMIN" --
     *   NUNCA {@code nome.equals("ADMIN")} -- precisamente porque esta fase torna o nome editável;
     *   uma comparação de nome deixaria de funcionar na primeira vez que um escritório renomeasse
     *   este papel. Mesmo raciocínio já documentado em
     *   {@code ResolucaoPapeisService.temPapelDeMolde}. O papel de plataforma
     *   ({@code PLATAFORMA_ADMIN}) também é recusado aqui por defesa em profundidade (PAPEL-09),
     *   embora nunca deva ser alcançável por este endpoint (nunca aparece na leitura tenant-scoped
     *   que alimentaria o ecrã).</li>
     *   <li>Atribuição (PAPEL-05/T-127-20): uma CONTAGEM
     *   ({@link UserRepository#countByTenantRolesId(UUID)}), nunca uma lista carregada e depois
     *   verificada com {@code isEmpty()}, e nunca uma escrita apanhada num
     *   {@code catch (DataIntegrityViolationException)} sobre a FK -- 127-CONTEXT.md Decisão 4
     *   exige que esta seja uma decisão que a aplicação toma, não um erro de base de dados que
     *   interpreta, para que a mensagem possa nomear o número exato e o cliente possa mostrar o
     *   caminho "remova-os em Gestão de Utilizadores" (UI-SPEC §7).</li>
     *   <li>Só então {@code deleteById} + 204. {@code TenantRole.permissions} é o lado
     *   PROPRIETÁRIO de {@code t_tenant_role_permission} (ver {@link TenantRole}), por isso as
     *   suas linhas de junção vão com a entidade sem limpeza manual; o passo 3 já garante que não
     *   restam linhas {@code t_user_tenant_role} a apontar para este id.</li>
     * </ol>
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteRole(@PathVariable UUID id) {
        UUID tenantId = getTenantId();
        UserPrincipal principal = getPrincipal();

        TenantRole tenantRole = tenantRoleRepository.findById(id).orElse(null);
        if (tenantRole == null || !tenantRole.getTenantId().equals(tenantId)) {
            return RecusaTransacional.recusar(
                    ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Papel não encontrado")));
        }

        Integer adminMoldeId = roleRepository.findByNome("ADMIN").map(Role::getId).orElse(null);
        Integer plataformaMoldeId = roleRepository.findByNome(PAPEL_PLATAFORMA).map(Role::getId).orElse(null);

        boolean ehPapelDeAdministrador = adminMoldeId != null && adminMoldeId.equals(tenantRole.getMoldeId());
        if (ehPapelDeAdministrador) {
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Este é o papel de administrador do escritório e não pode ser apagado.")));
        }

        boolean ehPapelDePlataforma = (plataformaMoldeId != null && plataformaMoldeId.equals(tenantRole.getMoldeId()))
                || PAPEL_PLATAFORMA.equals(tenantRole.getNome())
                || PAPEL_PLATAFORMA_AUTORIDADE.equals(tenantRole.getNome());
        if (ehPapelDePlataforma) {
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "O papel de administrador de plataforma é reservado e não pode ser apagado a partir daqui.")));
        }

        long atribuicoes = userRepository.countByTenantRolesId(id);
        if (atribuicoes > 0) {
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Este papel está atribuído a " + atribuicoes + " utilizador(es) e não pode ser apagado.")));
        }

        try {
            tenantRoleRepository.deleteById(id);
            // flush() explicito (Plano 03): sem isto, o DELETE so seria emitido para a base de
            // dados no commit da transaccao -- ja fora deste try -- e a violacao de FK chegaria ao
            // chamador como 500 nao tratado em vez do 409 que este catch existe para produzir.
            tenantRoleRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            // WR-01 (127-REVIEW.md), actualizado no Plano 03: a contagem acima continua
            // deliberadamente CHECK-then-ACT, nao uma transaccao/lock (ver o doc-comment da
            // guarda de atribuicao acima) -- entre o count e este deleteById, um PUT
            // /admin/users/{id} concorrente com este id em tenantRoleIds pode inserir uma linha
            // t_user_tenant_role que ainda nao existia na contagem. @Transactional ao nivel do
            // metodo, no isolamento por omissao (READ COMMITTED), NAO fecha esta janela -- nao e
            // um lock, e as duas transaccoes veem o estado uma da outra normalmente assim que cada
            // uma faz commit; o flush() acima e o que garante que a FK e avaliada AQUI, dentro
            // deste try, e nao silenciosamente adiada para o commit da transaccao global. Sem o
            // flush, a FK apanhava esta corrida como DataIntegrityViolationException nao tratada
            // no commit -> 500 nao estruturado, em vez do 409 que a contagem foi desenhada para
            // produzir. Apanha SO esta excecao NESTE ponto (nunca um catch generico a volta do
            // metodo inteiro -- a Fase 125 ja tinha corrigido um catch demasiado largo que
            // rotulava mal violacoes nao relacionadas) e devolve a MESMA mensagem que a contagem
            // ja da, porque semanticamente e o mesmo motivo de recusa (atribuicao concorrente), so
            // apanhado num sitio diferente. A janela de corrida check-then-act em si permanece
            // aceite tal e qual -- este catch so corrige a FORMA da falha (500 -> 409), nao
            // elimina a corrida. O evento de auditoria nunca sobrevive a este rollback: como
            // registarPapelApagado so e chamado DEPOIS do try (abaixo), uma FK apanhada aqui nunca
            // chega a gerar evento nenhum -- a transaccao inteira (DELETE + o que quer que tivesse
            // sido gravado antes dele) reverte junto com a recusa.
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Este papel está atribuído a um ou mais utilizadores e não pode ser apagado.")));
        }
        auditoriaRbacService.registarPapelApagado(tenantId, principal, tenantRole);
        return ResponseEntity.noContent().build();
    }
}
