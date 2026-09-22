package com.lexcv.services;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.models.Role;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.SystemSettingRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.TenantRoleRepository;
import com.lexcv.repositories.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 128 (128-VERIFICATION.md, item (b)): prova, sob um {@link TransactionInterceptor} REAL
 * (nunca um mock que so verifica "foi chamado" -- mesma tecnica de {@code RecusaTransacionalTest}
 * e {@code OfficeRolesControllerAuditoriaTest}), de que uma falha em {@code
 * AuditoriaRbacService.registarAtribuicoes} DENTRO de {@code SetupService.provisionTenant} faz a
 * transaccao inteira rebobinar -- nunca um commit parcial com a tenant/utilizador ja gravados mas
 * o evento de auditoria em falta.
 *
 * <p>Os testes existentes ({@code SetupServiceProvisionTenantTest}, mockando os repositorios
 * directamente sem nenhum {@link PlatformTransactionManager} real em jogo) nao conseguem observar
 * rollback transacional -- so podem confirmar que {@code registarAtribuicoes} foi chamado com os
 * argumentos certos. O unico teste la que faz um colaborador lancar excepcao durante {@code
 * provisionTenant} ({@code SetupServiceInstanciacaoMoldesTest.instanciarMoldes_quandoSaveFalha_...})
 * exercita um ponto de falha DIFERENTE ({@code tenantRoleRepository.save}), nao a escrita do
 * evento de auditoria. Este ficheiro fecha especificamente esse gap.
 */
@ExtendWith(MockitoExtension.class)
class SetupServiceProvisionTenantTransacaoTest {

    @Mock private SystemSettingRepository systemSettingRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TenantRoleRepository tenantRoleRepository;
    @Mock private AuditoriaRbacService auditoriaRbacService;
    @Mock private PlatformTransactionManager txManager;

    private SetupService novoServico() {
        return new SetupService(
                systemSettingRepository, tenantRepository, userRepository, roleRepository, passwordEncoder,
                tenantRoleRepository, auditoriaRbacService);
    }

    /**
     * Copia exacta da tecnica de {@code RecusaTransacionalTest.criarProxyComTransacaoReal()} e
     * {@code OfficeRolesControllerAuditoriaTest.novoProxyComTransacaoReal()}: um {@link
     * TransactionInterceptor} verdadeiro, avaliando de facto a anotacao {@code @Transactional} de
     * cada metodo via {@link AnnotationTransactionAttributeSource}, sobre um {@link
     * PlatformTransactionManager} mockado que devolve um {@link SimpleTransactionStatus} real.
     */
    private SetupService novoProxyComTransacaoReal() {
        ProxyFactory factory = new ProxyFactory(novoServico());
        factory.setProxyTargetClass(true);
        TransactionInterceptor interceptor = new TransactionInterceptor(
                (TransactionManager) txManager, new AnnotationTransactionAttributeSource());
        factory.addAdvice(interceptor);
        return (SetupService) factory.getProxy();
    }

    private SetupInitializeRequest requestValido(String clientName, String adminEmail, String adminPassword) {
        SetupInitializeRequest request = new SetupInitializeRequest();
        request.setClientName(clientName);
        request.setAdminEmail(adminEmail);
        request.setAdminPassword(adminPassword);
        request.setLogo(null);
        return request;
    }

    private void stubTenantSaveComIdGerado() {
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });
    }

    // Caso 1 (O TESTE QUE FECHA O GAP) -- registarAtribuicoes lanca durante provisionTenant: a
    // excepcao tem de propagar para fora do proxy, e o TransactionInterceptor tem de chamar
    // rollback sobre a MESMA transaccao que tinha aberto para tenant+utilizador -- nunca commit.
    @Test
    void provisionTenant_eventoDeAuditoriaFalha_rebobinaTodoOProvisionamentoENuncaComita() {
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);

        Role adminRoleGlobal = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRoleGlobal));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(adminRoleGlobal));
        when(userRepository.findByEmail("falharollback@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("falha ao gravar evento de auditoria"))
                .when(auditoriaRbacService).registarAtribuicoes(any(), any(), any(), any(), any(), any());

        SetupInitializeRequest request =
                requestValido("Escritorio Rollback", "falharollback@escritorio.cv", "Pa$$w0rd");
        SetupService proxy = novoProxyComTransacaoReal();

        assertThrows(RuntimeException.class, () -> proxy.provisionTenant(request));

        verify(txManager).rollback(status);
        verify(txManager, never()).commit(any());
    }

    // Caso 2 (controlo positivo da propria tecnica) -- sem falha nenhuma, a mesma transaccao real
    // comita normalmente e nunca fica rollback-only. Sem este caso, um proxy mal configurado que
    // nunca chegasse a invocar o metodo real passaria o Caso 1 vacuamente (rollback() nunca seria
    // chamado por NENHUM motivo). Prova tambem, de caminho, que provisionTenant continua
    // @Transactional (o interceptor so actua sobre metodos anotados).
    @Test
    void provisionTenant_semFalha_comitaATransaccaoComTodasAsEscritas() {
        SimpleTransactionStatus status = new SimpleTransactionStatus();
        when(txManager.getTransaction(any())).thenReturn(status);

        Role adminRoleGlobal = Role.builder().id(1).nome("ADMIN").permissions(new HashSet<>()).build();
        when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(adminRoleGlobal));
        when(roleRepository.findAllByInstanciavelTrue()).thenReturn(List.of(adminRoleGlobal));
        when(userRepository.findByEmail("semfalharollback@escritorio.cv")).thenReturn(Optional.empty());
        stubTenantSaveComIdGerado();
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        SetupInitializeRequest request =
                requestValido("Escritorio Sem Falha", "semfalharollback@escritorio.cv", "Pa$$w0rd");
        SetupService proxy = novoProxyComTransacaoReal();

        Tenant resultado = proxy.provisionTenant(request);

        assertEquals("Escritorio Sem Falha", resultado.getNome());
        verify(auditoriaRbacService).registarAtribuicoes(any(), any(), any(), any(), any(), any());
        verify(txManager).commit(status);
        verify(txManager, never()).rollback(any());
        assertFalse(status.isRollbackOnly());
    }
}
