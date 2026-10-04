import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@/hooks/use-toast", () => ({ toast: { error: vi.fn() } }));

import { toast } from "@/hooks/use-toast";
import { ApiError, apiFetch, isApiError } from "@/lib/api";

const fetchMock = vi.fn();

function responder(body: string | null, status: number, contentType = "application/json") {
  fetchMock.mockResolvedValueOnce(
    new Response(body, { status, headers: body === null ? undefined : { "Content-Type": contentType } }),
  );
}

async function capturarErro(promessa: Promise<unknown>): Promise<unknown> {
  try {
    await promessa;
  } catch (e) {
    return e;
  }
  throw new Error("esperava-se uma rejeição");
}

describe("apiFetch", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  it("409 com code e campo devolve ApiError e mostra toast", async () => {
    const body = { message: "Conflito", code: "NIF_BLOQUEADO", campo: "nif" };
    responder(JSON.stringify(body), 409);

    const erro = await capturarErro(apiFetch("/faturacao/configuracao"));

    expect(erro).toBeInstanceOf(Error);
    expect(erro).toBeInstanceOf(ApiError);
    const apiErro = erro as ApiError;
    expect(apiErro.message).toBe("API 409: Conflito");
    expect(apiErro.status).toBe(409);
    expect(apiErro.code).toBe("NIF_BLOQUEADO");
    expect(apiErro.campo).toBe("nif");
    expect(apiErro.body).toEqual(body);
    expect(toast.error).toHaveBeenCalledTimes(1);
    expect(toast.error).toHaveBeenCalledWith("Erro 409: Conflito");
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/v1/faturacao/configuracao",
      expect.objectContaining({ credentials: "include" }),
    );
  });

  it("semToastParaStatus suprime o toast para o status indicado", async () => {
    const body = { message: "Conflito", code: "NIF_BLOQUEADO", campo: "nif" };
    responder(JSON.stringify(body), 409);

    const erro = await capturarErro(apiFetch("/x", {}, { semToastParaStatus: [409] }));

    expect(erro).toBeInstanceOf(ApiError);
    expect((erro as ApiError).message).toBe("API 409: Conflito");
    expect((erro as ApiError).code).toBe("NIF_BLOQUEADO");
    expect(toast.error).not.toHaveBeenCalled();
  });

  it("semToastParaStatus não suprime outros status", async () => {
    responder(JSON.stringify({ message: "Falha" }), 500);

    await capturarErro(apiFetch("/x", {}, { semToastParaStatus: [409] }));

    expect(toast.error).toHaveBeenCalledWith("Erro 500: Falha");
  });

  it("400 de bean validation mantém o formato da mensagem com o texto bruto", async () => {
    const text = JSON.stringify({ nif: "O NIF deve ter 9 dígitos..." });
    responder(text, 400);

    const erro = (await capturarErro(apiFetch("/x"))) as ApiError;

    expect(erro).toBeInstanceOf(ApiError);
    expect(erro.status).toBe(400);
    expect(erro.code).toBeUndefined();
    expect(erro.campo).toBeUndefined();
    expect(erro.body).toEqual({ nif: "O NIF deve ter 9 dígitos..." });
    expect(erro.message).toBe(`API 400: ${text}`);
  });

  it("code e campo não-string são ignorados", async () => {
    responder(JSON.stringify({ message: "m", code: 5, campo: { a: 1 } }), 422);

    const erro = (await capturarErro(apiFetch("/x"))) as ApiError;

    expect(erro.code).toBeUndefined();
    expect(erro.campo).toBeUndefined();
  });

  it.each([401, 403])("%i não mostra toast", async (status) => {
    responder(JSON.stringify({ message: "Sem acesso" }), status);

    const erro = (await capturarErro(apiFetch("/x"))) as ApiError;

    expect(erro.status).toBe(status);
    expect(erro.message).toBe(`API ${status}: Sem acesso`);
    expect(toast.error).not.toHaveBeenCalled();
  });

  it("500 com texto não-JSON usa o texto bruto", async () => {
    responder("Internal boom", 500, "text/plain");

    const erro = (await capturarErro(apiFetch("/x"))) as ApiError;

    expect(erro).toBeInstanceOf(ApiError);
    expect(erro.message).toBe("API 500: Internal boom");
    expect(erro.code).toBeUndefined();
    expect(erro.body).toBeUndefined();
    expect(toast.error).toHaveBeenCalledWith("Erro 500: Internal boom");
  });

  it("204 resolve undefined", async () => {
    responder(null, 204);

    await expect(apiFetch("/x", { method: "POST" })).resolves.toBeUndefined();
  });

  it("200 JSON resolve o corpo", async () => {
    responder(JSON.stringify({ ok: true }), 200);

    await expect(apiFetch<{ ok: boolean }>("/x")).resolves.toEqual({ ok: true });
  });
});

describe("isApiError", () => {
  it("distingue ApiError de Error", () => {
    expect(isApiError(new ApiError("API 400: x", { status: 400 }))).toBe(true);
    expect(isApiError(new Error("x"))).toBe(false);
    expect(isApiError("x")).toBe(false);
  });
});
