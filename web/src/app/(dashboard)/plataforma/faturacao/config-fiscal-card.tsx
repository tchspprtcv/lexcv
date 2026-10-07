"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { CheckCircle2, Lock, ShieldAlert } from "lucide-react";
import { useForm, useWatch } from "react-hook-form";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect, NativeSelectOption } from "@/components/ui/native-select";
import { useMotivosIsencao } from "@/hooks/use-faturacao";
import {
  usePlatformConfiguracaoFiscal,
  useUpdatePlatformConfiguracaoFiscal,
} from "@/hooks/use-platform-faturacao";
import { toast } from "@/hooks/use-toast";
import {
  configuracaoFiscalSchema,
  MORADA_MAX,
  type ConfiguracaoFiscalFormInput,
  type ConfiguracaoFiscalFormValues,
} from "@/schemas/faturacao";
import type { PlatformConfiguracaoFiscal } from "@/types/platform-faturacao";

function valoresIniciais(data: PlatformConfiguracaoFiscal | undefined): ConfiguracaoFiscalFormInput {
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

export function PlatformConfigFiscalCard() {
  const { data, isLoading } = usePlatformConfiguracaoFiscal();
  const motivos = useMotivosIsencao(true);
  const updateConfig = useUpdatePlatformConfiguracaoFiscal();

  const form = useForm<ConfiguracaoFiscalFormInput, unknown, ConfiguracaoFiscalFormValues>({
    resolver: zodResolver(configuracaoFiscalSchema),
    mode: "onTouched",
    defaultValues: valoresIniciais(data),
  });

  const { errors, isDirty, isSubmitting } = form.formState;

  React.useEffect(() => {
    if (data) {
      form.reset(valoresIniciais(data));
    }
  }, [data, form]);

  const regimeIva = useWatch({ control: form.control, name: "regimeIva" });
  const isIsento = regimeIva === "ISENTO";

  const onSubmit = async (values: ConfiguracaoFiscalFormValues) => {
    try {
      await updateConfig.mutateAsync(values);
      toast.success("Dados fiscais da plataforma guardados com sucesso.");
    } catch {
      toast.error("Erro ao guardar configuração fiscal da plataforma.");
    }
  };

  if (isLoading) {
    return <div className="p-6 text-sm text-slate-500">A carregar dados fiscais da plataforma...</div>;
  }

  const nifBloqueado = Boolean(data?.nifBloqueado);
  const pronta = Boolean(data?.completa && data?.ativa);

  return (
    <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
      <CardHeader className="flex flex-row items-center justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <CardTitle className="text-lg font-semibold">Dados Fiscais de Emitente — LexCV</CardTitle>
            {pronta ? (
              <Badge variant="secondary" className="bg-emerald-100 text-emerald-800 dark:bg-emerald-950 dark:text-emerald-300 gap-1 text-xs">
                <CheckCircle2 className="h-3.5 w-3.5" />
                Pronta para Emitir
              </Badge>
            ) : (
              <Badge variant="outline" className="text-amber-600 border-amber-300 bg-amber-50 dark:bg-amber-950/40 gap-1 text-xs">
                <ShieldAlert className="h-3.5 w-3.5" />
                Configuração Pendente
              </Badge>
            )}
          </div>
          <CardDescription className="mt-1">
            Configuração fiscal da plataforma LexCV utilizada como emitente das faturas-recibo e notas de crédito de subscrição.
          </CardDescription>
        </div>
      </CardHeader>

      <form onSubmit={form.handleSubmit(onSubmit)}>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label htmlFor="nif" className="text-xs font-medium text-slate-700 dark:text-slate-300">
                NIF de Emitente da Plataforma *
              </Label>
              <div className="relative">
                <Input
                  id="nif"
                  disabled={nifBloqueado || isSubmitting}
                  placeholder="Ex.: 500123456"
                  {...form.register("nif")}
                  className={errors.nif ? "border-red-500 focus-visible:ring-red-500" : ""}
                />
                {nifBloqueado && (
                  <Lock className="absolute right-3 top-2.5 h-4 w-4 text-slate-400" aria-hidden="true" />
                )}
              </div>
              {errors.nif && <p className="text-xs text-red-500">{errors.nif.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="firma" className="text-xs font-medium text-slate-700 dark:text-slate-300">
                Firma / Denominação Social *
              </Label>
              <Input
                id="firma"
                disabled={isSubmitting}
                placeholder="Ex.: LexCV Tecnologias Lda"
                {...form.register("firma")}
                className={errors.firma ? "border-red-500 focus-visible:ring-red-500" : ""}
              />
              {errors.firma && <p className="text-xs text-red-500">{errors.firma.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="morada" className="text-xs font-medium text-slate-700 dark:text-slate-300">
                Morada da Sede *
              </Label>
              <Input
                id="morada"
                maxLength={MORADA_MAX}
                disabled={isSubmitting}
                placeholder="Ex.: Av. Cidade de Lisboa, 100"
                {...form.register("morada")}
                className={errors.morada ? "border-red-500 focus-visible:ring-red-500" : ""}
              />
              {errors.morada && <p className="text-xs text-red-500">{errors.morada.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="localidade" className="text-xs font-medium text-slate-700 dark:text-slate-300">
                Localidade / Cidade *
              </Label>
              <Input
                id="localidade"
                disabled={isSubmitting}
                placeholder="Ex.: Praia"
                {...form.register("localidade")}
                className={errors.localidade ? "border-red-500 focus-visible:ring-red-500" : ""}
              />
              {errors.localidade && <p className="text-xs text-red-500">{errors.localidade.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="emailContacto" className="text-xs font-medium text-slate-700 dark:text-slate-300">
                Email Financeiro de Contacto *
              </Label>
              <Input
                id="emailContacto"
                type="email"
                disabled={isSubmitting}
                placeholder="Ex.: financeiro@lexcv.cv"
                {...form.register("emailContacto")}
                className={errors.emailContacto ? "border-red-500 focus-visible:ring-red-500" : ""}
              />
              {errors.emailContacto && <p className="text-xs text-red-500">{errors.emailContacto.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="telefoneContacto" className="text-xs font-medium text-slate-700 dark:text-slate-300">
                Telefone de Contacto *
              </Label>
              <Input
                id="telefoneContacto"
                disabled={isSubmitting}
                placeholder="Ex.: +238 261 00 00"
                {...form.register("telefoneContacto")}
                className={errors.telefoneContacto ? "border-red-500 focus-visible:ring-red-500" : ""}
              />
              {errors.telefoneContacto && <p className="text-xs text-red-500">{errors.telefoneContacto.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="regimeIva" className="text-xs font-medium text-slate-700 dark:text-slate-300">
                Regime de IVA *
              </Label>
              <NativeSelect
                id="regimeIva"
                disabled={isSubmitting}
                {...form.register("regimeIva")}
                className={errors.regimeIva ? "border-red-500 focus-visible:ring-red-500" : ""}
              >
                <NativeSelectOption value="NORMAL">Regime Geral (IVA 15%)</NativeSelectOption>
                <NativeSelectOption value="ISENTO">Regime de Isenção</NativeSelectOption>
              </NativeSelect>
              {errors.regimeIva && <p className="text-xs text-red-500">{errors.regimeIva.message}</p>}
            </div>

            {isIsento && (
              <div className="space-y-1.5">
                <Label htmlFor="motivoIsencaoCodigo" className="text-xs font-medium text-slate-700 dark:text-slate-300">
                  Motivo de Isenção Oficial *
                </Label>
                <NativeSelect
                  id="motivoIsencaoCodigo"
                  disabled={isSubmitting || motivos.isLoading}
                  {...form.register("motivoIsencaoCodigo")}
                  className={errors.motivoIsencaoCodigo ? "border-red-500 focus-visible:ring-red-500" : ""}
                >
                  <NativeSelectOption value="">Selecione o motivo...</NativeSelectOption>
                  {motivos.data?.map((m) => (
                    <NativeSelectOption key={m.codigo} value={m.codigo}>
                      {m.codigo} - {m.descricao}
                    </NativeSelectOption>
                  ))}
                </NativeSelect>
                {errors.motivoIsencaoCodigo && (
                  <p className="text-xs text-red-500">{errors.motivoIsencaoCodigo.message}</p>
                )}
              </div>
            )}
          </div>
        </CardContent>

        <CardFooter className="flex justify-end gap-3 pt-2">
          <Button
            type="submit"
            disabled={!isDirty || isSubmitting || updateConfig.isPending}
            className="bg-blue-600 hover:bg-blue-700 text-white text-xs px-4 py-2 h-auto"
          >
            {isSubmitting || updateConfig.isPending ? "A guardar..." : "Guardar Dados Fiscais"}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
