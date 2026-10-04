// Phase 134 (D-10): chave de idempotência do registo de um pagamento faturado, gerada quando o
// diálogo de confirmação abre e reutilizada nas repetições do mesmo pedido (ver o ciclo de vida
// mais abaixo: sobrevive ao fecho do diálogo enquanto o desfecho estiver por resolver).
//
// `crypto.randomUUID()` só existe em contextos seguros (HTTPS ou localhost). O Caddyfile de
// desenvolvimento serve em :80 por http simples, e um acesso pela LAN (http://192.168.x.y) não é
// contexto seguro -- aí `randomUUID` não existe. `crypto.getRandomValues` existe em qualquer
// contexto, por isso o fallback monta um UUID v4 (RFC 9562) a partir de 16 bytes aleatórios.

function uuidV4DeBytes(bytes: Uint8Array): string {
  bytes[6] = (bytes[6] & 0x0f) | 0x40; // versão 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80; // variante RFC
  const hex = Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20, 32)}`;
}

export function gerarChaveIdempotencia(): string {
  const c: Partial<Crypto> | undefined = globalThis.crypto;
  if (c && typeof c.randomUUID === "function") {
    return c.randomUUID();
  }
  if (c && typeof c.getRandomValues === "function") {
    return uuidV4DeBytes(c.getRandomValues(new Uint8Array(16)));
  }
  throw new Error("Não foi possível gerar a chave do pedido: o navegador não disponibiliza crypto.");
}

// ---------------------------------------------------------------------------------------------
// Ciclo de vida da chave (CR-02 da revisão da Phase 134).
//
// A chave pertence ao PEDIDO (o payload), não à instância do diálogo. Depois de uma falha ambígua
// (5xx, 504 do proxy, rede em baixo), o backend pode ter feito commit sem que o cliente o saiba;
// se o utilizador fechar o diálogo ("Voltar e editar", Esc, X) e voltar a submeter os MESMOS
// valores, tem de ir a mesma chave, para o backend devolver o resultado guardado em vez de emitir
// uma segunda Fatura-Recibo, que não se pode anular. A chave só é descartada depois de um
// desfecho definitivo (sucesso ou recusa 4xx) ou quando o pedido muda.

export interface TentativaEmissao {
  /** Forma canónica do pedido (sem a chave) a que esta chave pertence. */
  readonly pedido: string;
  readonly chave: string;
  /** `true` depois de uma falha ambígua: o desfecho do pedido com esta chave é desconhecido. */
  readonly porResolver: boolean;
}

/** JSON com as chaves dos objetos ordenadas; `undefined` omitido, como no `JSON.stringify`. */
export function pedidoCanonico(pedido: unknown): string {
  return JSON.stringify(pedido, (_k, v: unknown) => {
    if (v && typeof v === "object" && !Array.isArray(v)) {
      const o = v as Record<string, unknown>;
      return Object.fromEntries(
        Object.keys(o)
          .sort()
          .map((k) => [k, o[k]]),
      );
    }
    return v;
  });
}

/**
 * Tentativa a usar para `pedido`: reutiliza a chave da anterior se o desfecho dela ainda está por
 * resolver e o pedido é o mesmo; caso contrário gera uma chave nova. O estado `porResolver` é
 * mantido, porque o desfecho continua desconhecido até a nova tentativa terminar.
 */
export function tentativaParaPedido(
  anterior: TentativaEmissao | null,
  pedido: unknown,
  gerar: () => string = gerarChaveIdempotencia,
): TentativaEmissao {
  const canonico = pedidoCanonico(pedido);
  if (anterior && anterior.porResolver && anterior.pedido === canonico) {
    return anterior;
  }
  return { pedido: canonico, chave: gerar(), porResolver: false };
}

/** Falha ambígua (rede/5xx): a chave tem de sobreviver ao fecho do diálogo. */
export function marcarPorResolver(tentativa: TentativaEmissao | null): TentativaEmissao | null {
  return tentativa ? { ...tentativa, porResolver: true } : null;
}
