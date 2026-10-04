// Phase 134 (D-10): chave de idempotência do registo de um pagamento faturado, gerada quando o
// diálogo de confirmação abre e reutilizada nas repetições dentro do mesmo diálogo.
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
