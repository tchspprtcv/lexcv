import { toast } from "@/hooks/use-toast";

const apiBasePath = process.env.NEXT_PUBLIC_API_BASE_PATH;
if (!apiBasePath) {
  throw new Error("NEXT_PUBLIC_API_BASE_PATH is required");
}

export const API_BASE = apiBasePath;

// Phase 133 (Fundação Fiscal) -- alteração ADITIVA. O UI-SPEC da aba Faturação precisa de
// mostrar copy inline para 409/422 e de mapear erros 4xx para campos do formulário, o que exige
// conhecer o status HTTP, o `code` e o `campo` devolvidos pelo backend, e poder suprimir o toast
// automático para os status que o ecrã já renderiza inline. Por isso:
// - o erro lançado passa a ser um `ApiError` (subclasse de `Error`), com `status`/`code`/`campo`/
//   `body`; quem faz `instanceof Error` continua a funcionar;
// - a mensagem mantém EXATAMENTE o formato `API <status>: <mensagem>` -- há consumidores que a
//   analisam (ex.: components/profile/user-password-form.tsx faz `replace("API 400: ", "")`);
// - o terceiro parâmetro `opcoes` é opcional, pelo que todas as chamadas existentes se comportam
//   como antes (mesmo toast, mesma mensagem).
//
// Phase 137 (PDF, Armazenamento, Email e Relatório) -- alteração ADITIVA.
// Introduz `apiFetchFicheiro` para descarregar anexos (XML, CSV) através do mesmo caminho de
// credenciais e tratamento de erros do `apiFetch`, extraindo o blob e o nome de ficheiro
// do cabeçalho Content-Disposition quando presente.
export interface ApiErrorDetalhes {
  status: number;
  code?: string;
  campo?: string;
  body?: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly campo?: string;
  readonly body?: unknown;

  constructor(message: string, { status, code, campo, body }: ApiErrorDetalhes) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.campo = campo;
    this.body = body;
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}

export interface ApiFetchOpcoes {
  /** Status HTTP para os quais o toast automático de erro NÃO é mostrado (o ecrã trata inline). */
  semToastParaStatus?: readonly number[];
}

export interface RespostaFicheiro {
  blob: Blob;
  nomeFicheiro: string | null;
}

async function tratarErroResposta(res: Response, opcoes: ApiFetchOpcoes): Promise<never> {
  const text = await res.text().catch(() => "");
  let errorMessage = text || res.statusText || "Falha na comunicação com o servidor.";
  let body: unknown;
  let code: string | undefined;
  let campo: string | undefined;

  try {
    if (text) {
      const json = JSON.parse(text);
      if (json && typeof json === "object") {
        body = json;
        errorMessage = json.message || json.error || errorMessage;
        if (typeof json.code === "string") code = json.code;
        if (typeof json.campo === "string") campo = json.campo;
      }
    }
  } catch {
    // Not a JSON object, use raw text
  }

  // Ignorar toast automático para rotas de auth check (401/403) para evitar spam se a sessão expirar
  // O hook useMe já trata do redirect. Os status em `semToastParaStatus` são tratados inline pelo ecrã.
  const semToast = opcoes.semToastParaStatus?.includes(res.status) ?? false;
  if (res.status !== 401 && res.status !== 403 && !semToast) {
    toast.error(`Erro ${res.status}: ${errorMessage}`);
  }

  throw new ApiError(`API ${res.status}: ${errorMessage}`, {
    status: res.status,
    code,
    campo,
    body,
  });
}

function extrairNomeFicheiro(header: string | null): string | null {
  if (!header) return null;
  // RFC 5987 filename*=UTF-8''encoded_name
  const rfc5987Match = header.match(/filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/i);
  if (rfc5987Match && rfc5987Match[1]) {
    try {
      return decodeURIComponent(rfc5987Match[1].trim());
    } catch {
      return rfc5987Match[1].trim();
    }
  }
  // Standard filename="name" or filename=name
  const standardMatch = header.match(/filename\s*=\s*(?:"([^"]+)"|([^;\s]+))/i);
  if (standardMatch) {
    return (standardMatch[1] || standardMatch[2] || "").trim() || null;
  }
  return null;
}

export async function apiFetch<TResponse>(
  path: string,
  init: RequestInit = {},
  opcoes: ApiFetchOpcoes = {},
): Promise<TResponse> {
  const headers = new Headers(init.headers);

  if (!headers.has("Content-Type") && init.body && !(init.body instanceof FormData)) {
    headers.set("Content-Type", "application/json");
  }

  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });

  if (!res.ok) {
    await tratarErroResposta(res, opcoes);
  }

  if (res.status === 204) {
    return undefined as TResponse;
  }

  return (await res.json()) as TResponse;
}

export async function apiFetchFicheiro(
  path: string,
  init: RequestInit = {},
  opcoes: ApiFetchOpcoes = {},
): Promise<RespostaFicheiro> {
  const headers = new Headers(init.headers);

  const res = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
    credentials: "include",
  });

  if (!res.ok) {
    await tratarErroResposta(res, opcoes);
  }

  const blob = await res.blob();
  const disposition = res.headers.get("Content-Disposition");
  const nomeFicheiro = extrairNomeFicheiro(disposition);

  return { blob, nomeFicheiro };
}
