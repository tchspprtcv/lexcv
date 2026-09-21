package com.lexcv.services;

import java.util.Set;

/**
 * CR-01/WR-04 (126-REVIEW.md): lancada por {@link ResolucaoPapeisService#resolverPapeisDeEscritorio}
 * quando apenas UMA PARTE dos papeis globais pedidos tem {@code TenantRole} homonimo no tenant --
 * nunca quando nenhum papel mapeia (esse continua a ser o caso "sem correspondencia", devolvido
 * como {@link Set#of()} exactamente como antes, um sinal que todos os chamadores ja sabiam
 * interpretar) nem quando todos mapeiam (devolvido o conjunto completo, tambem inalterado).
 *
 * <p>Antes desta excepcao existir, o caso "correspondencia parcial" nao tinha sinal proprio:
 * {@code resolverPapeisDeEscritorio} devolvia silenciosamente um subconjunto, e
 * {@code AdminController.createUser}/{@code updateUser} escreviam esse subconjunto directamente
 * em {@code user.tenantRoles}, respondendo {@code 200}/{@code 201} enquanto a autoridade efectiva
 * do utilizador (resolvida pelo lado de escritorio, que passa a ter dados) ficava
 * silenciosamente incompleta face aos papeis globais realmente pedidos (CR-01). Esta excepcao dá
 * a esse terceiro caso um sinal explicito que um chamador tem de tratar -- nunca pode ser
 * ignorado ou convertido num {@code Set} parcial escrito sem mais.
 */
public class MapeamentoParcialPapeisException extends RuntimeException {

    private final Set<String> papeisSemCorrespondencia;

    public MapeamentoParcialPapeisException(Set<String> papeisSemCorrespondencia) {
        super("Os seguintes papeis nao tem ainda um papel de escritorio correspondente neste "
                + "tenant: " + papeisSemCorrespondencia + ". O catalogo de moldes deste escritorio "
                + "precisa de ser actualizado antes de continuar.");
        this.papeisSemCorrespondencia = papeisSemCorrespondencia;
    }

    public Set<String> getPapeisSemCorrespondencia() {
        return papeisSemCorrespondencia;
    }
}
