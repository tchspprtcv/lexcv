"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import * as React from "react";
import { useForm } from "react-hook-form";
import {
  AlertCircle,
  Calendar,
  CheckCircle2,
  Circle,
  FileText,
  GitBranch,
  Paperclip,
  Pause,
  Play,
  Plus,
  RotateCcw,
  ShieldCheck,
  User,
  XCircle,
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useAdminUsers } from "@/hooks/use-admin";
import { useClientes } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import {
  useAddProcessoFase,
  useAddProcessoMovimentacao,
  useAddProcessoParte,
  useAuditLog,
  useConflictCheckDecisao,
  useCreatePrazo,
  useExecutarTransicao,
  useFormalizarProcesso,
  usePrazos,
  useProcesso,
  useProcessoFases,
  useProcessoMovimentacoes,
  useProcessoPartes,
  useTimeline,
  useTogglePrazoConcluido,
  useUpdateProcessoFaseStatus,
  useWorkflow,
} from "@/hooks/use-processos";
import { toast } from "@/hooks/use-toast";
import { conflictNivelToLabel, conflictNivelToVariant } from "@/lib/conflict-check";
import { prazosRiscoToLabel, prazosRiscoToVariant } from "@/lib/prazos";
import {
  prazoFormSchema,
  processoFaseFormSchema,
  processoFaseStatusSchema,
  processoMovimentacaoFormSchema,
  processoParteFormSchema,
  transicaoJustificativaFormSchema,
  type PrazoFormValues,
  type ProcessoFaseFormValues,
  type ProcessoMovimentacaoFormValues,
  type ProcessoParteFormValues,
  type TransicaoJustificativaFormValues,
} from "@/schemas/processos";
import type {
  AuditLogEntry,
  ProcessoFaseCreateRequest,
  ProcessoFaseStatus,
  ProcessoFaseUpdateRequest,
  ProcessoMovimentacaoCreateRequest,
  ProcessoParteCreateRequest,
  TimelineItem,
  TimelineItemType,
  TransicaoInfo,
} from "@/types/processos";

type PageProps = {
  params: Promise<{ id: string }>;
};

type TabKey = "timeline" | "partes" | "fases" | "auditoria";

const selectClassName =
  "flex h-9 w-full rounded-md border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300";

const textareaClassName =
  "flex min-h-24 w-full rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

function formatDateTime(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleString("pt-CV");
}

function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

const ACAO_ICONS: Record<string, React.ReactNode> = {
  ativar: <Play className="h-4 w-4" />,
  suspender: <Pause className="h-4 w-4" />,
  encerrar: <XCircle className="h-4 w-4" />,
  reabrir: <RotateCcw className="h-4 w-4" />,
};

export default function ProcessoDetailPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canViewProcessos = permissions.can.view("processos");
  const canEditProcessos = permissions.can.edit("processos");
  const canManageProcessos = permissions.can.manage("processos");

  if (!permissions.isLoading && !canViewProcessos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este processo."
        backHref="/processos"
      />
    );
  }

  return <ProcessoDetailContent id={id} canEditProcessos={canEditProcessos} canManageProcessos={canManageProcessos} />;
}

function ProcessoDetailContent({ id, canEditProcessos, canManageProcessos }: { id: string; canEditProcessos: boolean; canManageProcessos: boolean }) {
  const [tab, setTab] = React.useState<TabKey>("timeline");
  const [formalizarError, setFormalizarError] = React.useState<string | null>(null);

  // Workflow state
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [activeTransicao, setActiveTransicao] = React.useState<TransicaoInfo | null>(null);
  const [transicaoError, setTransicaoError] = React.useState<string | null>(null);

  // Prazo state
  const [prazoDialogOpen, setPrazoDialogOpen] = React.useState(false);
  const [prazoError, setPrazoError] = React.useState<string | null>(null);

  const processo = useProcesso(id);
  const clientes = useClientes({});
  const adminUsers = useAdminUsers();
  const decisao = useConflictCheckDecisao(id);
  const formalizarProcesso = useFormalizarProcesso(id);

  const partes = useProcessoPartes(id);
  const fases = useProcessoFases(id);
  const movimentacoes = useProcessoMovimentacoes(id);

  // Workflow hooks
  const workflow = useWorkflow(id);
  const prazos = usePrazos(id);
  const transicao = useExecutarTransicao(id);
  const createPrazo = useCreatePrazo(id);
  const toggleConcluido = useTogglePrazoConcluido(id);

  const addParte = useAddProcessoParte(id);
  const addFase = useAddProcessoFase(id);
  const updateFaseStatus = useUpdateProcessoFaseStatus(id);
  const addMov = useAddProcessoMovimentacao(id);

  // Timeline filter state
  const [selectedTipos, setSelectedTipos] = React.useState<Set<TimelineItemType>>(
    new Set(["movimentacao", "transicao", "evento", "documento", "decisao"]),
  );
  const [dateFrom, setDateFrom] = React.useState<string>("");
  const [dateTo, setDateTo] = React.useState<string>("");

  // Timeline and audit hooks
  const timeline = useTimeline(processo.data?.id ?? "");
  const auditLog = useAuditLog(processo.data?.id ?? "");

  // Filtered timeline items (client-side, no re-fetch)
  const filteredItems = React.useMemo(() => {
    return (timeline.data ?? []).filter((item: TimelineItem) => {
      if (!selectedTipos.has(item.tipo)) return false;
      if (dateFrom && item.timestamp < dateFrom) return false;
      if (dateTo && item.timestamp > dateTo + "T23:59:59") return false;
      return true;
    });
  }, [timeline.data, selectedTipos, dateFrom, dateTo]);

  const clienteNomeById = new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const));
  const userNomeById = new Map((adminUsers.data ?? []).map((u) => [u.id, u.nome] as const));

  const isLoading =
    processo.isLoading || clientes.isLoading || partes.isLoading || fases.isLoading || movimentacoes.isLoading;
  const isError =
    processo.isError || clientes.isError || partes.isError || fases.isError || movimentacoes.isError;

  const parteForm = useForm<ProcessoParteFormValues>({
    resolver: zodResolver(processoParteFormSchema),
    defaultValues: { tipo: undefined, nome: "", nif: undefined },
  });
  const [parteServerError, setParteServerError] = React.useState<string | null>(null);

  const faseForm = useForm<ProcessoFaseFormValues>({
    resolver: zodResolver(processoFaseFormSchema),
    defaultValues: { fase_id: "", status: "PENDENTE" },
  });
  const [faseServerError, setFaseServerError] = React.useState<string | null>(null);

  const movForm = useForm<ProcessoMovimentacaoFormValues>({
    resolver: zodResolver(processoMovimentacaoFormSchema),
    defaultValues: { titulo: "", descricao: undefined, data: undefined },
  });
  const [movServerError, setMovServerError] = React.useState<string | null>(null);

  const [faseDraftStatus, setFaseDraftStatus] = React.useState<Record<string, ProcessoFaseStatus>>({});

  // Justificativa form for critical transitions
  const justificativaForm = useForm<TransicaoJustificativaFormValues>({
    resolver: zodResolver(transicaoJustificativaFormSchema),
    defaultValues: { justificativa: "" },
  });

  // Prazo form for Novo Prazo dialog
  // The Zod schema uses .default("MEDIA") which makes prioridade optional at input
  // but required in output — the resolver type inference diverges from RHF's TFieldValues
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const prazoForm = useForm<PrazoFormValues>({ resolver: zodResolver(prazoFormSchema) as any, defaultValues: { descricao: "", dataLimite: "", prioridade: "MEDIA" as const, responsavelId: undefined } });

  const isFormalizarBlocked =
    !decisao.data || decisao.data.nivel === "impeditivo";

  const formalizarBlockReason = !decisao.data
    ? "Não é possível formalizar: o conflict check ainda não tem uma decisão registada."
    : decisao.data.nivel === "impeditivo"
      ? "Não é possível formalizar: existe um conflito impeditivo registado."
      : null;

  const onFormalizar = async () => {
    setFormalizarError(null);
    try {
      await formalizarProcesso.mutateAsync();
      toast.success("Processo formalizado com sucesso.");
    } catch (e) {
      setFormalizarError(e instanceof Error ? e.message : "Erro ao formalizar o processo");
      toast.error(e instanceof Error ? e.message : "Erro ao formalizar o processo");
    }
  };

  // Workflow transition handlers
  const onTransicaoClick = (t: TransicaoInfo) => {
    setTransicaoError(null);
    if (t.requerJustificativa) {
      setActiveTransicao(t);
      justificativaForm.reset({ justificativa: "" });
      setDialogOpen(true);
    } else {
      void onSubmitTransicaoDirecta(t.acao);
    }
  };

  const onSubmitTransicaoDirecta = async (acao: string) => {
    try {
      await transicao.mutateAsync({ acao });
      toast.success("Transição executada com sucesso.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Não foi possível executar a transição. Verifique os requisitos e tente novamente.";
      setTransicaoError(msg);
      toast.error(msg);
    }
  };

  const onSubmitTransicao = async (values: TransicaoJustificativaFormValues) => {
    if (!activeTransicao) return;
    try {
      await transicao.mutateAsync({
        acao: activeTransicao.acao,
        payload: { justificativa: values.justificativa },
      });
      setDialogOpen(false);
      toast.success("Transição com justificativa executada com sucesso.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Não foi possível executar a transição. Verifique os requisitos e tente novamente.";
      setTransicaoError(msg);
      toast.error(msg);
    }
  };

  // Prazo handlers
  const onSubmitPrazo = async (values: PrazoFormValues) => {
    setPrazoError(null);
    try {
      await createPrazo.mutateAsync({
        descricao: values.descricao,
        dataLimite: values.dataLimite,
        prioridade: values.prioridade,
        responsavelId: values.responsavelId || undefined,
      });
      prazoForm.reset({ descricao: "", dataLimite: "", prioridade: "MEDIA", responsavelId: undefined });
      setPrazoDialogOpen(false);
      toast.success("Prazo guardado com sucesso.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Não foi possível guardar o prazo. Tente novamente.";
      setPrazoError(msg);
      toast.error(msg);
    }
  };

  const onToggleConcluido = async (prazoId: string, concluido: boolean) => {
    try {
      await toggleConcluido.mutateAsync({ prazoId, concluido });
      toast.success("Estado do prazo atualizado.");
    } catch {
      setPrazoError("Erro ao atualizar o prazo. Tente novamente.");
      toast.error("Erro ao atualizar o prazo.");
    }
  };

  const onSubmitParte = async (values: ProcessoParteFormValues) => {
    setParteServerError(null);
    if (!canEditProcessos) return;
    try {
      await addParte.mutateAsync(values satisfies ProcessoParteCreateRequest);
      parteForm.reset({ tipo: undefined, nome: "", nif: undefined });
      toast.success("Parte adicionada ao processo.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao adicionar parte";
      setParteServerError(msg);
      toast.error(msg);
    }
  };

  const onSubmitFase = async (values: ProcessoFaseFormValues) => {
    setFaseServerError(null);
    if (!canEditProcessos) return;
    try {
      await addFase.mutateAsync(values satisfies ProcessoFaseCreateRequest);
      faseForm.reset({ fase_id: "", status: "PENDENTE" });
      toast.success("Fase adicionada ao processo.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao adicionar fase";
      setFaseServerError(msg);
      toast.error(msg);
    }
  };

  const onUpdateFaseStatus = async (faseId: string) => {
    const status = faseDraftStatus[faseId];
    const payload: ProcessoFaseUpdateRequest = { status };
    try {
      await updateFaseStatus.mutateAsync({ faseId, payload });
      toast.success("Status da fase atualizado.");
    } catch {
      setFaseServerError("Erro ao atualizar status da fase");
      toast.error("Erro ao atualizar status da fase");
    }
  };

  const onSubmitMov = async (values: ProcessoMovimentacaoFormValues) => {
    setMovServerError(null);
    if (!canEditProcessos) return;
    try {
      const data = values.data ? new Date(values.data).toISOString() : undefined;
      await addMov.mutateAsync({
        titulo: values.titulo,
        descricao: values.descricao,
        data,
      } satisfies ProcessoMovimentacaoCreateRequest);
      movForm.reset({ titulo: "", descricao: undefined, data: undefined });
      toast.success("Movimentação registada.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao adicionar movimentação";
      setMovServerError(msg);
      toast.error(msg);
    }
  };

  // Compute estado display values from processo data
  const estadoRaw = (processo.data?.estado ?? "").toUpperCase();
  const estadoVariant =
    estadoRaw === "ATIVO"
      ? ("green" as const)
      : estadoRaw === "SUSPENSO"
        ? ("amber" as const)
        : estadoRaw === "TRIAGEM"
          ? ("purple" as const)
          : estadoRaw === "CONCLUIDO" || estadoRaw === "ENCERRADO"
            ? ("gray" as const)
            : ("secondary" as const);
  const estadoLabel = estadoRaw === "TRIAGEM" ? "EM TRIAGEM" : (processo.data?.estado ?? "—");

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Processo</h1>
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            <Link href="/processos" className="hover:underline">
              Processos
            </Link>{" "}
            <span>/</span>{" "}
            <span className="text-neutral-900 dark:text-neutral-50">
              {processo.data?.numero ?? processo.data?.titulo ?? "…"}
            </span>
          </div>
        </div>

        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href="/processos">Voltar</Link>
          </Button>
          {canEditProcessos ? (
            <Button asChild>
              <Link href={`/processos/${encodeURIComponent(id)}/editar`}>Editar</Link>
            </Button>
          ) : null}
        </div>
      </div>

      {isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : isError ? (
        <div className="text-sm text-red-600">
          {processo.error instanceof Error
            ? processo.error.message
            : clientes.error instanceof Error
              ? clientes.error.message
              : partes.error instanceof Error
                ? partes.error.message
                : fases.error instanceof Error
                  ? fases.error.message
                  : movimentacoes.error instanceof Error
                    ? movimentacoes.error.message
                    : "Erro ao carregar"}
        </div>
      ) : processo.data ? (
        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Dados</CardTitle>
            </CardHeader>
            <CardContent>
              <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                <dt className="text-neutral-500 dark:text-neutral-400">Número</dt>
                <dd className="col-span-2 font-medium">{processo.data.numero ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Tipo do processo</dt>
                <dd className="col-span-2">{processo.data.tipo_processo ?? processo.data.titulo ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Área jurídica</dt>
                <dd className="col-span-2">{processo.data.area_juridica ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Tribunal</dt>
                <dd className="col-span-2">{processo.data.tribunal ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Cliente</dt>
                <dd className="col-span-2">
                  {clienteNomeById.get(processo.data.cliente_id) ?? processo.data.cliente_id}
                </dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Estado</dt>
                <dd className="col-span-2">{processo.data.estado ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Data de início</dt>
                <dd className="col-span-2">{processo.data.data_inicio ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Data de fim</dt>
                <dd className="col-span-2">{processo.data.data_fim ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Legal Hold</dt>
                <dd className="col-span-2">
                  {processo.data.legal_hold ? (
                    <Badge variant="red" className="rounded-none font-bold tracking-wide">
                      ATIVO
                    </Badge>
                  ) : (
                    <span className="text-neutral-400">Inativo</span>
                  )}
                </dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Data Retenção</dt>
                <dd className="col-span-2">{processo.data.data_retencao ? formatDate(processo.data.data_retencao) : "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Criado</dt>
                <dd className="col-span-2">{formatDateTime(processo.data.created_at)}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Atualizado</dt>
                <dd className="col-span-2">{formatDateTime(processo.data.updated_at)}</dd>
              </dl>
            </CardContent>
          </Card>

          {/* Conflict Check section — visible when TRIAGEM or decisao exists */}
          {(processo.data?.estado === "TRIAGEM" || decisao.data) ? (
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Conflict Check</CardTitle>
                  {decisao.data ? (
                    <Badge
                      variant={conflictNivelToVariant(decisao.data.nivel)}
                      className="rounded-none font-bold tracking-wide"
                    >
                      {conflictNivelToLabel(decisao.data.nivel)}
                    </Badge>
                  ) : null}
                </div>
              </CardHeader>
              <CardContent className="space-y-4">
                {decisao.data ? (
                  <>
                    <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                      <dt className="text-neutral-500 dark:text-neutral-400">Nível</dt>
                      <dd className="col-span-2 font-medium">
                        <Badge
                          variant={conflictNivelToVariant(decisao.data.nivel)}
                          className="rounded-none font-bold tracking-wide"
                        >
                          {conflictNivelToLabel(decisao.data.nivel)}
                        </Badge>
                      </dd>

                      <dt className="text-neutral-500 dark:text-neutral-400">Decisor</dt>
                      <dd className="col-span-2 font-medium">{decisao.data.decisorId}</dd>

                      <dt className="text-neutral-500 dark:text-neutral-400">Data</dt>
                      <dd className="col-span-2 font-medium">{decisao.data.dataDecisao}</dd>

                      {decisao.data.referenciaEvidencia ? (
                        <>
                          <dt className="text-neutral-500 dark:text-neutral-400">Referência</dt>
                          <dd className="col-span-2 font-medium">{decisao.data.referenciaEvidencia}</dd>
                        </>
                      ) : null}
                    </dl>

                    {decisao.data.justificativa ? (
                      <div className="space-y-1">
                        <div className="text-sm text-neutral-500 dark:text-neutral-400">Justificativa</div>
                        <p className="text-sm font-medium">{decisao.data.justificativa}</p>
                      </div>
                    ) : null}
                  </>
                ) : (
                  <div className="text-sm text-slate-500 dark:text-slate-400">
                    O conflict check ainda não foi executado para este processo.
                  </div>
                )}

                {/* Inline Formalizar action for TRIAGEM state */}
                {processo.data?.estado === "TRIAGEM" && canManageProcessos ? (
                  <div className="pt-2 space-y-2">
                    <Button
                      type="button"
                      className={`rounded-none font-bold shadow-none bg-blue-600 hover:bg-blue-700 text-white ${isFormalizarBlocked ? "opacity-50 cursor-not-allowed" : ""}`}
                      disabled={isFormalizarBlocked || formalizarProcesso.isPending}
                      onClick={onFormalizar}
                    >
                      {formalizarProcesso.isPending ? "A formalizar..." : "Formalizar Processo"}
                    </Button>
                    {isFormalizarBlocked && formalizarBlockReason ? (
                      <p className="text-sm text-red-600">{formalizarBlockReason}</p>
                    ) : null}
                    {formalizarError && !isFormalizarBlocked ? (
                      <p className="text-sm text-red-600">{formalizarError}</p>
                    ) : null}
                  </div>
                ) : null}
              </CardContent>
            </Card>
          ) : null}

          {/* ── Workflow card ── */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Workflow</CardTitle>
                <Badge
                  variant={estadoVariant as "green" | "amber" | "gray" | "purple" | "secondary"}
                  className="rounded-none font-bold tracking-wide"
                >
                  {estadoLabel}
                </Badge>
              </div>
            </CardHeader>
            <CardContent className="space-y-4">
              {workflow.isLoading ? (
                <p className="text-sm text-slate-500 dark:text-slate-400">A carregar workflow...</p>
              ) : workflow.isError ? (
                <p className="text-sm text-red-600">Erro ao carregar o workflow. Tente novamente.</p>
              ) : workflow.data ? (
                <>
                  {/* dl grid — Estado / Responsável / Próximo Passo */}
                  <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                    <dt className="text-neutral-500 dark:text-neutral-400">Estado</dt>
                    <dd className="col-span-2 font-medium">
                      <Badge
                        variant={estadoVariant as "green" | "amber" | "gray" | "purple" | "secondary"}
                        className="rounded-none font-bold tracking-wide"
                      >
                        {estadoLabel}
                      </Badge>
                    </dd>
                    <dt className="text-neutral-500 dark:text-neutral-400">Responsável</dt>
                    <dd className="col-span-2 font-medium flex items-center gap-1">
                      <User className="h-[14px] w-[14px] text-slate-400" />
                      {workflow.data.responsavelNome ? (
                        workflow.data.responsavelNome
                      ) : (
                        <span className="text-slate-400 dark:text-slate-500 italic">Não atribuído</span>
                      )}
                    </dd>
                    <dt className="text-neutral-500 dark:text-neutral-400">Próximo Passo</dt>
                    <dd className="col-span-2 text-sm text-slate-500 dark:text-slate-400">
                      {workflow.data.proximoPasso ?? "—"}
                    </dd>
                  </dl>

                  {/* Transitions section */}
                  <div className="pt-4 border-t border-slate-200 dark:border-slate-800">
                    <p className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400 mb-2">
                      AÇÕES DE TRANSIÇÃO
                    </p>
                    {transicaoError ? (
                      <p className="text-sm text-red-600 mb-2">{transicaoError}</p>
                    ) : null}
                    {workflow.data.transicoesDisponiveis.length === 0 ? (
                      <p className="text-sm text-slate-500 dark:text-slate-400">
                        Nenhuma transição disponível neste estado.
                      </p>
                    ) : (
                      <div className="flex flex-wrap gap-2">
                        {workflow.data.transicoesDisponiveis.map((t) => {
                          const isCritical = t.permissaoNecessaria === "processos:manage";
                          const hasPermission = isCritical ? canManageProcessos : canEditProcessos;
                          return (
                            <Button
                              key={t.acao}
                              type="button"
                              variant={isCritical ? "outline" : "default"}
                              disabled={!hasPermission || transicao.isPending}
                              title={
                                !hasPermission
                                  ? `Sem permissão: requer ${t.permissaoNecessaria}`
                                  : undefined
                              }
                              className={
                                isCritical
                                  ? "border-slate-300 dark:border-slate-700 rounded-none font-bold h-10 px-4 text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800"
                                  : "bg-blue-600 hover:bg-blue-700 text-white rounded-none font-bold h-10 px-4"
                              }
                              onClick={() => onTransicaoClick(t)}
                            >
                              {ACAO_ICONS[t.acao] ?? null}
                              {t.label}
                            </Button>
                          );
                        })}
                      </div>
                    )}
                  </div>
                </>
              ) : null}
            </CardContent>
          </Card>

          {/* ── Justification Dialog (critical transitions) ── */}
          <Dialog open={dialogOpen} onOpenChange={(open) => { setDialogOpen(open); if (!open) setTransicaoError(null); }}>
            <DialogContent className="rounded-none shadow-2xl max-sm:fixed max-sm:bottom-0 max-sm:left-0 max-sm:right-0 max-sm:top-auto max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-t-xl max-sm:rounded-b-none max-sm:w-full max-sm:max-w-none">
              <DialogHeader>
                <DialogTitle className="font-bold">{activeTransicao?.label} Processo</DialogTitle>
                <DialogDescription>
                  Esta ação requer justificativa e regista uma movimentação no processo.
                </DialogDescription>
              </DialogHeader>
              <form onSubmit={justificativaForm.handleSubmit(onSubmitTransicao)}>
                <div className="space-y-4 py-4">
                  <div className="space-y-2">
                    <Label htmlFor="justificativa">Justificativa</Label>
                    <Textarea
                      id="justificativa"
                      placeholder="Descreva o motivo desta transição (mínimo 10 caracteres)..."
                      className="min-h-[100px] rounded-none border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                      {...justificativaForm.register("justificativa")}
                    />
                    {justificativaForm.formState.errors.justificativa ? (
                      <p className="text-sm text-red-600">
                        {justificativaForm.formState.errors.justificativa.message}
                      </p>
                    ) : null}
                  </div>
                </div>
                <DialogFooter>
                  <Button
                    type="button"
                    variant="ghost"
                    className="rounded-none"
                    onClick={() => setDialogOpen(false)}
                  >
                    Cancelar
                  </Button>
                  <Button
                    type="submit"
                    disabled={transicao.isPending}
                    className="rounded-none font-bold bg-blue-600 hover:bg-blue-700"
                  >
                    {transicao.isPending ? "A processar..." : "Confirmar"}
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>

          {/* ── Prazos card ── */}
          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle>Prazos</CardTitle>
                {canEditProcessos ? (
                  <Button
                    type="button"
                    className="rounded-none font-bold bg-blue-600 hover:bg-blue-700 text-white h-10 px-4"
                    onClick={() => {
                      setPrazoError(null);
                      prazoForm.reset({ descricao: "", dataLimite: "", prioridade: "MEDIA", responsavelId: undefined });
                      setPrazoDialogOpen(true);
                    }}
                  >
                    <Plus className="h-4 w-4 mr-1" />
                    Novo Prazo
                  </Button>
                ) : null}
              </div>
            </CardHeader>
            <CardContent className="p-0">
              {prazoError ? (
                <p className="px-4 pt-3 text-sm text-red-600">{prazoError}</p>
              ) : null}
              {prazos.isLoading ? (
                <p className="p-6 text-sm text-slate-500 dark:text-slate-400">A carregar prazos...</p>
              ) : prazos.isError ? (
                <p className="p-6 text-sm text-red-600">Erro ao carregar os prazos. Tente novamente.</p>
              ) : (prazos.data ?? []).length === 0 ? (
                <p className="p-6 text-sm text-slate-500 dark:text-slate-400">
                  Nenhum prazo registado para este processo.
                </p>
              ) : (
                <div className="divide-y divide-slate-100 dark:divide-slate-800">
                  {(prazos.data ?? []).map((p) => (
                    <div
                      key={p.id}
                      className="px-4 py-3 flex items-center justify-between gap-4 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors"
                    >
                      {/* Left: descricao + data_limite */}
                      <div className="flex-1 min-w-0">
                        <div className="font-medium text-sm text-slate-900 dark:text-white truncate">
                          {p.descricao}
                        </div>
                        <div className="text-xs text-slate-500 dark:text-slate-400">
                          {formatDate(p.dataLimite)}
                        </div>
                      </div>

                      {/* Center/meta: prioridade + responsável */}
                      <div className="flex items-center gap-2 shrink-0">
                        <span className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
                          {p.prioridade}
                        </span>
                        {p.responsavelId ? (
                          <span className="text-xs text-slate-500 dark:text-slate-400">
                            {userNomeById.get(p.responsavelId) ?? p.responsavelId}
                          </span>
                        ) : null}
                      </div>

                      {/* Right: risco badge + escalonado icon + concluido toggle */}
                      <div className="flex items-center gap-2 shrink-0">
                        <Badge
                          variant={prazosRiscoToVariant(p.risco)}
                          className="rounded-none font-bold tracking-wide text-[11px]"
                        >
                          {prazosRiscoToLabel(p.risco)}
                        </Badge>
                        {p.escalonado ? (
                          <span title="Prazo escalado">
                            <AlertCircle
                              className="h-3 w-3 text-amber-500 dark:text-amber-400 inline-block"
                              aria-label="Prazo escalado"
                            />
                          </span>
                        ) : null}
                        {canEditProcessos ? (
                          <button
                            type="button"
                            title="Marcar como concluído"
                            onClick={() => void onToggleConcluido(p.id, !p.concluido)}
                            className="cursor-pointer"
                          >
                            {p.concluido ? (
                              <CheckCircle2 className="h-[14px] w-[14px] text-emerald-600 dark:text-emerald-400 hover:opacity-70 transition-opacity" />
                            ) : (
                              <Circle className="h-[14px] w-[14px] text-slate-300 dark:text-slate-600 hover:text-slate-500 transition-colors" />
                            )}
                          </button>
                        ) : null}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>

          {/* ── Novo Prazo Dialog ── */}
          <Dialog open={prazoDialogOpen} onOpenChange={setPrazoDialogOpen}>
            <DialogContent className="rounded-none shadow-2xl max-sm:fixed max-sm:bottom-0 max-sm:left-0 max-sm:right-0 max-sm:top-auto max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-t-xl max-sm:rounded-b-none max-sm:w-full max-sm:max-w-none">
              <DialogHeader>
                <DialogTitle className="font-bold">Novo Prazo</DialogTitle>
              </DialogHeader>
              {/* eslint-disable-next-line @typescript-eslint/no-explicit-any */}
              <form onSubmit={prazoForm.handleSubmit(onSubmitPrazo as any)}>
                <div className="space-y-4 py-4">
                  <div className="space-y-2">
                    <Label htmlFor="prazo_descricao">Descrição</Label>
                    <Input
                      id="prazo_descricao"
                      className="rounded-none border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                      {...prazoForm.register("descricao")}
                    />
                    {prazoForm.formState.errors.descricao ? (
                      <p className="text-sm text-red-600">
                        {prazoForm.formState.errors.descricao.message}
                      </p>
                    ) : null}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="prazo_dataLimite">Data Limite</Label>
                    <input
                      id="prazo_dataLimite"
                      type="date"
                      className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                      {...prazoForm.register("dataLimite")}
                    />
                    {prazoForm.formState.errors.dataLimite ? (
                      <p className="text-sm text-red-600">
                        {prazoForm.formState.errors.dataLimite.message}
                      </p>
                    ) : null}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="prazo_prioridade">Prioridade</Label>
                    <select
                      id="prazo_prioridade"
                      className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                      {...prazoForm.register("prioridade")}
                    >
                      <option value="ALTA">ALTA</option>
                      <option value="MEDIA">MÉDIA</option>
                      <option value="BAIXA">BAIXA</option>
                    </select>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="prazo_responsavel">Responsável</Label>
                    <select
                      id="prazo_responsavel"
                      className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                      {...prazoForm.register("responsavelId")}
                    >
                      <option value="">— Sem responsável —</option>
                      {(adminUsers.data ?? []).map((u) => (
                        <option key={u.id} value={u.id}>
                          {u.nome}
                        </option>
                      ))}
                    </select>
                  </div>

                  {prazoError ? (
                    <p className="text-sm text-red-600">{prazoError}</p>
                  ) : null}
                </div>
                <DialogFooter>
                  <Button
                    type="button"
                    variant="ghost"
                    className="rounded-none"
                    onClick={() => setPrazoDialogOpen(false)}
                  >
                    Cancelar
                  </Button>
                  <Button
                    type="submit"
                    disabled={createPrazo.isPending}
                    className="rounded-none font-bold bg-blue-600 hover:bg-blue-700"
                  >
                    {createPrazo.isPending ? "A processar..." : "Guardar Prazo"}
                  </Button>
                </DialogFooter>
              </form>
            </DialogContent>
          </Dialog>

          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant={tab === "timeline" ? "secondary" : "outline"}
              onClick={() => setTab("timeline")}
            >
              Timeline
            </Button>
            <Button
              type="button"
              variant={tab === "partes" ? "secondary" : "outline"}
              onClick={() => setTab("partes")}
            >
              Partes
            </Button>
            <Button
              type="button"
              variant={tab === "fases" ? "secondary" : "outline"}
              onClick={() => setTab("fases")}
            >
              Fases
            </Button>
            {canManageProcessos ? (
              <Button
                type="button"
                variant={tab === "auditoria" ? "secondary" : "outline"}
                onClick={() => setTab("auditoria")}
              >
                Auditoria
              </Button>
            ) : null}
          </div>

          {tab === "timeline" ? (
            <Card>
              <CardHeader>
                <CardTitle>Timeline</CardTitle>
                {/* Filter bar */}
                <div className="flex flex-wrap gap-2 items-end mt-3">
                  {/* Tipo filter chips */}
                  <button
                    type="button"
                    aria-pressed={selectedTipos.has("movimentacao")}
                    className={
                      selectedTipos.has("movimentacao")
                        ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 rounded-none h-8 px-3 text-xs"
                        : "border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-transparent text-slate-700 dark:text-slate-300 rounded-none h-8 px-3 text-xs hover:bg-slate-50 dark:hover:bg-slate-800"
                    }
                    onClick={() =>
                      setSelectedTipos((prev) => {
                        const next = new Set(prev);
                        if (next.has("movimentacao")) {
                          next.delete("movimentacao");
                        } else {
                          next.add("movimentacao");
                        }
                        return next;
                      })
                    }
                  >
                    Movimentações
                  </button>
                  <button
                    type="button"
                    aria-pressed={selectedTipos.has("transicao")}
                    className={
                      selectedTipos.has("transicao")
                        ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 rounded-none h-8 px-3 text-xs"
                        : "border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-transparent text-slate-700 dark:text-slate-300 rounded-none h-8 px-3 text-xs hover:bg-slate-50 dark:hover:bg-slate-800"
                    }
                    onClick={() =>
                      setSelectedTipos((prev) => {
                        const next = new Set(prev);
                        if (next.has("transicao")) {
                          next.delete("transicao");
                        } else {
                          next.add("transicao");
                        }
                        return next;
                      })
                    }
                  >
                    Transições
                  </button>
                  <button
                    type="button"
                    aria-pressed={selectedTipos.has("evento")}
                    className={
                      selectedTipos.has("evento")
                        ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 rounded-none h-8 px-3 text-xs"
                        : "border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-transparent text-slate-700 dark:text-slate-300 rounded-none h-8 px-3 text-xs hover:bg-slate-50 dark:hover:bg-slate-800"
                    }
                    onClick={() =>
                      setSelectedTipos((prev) => {
                        const next = new Set(prev);
                        if (next.has("evento")) {
                          next.delete("evento");
                        } else {
                          next.add("evento");
                        }
                        return next;
                      })
                    }
                  >
                    Eventos
                  </button>
                  <button
                    type="button"
                    aria-pressed={selectedTipos.has("documento")}
                    className={
                      selectedTipos.has("documento")
                        ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 rounded-none h-8 px-3 text-xs"
                        : "border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-transparent text-slate-700 dark:text-slate-300 rounded-none h-8 px-3 text-xs hover:bg-slate-50 dark:hover:bg-slate-800"
                    }
                    onClick={() =>
                      setSelectedTipos((prev) => {
                        const next = new Set(prev);
                        if (next.has("documento")) {
                          next.delete("documento");
                        } else {
                          next.add("documento");
                        }
                        return next;
                      })
                    }
                  >
                    Documentos
                  </button>
                  <button
                    type="button"
                    aria-pressed={selectedTipos.has("decisao")}
                    className={
                      selectedTipos.has("decisao")
                        ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 rounded-none h-8 px-3 text-xs"
                        : "border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-transparent text-slate-700 dark:text-slate-300 rounded-none h-8 px-3 text-xs hover:bg-slate-50 dark:hover:bg-slate-800"
                    }
                    onClick={() =>
                      setSelectedTipos((prev) => {
                        const next = new Set(prev);
                        if (next.has("decisao")) {
                          next.delete("decisao");
                        } else {
                          next.add("decisao");
                        }
                        return next;
                      })
                    }
                  >
                    Decisões
                  </button>
                  {/* Date range inputs */}
                  <div className="flex items-center gap-1">
                    <Label htmlFor="timeline_date_from" className="text-xs text-slate-500 shrink-0">
                      De
                    </Label>
                    <Input
                      id="timeline_date_from"
                      type="date"
                      className="h-8 text-xs rounded-none w-36"
                      value={dateFrom}
                      onChange={(e) => setDateFrom(e.target.value)}
                    />
                  </div>
                  <div className="flex items-center gap-1">
                    <Label htmlFor="timeline_date_to" className="text-xs text-slate-500 shrink-0">
                      Até
                    </Label>
                    <Input
                      id="timeline_date_to"
                      type="date"
                      className="h-8 text-xs rounded-none w-36"
                      value={dateTo}
                      onChange={(e) => setDateTo(e.target.value)}
                    />
                  </div>
                  <Button
                    type="button"
                    variant="ghost"
                    className="h-8 px-3 text-xs rounded-none"
                    onClick={() => {
                      setSelectedTipos(new Set(["movimentacao", "transicao", "evento", "documento", "decisao"]));
                      setDateFrom("");
                      setDateTo("");
                    }}
                  >
                    Limpar filtros
                  </Button>
                </div>
              </CardHeader>
              <CardContent>
                {timeline.isLoading ? (
                  <div className="space-y-4">
                    <div className="animate-pulse bg-slate-100 dark:bg-slate-800 rounded h-12 mb-4" />
                    <div className="animate-pulse bg-slate-100 dark:bg-slate-800 rounded h-12 mb-4" />
                    <div className="animate-pulse bg-slate-100 dark:bg-slate-800 rounded h-12 mb-4" />
                  </div>
                ) : timeline.isError ? (
                  <p className="text-red-600 text-sm py-4">
                    Não foi possível carregar a timeline. Verifique a ligação e tente novamente.
                  </p>
                ) : filteredItems.length === 0 ? (
                  <div className="py-12 text-center">
                    <p className="text-sm font-medium text-slate-500">Sem entradas na timeline</p>
                    <p className="text-xs text-slate-400 mt-1">
                      As movimentações, eventos e documentos do processo aparecem aqui assim que forem registados.
                    </p>
                  </div>
                ) : (
                  <div className="relative">
                    {filteredItems.map((item: TimelineItem, index: number) => {
                      const isLast = index === filteredItems.length - 1;
                      const dotColor =
                        item.tipo === "movimentacao"
                          ? "bg-emerald-500"
                          : item.tipo === "transicao"
                            ? "bg-blue-600"
                            : item.tipo === "evento"
                              ? "bg-amber-500"
                              : item.tipo === "documento"
                                ? "bg-slate-400"
                                : "bg-purple-500"; /* decisao */
                      const IconComponent =
                        item.tipo === "movimentacao"
                          ? FileText
                          : item.tipo === "transicao"
                            ? GitBranch
                            : item.tipo === "evento"
                              ? Calendar
                              : item.tipo === "documento"
                                ? Paperclip
                                : ShieldCheck; /* decisao */
                      const iconColor =
                        item.tipo === "movimentacao"
                          ? "text-emerald-500"
                          : item.tipo === "transicao"
                            ? "text-blue-600"
                            : item.tipo === "evento"
                              ? "text-amber-500"
                              : item.tipo === "documento"
                                ? "text-slate-400"
                                : "text-purple-500"; /* decisao */
                      const tipoLabel =
                        item.tipo === "movimentacao"
                          ? "Movimentação"
                          : item.tipo === "transicao"
                            ? "Transição"
                            : item.tipo === "evento"
                              ? "Evento"
                              : item.tipo === "documento"
                                ? "Documento"
                                : "Decisão";
                      const formattedDate = formatDateTime(item.timestamp);
                      return (
                        <div key={item.tipo + item.id} className="relative flex gap-3 py-4">
                          {/* Left column: dot + connector line */}
                          <div className="relative flex flex-col items-center">
                            <span className={`h-2.5 w-2.5 rounded-full shrink-0 ${dotColor}`} />
                            {!isLast ? (
                              <div className="absolute top-3 bottom-0 left-[5px] w-0.5 bg-slate-200 dark:bg-slate-700" />
                            ) : null}
                          </div>
                          {/* Right column: icon + content */}
                          <div className="flex gap-2 pb-4 min-w-0 flex-1">
                            <div
                              aria-label={`${tipoLabel} em ${formattedDate}`}
                              className="shrink-0 mt-0.5"
                            >
                              <IconComponent className={`h-4 w-4 ${iconColor}`} />
                            </div>
                            <div className="min-w-0 flex-1">
                              <p className="text-sm font-medium text-slate-900 dark:text-white">{item.titulo}</p>
                              <p className="text-xs text-slate-500 dark:text-slate-400">{formattedDate}</p>
                              {item.descricao ? (
                                <p className="text-sm text-slate-600 dark:text-slate-400 mt-1 whitespace-pre-wrap">
                                  {item.descricao}
                                </p>
                              ) : null}
                              {item.autorNome ? (
                                <p className="text-xs text-slate-400 dark:text-slate-500 italic mt-0.5">
                                  {item.autorNome}
                                </p>
                              ) : null}
                            </div>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>
          ) : tab === "partes" ? (
            <div className="grid gap-4 lg:grid-cols-2">
              {canEditProcessos ? (
                <Card>
                  <CardHeader>
                    <CardTitle>Adicionar parte</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <form className="space-y-4" onSubmit={parteForm.handleSubmit(onSubmitParte)}>
                      <div className="space-y-2">
                        <Label htmlFor="parte_nome">Nome</Label>
                        <Input id="parte_nome" {...parteForm.register("nome")} />
                        {parteForm.formState.errors.nome ? (
                          <p className="text-sm text-red-600">{parteForm.formState.errors.nome.message}</p>
                        ) : null}
                      </div>

                      <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                        <div className="space-y-2">
                          <Label htmlFor="parte_tipo">Tipo</Label>
                          <Input id="parte_tipo" {...parteForm.register("tipo")} placeholder="Ex.: Autor / Réu" />
                          {parteForm.formState.errors.tipo ? (
                            <p className="text-sm text-red-600">{parteForm.formState.errors.tipo.message}</p>
                          ) : null}
                        </div>
                        <div className="space-y-2">
                          <Label htmlFor="parte_nif">NIF</Label>
                          <Input id="parte_nif" {...parteForm.register("nif")} />
                          {parteForm.formState.errors.nif ? (
                            <p className="text-sm text-red-600">{parteForm.formState.errors.nif.message}</p>
                          ) : null}
                        </div>
                      </div>

                      {parteServerError ? <p className="text-sm text-red-600">{parteServerError}</p> : null}

                      <Button type="submit" disabled={parteForm.formState.isSubmitting || addParte.isPending}>
                        {parteForm.formState.isSubmitting || addParte.isPending ? "A guardar..." : "Adicionar"}
                      </Button>
                    </form>
                  </CardContent>
                </Card>
              ) : null}

              <Card>
                <CardHeader>
                  <CardTitle>Partes</CardTitle>
                </CardHeader>
                <CardContent>
                  {!partes.data?.length ? (
                    <div className="text-sm text-neutral-500 dark:text-neutral-400">Sem partes.</div>
                  ) : (
                    <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
                      <table className="w-full min-w-[400px] text-sm">
                        <thead className="text-left text-neutral-500 dark:text-neutral-400">
                          <tr className="border-b border-neutral-200 dark:border-neutral-800">
                            <th className="py-2 pr-4 font-medium">Tipo</th>
                            <th className="py-2 pr-4 font-medium">Nome</th>
                            <th className="py-2 pr-4 font-medium">NIF</th>
                          </tr>
                        </thead>
                        <tbody>
                          {partes.data.map((p) => (
                            <tr
                              key={p.id}
                              className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800"
                            >
                              <td className="py-2 pr-4">{p.tipo ?? "—"}</td>
                              <td className="py-2 pr-4 font-medium">{p.nome}</td>
                              <td className="py-2 pr-4">{p.nif ?? "—"}</td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          ) : tab === "fases" ? (
            <div className="grid gap-4 lg:grid-cols-2">
              {canEditProcessos ? (
                <Card>
                  <CardHeader>
                    <CardTitle>Adicionar fase</CardTitle>
                  </CardHeader>
                  <CardContent>
                    <form className="space-y-4" onSubmit={faseForm.handleSubmit(onSubmitFase)}>
                      <div className="space-y-2">
                        <Label htmlFor="fase_id">Fase</Label>
                        <Input id="fase_id" {...faseForm.register("fase_id")} placeholder="ID da fase" />
                        {faseForm.formState.errors.fase_id ? (
                          <p className="text-sm text-red-600">{faseForm.formState.errors.fase_id.message}</p>
                        ) : null}
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor="fase_status">Status</Label>
                        <select
                          id="fase_status"
                          className={selectClassName}
                          {...faseForm.register("status")}
                        >
                          <option value="PENDENTE">Pendente</option>
                          <option value="EM_ANDAMENTO">Em andamento</option>
                          <option value="CONCLUIDA">Concluída</option>
                        </select>
                        {faseForm.formState.errors.status ? (
                          <p className="text-sm text-red-600">{faseForm.formState.errors.status.message}</p>
                        ) : null}
                      </div>

                      {faseServerError ? <p className="text-sm text-red-600">{faseServerError}</p> : null}

                      <Button type="submit" disabled={faseForm.formState.isSubmitting || addFase.isPending}>
                        {faseForm.formState.isSubmitting || addFase.isPending ? "A guardar..." : "Adicionar"}
                      </Button>
                    </form>
                  </CardContent>
                </Card>
              ) : null}

              <Card>
                <CardHeader>
                  <CardTitle>Fases</CardTitle>
                </CardHeader>
                <CardContent>
                  {!fases.data?.length ? (
                    <div className="text-sm text-neutral-500 dark:text-neutral-400">Sem fases.</div>
                  ) : (
                    <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
                      <table className="w-full min-w-[480px] text-sm">
                        <thead className="text-left text-neutral-500 dark:text-neutral-400">
                          <tr className="border-b border-neutral-200 dark:border-neutral-800">
                            <th className="py-2 pr-4 font-medium">Fase</th>
                            <th className="py-2 pr-4 font-medium">Status</th>
                            <th className="py-2 pr-4 font-medium">Ações</th>
                          </tr>
                        </thead>
                        <tbody>
                          {fases.data.map((f) => (
                            <tr
                              key={f.id}
                              className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800"
                            >
                              <td className="py-2 pr-4 font-medium">{f.fase?.nome ?? f.fase_id}</td>
                              <td className="py-2 pr-4">
                                <select
                                  className={selectClassName}
                                  value={faseDraftStatus[f.id] ?? f.status}
                                  onChange={(e) =>
                                    setFaseDraftStatus((current) => ({
                                      ...current,
                                      [f.id]: processoFaseStatusSchema.parse(e.target.value),
                                    }))
                                  }
                                >
                                  <option value="PENDENTE">Pendente</option>
                                  <option value="EM_ANDAMENTO">Em andamento</option>
                                  <option value="CONCLUIDA">Concluída</option>
                                </select>
                              </td>
                              <td className="py-2 pr-4">
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="outline"
                                  onClick={() => onUpdateFaseStatus(f.id)}
                                  disabled={!canEditProcessos || updateFaseStatus.isPending}
                                >
                                  Guardar
                                </Button>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          ) : tab === "auditoria" && canManageProcessos ? (
            <Card>
              <CardHeader>
                <CardTitle>Auditoria</CardTitle>
              </CardHeader>
              <CardContent>
                {auditLog.isLoading ? (
                  <p className="text-sm text-slate-500 py-4">A carregar auditoria...</p>
                ) : auditLog.isError ? (
                  <p className="text-red-600 text-sm py-4">Erro ao carregar a auditoria. Tente novamente.</p>
                ) : (auditLog.data ?? []).length === 0 ? (
                  <div className="py-12 text-center">
                    <p className="text-sm font-medium text-slate-500">Sem registos de auditoria</p>
                    <p className="text-xs text-slate-400 mt-1">
                      Os eventos sensíveis do processo (transições, decisões, downloads e eliminações) ficam registados aqui.
                    </p>
                  </div>
                ) : (
                  <div>
                    {(auditLog.data ?? []).map((entry: AuditLogEntry) => {
                      const acaoBadgeVariant: "blue" | "purple" | "secondary" | "red" =
                        entry.acao === "transicao_estado"
                          ? "blue"
                          : entry.acao === "conflict_check_decisao"
                            ? "purple"
                            : entry.acao === "documento_eliminacao"
                              ? "red"
                              : "secondary";
                      return (
                        <div
                          key={entry.id}
                          className="flex items-center gap-3 py-2 border-b border-slate-100 dark:border-slate-800"
                        >
                          <span className="text-xs text-slate-500 w-36 shrink-0">
                            {formatDateTime(entry.timestamp)}
                          </span>
                          <Badge
                            variant={acaoBadgeVariant}
                            className="rounded-none font-bold tracking-wide text-[11px]"
                          >
                            {entry.acao}
                          </Badge>
                          <span className="text-xs text-slate-500">{entry.entidadeTipo}</span>
                          <span className="text-xs text-slate-400 font-mono truncate max-w-[120px]">
                            {entry.entidadeId}
                          </span>
                          {entry.autorNome ? (
                            <span className="text-xs text-slate-600 dark:text-slate-300">{entry.autorNome}</span>
                          ) : null}
                        </div>
                      );
                    })}
                  </div>
                )}
              </CardContent>
            </Card>
          ) : null
          }
        </div>
      ) : (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">Processo não encontrado.</div>
      )}
    </div>
  );
}
