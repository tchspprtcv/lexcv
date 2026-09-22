package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.OfficeRbacResponse;
import com.lexcv.dtos.OfficeRbacUpdateRequest;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.TenantRole;
import com.lexcv.models.User;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import com.lexcv.services.AuditoriaRbacService;
import com.lexcv.services.ResolucaoPapeisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
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

    // Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 04: createUser, updateUser, deleteUser e
    // updateRbac (abaixo) sao @Transactional a partir daqui. A razao: o evento de auditoria
    // (Plano 02, AuditoriaRbacService) que o Plano 05 acrescenta a estes quatro caminhos de
    // escrita tem de fazer commit ou rollback JUNTO com a mudanca que descreve, nunca
    // separadamente -- ou ficam ambos gravados, ou nenhum.
    //
    // Isto introduz um problema que nao existia enquanto estes handlers nao eram transaccionais:
    // com open-in-view ligado (default do Spring Boot, nao sobreposto em application.yml deste
    // repositorio), a entidade User carregada por findById em updateUser permanece GERIDA durante
    // o pedido inteiro. Sem a regra abaixo, um updateUser recusado a meio do metodo (por exemplo,
    // um "roles" invalido devolvido DEPOIS de ja ter chamado
    // setEmail/setNome/setTelefone/setAvatarUrl/setAtivo mais acima no mesmo pedido) faria o
    // dirty-checking do Hibernate persistir essas mutacoes no commit -- mesmo o pedido tendo sido
    // recusado. Um ResponseEntity de erro (400/403/404/409) devolvido de dentro de um metodo
    // @Transactional e, do ponto de vista do Spring, um retorno NORMAL: o TransactionInterceptor
    // faz commit, recusa incluida, a nao ser que algo marque a transaccao como rollback-only.
    //
    // Por isso: TODA resposta nao-2xx destes quatro handlers passa por
    // RecusaTransacional.recusar(...), que marca a transaccao corrente como rollback-only -- ver
    // essa classe para o mecanismo completo e para o porque de tambem cobrir
    // DataIntegrityViolationException apanhadas. Sucessos nunca passam por recusar.
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantRepository tenantRepository;
    private final ResolucaoPapeisService resolucaoPapeisService;
    // Phase 127 (Plano 03, PAPEL-01/PAPEL-08): fonte tenant-scoped de leitura/escrita de
    // getRbac/updateRbac -- ver os dois handlers abaixo. Adicionado no fim da lista, apos
    // resolucaoPapeisService, para nao reordenar os construtores posicionais ja existentes nos
    // testes deste controller.
    private final TenantRoleRepository tenantRoleRepository;
    // Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 05: unico escritor dos eventos de auditoria
    // RBAC (AuditoriaRbacService, Plano 02) chamado pelas quatro escritas transaccionais abaixo --
    // ver updateRbac/createUser/updateUser/deleteUser. Adicionado no fim da lista, apos
    // tenantRoleRepository, pela mesma razao: os construtores posicionais ja existentes nos testes
    // deste controller so ganham um argumento final, nunca sao reordenados.
    private final AuditoriaRbacService auditoriaRbacService;

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
                    // Phase 127 (Plano 05, Decisao 6): a chave de atribuicao que o formulario de
                    // edicao usa para pre-selecionar -- ver o doc-comment em UserResponse. u.getTenantRoles()
                    // e EAGER, por isso isto nao acrescenta nenhuma query.
                    .tenant_role_ids(u.getTenantRoles().stream().map(TenantRole::getId).collect(Collectors.toSet()))
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

    // Phase 127 fix (CR-01, 127-REVIEW.md): guarda de "ultimo administrador ACTIVO" -- simetrica a
    // guarda de existencia de OfficeRolesController#deleteRole (PAPEL-05/Decisao 4) e ao piso de
    // permissoes de updateRbac (PAPEL-08), mas para a ATRIBUICAO/ACTIVACAO do papel protegido
    // (moldeId do molde global "ADMIN"), que nenhuma das outras duas guardas cobre. Sem esta
    // guarda, updateUser (via tenantRoleIds OU via "ativo": false) e deleteUser podiam reduzir a
    // zero o numero de utilizadores ACTIVOS do papel protegido, trancando o escritorio inteiro
    // fora de /api/v1/admin/** -- o auto-trancamento silencioso que PAPEL-08 proibe. createUser
    // nunca precisa desta guarda: so acrescenta utilizadores, nunca reduz detentores existentes.
    //
    // Contagem, nunca tentativa-e-erro sobre uma FK -- mesma disciplina de
    // OfficeRolesController#deleteRole (userRepository.countByTenantRolesIdAndAtivoTrue).
    //
    // `detentorContadoAntes`/`detentorContadoDepois` sao passados EXPLICITAMENTE pelo chamador
    // (nunca lidos de dentro deste metodo a partir de `user`) porque updateUser pode mutar `user`
    // a meio do pedido (ex.: "ativo" processado antes de "tenantRoleIds" no mesmo pedido) -- ler
    // o estado directamente daqui seria ambiguo sobre se reflecte o ANTES ou o DEPOIS de uma
    // mutacao anterior no mesmo metodo. Cada chamador calcula os dois flags a partir do seu
    // proprio contexto, sempre relativos ao ESTADO ORIGINAL em BD (nunca ao objeto `user` ja
    // mutado em memoria) para a parcela "antes".
    //
    // Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 04 -- fecho da corrida (127-REVIEW.md CR-01
    // continuava aberto para concorrencia, mesmo depois da guarda original): antes desta
    // alteracao, dois pedidos concorrentes que reduzissem CADA UM um detentor diferente do mesmo
    // papel protegido podiam ambos observar a mesma contagem "antes" (ex.: 2), ambos concluir que
    // sobra pelo menos um, e ambos prosseguir -- deixando a contagem real em zero apesar de cada
    // pedido, isolado, ter sido correctamente avaliado. Agora, ANTES de contar, o metodo adquire
    // um lock PESSIMISTIC_WRITE de uma UNICA linha sobre o proprio TenantRole protegido
    // (tenantRoleRepository.bloquearParaAlteracaoDeDetentores) -- so funciona porque este metodo
    // corre sempre dentro de um chamador @Transactional (Plano 04 garante isso nos tres
    // call sites), e o lock e mantido ate ao commit dessa transaccao. O segundo pedido concorrente
    // bloqueia nesse ponto ate o primeiro terminar; sob PostgreSQL READ COMMITTED (isolamento por
    // omissao, nao alterado por este plano), CADA instrucao SQL le um snapshot fresco no momento
    // em que corre -- por isso a contagem que corre a seguir ao lock, no segundo pedido, ja ve o
    // efeito COMMITADO do primeiro, e recusa correctamente. A contagem em si tambem mudou: passa a
    // excluir o proprio utilizador a ser alterado (countByTenantRolesIdAndAtivoTrueAndIdNot),
    // porque sob @Transactional o Hibernate pode fazer flush automatico da mutacao pendente deste
    // utilizador antes desta query correr -- contar os OUTROS e correcto quer isso tenha
    // acontecido ou nao, sem depender de uma assuncao sobre o estado do flush.
    //
    // Limites residuais, registados explicitamente:
    // - so os caminhos que passam por esta guarda ficam serializados. Sao os UNICOS caminhos
    //   capazes de reduzir detentores activos do papel protegido: createUser so acrescenta, e
    //   OfficeRolesController#deleteRole recusa apagar o proprio papel protegido -- nenhum dos
    //   dois precisa do lock.
    // - uma edicao directa na base de dados (fora da aplicacao) esta fora deste ambito -- mesma
    //   postura ja assumida para o floor-lock da Phase 127.
    // - o lock e de UMA UNICA linha (sempre o mesmo TenantRole protegido), nunca dois locks em
    //   sequencia dentro do mesmo pedido, por isso nao introduz risco de deadlock por ordenacao.
    // - em testes unitarios sem transaccao real, o lock e um no-op de mock -- a serializacao real
    //   e demonstrada por este raciocinio mais o teste de integracao (IT) abaixo, nao por um teste
    //   concorrente local (Testcontainers/Docker nao corre localmente neste ambiente).
    private Optional<ResponseEntity<?>> guardaUltimoAdministrador(UUID utilizadorId, UUID papelProtegidoId,
            boolean detentorContadoAntes, boolean detentorContadoDepois) {
        if (papelProtegidoId == null || !detentorContadoAntes || detentorContadoDepois) {
            // Nada a guardar: ou este utilizador nunca contou como detentor activo do papel
            // protegido, ou continua a contar depois da operacao -- em nenhum dos casos esta
            // operacao pode reduzir a contagem de detentores activos a zero. Nem o lock nem a
            // contagem sao chamados quando a guarda nao e relevante.
            return Optional.empty();
        }
        // Lock ANTES da contagem, sempre -- e a ordem que fecha a corrida (ver o doc-comment
        // acima). O resultado devolvido nao e usado; o proposito e exclusivamente adquirir o lock
        // sobre esta linha e mante-lo ate ao commit da transaccao do chamador.
        tenantRoleRepository.bloquearParaAlteracaoDeDetentores(papelProtegidoId);
        long outrosDetentoresAtivos =
                userRepository.countByTenantRolesIdAndAtivoTrueAndIdNot(papelProtegidoId, utilizadorId);
        if (outrosDetentoresAtivos == 0) {
            // Nenhum OUTRO utilizador activo detem o papel protegido -- esta operacao deixaria a
            // contagem em zero.
            return Optional.of(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Esta operação deixaria o escritório sem nenhum utilizador ativo com o papel de "
                            + "administrador do escritório.")));
        }
        return Optional.empty();
    }

    // Phase 127 fix (CR-01): par (id do papel protegido que este utilizador detem, se algum;
    // detinha-o enquanto activo) calculado a partir do ESTADO ORIGINAL de `user`, ANTES de
    // qualquer mutacao de updateUser/deleteUser -- ver o doc-comment de guardaUltimoAdministrador
    // acima para porque isto nao pode ser recalculado a meio do metodo a partir de `user` mutado.
    private record DetentorPapelProtegidoOriginal(UUID papelProtegidoId, boolean detinhaAtivo) {
        static DetentorPapelProtegidoOriginal de(User user, Integer adminMoldeId) {
            if (adminMoldeId == null) {
                return new DetentorPapelProtegidoOriginal(null, false);
            }
            UUID papelId = user.getTenantRoles().stream()
                    .filter(tr -> adminMoldeId.equals(tr.getMoldeId()))
                    .map(TenantRole::getId)
                    .findFirst().orElse(null);
            boolean detinhaAtivo = papelId != null && Boolean.TRUE.equals(user.getAtivo());
            return new DetentorPapelProtegidoOriginal(papelId, detinhaAtivo);
        }
    }

    // Phase 127 (Plano 05, 127-CONTEXT.md Decisao 6): par de valores devolvido pelo resolvedor de
    // atribuicao POR ID abaixo -- mesma forma/razao de ser do par historico ResolucaoTenantRolesOuErro
    // (ainda usado por updateUser ate ao Plano 05/Tarefa 2 converter tambem esse handler).
    private record ResolucaoPapeisEscritorioOuErro(Set<TenantRole> tenantRoles, ResponseEntity<?> erro) {
        static ResolucaoPapeisEscritorioOuErro sucesso(Set<TenantRole> tenantRoles) {
            return new ResolucaoPapeisEscritorioOuErro(tenantRoles, null);
        }

        static ResolucaoPapeisEscritorioOuErro erro(ResponseEntity<?> erro) {
            return new ResolucaoPapeisEscritorioOuErro(null, erro);
        }
    }

    // Phase 128 (AUDT-01, Decisao 3, 128-CONTEXT.md), Plano 05: par de conjuntos de chaves de
    // permissao (adicionadas/removidas) calculado por updateRbac para UM papel, contra o estado
    // CORRENTE em BD -- ver o comentario no corpo de updateRbac. Passado directamente a
    // AuditoriaRbacService.registarPermissoesAlteradas, que e um no-op quando ambos os lados estao
    // vazios.
    private record DiffPermissoesPapel(Set<String> adicionadas, Set<String> removidas) {
    }

    // Phase 127 (Plano 05, Decisao 6): ponto UNICO de resolucao de "tenantRoleIds" submetido por
    // createUser/updateUser. Fecha a colisao que o CONTEXT.md descreve: atribuicao deixou de
    // resolver por NOME de papel global (roleRepository.findByNome + resolverPapeisDeEscritorio),
    // que quebrava com 409 sempre que um escritorio renomeasse um papel (Decisao 6, Fase 126
    // 409 de mapeamento parcial). Cada id e resolvido apenas contra um mapa construido a partir de
    // tenantRoleRepository.findByTenantId(tenantId) -- NUNCA findById -- para que um id de outro
    // tenant fique simplesmente ausente, sem depender de uma comparacao de tenant que alguem se
    // pudesse esquecer de escrever (T-127-23, mesma tecnica do plano 03 em updateRbac).
    //
    // Validate-then-resolve: cada guarda abaixo devolve erro ANTES de acrescentar o papel ao
    // conjunto resolvido, nunca depois -- por isso um pedido recusado nunca deixa um subconjunto
    // parcial no valor de retorno (mesma disciplina de updateRbac/CR-01 126-REVIEW.md).
    private ResolucaoPapeisEscritorioOuErro resolverPapeisEscritorioPorId(UUID tenantId, List<?> idsSubmetidos) {
        if (idsSubmetidos == null || idsSubmetidos.isEmpty()) {
            return ResolucaoPapeisEscritorioOuErro.erro(ResponseEntity.badRequest()
                    .body(Map.of("message", "Pelo menos um papel do escritório é obrigatório.")));
        }

        // UNICA chamada a repositorio ate este ponto -- "a lista de papeis do tenant" que a
        // acceptance criteria do plano 05 refere para o caso de id malformado: nenhum outro
        // repositorio e tocado antes de todos os ids terem sido validados como UUID.
        Map<UUID, TenantRole> papeisDoTenant = tenantRoleRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(TenantRole::getId, tr -> tr));

        // Primeira passagem: so faz parsing, nunca toca em papeisDoTenant nem em roleRepository --
        // um id malformado falha aqui, antes de qualquer outra consulta, mesmo que outro id da
        // mesma lista fosse valido.
        List<UUID> ids = new ArrayList<>(idsSubmetidos.size());
        for (Object idObj : idsSubmetidos) {
            try {
                ids.add(UUID.fromString(String.valueOf(idObj)));
            } catch (IllegalArgumentException e) {
                return ResolucaoPapeisEscritorioOuErro.erro(ResponseEntity.badRequest()
                        .body(Map.of("message", "Identificador de papel inválido: " + idObj)));
            }
        }

        // Resolvido UMA vez, so depois de todos os ids terem passado no parsing -- mesmo idioma de
        // getRbac/updateRbac (plano 03) para o discriminador de proveniencia do papel reservado.
        Integer plataformaMoldeId = roleRepository.findByNome(PAPEL_PLATAFORMA).map(Role::getId).orElse(null);

        Set<TenantRole> resolvidos = new HashSet<>();
        for (UUID id : ids) {
            TenantRole tenantRole = papeisDoTenant.get(id);
            if (tenantRole == null) {
                return ResolucaoPapeisEscritorioOuErro.erro(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Papel não encontrado neste escritório: " + id)));
            }

            // Tripla guarda (PAPEL-09, T-127-24): nome cru, nome prefixado ROLE_ (defesa em
            // profundidade, mesma disciplina de PAPEL_PLATAFORMA_AUTORIDADE acima) e proveniencia
            // (moldeId) -- so a proveniencia sobrevive a uma renomeacao do papel reservado, mas as
            // outras duas ficam para fechar o terceiro caminho (atribuicao a utilizador) com a
            // mesma tripla guarda que getRbac/updateRbac ja aplicam a leitura/escrita da matriz.
            if (PAPEL_PLATAFORMA.equals(tenantRole.getNome())
                    || PAPEL_PLATAFORMA_AUTORIDADE.equals(tenantRole.getNome())
                    || (plataformaMoldeId != null && plataformaMoldeId.equals(tenantRole.getMoldeId()))) {
                return ResolucaoPapeisEscritorioOuErro.erro(ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                        "O papel de administrador de plataforma é reservado e não pode ser atribuído a partir da gestão de utilizadores do escritório.")));
            }

            resolvidos.add(tenantRole);
        }

        return ResolucaoPapeisEscritorioOuErro.sucesso(resolvidos);
    }

    // Phase 127 (Plano 05, Decisao 6): deriva o mirror global t_user_role a partir da PROVENIENCIA
    // dos papeis de escritorio resolvidos acima -- nunca a partir de um nome que o utilizador
    // tenha escrito. Fase 126 manteve t_user_role povoado deliberadamente para reversibilidade
    // (remover a coluna esta diferido para depois deste marco), e
    // UserRepository.findByTenantIdAndRoleName/findByTenantIdAndRoleNameIn/
    // findByTenantIdAndRoleNameAndAtivoTrue continuam a le-lo -- consumidos por
    // NotificacaoService (fan-out ADMIN) e AlertasDiariosJob. Um papel que um escritorio criou de
    // raiz (moldeId nulo) nao contribui nada para este mirror -- consequencia aceite e inofensiva:
    // ResolucaoPapeisService.usaPapeisDeEscritorio escolhe sempre o ramo de escritorio quando
    // tenantRoles nao esta vazio, por isso a autoridade EFECTIVA do utilizador nunca depende deste
    // mirror (T-127-29).
    private Set<Role> derivarMirrorGlobalDePapeis(Set<TenantRole> tenantRoles) {
        Set<Role> mirror = new HashSet<>();
        for (TenantRole tenantRole : tenantRoles) {
            if (tenantRole.getMoldeId() != null) {
                roleRepository.findById(tenantRole.getMoldeId()).ifPresent(mirror::add);
            }
        }
        return mirror;
    }

    // Phase 127 (Plano 05, Decisao 6): o wrapper por-nome que existia aqui -- envolvendo
    // ResolucaoPapeisService.resolverPapeisDeEscritorio para traduzir a excepcao de mapeamento
    // parcial (Fase 126) num 409 -- foi removido: o ultimo chamador (updateUser) converteu-se
    // para resolverPapeisEscritorioPorId acima. Esse 409 desaparece do AdminController porque a
    // atribuicao deixou de passar por nomes globais (a correcao estrutural que a Decisao 6 pediu,
    // nao um abrandamento do achado da Fase 126: o metodo do servico e a sua excepcao continuam
    // intactos, com MigracaoPapeisEscritorioService como unico chamador restante -- ver
    // ResolucaoPapeisService.resolverPapeisDeEscritorio). NAO restaurar este wrapper: qualquer
    // caminho novo de atribuicao tem de usar ids de TenantRole, nunca nomes globais.

    @PostMapping("/users")
    @Transactional
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("nome") || !body.containsKey("email") || !body.containsKey("password") || !body.containsKey("tenantRoleIds")) {
            return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message", "Nome, email, password e tenantRoleIds são obrigatórios.")));
        }

        // Phase 127 (Plano 05, Decisao 6, T-127-28): "roles" (nome global) deixou de ser aceite
        // para atribuicao -- um cliente desatualizado que ainda o envie e recusado explicitamente,
        // nunca ignorado em silencio, para que a atribuicao nunca "nao faca nada" sem aviso.
        if (body.containsKey("roles")) {
            return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message",
                    "O campo \"roles\" deixou de ser aceite para atribuição de papéis; use \"tenantRoleIds\" com os ids dos papéis do escritório.")));
        }

        // IN-01 (128-REVIEW.md): body.containsKey("password") acima e verdadeiro mesmo para um
        // corpo JSON com "password": null -- so o instanceof (nunca um cast cru) distingue esse
        // caso, evitando uma NullPointerException em password.matches(...) que antes so era
        // apanhada pelo catch-all de GlobalExceptionHandler como 500 nao estruturado.
        if (!(body.get("password") instanceof String password)
                || !password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
            return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message", "A password deve ter no mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial.")));
        }

        String email = (String) body.get("email");
        if (userRepository.findByEmail(email).isPresent()) {
            return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message", "Já existe um utilizador registado com este endereço de email.")));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        // Phase 127 (Plano 05, Decisao 6): atribuicao por id de papel de escritorio, nunca por
        // nome global -- ver resolverPapeisEscritorioPorId. O tenant usado e sempre o do principal
        // autenticado -- nunca um valor vindo do corpo do pedido -- a mesma fronteira de
        // isolamento multi-tenant que o resto deste controller ja respeita (T-127-23).
        List<?> tenantRoleIdsList = (List<?>) body.get("tenantRoleIds");
        ResolucaoPapeisEscritorioOuErro resolucao =
                resolverPapeisEscritorioPorId(principal.getTenantId(), tenantRoleIdsList);
        if (resolucao.erro() != null) {
            return RecusaTransacional.recusar(resolucao.erro());
        }
        Set<TenantRole> tenantRoles = resolucao.tenantRoles();
        Set<Role> mirror = derivarMirrorGlobalDePapeis(tenantRoles);

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
                return RecusaTransacional.recusar(limiteExcedido.get());
            }
        }

        List<?> permsList = body.containsKey("permissions") ? (List<?>) body.get("permissions") : Collections.emptyList();

        // CR-01 (119-REVIEW.md): mesma recusa aplicada acima a "roles" -- ver o comentario de
        // PAPEL_PLATAFORMA_AUTORIDADE. Bloqueia tanto a forma crua do papel como a forma
        // ja-prefixada que realmente satisfaz hasRole('PLATAFORMA_ADMIN') quando vinda deste campo.
        for (Object pObj : permsList) {
            if (PAPEL_PLATAFORMA.equals(pObj) || PAPEL_PLATAFORMA_AUTORIDADE.equals(pObj)) {
                return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                        "O papel de administrador de plataforma é reservado e não pode ser atribuído a partir da gestão de utilizadores do escritório.")));
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
                .roles(mirror)
                .tenantRoles(tenantRoles)
                .permissions(permissions)
                .ativo(ativoInicial)
                .telefone(body.containsKey("telefone") ? (String) body.get("telefone") : "")
                .avatarUrl(body.containsKey("avatar_url") ? (String) body.get("avatar_url") : "")
                .build();

        user = userRepository.save(user);

        // Phase 128 (AUDT-02, Decisao 3): papel_atribuir por cada papel atribuido na criacao --
        // "antes" e sempre vazio (utilizador novo nunca deteve nenhum papel), "motivo" e null
        // (criacao normal, nao provisionamento nem eliminacao). Chamado DEPOIS do save, dentro da
        // mesma transaccao @Transactional deste handler -- uma falha aqui reverte tambem a
        // criacao do utilizador (RecusaTransacional nao se aplica: este e o caminho de SUCESSO).
        auditoriaRbacService.registarAtribuicoes(principal.getTenantId(), principal, user, Set.of(), user.getTenantRoles(), null);

        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .tenant_id(user.getTenantId())
                .nome(user.getNome())
                .email(user.getEmail())
                .roles(resolucaoPapeisService.resolverNomesPapeis(user))
                .tenant_role_ids(user.getTenantRoles().stream().map(TenantRole::getId).collect(Collectors.toSet()))
                .permissions(permissions)
                .ativo(user.getAtivo())
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> updateUser(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        User user = userRepository.findById(id).orElse(null);
        if (user == null || !user.getTenantId().equals(principal.getTenantId())) {
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilizador não encontrado")));
        }

        // CR-01 (127-REVIEW.md): snapshot do ESTADO ORIGINAL -- antes de QUALQUER mutação feita
        // por este método -- usado pelas duas guardas de "último administrador ativo" abaixo
        // (ativo: true -> false, e remoção do papel via tenantRoleIds). Ver o doc-comment de
        // guardaUltimoAdministrador para porque isto tem de ser calculado aqui, e não a meio do
        // método a partir de `user` já mutado.
        Integer adminMoldeIdParaUltimoAdministrador = roleRepository.findByNome("ADMIN").map(Role::getId).orElse(null);
        DetentorPapelProtegidoOriginal detentorOriginal =
                DetentorPapelProtegidoOriginal.de(user, adminMoldeIdParaUltimoAdministrador);

        // Phase 128 (AUDT-02, Decisao 3): snapshot do conjunto de papeis ANTES de qualquer
        // mutacao deste metodo -- mesma disciplina do snapshot de "detentorOriginal" acima, e pela
        // mesma razao: user.getTenantRoles() e mutado mais abaixo (bloco "tenantRoleIds"), por
        // isso o "antes" tem de ser copiado agora, nunca lido do objeto ja mutado. Copia (HashSet
        // novo), nunca a colecao gerida pelo Hibernate.
        Set<TenantRole> papeisAntes = new HashSet<>(user.getTenantRoles());

        if (body.containsKey("email")) {
            String email = (String) body.get("email");
            Optional<User> existing = userRepository.findByEmail(email);
            if (existing.isPresent() && !existing.get().getId().equals(id)) {
                return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message", "Já existe outro utilizador com este email.")));
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
                return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message", "O campo ativo deve ser um valor booleano.")));
            }
            // CR-01 (117-REVIEW.md): reativar um utilizador (false -> true) é o segundo caminho capaz
            // de tornar um utilizador ativo, além de createUser — sem esta verificação o limite era
            // totalmente contornável (criar com ativo=false, que nunca sobe a contagem, e reativar
            // depois sem qualquer controlo). Só a transição false -> true paga o custo da verificação;
            // true -> false, ou qualquer update que não mexa em `ativo`, nunca chamam o helper.
            if (novoAtivo && !Boolean.TRUE.equals(user.getAtivo())) {
                Optional<ResponseEntity<?>> limiteExcedido = limiteUtilizadoresExcedido(principal.getTenantId());
                if (limiteExcedido.isPresent()) {
                    return RecusaTransacional.recusar(limiteExcedido.get());
                }
            }
            // CR-01 (127-REVIEW.md): desativar (true -> false) e o segundo caminho, alem da
            // remoção via tenantRoleIds abaixo, capaz de reduzir a zero os detentores ATIVOS do
            // papel protegido -- se este utilizador for o único, recusar antes de qualquer
            // mutação. `detentorContadoDepois` é sempre `false` aqui: por definição, desativar
            // significa que este utilizador deixa de contar como ativo, independentemente de
            // continuar a deter o papel.
            if (!novoAtivo) {
                Optional<ResponseEntity<?>> bloqueioUltimoAdministrador = guardaUltimoAdministrador(
                        id, detentorOriginal.papelProtegidoId(), detentorOriginal.detinhaAtivo(), false);
                if (bloqueioUltimoAdministrador.isPresent()) {
                    return RecusaTransacional.recusar(bloqueioUltimoAdministrador.get());
                }
            }
            user.setAtivo(novoAtivo);
        }

        // A password e OPCIONAL neste handler: ausente, nula ou em branco significam "nao mudar".
        // O `instanceof` (nunca um cast cru) cobre os tres casos de uma vez -- `(String) null`
        // passa no cast e fazia `.trim()` lancar uma NullPointerException nao tratada, devolvendo
        // 500 onde o contrato manda ignorar o campo. Mesma correcao que IN-01 em createUser.
        if (body.get("password") instanceof String password && !password.trim().isEmpty()) {
            if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
                return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message", "A password deve ter no mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial.")));
            }
            user.setPasswordHash(passwordEncoder.encode(password));
        }

        // Phase 127 (Plano 05, Decisao 6, T-127-28): "roles" (nome global) deixou de ser aceite
        // para atribuicao -- mesma recusa explicita de createUser, nunca um ignorar silencioso.
        // O return acontece antes de qualquer userRepository.save(user), por isso nenhuma
        // mutacao ja aplicada em memoria (nome/email/telefone/etc.) e persistida.
        if (body.containsKey("roles")) {
            return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message",
                    "O campo \"roles\" deixou de ser aceite para atribuição de papéis; use \"tenantRoleIds\" com os ids dos papéis do escritório.")));
        }

        if (body.containsKey("tenantRoleIds")) {
            List<?> tenantRoleIdsList = (List<?>) body.get("tenantRoleIds");
            // CR-01/WR-04 (126-REVIEW.md), preservado: resolver (e devolver qualquer erro) ANTES
            // de mutar `user` -- um pedido recusado (id malformado, estrangeiro, reservado, ou
            // selecao vazia) nunca deixa `user` parcialmente mutado em memoria, mesmo que nada
            // ainda tivesse sido persistido (nenhum userRepository.save ainda ocorreu).
            ResolucaoPapeisEscritorioOuErro resolucao =
                    resolverPapeisEscritorioPorId(principal.getTenantId(), tenantRoleIdsList);
            if (resolucao.erro() != null) {
                return RecusaTransacional.recusar(resolucao.erro());
            }

            Set<TenantRole> tenantRoles = resolucao.tenantRoles();

            // CR-01 (127-REVIEW.md): a remoção do papel protegido é o caminho PRINCIPAL desta
            // guarda -- recusar ANTES de qualquer mutação (mesma disciplina validate-then-write
            // de CR-01/WR-04 126-REVIEW.md acima). `detentorContadoDepois` usa o `user.getAtivo()`
            // JÁ MUTADO se "ativo" também estava presente neste pedido (processado antes deste
            // bloco) -- correto: se o mesmo pedido desativa e remove o papel, só a desativação
            // (guardada acima) precisa de recusar; se o utilizador já foi confirmado como
            // continuando ativo, esta guarda usa esse facto para decidir se ele continua a contar.
            boolean deteraPapelProtegidoDepois = detentorOriginal.papelProtegidoId() != null
                    && tenantRoles.stream().anyMatch(tr -> detentorOriginal.papelProtegidoId().equals(tr.getId()))
                    && Boolean.TRUE.equals(user.getAtivo());
            Optional<ResponseEntity<?>> bloqueioUltimoAdministrador = guardaUltimoAdministrador(
                    id, detentorOriginal.papelProtegidoId(), detentorOriginal.detinhaAtivo(), deteraPapelProtegidoDepois);
            if (bloqueioUltimoAdministrador.isPresent()) {
                return RecusaTransacional.recusar(bloqueioUltimoAdministrador.get());
            }

            Set<Role> mirror = derivarMirrorGlobalDePapeis(tenantRoles);
            // Copia (HashSet novo), nunca a colecao devolvida pelo resolvedor -- mesma disciplina
            // do createUser/mirror acima.
            user.setTenantRoles(new HashSet<>(tenantRoles));
            user.setRoles(new HashSet<>(mirror));
        }

        if (body.containsKey("permissions")) {
            List<?> permsList = (List<?>) body.get("permissions");

            // CR-01 (119-REVIEW.md): mesma recusa de createUser -- ver o comentario de
            // PAPEL_PLATAFORMA_AUTORIDADE. O return acontece antes de qualquer
            // userRepository.save(user), por isso nenhuma mutacao ja aplicada em memoria
            // (nome/email/telefone/roles/etc.) e persistida.
            for (Object pObj : permsList) {
                if (PAPEL_PLATAFORMA.equals(pObj) || PAPEL_PLATAFORMA_AUTORIDADE.equals(pObj)) {
                    return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                            "O papel de administrador de plataforma é reservado e não pode ser atribuído a partir da gestão de utilizadores do escritório.")));
                }
            }

            Set<String> permissions = new HashSet<>();
            for (Object pObj : permsList) {
                permissions.add((String) pObj);
            }
            user.setPermissions(permissions);
        }

        user = userRepository.save(user);

        // Phase 128 (AUDT-02, Decisao 3): so registamos atribuicao/remocao de papel quando o
        // pedido efectivamente submeteu "tenantRoleIds" -- desativacao, nome, email, password e
        // "permissions" nao sao atribuicoes de papel e nao devem gerar papel_atribuir/
        // papel_retirar (fora do ambito da Decisao 3). registarAtribuicoes por si so ja e um
        // no-op quando antes/depois resolvem ao mesmo conjunto de ids, mas a guarda abaixo evita
        // sequer chamar o servico quando "tenantRoleIds" nao fez parte deste pedido.
        if (body.containsKey("tenantRoleIds")) {
            auditoriaRbacService.registarAtribuicoes(principal.getTenantId(), principal, user, papeisAntes, user.getTenantRoles(), null);
        }

        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        if (principal.getUserId().equals(id)) {
            return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message", "Não é permitido apagar a sua própria conta de utilizador administrador.")));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null || !user.getTenantId().equals(principal.getTenantId())) {
            return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Utilizador não encontrado")));
        }

        // CR-01 (127-REVIEW.md): a auto-eliminação já está recusada acima (principal == id); esta
        // guarda fecha a lacuna irmã -- eliminar o ÚLTIMO detentor ativo do papel protegido A
        // PARTIR DE OUTRA conta com users:manage próprio. `detentorContadoDepois` é sempre `false`:
        // eliminar o utilizador remove-o incondicionalmente de qualquer contagem.
        Integer adminMoldeIdParaUltimoAdministrador = roleRepository.findByNome("ADMIN").map(Role::getId).orElse(null);
        DetentorPapelProtegidoOriginal detentorOriginal =
                DetentorPapelProtegidoOriginal.de(user, adminMoldeIdParaUltimoAdministrador);
        Optional<ResponseEntity<?>> bloqueioUltimoAdministrador = guardaUltimoAdministrador(
                id, detentorOriginal.papelProtegidoId(), detentorOriginal.detinhaAtivo(), false);
        if (bloqueioUltimoAdministrador.isPresent()) {
            return RecusaTransacional.recusar(bloqueioUltimoAdministrador.get());
        }

        // Phase 128 (AUDT-02, Decisao 3, T-128-25): snapshot ANTES do hard delete -- depois de
        // userRepository.deleteById(id) o utilizador deixa de existir em BD, mas o objeto `user`
        // em memoria continua a transportar o id e o nome (JPA nao os limpa), que
        // AuditoriaRbacService.registarAtribuicoes usa para o snapshot "alvoNome" gravado em
        // detalhe (revised Decisao 2). Sem este evento, a eliminacao apagaria a historia de quais
        // papeis este utilizador deteve, sem deixar qualquer registo do fim dessa atribuicao.
        Set<TenantRole> papeisAntes = new HashSet<>(user.getTenantRoles());

        userRepository.deleteById(id);

        // Um utilizador sem nenhum papel produz zero eventos aqui -- registarAtribuicoes
        // devolve-se sem gravar quando antes/depois resolvem ao mesmo conjunto (vazio, neste
        // caso).
        auditoriaRbacService.registarAtribuicoes(principal.getTenantId(), principal, user, papeisAntes, Set.of(),
                AuditoriaRbacService.MOTIVO_UTILIZADOR_ELIMINADO);

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
    // para apertar).
    //
    // Phase 127 (Plano 03, PAPEL-01/PAPEL-08): o corpo deste handler deixou de ser global -- le
    // exclusivamente os TenantRole do tenant do chamador (tenantRoleRepository.findByTenantId
    // (principal.getTenantId())), nunca roleRepository.findAll(), no mesmo idioma de tenant-
    // scoping que listUsers ja usa neste ficheiro (linha ~88): o tenant vem sempre do principal
    // autenticado, nunca de um parametro ou corpo de pedido. "protegido"/"podeApagar" sao
    // calculados aqui por proveniencia (TenantRole.moldeId contra o id do Role global "ADMIN"),
    // nunca por comparacao de nome -- ver o doc-comment de OfficeRbacResponse.PapelDto.
    //
    // WR-03 (124-REVIEW.md) -- FECHADO ESTRUTURALMENTE por este plano, nao apenas corrigido: a
    // preocupacao original era que rolePermissions pudesse nomear uma chave sem coluna
    // correspondente em systemPermissions. Essa possibilidade desaparece aqui porque as
    // permissoes de cada papel (PapelDto.permissoes) vem agora do proprio snapshot
    // t_tenant_role_permission do papel, nao de um mapa Java construido a parte -- uma chave fora
    // do catalogo servido simplesmente nao e uma coluna renderizavel na matriz, mas continua a
    // ser um dado legitimo do papel (nunca escondido/filtrado aqui). E o caminho de ESCRITA
    // (updateRbac, abaixo) que mantem os dois conjuntos alinhados na origem, recusando qualquer
    // chave submetida que nao exista no catalogo servido -- ver "Permissão desconhecida" ali.
    @PreAuthorize("hasAuthority('rbac:manage')")
    @GetMapping("/rbac")
    public ResponseEntity<?> getRbac() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        // Resolvidos UMA vez por pedido, antes do laço de projeção -- nunca dentro dele.
        Integer adminMoldeId = roleRepository.findByNome("ADMIN").map(Role::getId).orElse(null);
        Integer plataformaMoldeId = roleRepository.findByNome(PAPEL_PLATAFORMA).map(Role::getId).orElse(null);

        List<TenantRole> tenantRoles = tenantRoleRepository.findByTenantId(principal.getTenantId());

        // Phase 119 (Plan 03) + Phase 127 (Plano 03, PAPEL-09): tripla guarda -- nome cru, nome
        // prefixado ROLE_ (defesa em profundidade, mesma disciplina de PAPEL_PLATAFORMA_AUTORIDADE
        // acima) e proveniencia (moldeId). Só a proveniência sobrevive a uma renomeação do papel
        // reservado -- um TenantRole instanciado do molde PLATAFORMA_ADMIN nunca deve aparecer na
        // matriz de um escritório, mesmo que alguém o renomeie.
        List<OfficeRbacResponse.PapelDto> papeis = tenantRoles.stream()
                .filter(tr -> !PAPEL_PLATAFORMA.equals(tr.getNome())
                        && !PAPEL_PLATAFORMA_AUTORIDADE.equals(tr.getNome())
                        && (plataformaMoldeId == null || !plataformaMoldeId.equals(tr.getMoldeId())))
                .sorted(Comparator.comparing(TenantRole::getNome))
                .map(tr -> {
                    boolean protegido = adminMoldeId != null && adminMoldeId.equals(tr.getMoldeId());
                    long utilizadoresAtribuidos = userRepository.countByTenantRolesId(tr.getId());
                    List<String> permissoesDoPapel = tr.getPermissions().stream()
                            .map(Permission::getNome)
                            .collect(Collectors.toList());
                    return OfficeRbacResponse.PapelDto.builder()
                            .id(tr.getId())
                            .nome(tr.getNome())
                            .sistema(Boolean.TRUE.equals(tr.getSistema()))
                            .protegido(protegido)
                            .podeApagar(utilizadoresAtribuidos == 0 && !protegido)
                            .utilizadoresAtribuidos(utilizadoresAtribuidos)
                            .permissoes(permissoesDoPapel)
                            .build();
                })
                .collect(Collectors.toList());

        // Phase 124 (Plan 02): a lista hardcoded de 17 entradas foi removida --
        // DatabaseSeeder.seedRbac() e agora a fonte de verdade do catalogo (CATL-01). O filtro
        // de reservadas a plataforma fica na query derivada do repositorio abaixo, de proposito,
        // nao aqui (CATL-03). A ordenacao por "ordem" existe porque o RbacTab do ecra de
        // Definicoes deriva a ordem dos modulos da ordem de chegada deste array.
        List<Permission> permissoesCatalogo = permissionRepository.findAllByReservadaPlataformaFalse();
        List<OfficeRbacResponse.PermissaoDefDto> permissoesDoCatalogo = permissoesCatalogo.stream()
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

        OfficeRbacResponse response = OfficeRbacResponse.builder()
                .papeis(papeis)
                .permissoes(permissoesDoCatalogo)
                .build();

        return ResponseEntity.ok(response);
    }

    // Phase 124 (Plan 02); retargetado para OfficeRbacResponse pelo Plano 03: mapper entidade ->
    // DTO para getRbac() (analog: PlatformAdminController.toSummary). key <- Permission.nome
    // (chave tecnica), nome <- Permission.rotulo (rotulo legivel) -- os dois campos chamam-se
    // "nome" em sitios diferentes e trocar a atribuicao faria a matriz RBAC mostrar a chave
    // tecnica como rotulo.
    private OfficeRbacResponse.PermissaoDefDto toPermissionDef(Permission p) {
        return OfficeRbacResponse.PermissaoDefDto.builder()
                .key(p.getNome())
                .nome(p.getRotulo())
                .descricao(p.getDescricao())
                .modulo(p.getModulo())
                .build();
    }

    // ISOL-03 (Phase 121) -- HISTORICO, substituido pelo comentario abaixo, nunca apagado sem
    // explicacao (127-CONTEXT.md Decisao 1): este handler foi fechado a PLATAFORMA_ADMIN porque
    // Role e Permission eram tabelas globais de plataforma, sem coluna tenant_id, pelo que um
    // ADMIN de escritorio a gravar esta matriz reescreveria exatamente as mesmas linhas de que
    // dependiam os utilizadores TECNICO/ADVOGADO/ASSISTENTE de TODOS os outros escritorios --
    // correto na altura.
    //
    // Phase 127 (Plano 03, 127-CONTEXT.md Decisao 1): essa razao desapareceu. As Fases 125/126
    // deram a cada escritorio os seus proprios papeis em t_tenant_role, e a resolucao de
    // autoridade (ResolucaoPapeisService/JwtAuthenticationFilter) ja le de la -- gravar esta
    // matriz deixou de tocar em linhas partilhadas. Este handler agora escreve exclusivamente
    // TenantRole cujo tenant_id e o do chamador (ver a resolucao de papeisDoTenant abaixo,
    // idioma identico ao de listUsers/resolverPapeisEscritorioPorId neste ficheiro), por isso o gate
    // pode ser uma permissao de escritorio (hasAuthority('rbac:manage')) em vez do papel de
    // plataforma. ISTO NAO E uma reversao da Fase 121 -- e o cumprimento da condicao que a
    // tornava temporaria. QUALQUER alteracao futura que alargue este handler de volta a Role/
    // Permission globais TEM de restaurar o gate hasRole('PLATAFORMA_ADMIN') NA MESMA alteracao,
    // ou reabre exatamente a escrita cross-tenant que o ISOL-03 original fechava.
    //
    // A anotacao de metodo abaixo substitui -- nunca soma a -- o gate de classe desta controller
    // (a mais especifica ganha, nunca sao combinadas com E logico); todos os restantes handlers
    // continuam governados apenas pelo gate de classe.
    //
    // Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 04: agora @Transactional -- a razao "Sem
    // @Transactional, de proposito" que esteve aqui ate agora deixou de se aplicar. A premissa
    // continua correcta: cada guarda abaixo (id desconhecido, chave de permissao desconhecida,
    // papel de plataforma, piso do administrador) recusa o pedido INTEIRO antes de tocar em
    // tenantRoleRepository.save -- validate-then-write, mesmo idioma de
    // PlatformAdminController.updateMoldes -- por isso a fase de validacao continua inteiramente
    // sem efeitos secundarios. Mas a CONCLUSAO ("nao precisa de transaccao") ja nao se sustenta,
    // por duas razoes.
    //
    // Primeira: o evento de auditoria (Plano 02, AuditoriaRbacService) que o Plano 05 acrescenta a
    // este caminho e uma segunda escrita que tem de fazer commit ou rollback JUNTO com a matriz de
    // permissoes que descreve.
    //
    // Segunda -- um defeito PRE-EXISTENTE que esta transaccao tambem corrige, nao um risco novo
    // introduzido pela auditoria: o caminho aceite grava cada papel num save() PROPRIO, cada um na
    // sua transaccao implicita. Uma falha a meio do ciclo (por exemplo, uma constraint de base de
    // dados violada no segundo save de tres) deixava a matriz parcialmente escrita -- o primeiro
    // papel ja gravado com o novo conjunto de permissoes, os restantes intactos -- um estado que
    // nenhum pedido, aceite ou recusado, deveria conseguir produzir.
    //
    // As recusas abaixo continuam a passar por RecusaTransacional.recusar, apesar de
    // validate-then-write significar que, na pratica, elas proprias nao mutam nada -- mantem a
    // regra uniforme nos sete handlers cobertos por esta fase (Planos 03/04), em vez de updateRbac
    // ser a unica excepcao.
    @PreAuthorize("hasAuthority('rbac:manage')")
    @PutMapping("/rbac")
    @Transactional
    public ResponseEntity<?> updateRbac(@RequestBody OfficeRbacUpdateRequest request) {
        if (request == null || request.getPapeis() == null) {
            return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message", "Mapeamento de papéis é obrigatório")));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        // Cada id submetido só é resolvido contra ESTE mapa -- construído exclusivamente a partir
        // do tenant do chamador (nunca de um valor do corpo do pedido, mesmo idioma de listUsers
        // acima). Um id de outro tenant está simplesmente ausente daqui, por isso a
        // inalcançabilidade cross-tenant é garantida por construção, não por uma comparação que
        // se poderia esquecer de fazer (T-127-11).
        Map<UUID, TenantRole> papeisDoTenant = tenantRoleRepository.findByTenantId(principal.getTenantId()).stream()
                .collect(Collectors.toMap(TenantRole::getId, tr -> tr));

        // Resolvido UMA vez, a partir do MESMO catálogo servido por getRbac -- nunca por
        // permissionRepository.findById sobre um id cru -- para que uma permissão reservada à
        // plataforma nunca possa entrar num papel de escritório por via de um corpo de pedido
        // (T-127-13, mesma defesa de PlatformAdminController.updateMoldes).
        Map<String, Permission> catalogoPorChave = permissionRepository.findAllByReservadaPlataformaFalse().stream()
                .collect(Collectors.toMap(Permission::getNome, p -> p));

        Integer adminMoldeId = roleRepository.findByNome("ADMIN").map(Role::getId).orElse(null);
        Integer plataformaMoldeId = roleRepository.findByNome(PAPEL_PLATAFORMA).map(Role::getId).orElse(null);

        // Validate-then-write: NENHUMA entrada é gravada antes de TODAS as entradas do pedido
        // passarem todas as guardas -- mesmo idioma de PlatformAdminController.updateMoldes.
        Map<TenantRole, Set<Permission>> resolvido = new LinkedHashMap<>();
        // Phase 128 (AUDT-01, Decisao 3): diff de chaves de permissao por papel, calculado AQUI --
        // contra tenantRole.getPermissions() CORRENTE, antes de qualquer setPermissions -- e
        // guardado num mapa paralelo indexado pelo mesmo TenantRole que o ciclo de escrita abaixo
        // percorre. AuditoriaRbacService.registarPermissoesAlteradas ja e um no-op quando os dois
        // conjuntos estao vazios, por isso um PUT de matriz completa que so muda um papel produz
        // exactamente um evento.
        Map<TenantRole, DiffPermissoesPapel> diffsPorPapel = new LinkedHashMap<>();
        for (OfficeRbacUpdateRequest.PapelPermissoesDto entrada : request.getPapeis()) {
            UUID id = entrada.getId();
            TenantRole tenantRole = id == null ? null : papeisDoTenant.get(id);
            if (tenantRole == null) {
                return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Papel não encontrado: " + id)));
            }

            // Tripla guarda (T-127-12): nome cru, nome prefixado ROLE_ e proveniência -- só a
            // proveniência sobrevive a uma renomeação, mas as outras duas ficam por defesa em
            // profundidade, mesma disciplina do resto deste ficheiro.
            if (PAPEL_PLATAFORMA.equals(tenantRole.getNome())
                    || PAPEL_PLATAFORMA_AUTORIDADE.equals(tenantRole.getNome())
                    || (plataformaMoldeId != null && plataformaMoldeId.equals(tenantRole.getMoldeId()))) {
                return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message",
                        "O papel de administrador de plataforma é reservado e não pode ser alterado a partir daqui.")));
            }

            List<String> chavesSubmetidas = entrada.getPermissoes() == null ? List.of() : entrada.getPermissoes();
            Set<Permission> permissoesResolvidas = new HashSet<>();
            for (String chave : chavesSubmetidas) {
                Permission permissao = catalogoPorChave.get(chave);
                if (permissao == null) {
                    return RecusaTransacional.recusar(ResponseEntity.badRequest().body(Map.of("message", "Permissão desconhecida: " + chave)));
                }
                permissoesResolvidas.add(permissao);
            }

            // Piso do administrador (PAPEL-08, 127-CONTEXT.md Decisao 4b): discriminado por
            // proveniência (moldeId), nunca por nome -- o nome deste papel passa a ser editável
            // nesta fase.
            boolean protegido = adminMoldeId != null && adminMoldeId.equals(tenantRole.getMoldeId());
            if (protegido) {
                Set<String> chavesAtuais = tenantRole.getPermissions().stream()
                        .map(Permission::getNome).collect(Collectors.toSet());
                Set<String> chavesSubmetidasSet = permissoesResolvidas.stream()
                        .map(Permission::getNome).collect(Collectors.toSet());

                Set<String> removidas = new HashSet<>(chavesAtuais);
                removidas.removeAll(chavesSubmetidasSet);
                if (!removidas.isEmpty()) {
                    return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                            "O papel de administrador do escritório não pode perder as permissões que o "
                                    + "tornam administrador: " + String.join(", ", removidas))));
                }

                // Verificação independente da regra de superconjunto acima, embora esta
                // normalmente já a implique: é o invariante que mantém os gates do plano 02
                // (users:manage/rbac:manage) alcançáveis mesmo que o estado guardado tenha sido
                // editado à mão fora da aplicação (ex.: SQL direto) -- nunca confiar apenas na
                // comparação de conjuntos para esta garantia específica.
                List<String> autoridadesDeGateEmFalta = new ArrayList<>();
                if (!chavesSubmetidasSet.contains("rbac:manage")) {
                    autoridadesDeGateEmFalta.add("rbac:manage");
                }
                if (!chavesSubmetidasSet.contains("users:manage")) {
                    autoridadesDeGateEmFalta.add("users:manage");
                }
                if (!autoridadesDeGateEmFalta.isEmpty()) {
                    return RecusaTransacional.recusar(ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                            "O papel de administrador do escritório não pode perder as permissões que o "
                                    + "tornam administrador: " + String.join(", ", autoridadesDeGateEmFalta))));
                }
            }

            // Phase 128 (AUDT-01): diff calculado contra o conjunto CORRENTE (tenantRole.getPermissions(),
            // ainda nao mutado) e o conjunto submetido -- guardado ANTES do resolvido.put abaixo,
            // que e o unico ponto que este ciclo usa para decidir o que persistir.
            Set<String> chavesAtuaisParaDiff = tenantRole.getPermissions().stream()
                    .map(Permission::getNome).collect(Collectors.toSet());
            Set<String> chavesSubmetidasParaDiff = permissoesResolvidas.stream()
                    .map(Permission::getNome).collect(Collectors.toSet());
            Set<String> adicionadas = new HashSet<>(chavesSubmetidasParaDiff);
            adicionadas.removeAll(chavesAtuaisParaDiff);
            Set<String> removidasDiff = new HashSet<>(chavesAtuaisParaDiff);
            removidasDiff.removeAll(chavesSubmetidasParaDiff);
            diffsPorPapel.put(tenantRole, new DiffPermissoesPapel(adicionadas, removidasDiff));

            resolvido.put(tenantRole, permissoesResolvidas);
        }

        for (Map.Entry<TenantRole, Set<Permission>> entry : resolvido.entrySet()) {
            TenantRole tenantRole = entry.getKey();
            tenantRole.setPermissions(new HashSet<>(entry.getValue()));
            tenantRoleRepository.save(tenantRole);

            // Phase 128 (AUDT-01, Decisao 3): evento gravado DEPOIS do save, dentro da mesma
            // transaccao -- o servico e um no-op quando o diff deste papel esta vazio (papel
            // resubmetido sem alteracao real), por isso um PUT de matriz completa que so muda um
            // papel produz exactamente um evento.
            DiffPermissoesPapel diff = diffsPorPapel.get(tenantRole);
            auditoriaRbacService.registarPermissoesAlteradas(principal.getTenantId(), principal, tenantRole,
                    diff.adicionadas(), diff.removidas());
        }

        // PAPEL-03: nenhum mecanismo extra é preciso para que isto tenha efeito numa sessão já
        // aberta -- JwtAuthenticationFilter re-resolve a autoridade a partir de tenantRoles
        // (EAGER) do utilizador em CADA pedido autenticado, deliberadamente sem memorização (ver
        // JwtAuthenticationFilter:50-58), por isso o pedido seguinte a este save já traz as
        // permissões novas.
        return ResponseEntity.ok(Map.of("message", "Permissões de perfis (RBAC) atualizadas com sucesso!"));
    }
}
