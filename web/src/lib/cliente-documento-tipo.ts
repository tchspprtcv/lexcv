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

const OPTIONS_BY_TIPO: Record<ClienteTipo, DocumentoTipoOption[]> = {
  PARTICULAR: [
    { value: "CNI", label: "CNI" },
    { value: "BI", label: "BI" },
    { value: "PASSAPORTE", label: "Passaporte" },
  ],
  EMPRESA: [{ value: "REG_COMERCIAL", label: "Registo Comercial" }],
};

/**
 * Devolve as opções de `documento_tipo` válidas para o tipo de cliente dado.
 * Quando `tipo` ainda não foi escolhido, usa o conjunto de Particular como
 * fallback para evitar um dropdown vazio antes da seleção.
 */
export function getDocumentoTipoOptions(
  tipo: ClienteTipo | undefined,
): DocumentoTipoOption[] {
  return OPTIONS_BY_TIPO[tipo ?? "PARTICULAR"];
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
