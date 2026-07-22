"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowLeft, ArrowRight, Check, CheckCircle2, ChevronRight, Search } from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import { useForm } from "react-hook-form";

import { Badge } from "@/components/ui/badge";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useClientes } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import {
  useCreateIntake,
  useFormalizarProcesso,
  useRegistarDecisaoConflito,
  useRunConflictCheck,
} from "@/hooks/use-processos";
import { toast } from "@/hooks/use-toast";
import { conflictNivelToLabel, conflictNivelToVariant } from "@/lib/conflict-check";
import { origemProcessoToLabel } from "@/lib/origem-processo";
import {
  conflictCheckDecisaoFormSchema,
  processoIntakeFormSchema,
  type ConflictCheckDecisaoFormValues,
  type ProcessoIntakeFormValues,
} from "@/schemas/processos";
import type { ConflictCheckDecisaoRequest, ConflictCheckResponse, Processo } from "@/types/processos";

const textareaClassName =
  "flex min-h-24 w-full border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] px-3 py-2 text-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 disabled:cursor-not-allowed disabled:opacity-50 dark:placeholder:text-neutral-400";

export default function ProcessoCreatePage() {
  const permissions = usePermissions();
  const canCreateProcessos = permissions.can.create("processos");

  if (permissions.isFetched && !canCreateProcessos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para criar processos."
        backHref="/processos"
      />
    );
  }

  return <ProcessoWizardContent />;
}

function ProcessoWizardContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const lockedClienteId = searchParams.get("clienteId");
  const permissions = usePermissions();
  const canCreateProcessos = permissions.can.create("processos");
  const canManageProcessos = permissions.can.manage("processos");

  const [step, setStep] = React.useState<1 | 2 | 3>(1);
  const [processoId, setProcessoId] = React.useState<string | null>(null);
  const [processoData, setProcessoData] = React.useState<Processo | null>(null);
  const [conflictResult, setConflictResult] = React.useState<ConflictCheckResponse | null>(null);
  const [decisaoData, setDecisaoData] = React.useState<{ nivel: string; justificativa?: string; referenciaEvidencia?: string; dataDecisao?: string } | null>(null);
  const [step1Error, setStep1Error] = React.useState<string | null>(null);
  const [step2Error, setStep2Error] = React.useState<string | null>(null);
  const [step3Error, setStep3Error] = React.useState<string | null>(null);
  const [formalizarError, setFormalizarError] = React.useState<string | null>(null);

  const clientes = useClientes({});
  const createIntake = useCreateIntake();
  const runCheck = useRunConflictCheck(processoId ?? "");
  const registarDecisao = useRegistarDecisaoConflito(processoId ?? "");
  const formalizarProcesso = useFormalizarProcesso(processoId ?? "");

  // Step 1 form
  const intakeForm = useForm<ProcessoIntakeFormValues>({
    resolver: zodResolver(processoIntakeFormSchema),
    defaultValues: {
      cliente_id: lockedClienteId ?? "",
      numero: undefined,
      titulo: undefined,
      tipo_processo: undefined,
      area_juridica: undefined,
      tribunal: undefined,
      descricao: undefined,
      estado: undefined,
      data_inicio: undefined,
      data_fim: undefined,
      origem: undefined,
    },
  });

  // Step 2 decisao form
  const decisaoForm = useForm<ConflictCheckDecisaoFormValues>({
    resolver: zodResolver(conflictCheckDecisaoFormSchema),
    defaultValues: {
      nivel: undefined,
      justificativa: undefined,
      referenciaEvidencia: undefined,
    },
  });

  const onStep1Submit = async (values: ProcessoIntakeFormValues) => {
    setStep1Error(null);
    if (!canCreateProcessos) {
      setStep1Error("Não tem permissão para criar processos");
      return;
    }
    try {
      // Do NOT include estado — backend forces TRIAGEM on intake
      const { estado: _estado, ...intakeValues } = values;
      const res = await createIntake.mutateAsync(intakeValues);
      setProcessoId(res.id);
      setProcessoData(res);
      setStep(2);
      toast.success("Processo iniciado com sucesso (Intake).");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao criar processo";
      setStep1Error(msg);
      toast.error(msg);
    }
  };

  const onRunConflictCheck = async () => {
    if (!processoId) return;
    setStep2Error(null);
    try {
      const result = await runCheck.mutateAsync();
      setConflictResult(result);
      toast.success("Conflict check executado.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao executar o conflict check. Verifique a ligação e tente novamente.";
      setStep2Error(msg);
      toast.error(msg);
    }
  };

  const onRegistarDecisao = async (values: ConflictCheckDecisaoFormValues) => {
    if (!processoId) return;
    setStep2Error(null);
    try {
      const payload: ConflictCheckDecisaoRequest = {
        nivel: values.nivel,
        justificativa: values.justificativa,
        referenciaEvidencia: values.referenciaEvidencia,
      };
      const saved = await registarDecisao.mutateAsync(payload);
      setDecisaoData({
        nivel: values.nivel,
        justificativa: values.justificativa,
        referenciaEvidencia: values.referenciaEvidencia,
        dataDecisao: saved.dataDecisao ?? new Date().toLocaleDateString("pt-CV"),
      });
      toast.success("Decisão registada com sucesso.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Não foi possível registar a decisão. Tente novamente ou contacte o suporte.";
      setStep2Error(msg);
      toast.error(msg);
    }
  };

  const onFormalizar = async () => {
    if (!processoId) return;
    setFormalizarError(null);
    setStep3Error(null);
    try {
      await formalizarProcesso.mutateAsync();
      toast.success("Processo formalizado e aberto com sucesso.");
      router.push(`/processos/${encodeURIComponent(processoId)}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao formalizar o processo";
      setFormalizarError(msg);
      setStep3Error(msg);
      toast.error(msg);
    }
  };

  const isFormalizarBlocked =
    !decisaoData || decisaoData.nivel === "impeditivo";

  const formalizarBlockReason = !decisaoData
    ? "Não é possível formalizar: o conflict check ainda não tem uma decisão registada."
    : decisaoData.nivel === "impeditivo"
      ? "Não é possível formalizar: existe um conflito impeditivo registado."
      : null;

  const canAdvanceToStep3 = decisaoData !== null && decisaoData.nivel !== "impeditivo";

  const intakeFormValues = intakeForm.getValues();
  const selectedClienteName =
    (clientes.data ?? []).find((c) => c.id === intakeFormValues.cliente_id)?.nome ?? "";

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Novo Processo</h1>
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link href="/processos">Processos</Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem>
                <BreadcrumbPage>Novo Processo</BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
          <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
            Registe os dados de intake, execute o conflict check e formalize a abertura.
          </p>
        </div>
        <Button asChild variant="outline" className="">
          <Link href="/processos">
            <ArrowLeft className="h-4 w-4" />
            Voltar
          </Link>
        </Button>
      </div>

      {/* Step indicator */}
      <div className="flex items-center px-6 py-4 gap-4">
        {/* Step 1 */}
        <div className="flex items-center gap-2">
          <div
            className={`h-8 w-8 flex items-center justify-center text-sm font-bold shrink-0 ${
              step === 1
                ? "bg-blue-600 text-white"
                : step > 1
                  ? "bg-emerald-600 text-white"
                  : "border border-slate-300 dark:border-slate-700 text-slate-400"
            }`}
          >
            {step > 1 ? <Check className="h-[14px] w-[14px]" /> : "1"}
          </div>
          <span className={`text-sm ${step === 1 ? "font-bold text-slate-900 dark:text-white" : "text-slate-500 dark:text-slate-400"}`}>
            Intake
          </span>
        </div>

        <ChevronRight className="h-4 w-4 text-slate-300 dark:text-slate-700" />

        {/* Step 2 */}
        <div className="flex items-center gap-2">
          <div
            className={`h-8 w-8 flex items-center justify-center text-sm font-bold shrink-0 ${
              step === 2
                ? "bg-blue-600 text-white"
                : step > 2
                  ? "bg-emerald-600 text-white"
                  : "border border-slate-300 dark:border-slate-700 text-slate-400"
            }`}
          >
            {step > 2 ? <Check className="h-[14px] w-[14px]" /> : "2"}
          </div>
          <span className={`text-sm ${step === 2 ? "font-bold text-slate-900 dark:text-white" : "text-slate-500 dark:text-slate-400"}`}>
            Conflict Check
          </span>
        </div>

        <ChevronRight className="h-4 w-4 text-slate-300 dark:text-slate-700" />

        {/* Step 3 */}
        <div className="flex items-center gap-2">
          <div
            className={`h-8 w-8 flex items-center justify-center text-sm font-bold shrink-0 ${
              step === 3
                ? "bg-blue-600 text-white"
                : "border border-slate-300 dark:border-slate-700 text-slate-400"
            }`}
          >
            3
          </div>
          <span className={`text-sm ${step === 3 ? "font-bold text-slate-900 dark:text-white" : "text-slate-500 dark:text-slate-400"}`}>
            Abertura
          </span>
        </div>
      </div>

      {/* Step 1: Intake */}
      {step === 1 ? (
        <Card>
          <CardHeader>
            <CardTitle>Dados de Intake</CardTitle>
          </CardHeader>
          <CardContent>
            <form className="space-y-4" onSubmit={intakeForm.handleSubmit(onStep1Submit)}>
              {/* Row 1: Cliente + Tipo de Processo */}
              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="cliente_id">Cliente</Label>
                  <NativeSelect
                    id="cliente_id"
                    size="default"
                    className="w-full"
                    disabled={clientes.isPending || clientes.isError || Boolean(lockedClienteId)}
                    {...intakeForm.register("cliente_id")}
                  >
                    <option value="">{clientes.isPending ? "A carregar..." : "Selecionar cliente"}</option>
                    {(clientes.data ?? []).map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.nome}
                      </option>
                    ))}
                  </NativeSelect>
                  {lockedClienteId ? (
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      Cliente pré-preenchido a partir da ficha de cliente — não pode ser alterado aqui.
                    </p>
                  ) : null}
                  {clientes.isError ? (
                    <p className="text-sm text-red-600">
                      {clientes.error instanceof Error ? clientes.error.message : "Erro ao carregar clientes"}
                    </p>
                  ) : null}
                  {intakeForm.formState.errors.cliente_id ? (
                    <p className="text-sm text-red-600">{intakeForm.formState.errors.cliente_id.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="tipo_processo">Tipo de Processo</Label>
                  <NativeSelect
                    id="tipo_processo"
                    size="default"
                    className="w-full"
                    {...intakeForm.register("tipo_processo")}
                  >
                    <option value="">Selecionar tipo</option>
                    <option value="civel">Cível</option>
                    <option value="penal">Penal</option>
                    <option value="laboral">Laboral</option>
                    <option value="administrativo">Administrativo</option>
                    <option value="comercial">Comercial</option>
                    <option value="familia">Família</option>
                    <option value="outro">Outro</option>
                  </NativeSelect>
                  {intakeForm.formState.errors.tipo_processo ? (
                    <p className="text-sm text-red-600">{intakeForm.formState.errors.tipo_processo.message}</p>
                  ) : null}
                </div>
              </div>

              {/* Row: Origem */}
              <div className="space-y-2">
                <Label htmlFor="origem">Origem</Label>
                <NativeSelect
                  id="origem"
                  size="default"
                  className="w-full"
                  {...intakeForm.register("origem")}
                >
                  <option value="">Selecionar origem</option>
                  <option value="PETICAO_INICIAL">{origemProcessoToLabel("PETICAO_INICIAL")}</option>
                  <option value="NOTIFICACOES_AVULSAS">{origemProcessoToLabel("NOTIFICACOES_AVULSAS")}</option>
                </NativeSelect>
                {intakeForm.formState.errors.origem ? (
                  <p className="text-sm text-red-600">{intakeForm.formState.errors.origem.message}</p>
                ) : null}
              </div>

              {/* Row 2: Número + Área Jurídica */}
              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="numero">Número</Label>
                  <Input
                    id="numero"
                    className="focus-visible:ring-blue-500 max-sm:h-12 max-sm:text-base"
                    placeholder="Ex.: 123/2026"
                    {...intakeForm.register("numero")}
                  />
                  {intakeForm.formState.errors.numero ? (
                    <p className="text-sm text-red-600">{intakeForm.formState.errors.numero.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="area_juridica">Área Jurídica</Label>
                  <Input
                    id="area_juridica"
                    className="focus-visible:ring-blue-500 max-sm:h-12 max-sm:text-base"
                    placeholder="Ex.: Cível"
                    {...intakeForm.register("area_juridica")}
                  />
                  {intakeForm.formState.errors.area_juridica ? (
                    <p className="text-sm text-red-600">{intakeForm.formState.errors.area_juridica.message}</p>
                  ) : null}
                </div>
              </div>

              {/* Row 3: Tribunal */}
              <div className="space-y-2">
                <Label htmlFor="tribunal">Tribunal</Label>
                <Input
                  id="tribunal"
                  className="focus-visible:ring-blue-500 max-sm:h-12 max-sm:text-base"
                  placeholder="Ex.: Tribunal da Comarca da Praia"
                  {...intakeForm.register("tribunal")}
                />
                {intakeForm.formState.errors.tribunal ? (
                  <p className="text-sm text-red-600">{intakeForm.formState.errors.tribunal.message}</p>
                ) : null}
              </div>

              {/* Row 4: Data Início + Data Fim */}
              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="data_inicio">Data de Início</Label>
                  <Input
                    id="data_inicio"
                    type="date"
                    className="focus-visible:ring-blue-500 max-sm:h-12 max-sm:text-base"
                    {...intakeForm.register("data_inicio")}
                  />
                  {intakeForm.formState.errors.data_inicio ? (
                    <p className="text-sm text-red-600">{intakeForm.formState.errors.data_inicio.message}</p>
                  ) : null}
                </div>

                <div className="space-y-2">
                  <Label htmlFor="data_fim">Data de Fim</Label>
                  <Input
                    id="data_fim"
                    type="date"
                    className="focus-visible:ring-blue-500 max-sm:h-12 max-sm:text-base"
                    {...intakeForm.register("data_fim")}
                  />
                  {intakeForm.formState.errors.data_fim ? (
                    <p className="text-sm text-red-600">{intakeForm.formState.errors.data_fim.message}</p>
                  ) : null}
                </div>
              </div>

              {/* Row 5: Descrição */}
              <div className="space-y-2">
                <Label htmlFor="descricao">Descrição</Label>
                <textarea
                  id="descricao"
                  className={textareaClassName}
                  placeholder="Notas adicionais (opcional)"
                  {...intakeForm.register("descricao")}
                />
                {intakeForm.formState.errors.descricao ? (
                  <p className="text-sm text-red-600">{intakeForm.formState.errors.descricao.message}</p>
                ) : null}
              </div>

              {step1Error ? <p className="text-sm text-red-600">{step1Error}</p> : null}

              <div className="flex gap-2">
                <Button
                  type="submit"
                  className="font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white max-sm:min-h-[48px]"
                  disabled={intakeForm.formState.isSubmitting || createIntake.isPending || permissions.isLoading || !canCreateProcessos}
                >
                  <ArrowRight className="h-4 w-4" />
                  {intakeForm.formState.isSubmitting || createIntake.isPending ? "A guardar..." : "Continuar para Conflict Check"}
                </Button>
                <Button asChild type="button" variant="outline" className="">
                  <Link href="/processos">
                    <ArrowLeft className="h-4 w-4" />
                    Voltar
                  </Link>
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      ) : null}

      {/* Step 2: Conflict Check */}
      {step === 2 ? (
        <Card>
          <CardHeader>
            <CardTitle>Verificação de Conflitos</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* Panel A: Trigger */}
            <div className="space-y-3">
              <p className="text-sm text-slate-600 dark:text-slate-400">
                O sistema vai verificar conflitos de interesse para o cliente, partes relacionadas e parte contrária declaradas no intake.
              </p>
              <Button
                type="button"
                className="font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white"
                disabled={!canCreateProcessos || runCheck.isPending}
                onClick={onRunConflictCheck}
              >
                <Search className="h-4 w-4" />
                {runCheck.isPending ? "A verificar conflitos..." : "Executar Conflict Check"}
              </Button>
              {!canCreateProcessos ? (
                <p className="text-sm text-slate-500">Não tem permissão para executar o conflict check.</p>
              ) : null}
            </div>

            {/* Panel B: Match Results */}
            {conflictResult ? (
              <div className="space-y-4">
                <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                  Resultados
                </div>

                {conflictResult.matches.length === 0 ? (
                  <div className="border border-slate-200 dark:border-slate-800 p-4 space-y-1">
                    <div className="flex items-center gap-2">
                      <Badge variant="green" className="font-bold tracking-wide">
                        SEM CONFLITO
                      </Badge>
                      <span className="text-sm font-medium text-slate-700 dark:text-slate-300">Sem conflitos detectados</span>
                    </div>
                    <p className="text-sm text-slate-500 dark:text-slate-400">
                      Nenhuma correspondência encontrada para este cliente, partes relacionadas e parte contrária. Pode prosseguir com a formalização.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-2">
                    {conflictResult.matches.map((match) => (
                      <div
                        key={`${match.entidadeTipo}-${match.entidadeId}`}
                        className="border border-slate-200 dark:border-slate-800 p-4 flex items-center justify-between gap-4 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors"
                      >
                        <div className="flex items-center gap-3 flex-wrap">
                          <Badge
                            variant={conflictNivelToVariant(match.nivelConflito)}
                            className="font-bold tracking-wide"
                          >
                            {conflictNivelToLabel(match.nivelConflito)}
                          </Badge>
                          <div>
                            <span className="text-sm font-medium text-slate-900 dark:text-white">{match.nome}</span>
                            <span className="text-xs text-slate-500 dark:text-slate-400 ml-2">({match.entidadeTipo})</span>
                            {match.nif ? <span className="text-xs text-slate-400 ml-2">NIF: {match.nif}</span> : null}
                          </div>
                        </div>
                        <Link
                          href={
                            match.entidadeTipo === "cliente"
                              ? `/clientes/${match.entidadeId}`
                              : `/processos/${processoId}`
                          }
                          className="text-xs text-blue-600 hover:underline shrink-0"
                        >
                          Ver registo
                        </Link>
                      </div>
                    ))}
                  </div>
                )}

                {/* Decisao form */}
                {canManageProcessos ? (
                  <form className="space-y-4 pt-4 border-t border-slate-200 dark:border-slate-800" onSubmit={decisaoForm.handleSubmit(onRegistarDecisao)}>
                    <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                      Registar Decisão
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="nivel_final">Nível final</Label>
                      <NativeSelect
                        id="nivel_final"
                        size="default"
                        className="w-full"
                        {...decisaoForm.register("nivel")}
                      >
                        <option value="">Selecionar nível</option>
                        <option value="sem_conflito">Sem conflito</option>
                        <option value="potencial">Potencial</option>
                        <option value="sanavel">Sanável</option>
                        <option value="impeditivo">Impeditivo</option>
                      </NativeSelect>
                      {decisaoForm.formState.errors.nivel ? (
                        <p className="text-sm text-red-600">{decisaoForm.formState.errors.nivel.message}</p>
                      ) : null}
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="justificativa">Justificativa</Label>
                      <textarea
                        id="justificativa"
                        className={textareaClassName}
                        placeholder="Justificativa da decisão (obrigatória para potencial e sanável)"
                        {...decisaoForm.register("justificativa")}
                      />
                      {decisaoForm.formState.errors.justificativa ? (
                        <p className="text-sm text-red-600">{decisaoForm.formState.errors.justificativa.message}</p>
                      ) : null}
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="referencia_evidencia">Referência a evidência</Label>
                      <Input
                        id="referencia_evidencia"
                        className="focus-visible:ring-blue-500 max-sm:h-12 max-sm:text-base"
                        placeholder="Ref. opcional"
                        {...decisaoForm.register("referenciaEvidencia")}
                      />
                      {decisaoForm.formState.errors.referenciaEvidencia ? (
                        <p className="text-sm text-red-600">{decisaoForm.formState.errors.referenciaEvidencia.message}</p>
                      ) : null}
                    </div>

                    <div className="space-y-2">
                      <Label>Decisor</Label>
                      <Input
                        className="focus-visible:ring-blue-500 bg-slate-50 dark:bg-slate-900 cursor-not-allowed"
                        value={permissions.data?.nome ?? "—"}
                        disabled
                        readOnly
                      />
                    </div>

                    <div className="space-y-2">
                      <Label>Data</Label>
                      <Input
                        className="focus-visible:ring-blue-500 bg-slate-50 dark:bg-slate-900 cursor-not-allowed"
                        value={new Date().toLocaleDateString("pt-CV")}
                        disabled
                        readOnly
                      />
                    </div>

                    {step2Error ? <p className="text-sm text-red-600">{step2Error}</p> : null}

                    <Button
                      type="submit"
                      className="font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white"
                      disabled={decisaoForm.formState.isSubmitting || registarDecisao.isPending}
                    >
                      <Check className="h-4 w-4" />
                      {decisaoForm.formState.isSubmitting || registarDecisao.isPending ? "A guardar..." : "Registar Decisão"}
                    </Button>
                  </form>
                ) : (
                  <div className="pt-4 border-t border-slate-200 dark:border-slate-800">
                    <Button
                      type="button"
                      className="font-bold shadow-none opacity-50 cursor-not-allowed"
                      disabled
                    >
                      <Check className="h-4 w-4" />
                      Registar Decisão
                    </Button>
                    <p className="text-sm text-slate-500 mt-2">
                      Não tem permissão para registar decisões de conflito. Requer permissão de gestão de processos.
                    </p>
                  </div>
                )}

                {/* Decisao registada: continuar ou bloqueio impeditivo */}
                {decisaoData ? (
                  <div className="pt-4 space-y-3">
                    {decisaoData.nivel === "impeditivo" ? (
                      <div className="bg-amber-50 dark:bg-amber-500/10 border border-amber-200 dark:border-amber-500/30 p-4">
                        <p className="text-sm text-amber-800 dark:text-amber-400">
                          Conflito impeditivo registado. A abertura formal do processo está bloqueada até esta decisão ser revista por um utilizador com permissão de gestão.
                        </p>
                      </div>
                    ) : null}

                    <Button
                      type="button"
                      className={`font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white ${!canAdvanceToStep3 ? "opacity-50 cursor-not-allowed" : ""}`}
                      disabled={!canAdvanceToStep3}
                      onClick={() => canAdvanceToStep3 && setStep(3)}
                    >
                      <ArrowRight className="h-4 w-4" />
                      Continuar para Abertura
                    </Button>
                  </div>
                ) : null}
              </div>
            ) : null}

            {step2Error && !conflictResult ? (
              <p className="text-sm text-red-600">{step2Error}</p>
            ) : null}
          </CardContent>
        </Card>
      ) : null}

      {/* Step 3: Abertura */}
      {step === 3 ? (
        <Card>
          <CardHeader>
            <CardTitle>Revisão e Abertura</CardTitle>
          </CardHeader>
          <CardContent className="space-y-6">
            {/* Summary read-only dl grid */}
            <div>
              <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400 mb-3">
                Dados do Intake
              </div>
              <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                <dt className="text-neutral-500 dark:text-neutral-400">Cliente</dt>
                <dd className="col-span-2 font-medium">{selectedClienteName || "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Número</dt>
                <dd className="col-span-2 font-medium">{processoData?.numero ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Tipo do Processo</dt>
                <dd className="col-span-2 font-medium">{processoData?.tipo_processo ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Área Jurídica</dt>
                <dd className="col-span-2 font-medium">{processoData?.area_juridica ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Tribunal</dt>
                <dd className="col-span-2 font-medium">{processoData?.tribunal ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Data de Início</dt>
                <dd className="col-span-2 font-medium">{processoData?.data_inicio ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Data de Fim</dt>
                <dd className="col-span-2 font-medium">{processoData?.data_fim ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Estado</dt>
                <dd className="col-span-2">
                  <Badge variant="purple" className="font-bold tracking-wide">
                    EM TRIAGEM
                  </Badge>
                </dd>
              </dl>
            </div>

            {decisaoData ? (
              <div>
                <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400 mb-3">
                  Decisão de Conflict Check
                </div>
                <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                  <dt className="text-neutral-500 dark:text-neutral-400">Nível</dt>
                  <dd className="col-span-2">
                    <Badge
                      variant={conflictNivelToVariant(decisaoData.nivel as Parameters<typeof conflictNivelToVariant>[0])}
                      className="font-bold tracking-wide"
                    >
                      {conflictNivelToLabel(decisaoData.nivel as Parameters<typeof conflictNivelToLabel>[0])}
                    </Badge>
                  </dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Decisor</dt>
                  <dd className="col-span-2 font-medium">{permissions.data?.nome ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Data</dt>
                  <dd className="col-span-2 font-medium">{decisaoData.dataDecisao ?? "—"}</dd>

                  {decisaoData.referenciaEvidencia ? (
                    <>
                      <dt className="text-neutral-500 dark:text-neutral-400">Referência</dt>
                      <dd className="col-span-2 font-medium">{decisaoData.referenciaEvidencia}</dd>
                    </>
                  ) : null}

                  {decisaoData.justificativa ? (
                    <>
                      <dt className="text-neutral-500 dark:text-neutral-400">Justificativa</dt>
                      <dd className="col-span-2 font-medium">{decisaoData.justificativa}</dd>
                    </>
                  ) : null}
                </dl>
              </div>
            ) : null}

            {step3Error ? <p className="text-sm text-red-600">{step3Error}</p> : null}

            {/* Formalizar CTA */}
            <div className="space-y-2">
              <div className="flex gap-2">
                <Button
                  type="button"
                  className={`font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white ${isFormalizarBlocked ? "opacity-50 cursor-not-allowed" : ""}`}
                  disabled={isFormalizarBlocked || formalizarProcesso.isPending}
                  onClick={onFormalizar}
                >
                  <CheckCircle2 className="h-4 w-4" />
                  {formalizarProcesso.isPending ? "A formalizar..." : "Formalizar Processo"}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  className=""
                  onClick={() => setStep(2)}
                >
                  <ArrowLeft className="h-4 w-4" />
                  Voltar
                </Button>
              </div>
              {isFormalizarBlocked && formalizarBlockReason ? (
                <p className="text-sm text-red-600">{formalizarBlockReason}</p>
              ) : null}
              {formalizarError && !isFormalizarBlocked ? (
                <p className="text-sm text-red-600">{formalizarError}</p>
              ) : null}
            </div>
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
