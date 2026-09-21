package com.lexcv.services;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Phase 126 (MIGR-02): a rede de seguranca da conversao de papeis existentes -- compara, por
 * utilizador, o conjunto de permissoes efectivas ANTES e DEPOIS da conversao para papeis de
 * escritorio, e aborta com nomes se qualquer divergencia for encontrada. E a unica coisa que
 * separa esta fase de uma alteracao silenciosa de quem ve dados de clientes e financeiros
 * (126-CONTEXT.md).
 *
 * <p>As tres parcelas da uniao real de permissoes -- permissoes dos papeis, permissoes directas
 * do utilizador, e o bloco ADMIN acrescentado por codigo -- sao obtidas chamando
 * o metodo estatico {@code create} de {@link UserPrincipal} em ambos os lados da comparacao, NUNCA reescrevendo a lista de 20
 * permissoes do bloco ADMIN. Isto garante as tres parcelas por construcao: uma alteracao futura
 * ao bloco ADMIN fica automaticamente coberta, sem duplicacao capaz de divergir.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificacaoDerivaPapeisService {

    private final ResolucaoPapeisService resolucaoPapeisService;

    /**
     * Snapshot imutavel do estado efectivo de um utilizador (nomes de papeis + permissoes),
     * obtido via o metodo estatico {@code create} de {@link UserPrincipal}.
     */
    public static final class EstadoEfectivo {
        private final Set<String> papeis;
        private final Set<String> permissoes;

        public EstadoEfectivo(Set<String> papeis, Set<String> permissoes) {
            this.papeis = Set.copyOf(papeis);
            this.permissoes = Set.copyOf(permissoes);
        }

        public Set<String> getPapeis() {
            return papeis;
        }

        public Set<String> getPermissoes() {
            return permissoes;
        }
    }

    /**
     * Computa, por utilizador e ANTES de qualquer mutacao, o estado efectivo actual --
     * estritamente a partir dos papeis GLOBAIS (a forma exacta de
     * JwtAuthenticationFilter:60-67/69), NUNCA via {@link ResolucaoPapeisService}.
     *
     * <p>Esta e a UNICA duplicacao deliberada de toda a fase, e este comentario existe para que
     * uma revisao futura nao a "limpe": este metodo nao pode usar
     * {@code ResolucaoPapeisService}, porque o resolvedor escolhe o caminho de escritorio quando
     * ja existem {@code TenantRole}s -- e o ponto inteiro deste metodo e medir o mundo ANTES da
     * conversao, que e por definicao o mundo dos papeis globais. Se este metodo passasse pelo
     * resolvedor, mediria o "depois" duas vezes e daria luz verde a qualquer migracao, mesmo uma
     * incorrecta.
     */
    public Map<UUID, EstadoEfectivo> capturarAntes(Collection<User> utilizadores) {
        Map<UUID, EstadoEfectivo> antes = new HashMap<>();
        for (User user : utilizadores) {
            Set<String> nomesGlobais = user.getRoles().stream()
                    .map(Role::getNome)
                    .collect(Collectors.toSet());

            Set<String> permissoesGlobais = user.getRoles().stream()
                    .flatMap(r -> r.getPermissions().stream())
                    .map(Permission::getNome)
                    .collect(Collectors.toSet());
            permissoesGlobais.addAll(user.getPermissions());

            // Phase 127 (PAPEL-04): Set.of() aqui, nao resolverMoldeIds -- este metodo mede o
            // mundo ANTES da conversao (ver o comentario acima do metodo), que por definicao nao
            // tem proveniencia de escritorio a medir, e nao pode passar por ResolucaoPapeisService
            // pela mesma razao que o resto do metodo nao passa. Nao e um esquecimento.
            UserPrincipal principal = UserPrincipal.create(
                    user.getId(),
                    user.getTenantId(),
                    user.getNome(),
                    user.getEmail(),
                    nomesGlobais,
                    permissoesGlobais,
                    Set.of());

            antes.put(user.getId(), new EstadoEfectivo(principal.getRoles(), principal.getPermissions()));
        }
        return antes;
    }

    /**
     * Recomputa o estado efectivo de cada utilizador em {@code depois} pelo caminho NOVO
     * (via {@link ResolucaoPapeisService}, seguido do mesmo metodo estatico {@code create} de {@link UserPrincipal} para
     * ganhar a parcela 3), e compara com o snapshot correspondente em {@code antes}. Acumula
     * TODAS as divergencias antes de falhar -- um operador precisa da lista completa para
     * diagnosticar, nao apenas a primeira. Um utilizador presente em {@code depois} e ausente de
     * {@code antes} e ele proprio uma divergencia (o conjunto de utilizadores mudou durante a
     * conversao) e nunca e ignorado silenciosamente.
     *
     * <p>Nunca devolve um booleano: perante qualquer divergencia, lanca
     * {@link IllegalStateException} cuja mensagem contem a contagem de utilizadores afectados e
     * os seus emails, e regista em {@code log.error} cada divergencia nomeando o utilizador e as
     * permissoes/papeis perdidos e ganhos (diferenca simetrica, dos dois lados explicitamente
     * nomeados). Se nao houver divergencias, regista em {@code log.info} quantos utilizadores
     * foram verificados -- um zero silencioso e indistinguivel de "a verificacao nao correu", e
     * essa ambiguidade e inaceitavel num gate de autorizacao.
     */
    public void verificarSemDeriva(Map<UUID, EstadoEfectivo> antes, Collection<User> depois) {
        List<String> emailsDivergentes = new ArrayList<>();
        List<String> mensagensDivergencia = new ArrayList<>();

        for (User user : depois) {
            EstadoEfectivo estadoAntes = antes.get(user.getId());

            if (estadoAntes == null) {
                String msg = "Utilizador " + user.getEmail()
                        + " presente APOS a conversao mas AUSENTE da captura anterior -- "
                        + "o conjunto de utilizadores mudou durante a conversao.";
                log.error(msg);
                emailsDivergentes.add(user.getEmail());
                mensagensDivergencia.add(msg);
                continue;
            }

            Set<String> nomesDepois = resolucaoPapeisService.resolverNomesPapeis(user);
            Set<String> permissoesDepois = resolucaoPapeisService.resolverPermissoesEfectivas(user);
            Set<Integer> moldeIdsDepois = resolucaoPapeisService.resolverMoldeIds(user);

            UserPrincipal principalDepois = UserPrincipal.create(
                    user.getId(),
                    user.getTenantId(),
                    user.getNome(),
                    user.getEmail(),
                    nomesDepois,
                    permissoesDepois,
                    moldeIdsDepois);

            Set<String> papeisPerdidos = diferenca(estadoAntes.getPapeis(), principalDepois.getRoles());
            Set<String> papeisGanhos = diferenca(principalDepois.getRoles(), estadoAntes.getPapeis());
            Set<String> permissoesPerdidas = diferenca(estadoAntes.getPermissoes(), principalDepois.getPermissions());
            Set<String> permissoesGanhas = diferenca(principalDepois.getPermissions(), estadoAntes.getPermissoes());

            boolean divergiu = !papeisPerdidos.isEmpty() || !papeisGanhos.isEmpty()
                    || !permissoesPerdidas.isEmpty() || !permissoesGanhas.isEmpty();

            if (divergiu) {
                String msg = String.format(
                        "Deriva de autoridade detectada para %s -- papeis perdidos: %s, papeis ganhos: %s, "
                                + "permissoes perdidas: %s, permissoes ganhas: %s",
                        user.getEmail(), papeisPerdidos, papeisGanhos, permissoesPerdidas, permissoesGanhas);
                log.error(msg);
                emailsDivergentes.add(user.getEmail());
                mensagensDivergencia.add(msg);
            }
        }

        if (!mensagensDivergencia.isEmpty()) {
            throw new IllegalStateException(
                    mensagensDivergencia.size() + " utilizador(es) com deriva de autoridade detectada apos a "
                            + "conversao -- emails afectados: " + emailsDivergentes + ". Detalhes: "
                            + String.join(" | ", mensagensDivergencia));
        }

        log.info("Verificacao de deriva zero concluida sem divergencias para {} utilizador(es).", depois.size());
    }

    private Set<String> diferenca(Set<String> ladoA, Set<String> ladoB) {
        Set<String> resultado = new HashSet<>(ladoA);
        resultado.removeAll(ladoB);
        return resultado;
    }
}
