package com.lexcv.services;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 128 (128-VERIFICATION.md, item (a)): prova ESTRUTURAL, por reflexao, de que {@link
 * MigracaoPapeisEscritorioService#migrar()} -- a conversao de arranque da Phase 126 -- nunca
 * grava um evento de auditoria RBAC. 128-CONTEXT.md Decisao 4 explica o porque: a conversao nao
 * e uma decisao tomada por uma pessoa, e verificacao de deriva zero ja prova que o acesso de cada
 * utilizador e preservado exactamente; auditar esta conversao inundaria o historico de cada
 * escritorio com eventos que nao correspondem a decisao de ninguem.
 *
 * <p><b>Por que reflexao, e nao {@code verifyNoInteractions}</b> (o padrao ja usado para o caso
 * irmao {@code initializeSystem} em {@code SetupServiceAtribuicaoAdminFundadorTest}): naquele
 * caso {@code AuditoriaRbacService} E injectado em {@code SetupService} -- o teste prova que o
 * caminho {@code initializeSystem} simplesmente nao o chama, apesar de o colaborador existir.
 * Aqui a garantia e mais forte: {@link MigracaoPapeisEscritorioService} nao declara, e nunca
 * declarou, nenhum campo do tipo {@link AuditoriaRbacService} -- nao ha nenhum mock desse tipo
 * disponivel no construtor gerado por {@code @RequiredArgsConstructor} contra o qual correr
 * {@code verifyNoInteractions}. Uma asserção de reflexao sobre os campos declarados da classe e
 * o teste que efectivamente apanha uma regressao: falha no INSTANTE em que um futuro editor
 * acrescentar um campo {@code AuditoriaRbacService} a esta classe, mesmo antes de esse campo ser
 * alguma vez chamado -- o ponto de falha mais cedo possivel para este tipo de regressao, porque
 * a mera presenca do colaborador e o primeiro passo necessario para o voltar a chamar.
 *
 * <p>O Teste 2 (construtor) e um segundo angulo sobre a mesma garantia, mais resiliente a uma
 * eventual mudanca de {@code @RequiredArgsConstructor} para um construtor escrito a mao que
 * declarasse um parametro sem o guardar num campo com o mesmo nome -- um cenario improvavel
 * neste codebase (nenhuma outra classe de servico faz isso), mas barato de cobrir.
 */
class MigracaoPapeisEscritorioServiceAuditoriaTest {

    @Test
    void classeNaoDeclaraNenhumCampoDoTipoAuditoriaRbacService() {
        boolean temCampoDeAuditoria = Arrays.stream(MigracaoPapeisEscritorioService.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(AuditoriaRbacService.class::isAssignableFrom);

        assertFalse(temCampoDeAuditoria,
                "MigracaoPapeisEscritorioService passou a declarar um campo AuditoriaRbacService -- "
                        + "128-CONTEXT.md Decisao 4 exige que a conversao de arranque da Phase 126 nunca "
                        + "grave eventos de auditoria RBAC (nao e uma decisao tomada por uma pessoa). Se "
                        + "este teste falhou, reverter o novo campo, ou reabrir a Decisao 4 explicitamente "
                        + "com o coordenador antes de prosseguir.");
    }

    @Test
    void construtorNaoDeclaraNenhumParametroDoTipoAuditoriaRbacService() {
        Constructor<?>[] construtores = MigracaoPapeisEscritorioService.class.getDeclaredConstructors();

        assertTrue(construtores.length >= 1,
                "MigracaoPapeisEscritorioService deveria ter pelo menos um construtor declarado -- "
                        + "uma pesquisa vazia nao pode passar vacuamente este teste");

        for (Constructor<?> construtor : construtores) {
            boolean temParametroDeAuditoria = Arrays.stream(construtor.getParameterTypes())
                    .anyMatch(AuditoriaRbacService.class::isAssignableFrom);
            assertFalse(temParametroDeAuditoria,
                    "Um construtor de MigracaoPapeisEscritorioService passou a receber "
                            + "AuditoriaRbacService -- ver justificacao no Teste 1 desta classe.");
        }
    }
}
