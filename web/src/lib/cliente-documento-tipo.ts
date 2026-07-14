import type { DocumentoTipo } from "@/types/clientes";

/**
 * Fonte única de verdade para as opções de `documento_tipo` disponíveis por
 * tipo de cliente (Particular vs. Empresa).
 *
 * Particular: CNI, BI ou Passaporte.
 * Empresa: apenas Registo Comercial.
 *
 * Nota: a opção vazia ("Nenhum") é uma decisão de renderização dos
 * formulários (Plan 03) e não pertence a este módulo — aqui apenas os
 * tipos de documento reais.
 */
export interface DocumentoTipoOption {
  value: DocumentoTipo;
  label: string;
}

type ClienteTipo = "PARTICULAR" | "EMPRESA";

/**
 * Fonte única de verdade para o rótulo em português de cada `DocumentoTipo`.
 * Reutilizada por `OPTIONS_BY_TIPO` (dropdowns) e por `getDocumentoTipoLabel`
 * (render read-only), para que um rótulo nunca divirja entre os dois usos.
 */
const DOCUMENTO_TIPO_LABELS: Record<DocumentoTipo, string> = {
  BI: "BI",
  CNI: "CNI",
  PASSAPORTE: "Passaporte",
  REG_COMERCIAL: "Registo Comercial",
};

const OPTIONS_BY_TIPO: Record<ClienteTipo, DocumentoTipoOption[]> = {
  PARTICULAR: [
    { value: "CNI", label: DOCUMENTO_TIPO_LABELS.CNI },
    { value: "BI", label: DOCUMENTO_TIPO_LABELS.BI },
    { value: "PASSAPORTE", label: DOCUMENTO_TIPO_LABELS.PASSAPORTE },
  ],
  EMPRESA: [{ value: "REG_COMERCIAL", label: DOCUMENTO_TIPO_LABELS.REG_COMERCIAL }],
};

/**
 * Traduz um valor de `documento_tipo` guardado (possivelmente legado/desconhecido)
 * para o rótulo em português a apresentar ao utilizador.
 *
 * - `null`/`undefined`/`""` devolve `undefined`, para que o fallback existente do
 *   chamador (`?? "—"` ou `fmt()`) continue a aplicar-se.
 * - Um valor conhecido devolve o rótulo canónico (`DOCUMENTO_TIPO_LABELS`).
 * - Um valor desconhecido/legado (ex.: `NIF`, removido na Phase 74) é devolvido
 *   verbatim — nunca esconder um valor real guardado na base de dados.
 */
export function getDocumentoTipoLabel(
  value: string | null | undefined,
): string | undefined {
  if (!value) return undefined;
  return Object.prototype.hasOwnProperty.call(DOCUMENTO_TIPO_LABELS, value)
    ? DOCUMENTO_TIPO_LABELS[value as DocumentoTipo]
    : value;
}

/**
 * Devolve as opções de `documento_tipo` válidas para o tipo de cliente dado.
 * Quando `tipo` ainda não foi escolhido, usa o conjunto de Particular como
 * fallback para evitar um dropdown vazio antes da seleção.
 */
export function getDocumentoTipoOptions(
  tipo: ClienteTipo | undefined,
): DocumentoTipoOption[] {
  return [...OPTIONS_BY_TIPO[tipo ?? "PARTICULAR"]];
}

/**
 * Converte um valor de origem externa (ex.: input do utilizador) num
 * `DocumentoTipo` válido, apenas se pertencer ao conjunto permitido para o
 * tipo de cliente dado. Devolve `undefined` caso contrário.
 */
export function toDocumentoTipo(
  value: string | undefined,
  tipo: ClienteTipo | undefined,
): DocumentoTipo | undefined {
  if (!value) return undefined;
  const options = getDocumentoTipoOptions(tipo);
  return options.some((option) => option.value === value)
    ? (value as DocumentoTipo)
    : undefined;
}
