package com.lexcv.seed;

import com.lexcv.models.*;
import com.lexcv.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final ClienteRepository clienteRepository;
    private final ContaCorrenteRepository contaCorrenteRepository;
    private final ProcessoRepository processoRepository;
    private final ParteRepository parteRepository;
    private final FaseProcessualRepository faseProcessualRepository;
    private final ProcessoFaseRepository processoFaseRepository;
    private final EventoRepository eventoRepository;
    private final HonorarioRepository honorarioRepository;
    private final PagamentoRepository pagamentoRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.enabled}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) throws Exception {
        seedRbac();

        if (!seedEnabled) {
            return;
        }

        boolean initialized = systemSettingRepository.findById(SystemSetting.SINGLETON_ID)
                .map(SystemSetting::getInitialized)
                .orElse(false);
        if (!initialized) {
            return;
        }

        if (tenantRepository.count() > 0 || userRepository.count() > 0 || clienteRepository.count() > 0) {
            return;
        }

        System.out.println("🌱 Seeding LexCV database...");

        Role adminRole = roleRepository.findByNome("ADMIN").orElseThrow();
        Role assistenteRole = roleRepository.findByNome("ASSISTENTE").orElseThrow();

        // 3. Tenants
        Tenant tenant = Tenant.builder()
                .nome("NOSi (Demonstração)")
                .nif("500100200")
                .tipoEntidade("PUBLICO")
                .email("contacto@nosi.cv")
                .telefone("+238 2607900")
                .build();
        tenant = tenantRepository.save(tenant);
        UUID tenantId = tenant.getId();

        // 4. Users
        User adminUser = User.builder()
                .tenantId(tenantId)
                .nome("Administrador (PostgreSQL Real)")
                .email("admin@lexcv.cv")
                .passwordHash(passwordEncoder.encode("admin123"))
                .ativo(true)
                .roles(Set.of(adminRole))
                .build();
        userRepository.save(adminUser);

        User assistenteUser = User.builder()
                .tenantId(tenantId)
                .nome("Assistente")
                .email("assistente@lexcv.cv")
                .passwordHash(passwordEncoder.encode("assist123"))
                .ativo(true)
                .roles(Set.of(assistenteRole))
                .build();
        userRepository.save(assistenteUser);

        // 5. Clientes
        Cliente cliente1 = Cliente.builder()
                .tenantId(tenantId)
                .tipo("SINGULAR")
                .nome("João Andrade (PostgreSQL Real)")
                .nif("123456789")
                .email("joao.andrade@email.com")
                .telefone("+238 991 1234")
                .morada("Achada Santo António, Praia")
                .documentoTipo(DocumentoTipo.NIF)
                .documentoNumero("123456789")
                .ramoAtividade("Serviços")
                .detalhesAdicionais("Cliente habitual de consultoria")
                .build();
        cliente1 = clienteRepository.save(cliente1);
        UUID clienteId1 = cliente1.getId();

        Cliente cliente2 = Cliente.builder()
                .tenantId(tenantId)
                .tipo("COLETIVA")
                .nome("Empresa Atlântico, SA")
                .nif("512345678")
                .email("info@atlanticosa.cv")
                .telefone("+238 231 5678")
                .morada("Avenida Marginal, Mindelo")
                .documentoTipo(DocumentoTipo.NIF)
                .documentoNumero("512345678")
                .ramoAtividade("Comércio")
                .detalhesAdicionais("Empresa líder no setor marítimo")
                .build();
        cliente2 = clienteRepository.save(cliente2);
        UUID clienteId2 = cliente2.getId();

        // 6. Conta Corrente
        ContaCorrente cc1 = ContaCorrente.builder()
                .clienteId(clienteId1)
                .saldo(new BigDecimal("45000.00"))
                .build();
        contaCorrenteRepository.save(cc1);

        ContaCorrente cc2 = ContaCorrente.builder()
                .clienteId(clienteId2)
                .saldo(new BigDecimal("-120000.00"))
                .build();
        contaCorrenteRepository.save(cc2);

        // 7. Processos
        Processo processo1 = Processo.builder()
                .tenantId(tenantId)
                .clienteId(clienteId1)
                .numeroProcesso("PROC-2026-0001")
                .tipoProcesso("CÍVEL")
                .areaJuridica("CONTRATOS")
                .tribunal("Tribunal de Comarca da Praia")
                .estado("ATIVO")
                .dataInicio(LocalDate.of(2026, 5, 12))
                .descricao("Processo de demonstração para validar endpoints de fases, partes e movimentações.")
                .build();
        processo1 = processoRepository.save(processo1);
        UUID processoId1 = processo1.getId();

        Processo processo2 = Processo.builder()
                .tenantId(tenantId)
                .clienteId(clienteId2)
                .numeroProcesso("PROC-2026-0002")
                .tipoProcesso("FISCAL")
                .areaJuridica("TRIBUTÁRIO")
                .tribunal("Tribunal Fiscal Aduaneiro")
                .estado("ENCERRADO")
                .dataInicio(LocalDate.of(2026, 4, 5))
                .dataFim(LocalDate.of(2026, 5, 1))
                .descricao("Processo encerrado para testes de listagens e filtros no frontend.")
                .build();
        processo2 = processoRepository.save(processo2);
        UUID processoId2 = processo2.getId();

        // 8. Partes
        Parte parte1 = Parte.builder()
                .processoId(processoId1)
                .nome("João Andrade")
                .tipo("AUTOR")
                .build();
        parteRepository.save(parte1);

        Parte parte2 = Parte.builder()
                .processoId(processoId1)
                .nome("Réu Exemplo")
                .tipo("RÉU")
                .build();
        parteRepository.save(parte2);

        Parte parte3 = Parte.builder()
                .processoId(processoId2)
                .nome("Empresa Atlântico, SA")
                .tipo("EXECUTADO")
                .build();
        parteRepository.save(parte3);

        // 9. Fases Processuais (Catalog)
        List<String> fases = Arrays.asList("Distribuição", "Citação", "Contestação", "Audiência", "Sentença");
        Map<String, FaseProcessual> faseMap = new HashMap<>();
        for (String fName : fases) {
            FaseProcessual f = FaseProcessual.builder().nome(fName).build();
            f = faseProcessualRepository.save(f);
            faseMap.put(fName, f);
        }

        // 10. Processo Fases
        ProcessoFase pf1 = ProcessoFase.builder()
                .processoId(processoId1)
                .faseId(faseMap.get("Distribuição").getId())
                .dataInicio(LocalDate.of(2026, 5, 12))
                .dataFim(LocalDate.of(2026, 5, 12))
                .ativa(false)
                .build();
        processoFaseRepository.save(pf1);

        ProcessoFase pf2 = ProcessoFase.builder()
                .processoId(processoId1)
                .faseId(faseMap.get("Citação").getId())
                .dataInicio(LocalDate.of(2026, 5, 13))
                .ativa(true)
                .build();
        processoFaseRepository.save(pf2);

        ProcessoFase pf3 = ProcessoFase.builder()
                .processoId(processoId2)
                .faseId(faseMap.get("Sentença").getId())
                .dataInicio(LocalDate.of(2026, 4, 5))
                .dataFim(LocalDate.of(2026, 5, 1))
                .ativa(false)
                .build();
        processoFaseRepository.save(pf3);

        // 11. Eventos (Agenda)
        Evento ev1 = Evento.builder()
                .tenantId(tenantId)
                .processoId(processoId1)
                .tipo("PRAZO")
                .titulo("Prazo: apresentar documento")
                .descricao("Submeter os comprovativos físicos de pagamento.")
                .dataInicio(LocalDateTime.of(2026, 5, 28, 9, 0))
                .dataFim(LocalDateTime.of(2026, 5, 28, 10, 0))
                .prioridade("ALTA")
                .concluido(false)
                .build();
        eventoRepository.save(ev1);

        Evento ev2 = Evento.builder()
                .tenantId(tenantId)
                .processoId(processoId1)
                .tipo("AUDIENCIA")
                .titulo("Audiência preliminar")
                .descricao("Comparecer com as partes e documentos de identificação.")
                .dataInicio(LocalDateTime.of(2026, 6, 15, 13, 0))
                .dataFim(LocalDateTime.of(2026, 6, 15, 14, 0))
                .prioridade("MEDIA")
                .concluido(false)
                .build();
        eventoRepository.save(ev2);

        // 12. Honorários
        Honorario h1 = Honorario.builder()
                .processoId(processoId1)
                .valorTotal(new BigDecimal("200000.00"))
                .descricao("Honorários iniciais (exemplo)")
                .dataAcordo(LocalDate.of(2026, 5, 1))
                .build();
        h1 = honorarioRepository.save(h1);

        Honorario h2 = Honorario.builder()
                .processoId(processoId2)
                .valorTotal(new BigDecimal("500000.00"))
                .descricao("Honorários finais (exemplo)")
                .dataAcordo(LocalDate.of(2026, 5, 10))
                .build();
        h2 = honorarioRepository.save(h2);

        // 13. Pagamentos
        Pagamento pag1 = Pagamento.builder()
                .honorarioId(h1.getId())
                .valorPago(new BigDecimal("120000.00"))
                .dataPagamento(LocalDate.of(2026, 5, 3))
                .metodo("TRANSFERENCIA")
                .build();
        pagamentoRepository.save(pag1);

        Pagamento pag2 = Pagamento.builder()
                .honorarioId(h2.getId())
                .valorPago(new BigDecimal("380000.00"))
                .dataPagamento(LocalDate.of(2026, 5, 18))
                .metodo("DINHEIRO")
                .build();
        pagamentoRepository.save(pag2);

        System.out.println("✅ LexCV database successfully seeded with default fixtures.");
    }

    private void seedRbac() {
        List<String> permKeys = Arrays.asList(
                "clientes:view", "clientes:edit",
                "processos:view", "processos:edit",
                "processos:create", "processos:manage",
                "agenda:view", "agenda:edit",
                "documentos:view", "documentos:edit",
                "financeiro:view", "financeiro:edit",
                "rbac:manage", "users:manage",
                "pareceres:view", "pareceres:create", "pareceres:edit", "pareceres:manage"
        );

        Map<String, Permission> permissionMap = new HashMap<>();
        for (String key : permKeys) {
            Permission perm = permissionRepository.findByNome(key)
                    .orElseGet(() -> permissionRepository.save(Permission.builder().nome(key).build()));
            permissionMap.put(key, perm);
        }

        upsertRolePermissions("ADMIN", permissionMap.values());

        upsertRolePermissions("ASSISTENTE", Arrays.asList(
                permissionMap.get("clientes:view"),
                permissionMap.get("clientes:edit"),
                permissionMap.get("processos:view"),
                permissionMap.get("agenda:view"),
                permissionMap.get("documentos:view"),
                permissionMap.get("pareceres:view")
        ));

        upsertRolePermissions("TECNICO", Arrays.asList(
                permissionMap.get("clientes:view"),
                permissionMap.get("processos:view"),
                permissionMap.get("agenda:view"),
                permissionMap.get("agenda:edit"),
                permissionMap.get("documentos:view"),
                permissionMap.get("financeiro:view"),
                permissionMap.get("pareceres:view")
        ));

        upsertRolePermissions("ADVOGADO", Arrays.asList(
                permissionMap.get("clientes:view"),
                permissionMap.get("clientes:edit"),
                permissionMap.get("processos:view"),
                permissionMap.get("processos:edit"),
                permissionMap.get("processos:create"),
                permissionMap.get("processos:manage"),
                permissionMap.get("agenda:view"),
                permissionMap.get("agenda:edit"),
                permissionMap.get("documentos:view"),
                permissionMap.get("documentos:edit"),
                permissionMap.get("financeiro:view"),
                permissionMap.get("pareceres:view"),
                permissionMap.get("pareceres:create"),
                permissionMap.get("pareceres:edit")
        ));
    }

    private void upsertRolePermissions(String roleName, Collection<Permission> permissions) {
        Role role = roleRepository.findByNome(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().nome(roleName).build()));

        boolean changed = role.getPermissions().addAll(permissions);
        if (changed) {
            roleRepository.save(role);
        }
    }
}
