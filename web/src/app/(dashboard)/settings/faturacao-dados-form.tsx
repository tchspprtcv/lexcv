"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import type { UseQueryResult } from "@tanstack/react-query";
import { Lock } from "lucide-react";
import { useForm, useWatch } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { Skeleton } from "@/components/ui/skeleton";
import {
  mensagemErroFaturacao,
  useGuardarConfiguracaoFiscal,
  useMotivosIsencao,
} from "@/hooks/use-faturacao";
import { toast } from "@/hooks/use-toast";
import { isApiError } from "@/lib/api";
import {
  configuracaoFiscalSchema,
  MORADA_MAX,
  type ConfiguracaoFiscalFormInput,
  type ConfiguracaoFiscalFormValues,
} from "@/schemas/faturacao";
import type { ConfiguracaoFiscal, ConfiguracaoFiscalPayload } from "@/types/faturacao";

// Card "Dados fiscais do escritório" (133-UI-SPEC Block 3, CFG-01/CFG-02). O schema Zod é um
// espelho de UX; a autoridade é a validação do backend (incl. NIF_BLOQUEADO -- o campo NIF
// desativado aqui é apenas cosmético). A lista de motivos de isenção vem SEMPRE do backend
// (fonte única dos códigos oficiais) -- nunca é codificada neste ficheiro.

const ERRO_GUARDAR =
  "Não foi possível guardar os dados fiscais. Verifique os campos assinalados e tente novamente.";
const NIF_BLOQUEADO_MSG = "O NIF não pode ser alterado depois de emitido o primeiro documento.";

const CAMPOS_FORM = [
  "nif",
  "firma",
  "morada",
  "localidade",
  "emailContacto",
  "telefoneContacto",
  "regimeIva",
  "motivoIsencaoCodigo",
] as const satisfies readonly (keyof ConfiguracaoFiscalFormInput)[];

type CampoForm = (typeof CAMPOS_FORM)[number];

function isCampoForm(campo: string): campo is CampoForm {
  return (CAMPOS_FORM as readonly string[]).includes(campo);
}

function valoresIniciais(data: ConfiguracaoFiscal | undefined): ConfiguracaoFiscalFormInput {
  return {
    nif: data?.nif ?? "",
    firma: data?.firma ?? "",
    morada: data?.morada ?? "",
    localidade: data?.localidade ?? "",
    emailContacto: data?.emailContacto ?? "",
    telefoneContacto: data?.telefoneContacto ?? "",
    regimeIva: data?.regimeIva ?? "NORMAL",
    motivoIsencaoCodigo: data?.motivoIsencaoCodigo ?? null,
  };
}

/** Junta os ids não vazios para `aria-describedby` (helper e/ou erro do campo). */
function descritoPor(...ids: (string | false | undefined)[]) {
  const validos = ids.filter(Boolean);
  return validos.length ? validos.join(" ") : undefined;
}

export function FaturacaoDadosForm({
  configuracao,
  onAlteracoesPorGravarChange,
}: {
  configuracao: Pick<UseQueryResult<ConfiguracaoFiscal>, "data" | "isPending" | "isError">;
  onAlteracoesPorGravarChange?: (porGravar: boolean) => void;
}) {
  const { data } = configuracao;
  const motivos = useMotivosIsencao(true);
  const guardar = useGuardarConfiguracaoFiscal();
  const [erroGuardar, setErroGuardar] = React.useState<string | null>(null);

  const form = useForm<ConfiguracaoFiscalFormInput, unknown, ConfiguracaoFiscalFormValues>({
    resolver: zodResolver(configuracaoFiscalSchema),
    mode: "onTouched",
    defaultValues: valoresIniciais(data),
  });

  const { errors, isDirty, isSubmitting } = form.formState;

  // A resposta gravada (ou um refetch com dados novos) passa a ser o novo estado pristino, mas
  // SEM apagar edições em curso (WR-03 da revisão): outra mutação (email, desativar) ou um
  // refetch ao focar a janela muda a referência de `data`, e um reset simples deitaria fora o
  // que o utilizador ainda não gravou. `keepDirtyValues` só atualiza os campos não editados.
  // Exceção: com o NIF bloqueado o campo deixa de ser editável, por isso passa a mostrar sempre o
  // valor do servidor (nunca um NIF editado que o backend recusaria com NIF_BLOQUEADO).
  React.useEffect(() => {
    if (!data) return;
    form.reset(valoresIniciais(data), { keepDirtyValues: true });
    if (data.nifBloqueado) form.resetField("nif", { defaultValue: data.nif ?? "" });
  }, [data, form]);

  React.useEffect(() => {
    onAlteracoesPorGravarChange?.(isDirty);
  }, [isDirty, onAlteracoesPorGravarChange]);

  const regimeIva = useWatch({ control: form.control, name: "regimeIva" });
  const motivoIsencaoCodigo = useWatch({ control: form.control, name: "motivoIsencaoCodigo" });
  const morada = useWatch({ control: form.control, name: "morada" }) ?? "";
  const nifBloqueado = data?.nifBloqueado ?? false;

  const onSubmit = async (values: ConfiguracaoFiscalFormValues) => {
    setErroGuardar(null);
    try {
      const gravada = await guardar.mutateAsync(values as ConfiguracaoFiscalPayload);
      form.reset(valoresIniciais(gravada));
      toast.success("Dados fiscais guardados.");
    } catch (error) {
      const erro = mensagemErroFaturacao(error);
      if (erro.codigo === "NIF_BLOQUEADO") {
        form.setError("nif", { type: "server", message: NIF_BLOQUEADO_MSG });
      } else if (erro.campo && isCampoForm(erro.campo)) {
        form.setError(erro.campo, { type: "server", message: erro.mensagem ?? ERRO_GUARDAR });
      }
      if (erro.camposValidacao) {
        for (const [campo, mensagem] of Object.entries(erro.camposValidacao)) {
          if (isCampoForm(campo)) form.setError(campo, { type: "server", message: mensagem });
        }
      }
      // 400/409/422 não são toastados pelo apiFetch (semToastParaStatus) -- renderizados aqui.
      // Os restantes status já foram toastados pelo apiFetch: não duplicar.
      if (isApiError(error) && [400, 409, 422].includes(error.status)) {
        setErroGuardar(ERRO_GUARDAR);
      }
    }
  };

  const regimeRegisto = form.register("regimeIva", {
    onChange: (event: React.ChangeEvent<HTMLSelectElement>) => {
      if (event.target.value === "NORMAL") {
        form.setValue("motivoIsencaoCodigo", null, { shouldDirty: true });
        form.clearErrors("motivoIsencaoCodigo");
      }
    },
  });

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl font-semibold">Dados fiscais do escritório</CardTitle>
      </CardHeader>

      {configuracao.isPending ? (
        <CardContent>
          <div className="grid gap-4 sm:grid-cols-2" aria-label="A carregar...">
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
            <Skeleton className="h-9 w-full" />
          </div>
        </CardContent>
      ) : configuracao.isError ? (
        <CardContent>
          <div className="text-sm text-red-600">
            Não foi possível carregar os dados fiscais. Tente novamente.
          </div>
        </CardContent>
      ) : (
        <form noValidate onSubmit={form.handleSubmit(onSubmit)}>
          <CardContent className="space-y-4">
            <div className="grid gap-4 sm:grid-cols-2">
              {/* NIF */}
              <div className="space-y-2">
                <Label htmlFor="fiscal-nif">NIF</Label>
                {nifBloqueado ? (
                  <div className="relative">
                    <Input
                      id="fiscal-nif"
                      value={data?.nif ?? ""}
                      disabled
                      readOnly
                      className="bg-slate-50 pr-9 font-mono dark:bg-slate-900"
                      aria-invalid={!!errors.nif}
                      aria-describedby={descritoPor(
                        "fiscal-nif-helper",
                        errors.nif && "fiscal-nif-erro",
                      )}
                    />
                    <Lock
                      className="pointer-events-none absolute top-1/2 right-3 h-4 w-4 -translate-y-1/2 text-slate-500 dark:text-slate-400"
                      aria-hidden="true"
                    />
                  </div>
                ) : (
                  <Input
                    id="fiscal-nif"
                    inputMode="numeric"
                    maxLength={9}
                    autoComplete="off"
                    className="font-mono"
                    aria-invalid={!!errors.nif}
                    aria-describedby={descritoPor(
                      "fiscal-nif-helper",
                      errors.nif && "fiscal-nif-erro",
                    )}
                    {...form.register("nif")}
                  />
                )}
                <p id="fiscal-nif-helper" className="text-xs text-slate-500 dark:text-slate-400">
                  {nifBloqueado
                    ? NIF_BLOQUEADO_MSG
                    : "O NIF tem 9 dígitos e começa por um algarismo de 1 a 9."}
                </p>
                {errors.nif ? (
                  <p id="fiscal-nif-erro" className="text-sm text-red-600">
                    {errors.nif.message}
                  </p>
                ) : null}
              </div>

              {/* País (fixo, não submetido) */}
              <div className="space-y-2">
                <Label htmlFor="fiscal-pais">País</Label>
                <Input id="fiscal-pais" value="Cabo Verde" disabled readOnly />
              </div>

              {/* Firma */}
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="fiscal-firma">Firma</Label>
                <Input
                  id="fiscal-firma"
                  autoComplete="organization"
                  aria-invalid={!!errors.firma}
                  aria-describedby={descritoPor(errors.firma && "fiscal-firma-erro")}
                  {...form.register("firma")}
                />
                {errors.firma ? (
                  <p id="fiscal-firma-erro" className="text-sm text-red-600">
                    {errors.firma.message}
                  </p>
                ) : null}
              </div>

              {/* Morada */}
              <div className="space-y-2 sm:col-span-2">
                <Label htmlFor="fiscal-morada">Morada</Label>
                <Input
                  id="fiscal-morada"
                  autoComplete="street-address"
                  aria-invalid={!!errors.morada}
                  aria-describedby={descritoPor(
                    "fiscal-morada-helper",
                    errors.morada && "fiscal-morada-erro",
                  )}
                  {...form.register("morada")}
                />
                <div
                  id="fiscal-morada-helper"
                  className="flex justify-between gap-2 text-xs text-slate-500 dark:text-slate-400"
                >
                  <span>Máximo de 100 caracteres.</span>
                  <span className="tabular-nums">
                    {morada.length}/{MORADA_MAX}
                  </span>
                </div>
                {errors.morada ? (
                  <p id="fiscal-morada-erro" className="text-sm text-red-600">
                    {errors.morada.message}
                  </p>
                ) : null}
              </div>

              {/* Localidade */}
              <div className="space-y-2">
                <Label htmlFor="fiscal-localidade">Localidade</Label>
                <Input
                  id="fiscal-localidade"
                  autoComplete="address-level2"
                  aria-invalid={!!errors.localidade}
                  aria-describedby={descritoPor(errors.localidade && "fiscal-localidade-erro")}
                  {...form.register("localidade")}
                />
                {errors.localidade ? (
                  <p id="fiscal-localidade-erro" className="text-sm text-red-600">
                    {errors.localidade.message}
                  </p>
                ) : null}
              </div>

              {/* Email de contacto */}
              <div className="space-y-2">
                <Label htmlFor="fiscal-email">Email de contacto</Label>
                <Input
                  id="fiscal-email"
                  type="email"
                  autoComplete="email"
                  aria-invalid={!!errors.emailContacto}
                  aria-describedby={descritoPor(errors.emailContacto && "fiscal-email-erro")}
                  {...form.register("emailContacto")}
                />
                {errors.emailContacto ? (
                  <p id="fiscal-email-erro" className="text-sm text-red-600">
                    {errors.emailContacto.message}
                  </p>
                ) : null}
              </div>

              {/* Telefone de contacto */}
              <div className="space-y-2">
                <Label htmlFor="fiscal-telefone">Telefone de contacto</Label>
                <Input
                  id="fiscal-telefone"
                  type="tel"
                  autoComplete="tel"
                  aria-invalid={!!errors.telefoneContacto}
                  aria-describedby={descritoPor(
                    errors.telefoneContacto && "fiscal-telefone-erro",
                  )}
                  {...form.register("telefoneContacto")}
                />
                {errors.telefoneContacto ? (
                  <p id="fiscal-telefone-erro" className="text-sm text-red-600">
                    {errors.telefoneContacto.message}
                  </p>
                ) : null}
              </div>

              {/* Regime de IVA */}
              <div className="space-y-2">
                <Label htmlFor="fiscal-regime">Regime de IVA</Label>
                <NativeSelect
                  id="fiscal-regime"
                  className="w-full"
                  aria-invalid={!!errors.regimeIva}
                  aria-describedby={descritoPor(errors.regimeIva && "fiscal-regime-erro")}
                  {...regimeRegisto}
                >
                  <NativeSelectOption value="NORMAL">Normal</NativeSelectOption>
                  <NativeSelectOption value="ISENTO">Isento</NativeSelectOption>
                </NativeSelect>
                {errors.regimeIva ? (
                  <p id="fiscal-regime-erro" className="text-sm text-red-600">
                    {errors.regimeIva.message}
                  </p>
                ) : null}
              </div>

              {/* Motivo de isenção (só no regime Isento) */}
              {regimeIva === "ISENTO" ? (
                <div className="space-y-2 sm:col-span-2">
                  <Label htmlFor="fiscal-motivo">Motivo de isenção</Label>
                  <NativeSelect
                    id="fiscal-motivo"
                    className="w-full"
                    aria-invalid={!!errors.motivoIsencaoCodigo}
                    aria-describedby={descritoPor(
                      "fiscal-motivo-helper",
                      errors.motivoIsencaoCodigo && "fiscal-motivo-erro",
                    )}
                    {...form.register("motivoIsencaoCodigo", {
                      setValueAs: (v: unknown) => (v === "" || v == null ? null : String(v)),
                    })}
                    // Controlado: as opções chegam de forma assíncrona e o DOM tem de refletir
                    // o código guardado assim que existirem.
                    value={motivoIsencaoCodigo ?? ""}
                  >
                    <NativeSelectOption value="">Escolha o motivo</NativeSelectOption>
                    {(motivos.data ?? []).map((m) => (
                      <NativeSelectOption key={m.codigo} value={m.codigo}>
                        {`${m.codigo} — ${m.descricao}`}
                      </NativeSelectOption>
                    ))}
                  </NativeSelect>
                  <p
                    id="fiscal-motivo-helper"
                    className="text-xs text-slate-500 dark:text-slate-400"
                  >
                    Código oficial de isenção de IVA, obrigatório para o regime isento.
                  </p>
                  {errors.motivoIsencaoCodigo ? (
                    <p id="fiscal-motivo-erro" className="text-sm text-red-600">
                      {errors.motivoIsencaoCodigo.message}
                    </p>
                  ) : null}
                </div>
              ) : null}
            </div>

            {erroGuardar ? (
              <p role="alert" className="text-sm text-red-600">
                {erroGuardar}
              </p>
            ) : null}
          </CardContent>

          <CardFooter className="flex justify-end">
            <Button type="submit" disabled={!isDirty || isSubmitting}>
              {isSubmitting ? "A guardar..." : "Guardar dados fiscais"}
            </Button>
          </CardFooter>
        </form>
      )}
    </Card>
  );
}
