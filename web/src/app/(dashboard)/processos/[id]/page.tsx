"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import * as React from "react";
import { Suspense } from "react";
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

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
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
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { DataTable } from "@/components/shared/data-table/data-table";
import { FileDropZone } from "@/components/shared/file-drop-zone";
import { useTenantUsers } from "@/hooks/use-users";
import { useClientes } from "@/hooks/use-clientes";
import { useDocumentos, useUploadDocumentoComProgresso } from "@/hooks/use-documentos";
import { usePermissions } from "@/hooks/use-permissions";
import {
  useAddDecisao,
  useAddFacto,
  useAddProcessoFase,
  useAddProcessoParte,
  useAddTestemunha,
  useAuditLog,
  useConflictCheckDecisao,
  useCreatePrazo,
  useDecisoes,
  useDeleteDecisao,
  useDeleteFacto,
  useDeleteTestemunha,
  useExecutarTransicao,
  useFactos,
  useFormalizarProcesso,
  usePrazos,
  useProcesso,
  useProcessoFases,
  useProcessoPartes,
  useReatribuirResponsavel,
  useTestemunhas,
  useTimeline,
  useTogglePrazoConcluido,
  useUpdateDecisao,
  useUpdateFacto,
  useUpdateProcessoFaseStatus,
  useUpdateTestemunha,
  useWorkflow,
} from "@/hooks/use-processos";
import { toast } from "@/hooks/use-toast";
import { conflictNivelToLabel, conflictNivelToVariant } from "@/lib/conflict-check";
import { origemProcessoToLabel } from "@/lib/origem-processo";
import { prazosRiscoToLabel, prazosRiscoToVariant } from "@/lib/prazos";
import { tipoDecisaoToLabel } from "@/lib/tipo-decisao";
import { tipoTestemunhaToLabel } from "@/lib/tipo-testemunha";
import {
  decisaoFormSchema,
  factoFormSchema,
  prazoFormSchema,
  processoFaseFormSchema,
  processoFaseStatusSchema,
  processoParteFormSchema,
  testemunhaFormSchema,
  tipoDecisaoSchema,
  tipoTestemunhaSchema,
  transicaoJustificativaFormSchema,
  type DecisaoFormValues,
  type FactoFormValues,
  type PrazoFormValues,
  type ProcessoFaseFormValues,
  type ProcessoParteFormValues,
  type TestemunhaFormValues,
  type TransicaoJustificativaFormValues,
} from "@/schemas/processos";
import type {
  AuditLogEntry,
  Decisao,
  DecisaoUpdateRequest,
  Facto,
  FactoCreateRequest,
  FactoUpdateRequest,
  ProcessoFaseCreateRequest,
  ProcessoFaseStatus,
  ProcessoFaseUpdateRequest,
  ProcessoParteCreateRequest,
  Testemunha,
  TestemunhaCreateRequest,
  TestemunhaUpdateRequest,
  TimelineItem,
  TimelineItemType,
  TransicaoInfo,
} from "@/types/processos";
import { columns } from "./documentos-columns";

type PageProps = {
  params: Promise<{ id: string }>;
};

type TabKey =
  | "timeline"
  | "partes"
  | "fases"
  | "decisoes"
  | "factos"
  | "testemunhas"
  | "documentos"
  | "auditoria";

const TAB_KEYS: TabKey[] = [
  "timeline",
  "partes",
  "fases",
  "decisoes",
  "factos",
  "testemunhas",
  "documentos",
  "auditoria",
];

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

function deriveInitials(nome: string) {
  return nome
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase();
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

  if (permissions.isFetched && !canViewProcessos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este processo."
        backHref="/processos"
      />
    );
  }

  return (
    <Suspense fallback={<div className="h-8 w-8 animate-spin rounded-full border-b-2 border-slate-900 mx-auto"></div>}>
      <ProcessoDetailContent id={id} canEditProcessos={canEditProcessos} canManageProcessos={canManageProcessos} />
    </Suspense>
  );
}

function ProcessoDetailContent({ id, canEditProcessos, canManageProcessos }: { id: string; canEditProcessos: boolean; canManageProcessos: boolean }) {
  // Re-invoking usePermissions here is cheap — it's cached by TanStack Query
  // under the same permissions key already used by the parent
  // ProcessoDetailPage. documentos:edit is a scope distinct from
  // processos:edit and gates only the Documentos tab's write affordances.
  const permissions = usePermissions();
  const canEditDocumentos = permissions.can.edit("documentos");

  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");
  const initialTab: TabKey =
    tabParam && (TAB_KEYS as string[]).includes(tabParam) ? (tabParam as TabKey) : "timeline";
  const [tab, setTab] = React.useState<TabKey>(initialTab);

  // WR-03 (Phase 87 code review): useState's initializer only runs on first
  // mount, so a client-side navigation that changes only ?tab= on this same
  // route (e.g. following a FASE_ENTRADA notification link while already on
  // this processo's page) updates the URL/searchParams but not `tab`. Re-sync
  // whenever searchParams changes so ?tab= deep-links keep working post-mount.
  React.useEffect(() => {
    const p = searchParams.get("tab");
    if (p && (TAB_KEYS as string[]).includes(p) && p !== tab) {
      setTab(p as TabKey);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

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
  const tenantUsers = useTenantUsers();
  const decisao = useConflictCheckDecisao(id);
  const formalizarProcesso = useFormalizarProcesso(id);

  const partes = useProcessoPartes(id);
  const fases = useProcessoFases(id);

  // Workflow hooks
  const workflow = useWorkflow(id);
  const prazos = usePrazos(id);
  const transicao = useExecutarTransicao(id);
  const createPrazo = useCreatePrazo(id);
  const toggleConcluido = useTogglePrazoConcluido(id);

  const addParte = useAddProcessoParte(id);
  const addFase = useAddProcessoFase(id);
  const updateFaseStatus = useUpdateProcessoFaseStatus(id);

  const decisoes = useDecisoes(id);
  const addDecisao = useAddDecisao(id);
  const updateDecisao = useUpdateDecisao(id);
  const deleteDecisao = useDeleteDecisao(id);

  const testemunhas = useTestemunhas(id);
  const addTestemunha = useAddTestemunha(id);
  const updateTestemunha = useUpdateTestemunha(id);
  const deleteTestemunha = useDeleteTestemunha(id);

  const factos = useFactos(id);
  const addFacto = useAddFacto(id);
  const updateFacto = useUpdateFacto(id);
  const deleteFacto = useDeleteFacto(id);

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
  const userNomeById = new Map((tenantUsers.data ?? []).map((u) => [u.id, u.nome] as const));

  const isLoading = processo.isLoading || clientes.isLoading || partes.isLoading || fases.isLoading;
  const isError = processo.isError || clientes.isError || partes.isError || fases.isError;

  const parteForm = useForm<ProcessoParteFormValues>({
    resolver: zodResolver(processoParteFormSchema),
    defaultValues: { tipo: undefined, nome: "", nif: undefined },
  });
  const [parteServerError, setParteServerError] = React.useState<string | null>(null);
  const [addParteModal, setAddParteModal] = React.useState(false);

  const faseForm = useForm<ProcessoFaseFormValues>({
    resolver: zodResolver(processoFaseFormSchema),
    defaultValues: { nome: "" },
  });
  const [faseServerError, setFaseServerError] = React.useState<string | null>(null);
  const [addFaseModal, setAddFaseModal] = React.useState(false);

  const [addDecisaoModal, setAddDecisaoModal] = React.useState(false);
  const [editingDecisaoId, setEditingDecisaoId] = React.useState<number | null>(null);
  const [decisaoServerError, setDecisaoServerError] = React.useState<string | null>(null);
  const decisaoForm = useForm<DecisaoFormValues>({
    resolver: zodResolver(decisaoFormSchema),
    defaultValues: { data: "", tipo: undefined, resumo: undefined, file: undefined },
  });

  const [addTestemunhaModal, setAddTestemunhaModal] = React.useState(false);
  const [editingTestemunhaId, setEditingTestemunhaId] = React.useState<number | null>(null);
  const [testemunhaServerError, setTestemunhaServerError] = React.useState<string | null>(null);
  // testemunhaFormSchema's `tipo` field uses z.preprocess (to coerce a blank <select>
  // to undefined for this optional enum) which makes the resolver's inferred input
  // type diverge from TestemunhaFormValues (z.infer's output type) — same class of
  // mismatch as prazoForm below, same established workaround.
  const testemunhaForm = useForm<TestemunhaFormValues>({
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    resolver: zodResolver(testemunhaFormSchema) as any,
    defaultValues: { nome: "", tipo: undefined, contacto: undefined, notas: undefined },
  });

  const [addFactoModal, setAddFactoModal] = React.useState(false);
  const [editingFactoId, setEditingFactoId] = React.useState<number | null>(null);
  const [factoServerError, setFactoServerError] = React.useState<string | null>(null);
  const factoForm = useForm<FactoFormValues>({
    resolver: zodResolver(factoFormSchema),
    defaultValues: { descricao: "", data: undefined },
  });
  // Plain local state (not part of the Zod-validated form) — ordem is only
  // ever edited via this numeric input inside the "Editar Facto" Dialog; the
  // create form never collects it (backend recomputes it server-side).
  const [factoOrdemDraft, setFactoOrdemDraft] = React.useState(1);

  const [faseDraftStatus, setFaseDraftStatus] = React.useState<Record<number, ProcessoFaseStatus>>({});

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

  const onOpenAddParte = () => {
    parteForm.reset({ tipo: undefined, nome: "", nif: undefined });
    setParteServerError(null);
    setAddParteModal(true);
  };

  const onSubmitParte = async (values: ProcessoParteFormValues) => {
    setParteServerError(null);
    if (!canEditProcessos) return;
    try {
      await addParte.mutateAsync(values satisfies ProcessoParteCreateRequest);
      parteForm.reset({ tipo: undefined, nome: "", nif: undefined });
      setAddParteModal(false);
      toast.success("Parte adicionada ao processo.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao adicionar parte";
      setParteServerError(msg);
      toast.error(msg);
    }
  };

  const onOpenAddFase = () => {
    faseForm.reset({ nome: "" });
    setFaseServerError(null);
    setAddFaseModal(true);
  };

  const onSubmitFase = async (values: ProcessoFaseFormValues) => {
    setFaseServerError(null);
    if (!canEditProcessos) return;
    try {
      await addFase.mutateAsync(values satisfies ProcessoFaseCreateRequest);
      faseForm.reset({ nome: "" });
      setAddFaseModal(false);
      toast.success("Fase adicionada ao processo.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao adicionar fase";
      setFaseServerError(msg);
      toast.error(msg);
    }
  };

  const onUpdateFaseStatus = async (faseId: number, currentStatus: ProcessoFaseStatus) => {
    const status = faseDraftStatus[faseId] ?? currentStatus;
    const payload: ProcessoFaseUpdateRequest = { status };
    try {
      await updateFaseStatus.mutateAsync({ faseId, payload });
      toast.success("Status da fase atualizado.");
    } catch {
      setFaseServerError("Erro ao atualizar status da fase");
      toast.error("Erro ao atualizar status da fase");
    }
  };

  const onOpenAddDecisao = () => {
    decisaoForm.reset({ data: "", tipo: undefined, resumo: undefined, file: undefined });
    setEditingDecisaoId(null);
    setDecisaoServerError(null);
    setAddDecisaoModal(true);
  };

  const onOpenEditDecisao = (d: Decisao) => {
    decisaoForm.reset({ data: d.data, tipo: d.tipo, resumo: d.resumo, file: undefined });
    setEditingDecisaoId(d.id);
    setDecisaoServerError(null);
    setAddDecisaoModal(true);
  };

  const onSubmitDecisao = async (values: DecisaoFormValues) => {
    setDecisaoServerError(null);
    try {
      if (editingDecisaoId !== null) {
        await updateDecisao.mutateAsync({
          decisaoId: editingDecisaoId,
          payload: { data: values.data, tipo: values.tipo, resumo: values.resumo } satisfies DecisaoUpdateRequest,
        });
        toast.success("Decisão atualizada com sucesso.");
      } else {
        await addDecisao.mutateAsync({
          data: values.data,
          tipo: values.tipo,
          resumo: values.resumo,
          file: values.file?.[0],
        });
        toast.success("Decisão adicionada com sucesso.");
      }
      setAddDecisaoModal(false);
    } catch (e) {
      const msg =
        e instanceof Error
          ? e.message
          : editingDecisaoId !== null
            ? "Erro ao atualizar decisão"
            : "Erro ao adicionar decisão";
      setDecisaoServerError(msg);
      toast.error(msg);
    }
  };

  const onDeleteDecisao = async (decisaoId: number) => {
    const ok = window.confirm("Apagar esta decisão?");
    if (!ok) return;
    try {
      await deleteDecisao.mutateAsync(decisaoId);
      toast.success("Decisão apagada com sucesso.");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao apagar decisão");
    }
  };

  const onOpenAddTestemunha = () => {
    testemunhaForm.reset({ nome: "", tipo: undefined, contacto: undefined, notas: undefined });
    setEditingTestemunhaId(null);
    setTestemunhaServerError(null);
    setAddTestemunhaModal(true);
  };

  const onOpenEditTestemunha = (t: Testemunha) => {
    testemunhaForm.reset({ nome: t.nome, tipo: t.tipo, contacto: t.contacto, notas: t.notas });
    setEditingTestemunhaId(t.id);
    setTestemunhaServerError(null);
    setAddTestemunhaModal(true);
  };

  const onSubmitTestemunha = async (values: TestemunhaFormValues) => {
    setTestemunhaServerError(null);
    try {
      if (editingTestemunhaId !== null) {
        await updateTestemunha.mutateAsync({
          testemunhaId: editingTestemunhaId,
          payload: values satisfies TestemunhaUpdateRequest,
        });
        toast.success("Testemunha atualizada com sucesso.");
      } else {
        await addTestemunha.mutateAsync(values satisfies TestemunhaCreateRequest);
        toast.success("Testemunha adicionada com sucesso.");
      }
      setAddTestemunhaModal(false);
    } catch (e) {
      const msg =
        e instanceof Error
          ? e.message
          : editingTestemunhaId !== null
            ? "Erro ao atualizar testemunha"
            : "Erro ao adicionar testemunha";
      setTestemunhaServerError(msg);
      toast.error(msg);
    }
  };

  const onDeleteTestemunha = async (testemunhaId: number) => {
    const ok = window.confirm("Apagar esta testemunha?");
    if (!ok) return;
    try {
      await deleteTestemunha.mutateAsync(testemunhaId);
      toast.success("Testemunha apagada com sucesso.");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao apagar testemunha");
    }
  };

  const onOpenAddFacto = () => {
    factoForm.reset({ descricao: "", data: undefined });
    setEditingFactoId(null);
    setFactoServerError(null);
    setAddFactoModal(true);
  };

  const onOpenEditFacto = (f: Facto) => {
    factoForm.reset({ descricao: f.descricao, data: f.data });
    setFactoOrdemDraft(f.ordem);
    setEditingFactoId(f.id);
    setFactoServerError(null);
    setAddFactoModal(true);
  };

  const onSubmitFacto = async (values: FactoFormValues) => {
    setFactoServerError(null);
    try {
      if (editingFactoId !== null) {
        await updateFacto.mutateAsync({
          factoId: editingFactoId,
          payload: {
            descricao: values.descricao,
            data: values.data,
            ordem: factoOrdemDraft,
          } satisfies FactoUpdateRequest,
        });
        toast.success("Facto atualizado com sucesso.");
      } else {
        await addFacto.mutateAsync(values satisfies FactoCreateRequest);
        toast.success("Facto adicionado com sucesso.");
      }
      setAddFactoModal(false);
    } catch (e) {
      const msg =
        e instanceof Error
          ? e.message
          : editingFactoId !== null
            ? "Erro ao atualizar facto"
            : "Erro ao adicionar facto";
      setFactoServerError(msg);
      toast.error(msg);
    }
  };

  const onDeleteFacto = async (factoId: number) => {
    const ok = window.confirm("Apagar este facto?");
    if (!ok) return;
    try {
      await deleteFacto.mutateAsync(factoId);
      toast.success("Facto apagado com sucesso.");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao apagar facto");
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
          <h1 className="text-2xl font-semibold">Processo</h1>
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link href="/processos">Processos</Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem>
                <BreadcrumbPage>
                  {processo.data?.numero ?? processo.data?.titulo ?? "…"}
                </BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
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

                <dt className="text-neutral-500 dark:text-neutral-400">Juízo</dt>
                <dd className="col-span-2">{processo.data.juizo ?? "—"}</dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Origem</dt>
                <dd className="col-span-2">
                  {processo.data.origem ? origemProcessoToLabel(processo.data.origem) : "—"}
                </dd>

                <dt className="text-neutral-500 dark:text-neutral-400">Cliente</dt>
                <dd className="col-span-2">
                  {clienteNomeById.get(processo.data.cliente_id) ?? "—"}
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

              {processo.data?.estado === "ATIVO" ? (
                <div className="pt-4">
                  <Button asChild className="rounded-none font-bold bg-blue-600 hover:bg-blue-700 text-white">
                    <Link
                      href={`/processos/${encodeURIComponent(id)}/termo-honorarios`}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      Gerar Termo de Honorários
                    </Link>
                  </Button>
                </div>
              ) : null}
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
                      <dd className="col-span-2 font-medium">
                        {userNomeById.get(decisao.data.decisorId) ?? "—"}
                      </dd>

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
                      {canManageProcessos ? (
                        <ReatribuirResponsavelControl
                          processoId={id}
                          numero={processo.data?.numero ?? processo.data?.titulo ?? "…"}
                          currentResponsavelId={workflow.data.responsavelId ?? null}
                          currentResponsavelNome={workflow.data.responsavelNome ?? null}
                        />
                      ) : null}
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
                            {userNomeById.get(p.responsavelId) ?? "—"}
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
                    <NativeSelect
                      id="prazo_prioridade"
                      size="default"
                      className="w-full"
                      {...prazoForm.register("prioridade")}
                    >
                      <option value="ALTA">ALTA</option>
                      <option value="MEDIA">MÉDIA</option>
                      <option value="BAIXA">BAIXA</option>
                    </NativeSelect>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="prazo_responsavel">Responsável</Label>
                    <NativeSelect
                      id="prazo_responsavel"
                      size="default"
                      className="w-full"
                      {...prazoForm.register("responsavelId")}
                    >
                      <option value="">— Sem responsável —</option>
                      {(tenantUsers.data ?? [])
                        .filter((u) => u.ativo !== false)
                        .map((u) => (
                          <option key={u.id} value={u.id}>
                            {u.nome}
                          </option>
                        ))}
                    </NativeSelect>
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

          <Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}>
            <TabsList variant="default" className="h-auto w-full flex-wrap">
              <TabsTrigger value="timeline">Timeline</TabsTrigger>
              <TabsTrigger value="partes">Partes</TabsTrigger>
              <TabsTrigger value="fases">Fases</TabsTrigger>
              <TabsTrigger value="decisoes">Decisões</TabsTrigger>
              <TabsTrigger value="factos">Factos</TabsTrigger>
              <TabsTrigger value="testemunhas">Testemunhas</TabsTrigger>
              <TabsTrigger value="documentos">Documentos</TabsTrigger>
              {canManageProcessos ? <TabsTrigger value="auditoria">Auditoria</TabsTrigger> : null}
            </TabsList>

          <TabsContent value="timeline">
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
          </TabsContent>

          <TabsContent value="partes">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Partes</CardTitle>
                  {canEditProcessos ? (
                    <Dialog open={addParteModal} onOpenChange={setAddParteModal}>
                      <DialogTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="rounded-none"
                          onClick={onOpenAddParte}
                        >
                          Adicionar Parte
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>Adicionar Parte</DialogTitle>
                        </DialogHeader>
                        <form className="space-y-4" onSubmit={parteForm.handleSubmit(onSubmitParte)}>
                          <div className="space-y-2">
                            <Label htmlFor="parte_nome">Nome</Label>
                            <Input id="parte_nome" className="rounded-none" {...parteForm.register("nome")} />
                            {parteForm.formState.errors.nome ? (
                              <p className="text-sm text-red-600">{parteForm.formState.errors.nome.message}</p>
                            ) : null}
                          </div>

                          <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                            <div className="space-y-2">
                              <Label htmlFor="parte_tipo">Tipo</Label>
                              <Input
                                id="parte_tipo"
                                className="rounded-none"
                                {...parteForm.register("tipo")}
                                placeholder="Ex.: Autor / Réu"
                              />
                              {parteForm.formState.errors.tipo ? (
                                <p className="text-sm text-red-600">{parteForm.formState.errors.tipo.message}</p>
                              ) : null}
                            </div>
                            <div className="space-y-2">
                              <Label htmlFor="parte_nif">NIF</Label>
                              <Input id="parte_nif" className="rounded-none" {...parteForm.register("nif")} />
                              {parteForm.formState.errors.nif ? (
                                <p className="text-sm text-red-600">{parteForm.formState.errors.nif.message}</p>
                              ) : null}
                            </div>
                          </div>

                          {parteServerError ? <p className="text-sm text-red-600">{parteServerError}</p> : null}

                          <DialogFooter>
                            <Button
                              type="button"
                              variant="outline"
                              className="rounded-none"
                              onClick={() => setAddParteModal(false)}
                            >
                              Cancelar
                            </Button>
                            <Button
                              type="submit"
                              className="rounded-none"
                              disabled={parteForm.formState.isSubmitting || addParte.isPending}
                            >
                              {parteForm.formState.isSubmitting || addParte.isPending ? "A guardar..." : "Adicionar"}
                            </Button>
                          </DialogFooter>
                        </form>
                      </DialogContent>
                    </Dialog>
                  ) : null}
                </div>
              </CardHeader>
              <CardContent>
                {!partes.data?.length ? (
                  <div className="text-sm text-neutral-500 dark:text-neutral-400">Sem partes.</div>
                ) : (
                  <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
                    <Table className="min-w-[400px]">
                      <TableHeader>
                        <TableRow>
                          <TableHead>Tipo</TableHead>
                          <TableHead>Nome</TableHead>
                          <TableHead>NIF</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {partes.data.map((p) => (
                          <TableRow key={p.id}>
                            <TableCell>{p.tipo ?? "—"}</TableCell>
                            <TableCell className="font-medium">{p.nome}</TableCell>
                            <TableCell>{p.nif ?? "—"}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="fases">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Fases</CardTitle>
                  {canEditProcessos ? (
                    <Dialog open={addFaseModal} onOpenChange={setAddFaseModal}>
                      <DialogTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="rounded-none"
                          onClick={onOpenAddFase}
                        >
                          Adicionar Fase
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>Adicionar Fase</DialogTitle>
                        </DialogHeader>
                        <form className="space-y-4" onSubmit={faseForm.handleSubmit(onSubmitFase)}>
                          <div className="space-y-2">
                            <Label htmlFor="fase_nome">Nome da fase</Label>
                            <Input
                              id="fase_nome"
                              className="rounded-none"
                              {...faseForm.register("nome")}
                              placeholder="Ex.: Petição Inicial"
                            />
                            {faseForm.formState.errors.nome ? (
                              <p className="text-sm text-red-600">{faseForm.formState.errors.nome.message}</p>
                            ) : null}
                          </div>

                          {faseServerError ? <p className="text-sm text-red-600">{faseServerError}</p> : null}

                          <DialogFooter>
                            <Button
                              type="button"
                              variant="outline"
                              className="rounded-none"
                              onClick={() => setAddFaseModal(false)}
                            >
                              Cancelar
                            </Button>
                            <Button
                              type="submit"
                              className="rounded-none"
                              disabled={faseForm.formState.isSubmitting || addFase.isPending}
                            >
                              {faseForm.formState.isSubmitting || addFase.isPending ? "A guardar..." : "Adicionar"}
                            </Button>
                          </DialogFooter>
                        </form>
                      </DialogContent>
                    </Dialog>
                  ) : null}
                </div>
              </CardHeader>
              <CardContent>
                {!fases.data?.length ? (
                    <div className="text-sm text-neutral-500 dark:text-neutral-400">Sem fases.</div>
                  ) : (
                    <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
                      <Table className="min-w-[480px]">
                        <TableHeader>
                          <TableRow>
                            <TableHead>Fase</TableHead>
                            <TableHead>Status</TableHead>
                            <TableHead>Ações</TableHead>
                          </TableRow>
                        </TableHeader>
                        <TableBody>
                          {fases.data.map((f) => (
                            <TableRow key={f.id}>
                              <TableCell className="font-medium">{f.nome ?? "—"}</TableCell>
                              <TableCell>
                                <NativeSelect
                                  size="default"
                                  className="w-full"
                                  value={faseDraftStatus[f.id] ?? f.status}
                                  onChange={(e) =>
                                    setFaseDraftStatus((current) => ({
                                      ...current,
                                      [f.id]: processoFaseStatusSchema.parse(e.target.value),
                                    }))
                                  }
                                  disabled={!canEditProcessos}
                                >
                                  <option value="PENDENTE">Pendente</option>
                                  <option value="EM_ANDAMENTO">Em andamento</option>
                                  <option value="CONCLUIDA">Concluída</option>
                                </NativeSelect>
                              </TableCell>
                              <TableCell>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="outline"
                                  onClick={() => onUpdateFaseStatus(f.id, f.status)}
                                  disabled={!canEditProcessos || updateFaseStatus.isPending}
                                >
                                  Guardar
                                </Button>
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </div>
                  )}
                </CardContent>
              </Card>
          </TabsContent>

          <TabsContent value="decisoes">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Decisões</CardTitle>
                  {canEditProcessos ? (
                    <Dialog open={addDecisaoModal} onOpenChange={setAddDecisaoModal}>
                      <DialogTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="rounded-none"
                          onClick={onOpenAddDecisao}
                        >
                          Adicionar Decisão
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>
                            {editingDecisaoId === null ? "Adicionar Decisão" : "Editar Decisão"}
                          </DialogTitle>
                        </DialogHeader>
                        <form className="space-y-4" onSubmit={decisaoForm.handleSubmit(onSubmitDecisao)}>
                          <div className="space-y-2">
                            <Label htmlFor="decisao_data">Data</Label>
                            <input
                              id="decisao_data"
                              type="date"
                              className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                              {...decisaoForm.register("data")}
                            />
                            {decisaoForm.formState.errors.data ? (
                              <p className="text-sm text-red-600">{decisaoForm.formState.errors.data.message}</p>
                            ) : null}
                          </div>

                          <div className="space-y-2">
                            <Label htmlFor="decisao_tipo">Tipo</Label>
                            <NativeSelect
                              id="decisao_tipo"
                              size="default"
                              className="w-full"
                              {...decisaoForm.register("tipo")}
                            >
                              <option value="">Selecionar tipo</option>
                              {tipoDecisaoSchema.options.map((t) => (
                                <option key={t} value={t}>
                                  {tipoDecisaoToLabel(t)}
                                </option>
                              ))}
                            </NativeSelect>
                            {decisaoForm.formState.errors.tipo ? (
                              <p className="text-sm text-red-600">{decisaoForm.formState.errors.tipo.message}</p>
                            ) : null}
                          </div>

                          <div className="space-y-2">
                            <Label htmlFor="decisao_resumo">Resumo</Label>
                            <Textarea
                              id="decisao_resumo"
                              className="rounded-none"
                              {...decisaoForm.register("resumo")}
                            />
                            {decisaoForm.formState.errors.resumo ? (
                              <p className="text-sm text-red-600">{decisaoForm.formState.errors.resumo.message}</p>
                            ) : null}
                          </div>

                          {editingDecisaoId === null ? (
                            <div className="space-y-2">
                              <Label htmlFor="decisao_file">Anexo (opcional)</Label>
                              <input
                                id="decisao_file"
                                type="file"
                                className="rounded-none block w-full text-sm text-neutral-600 file:mr-3 file:rounded-none file:border-0 file:bg-slate-100 file:px-3 file:py-2 file:text-sm dark:text-neutral-400 dark:file:bg-slate-800"
                                {...decisaoForm.register("file")}
                              />
                              {decisaoForm.formState.errors.file ? (
                                <p className="text-sm text-red-600">{decisaoForm.formState.errors.file.message}</p>
                              ) : null}
                            </div>
                          ) : null}

                          {decisaoServerError ? <p className="text-sm text-red-600">{decisaoServerError}</p> : null}

                          <DialogFooter>
                            <Button
                              type="button"
                              variant="outline"
                              className="rounded-none"
                              onClick={() => setAddDecisaoModal(false)}
                            >
                              Cancelar
                            </Button>
                            <Button
                              type="submit"
                              className="rounded-none"
                              disabled={
                                decisaoForm.formState.isSubmitting || addDecisao.isPending || updateDecisao.isPending
                              }
                            >
                              Confirmar
                            </Button>
                          </DialogFooter>
                        </form>
                      </DialogContent>
                    </Dialog>
                  ) : null}
                </div>
              </CardHeader>
              <CardContent>
                {decisoes.isLoading ? (
                  <p className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</p>
                ) : decisoes.isError ? (
                  <p className="text-sm text-red-600">Não foi possível carregar as decisões deste processo.</p>
                ) : !decisoes.data?.length ? (
                  <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhuma decisão registada.</p>
                ) : (
                  <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
                    <table className="w-full min-w-[480px] text-sm">
                      <thead className="text-left text-neutral-500 dark:text-neutral-400">
                        <tr className="border-b border-neutral-200 dark:border-neutral-800">
                          <th className="py-2 pr-4 font-medium">Data</th>
                          <th className="py-2 pr-4 font-medium">Tipo</th>
                          <th className="py-2 pr-4 font-medium">Resumo</th>
                          <th className="py-2 pr-4 font-medium">Ações</th>
                        </tr>
                      </thead>
                      <tbody>
                        {decisoes.data.map((d) => (
                          <tr
                            key={d.id}
                            className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800"
                          >
                            <td className="py-2 pr-4">{formatDate(d.data)}</td>
                            <td className="py-2 pr-4">{tipoDecisaoToLabel(d.tipo)}</td>
                            <td className="py-2 pr-4">{d.resumo ?? "—"}</td>
                            <td className="py-2 pr-4">
                              {canEditProcessos ? (
                                <div className="flex items-center gap-2">
                                  <button
                                    type="button"
                                    className="text-blue-600 hover:underline text-xs"
                                    onClick={() => onOpenEditDecisao(d)}
                                  >
                                    Editar
                                  </button>
                                  <button
                                    type="button"
                                    className="text-neutral-500 hover:text-red-600"
                                    onClick={() => onDeleteDecisao(d.id)}
                                    aria-label="Apagar decisão"
                                  >
                                    ✕
                                  </button>
                                </div>
                              ) : null}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="factos">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Factos</CardTitle>
                  {canEditProcessos ? (
                    <Dialog open={addFactoModal} onOpenChange={setAddFactoModal}>
                      <DialogTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="rounded-none"
                          onClick={onOpenAddFacto}
                        >
                          Adicionar Facto
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>
                            {editingFactoId === null ? "Adicionar Facto" : "Editar Facto"}
                          </DialogTitle>
                        </DialogHeader>
                        <form className="space-y-4" onSubmit={factoForm.handleSubmit(onSubmitFacto)}>
                          <div className="space-y-2">
                            <Label htmlFor="facto_descricao">Descrição</Label>
                            <Textarea
                              id="facto_descricao"
                              className="rounded-none"
                              {...factoForm.register("descricao")}
                            />
                            {factoForm.formState.errors.descricao ? (
                              <p className="text-sm text-red-600">
                                {factoForm.formState.errors.descricao.message}
                              </p>
                            ) : null}
                          </div>

                          <div className="space-y-2">
                            <Label htmlFor="facto_data">Data</Label>
                            <input
                              id="facto_data"
                              type="date"
                              className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                              {...factoForm.register("data")}
                            />
                            {factoForm.formState.errors.data ? (
                              <p className="text-sm text-red-600">{factoForm.formState.errors.data.message}</p>
                            ) : null}
                          </div>

                          {editingFactoId !== null ? (
                            <div className="space-y-2">
                              <Label htmlFor="facto_ordem">Ordem</Label>
                              <input
                                id="facto_ordem"
                                type="number"
                                min={1}
                                className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                                value={factoOrdemDraft}
                                onChange={(e) =>
                                  setFactoOrdemDraft(Math.max(1, Math.trunc(Number(e.target.value) || 1)))
                                }
                              />
                            </div>
                          ) : null}

                          {factoServerError ? <p className="text-sm text-red-600">{factoServerError}</p> : null}

                          <DialogFooter>
                            <Button
                              type="button"
                              variant="outline"
                              className="rounded-none"
                              onClick={() => setAddFactoModal(false)}
                            >
                              Cancelar
                            </Button>
                            <Button
                              type="submit"
                              className="rounded-none"
                              disabled={
                                factoForm.formState.isSubmitting || addFacto.isPending || updateFacto.isPending
                              }
                            >
                              Confirmar
                            </Button>
                          </DialogFooter>
                        </form>
                      </DialogContent>
                    </Dialog>
                  ) : null}
                </div>
              </CardHeader>
              <CardContent>
                {factos.isLoading ? (
                  <p className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</p>
                ) : factos.isError ? (
                  <p className="text-sm text-red-600">Não foi possível carregar os factos deste processo.</p>
                ) : !factos.data?.length ? (
                  <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum facto registado.</p>
                ) : (
                  <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
                    <table className="w-full min-w-[480px] text-sm">
                      <thead className="text-left text-neutral-500 dark:text-neutral-400">
                        <tr className="border-b border-neutral-200 dark:border-neutral-800">
                          <th className="py-2 pr-4 font-medium">Ordem</th>
                          <th className="py-2 pr-4 font-medium">Descrição</th>
                          <th className="py-2 pr-4 font-medium">Data</th>
                          <th className="py-2 pr-4 font-medium">Ações</th>
                        </tr>
                      </thead>
                      <tbody>
                        {[...factos.data].sort((a, b) => a.ordem - b.ordem).map((f) => (
                          <tr
                            key={f.id}
                            className="border-b border-neutral-200 last:border-b-0 dark:border-neutral-800"
                          >
                            <td className="py-2 pr-4">{f.ordem}</td>
                            <td className="py-2 pr-4">{f.descricao}</td>
                            <td className="py-2 pr-4">{formatDate(f.data)}</td>
                            <td className="py-2 pr-4">
                              {canEditProcessos ? (
                                <div className="flex items-center gap-2">
                                  <button
                                    type="button"
                                    className="text-blue-600 hover:underline text-xs"
                                    onClick={() => onOpenEditFacto(f)}
                                  >
                                    Editar
                                  </button>
                                  <button
                                    type="button"
                                    className="text-neutral-500 hover:text-red-600"
                                    onClick={() => onDeleteFacto(f.id)}
                                    aria-label="Apagar facto"
                                  >
                                    ✕
                                  </button>
                                </div>
                              ) : null}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="testemunhas">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <CardTitle>Testemunhas</CardTitle>
                  {canEditProcessos ? (
                    <Dialog open={addTestemunhaModal} onOpenChange={setAddTestemunhaModal}>
                      <DialogTrigger asChild>
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="rounded-none"
                          onClick={onOpenAddTestemunha}
                        >
                          Adicionar Testemunha
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>
                            {editingTestemunhaId === null ? "Adicionar Testemunha" : "Editar Testemunha"}
                          </DialogTitle>
                        </DialogHeader>
                        <form className="space-y-4" onSubmit={testemunhaForm.handleSubmit(onSubmitTestemunha)}>
                          <div className="space-y-2">
                            <Label htmlFor="testemunha_nome">Nome</Label>
                            <Input
                              id="testemunha_nome"
                              className="rounded-none"
                              {...testemunhaForm.register("nome")}
                            />
                            {testemunhaForm.formState.errors.nome ? (
                              <p className="text-sm text-red-600">
                                {testemunhaForm.formState.errors.nome.message}
                              </p>
                            ) : null}
                          </div>

                          <div className="space-y-2">
                            <Label htmlFor="testemunha_tipo">Tipo</Label>
                            <NativeSelect
                              id="testemunha_tipo"
                              size="default"
                              className="w-full"
                              {...testemunhaForm.register("tipo")}
                            >
                              <option value="">Selecionar tipo</option>
                              {tipoTestemunhaSchema.options.map((t) => (
                                <option key={t} value={t}>
                                  {tipoTestemunhaToLabel(t)}
                                </option>
                              ))}
                            </NativeSelect>
                            {testemunhaForm.formState.errors.tipo ? (
                              <p className="text-sm text-red-600">
                                {testemunhaForm.formState.errors.tipo.message}
                              </p>
                            ) : null}
                          </div>

                          <div className="space-y-2">
                            <Label htmlFor="testemunha_contacto">Contacto</Label>
                            <Input
                              id="testemunha_contacto"
                              className="rounded-none"
                              {...testemunhaForm.register("contacto")}
                            />
                            {testemunhaForm.formState.errors.contacto ? (
                              <p className="text-sm text-red-600">
                                {testemunhaForm.formState.errors.contacto.message}
                              </p>
                            ) : null}
                          </div>

                          <div className="space-y-2">
                            <Label htmlFor="testemunha_notas">Notas</Label>
                            <Textarea
                              id="testemunha_notas"
                              className="rounded-none"
                              {...testemunhaForm.register("notas")}
                            />
                            {testemunhaForm.formState.errors.notas ? (
                              <p className="text-sm text-red-600">
                                {testemunhaForm.formState.errors.notas.message}
                              </p>
                            ) : null}
                          </div>

                          {testemunhaServerError ? (
                            <p className="text-sm text-red-600">{testemunhaServerError}</p>
                          ) : null}

                          <DialogFooter>
                            <Button
                              type="button"
                              variant="outline"
                              className="rounded-none"
                              onClick={() => setAddTestemunhaModal(false)}
                            >
                              Cancelar
                            </Button>
                            <Button
                              type="submit"
                              className="rounded-none"
                              disabled={
                                testemunhaForm.formState.isSubmitting ||
                                addTestemunha.isPending ||
                                updateTestemunha.isPending
                              }
                            >
                              Confirmar
                            </Button>
                          </DialogFooter>
                        </form>
                      </DialogContent>
                    </Dialog>
                  ) : null}
                </div>
              </CardHeader>
              <CardContent>
                {testemunhas.isLoading ? (
                  <p className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</p>
                ) : testemunhas.isError ? (
                  <p className="text-sm text-red-600">
                    Não foi possível carregar as testemunhas deste processo.
                  </p>
                ) : !testemunhas.data?.length ? (
                  <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhuma testemunha registada.</p>
                ) : (
                  <div className="overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0">
                    <Table className="min-w-[480px]">
                      <TableHeader>
                        <TableRow>
                          <TableHead>Nome</TableHead>
                          <TableHead>Tipo</TableHead>
                          <TableHead>Contacto</TableHead>
                          <TableHead>Ações</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {testemunhas.data.map((t) => (
                          <TableRow key={t.id}>
                            <TableCell>
                              <div className="flex items-center gap-2">
                                <Avatar size="sm">
                                  <AvatarFallback>{deriveInitials(t.nome)}</AvatarFallback>
                                </Avatar>
                                <span className="font-medium">{t.nome}</span>
                              </div>
                            </TableCell>
                            <TableCell>{t.tipo ? tipoTestemunhaToLabel(t.tipo) : "—"}</TableCell>
                            <TableCell>{t.contacto ?? "—"}</TableCell>
                            <TableCell>
                              {canEditProcessos ? (
                                <div className="flex items-center gap-2">
                                  <button
                                    type="button"
                                    className="text-blue-600 hover:underline text-xs"
                                    onClick={() => onOpenEditTestemunha(t)}
                                  >
                                    Editar
                                  </button>
                                  <button
                                    type="button"
                                    className="text-neutral-500 hover:text-red-600"
                                    onClick={() => onDeleteTestemunha(t.id)}
                                    aria-label="Apagar testemunha"
                                  >
                                    ✕
                                  </button>
                                </div>
                              ) : null}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </div>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="documentos">
            <ProcessoDocumentosTab processoId={id} canEditDocumentos={canEditDocumentos} />
          </TabsContent>

          <TabsContent value="auditoria">
            {canManageProcessos ? (
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
            ) : null}
          </TabsContent>
          </Tabs>
        </div>
      ) : (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">Processo não encontrado.</div>
      )}
    </div>
  );
}

function ReatribuirResponsavelControl({
  processoId,
  numero,
  currentResponsavelId,
  currentResponsavelNome,
}: {
  processoId: string;
  numero: string;
  currentResponsavelId: string | null;
  currentResponsavelNome: string | null;
}) {
  const [dialogOpen, setDialogOpen] = React.useState(false);
  const [confirmOpen, setConfirmOpen] = React.useState(false);
  const [selectedUserId, setSelectedUserId] = React.useState(currentResponsavelId ?? "");
  const [reatribuirError, setReatribuirError] = React.useState<string | null>(null);

  const reatribuir = useReatribuirResponsavel(processoId);
  const tenantUsers = useTenantUsers();

  const novoNome = (tenantUsers.data ?? []).find((u) => u.id === selectedUserId)?.nome ?? "";
  // WR-03 (Phase 87 code review, iteration 3): the current responsável may have been
  // deactivated since being assigned. filteredUsers (active-only) drives the normal
  // <option> list below; when the current responsável isn't in it, a synthetic
  // "(inativo)" option is injected so the <select> always has a matching <option> for
  // selectedUserId's initial value -- otherwise the browser falls back to showing the
  // disabled placeholder, silently misrepresenting who is actually assigned and leaving
  // the confirm button disabled with no visible explanation.
  const filteredUsers = (tenantUsers.data ?? []).filter((u) => u.ativo !== false);
  const currentStillActive = filteredUsers.some((u) => u.id === currentResponsavelId);

  const handleConfirm = async () => {
    setReatribuirError(null);
    try {
      await reatribuir.mutateAsync(selectedUserId);
      toast.success("Responsável reatribuído com sucesso.");
      setConfirmOpen(false);
    } catch (e) {
      const msg =
        e instanceof Error
          ? e.message
          : "Não foi possível reatribuir o responsável. Verifique a ligação e tente novamente.";
      setReatribuirError(msg);
      toast.error(msg);
    }
  };

  return (
    <>
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogTrigger asChild>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="rounded-none"
            onClick={() => {
              setSelectedUserId(currentResponsavelId ?? "");
              setReatribuirError(null);
              setDialogOpen(true);
            }}
          >
            Reatribuir
          </Button>
        </DialogTrigger>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reatribuir Responsável</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Responsável atual: {currentResponsavelNome ?? "Não atribuído"}
            </p>
            <div className="space-y-2">
              <Label htmlFor="reatribuir_responsavel">Novo Responsável</Label>
              <NativeSelect
                id="reatribuir_responsavel"
                size="default"
                className="w-full"
                value={selectedUserId}
                onChange={(e) => setSelectedUserId(e.target.value)}
              >
                <option value="" disabled>
                  Selecione um utilizador
                </option>
                {!currentStillActive && currentResponsavelId && currentResponsavelNome ? (
                  <option value={currentResponsavelId}>{currentResponsavelNome} (inativo)</option>
                ) : null}
                {filteredUsers.map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.nome}
                  </option>
                ))}
              </NativeSelect>
            </div>
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="outline"
              className="rounded-none"
              onClick={() => setDialogOpen(false)}
            >
              Cancelar
            </Button>
            <Button
              type="button"
              className="rounded-none"
              disabled={!selectedUserId || selectedUserId === currentResponsavelId || tenantUsers.isLoading}
              onClick={() => {
                setDialogOpen(false);
                setConfirmOpen(true);
              }}
            >
              Reatribuir
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={confirmOpen}
        onOpenChange={(next) => {
          if (reatribuir.isPending) return;
          setConfirmOpen(next);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Confirmar Reatribuição</AlertDialogTitle>
            <AlertDialogDescription>
              O processo {numero} passará a ser da responsabilidade de {novoNome}. O novo
              responsável é notificado de imediato.
            </AlertDialogDescription>
          </AlertDialogHeader>

          {reatribuirError ? <p className="text-sm text-red-600">{reatribuirError}</p> : null}

          <AlertDialogFooter>
            <AlertDialogCancel disabled={reatribuir.isPending}>Cancelar</AlertDialogCancel>
            <AlertDialogAction
              disabled={reatribuir.isPending}
              className="bg-blue-600 hover:bg-blue-700 text-white"
              onClick={(e) => {
                e.preventDefault();
                void handleConfirm();
              }}
            >
              {reatribuir.isPending ? "A reatribuir..." : "Confirmar Reatribuição"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

function ProcessoDocumentosTab({
  processoId,
  canEditDocumentos,
}: {
  processoId: string;
  canEditDocumentos: boolean;
}) {
  const list = useDocumentos({ processo_id: processoId });
  const documentosData = list.data;
  const documentos = documentosData ?? [];

  const [addDocumentoModal, setAddDocumentoModal] = React.useState(false);
  const [novoTipo, setNovoTipo] = React.useState("");
  const [novoFicheiro, setNovoFicheiro] = React.useState<File | null>(null);
  const [progresso, setProgresso] = React.useState<number | null>(null);
  const [uploadError, setUploadError] = React.useState<string | null>(null);

  const upload = useUploadDocumentoComProgresso({ onProgress: setProgresso });

  const tipoOptions = React.useMemo(
    () =>
      Array.from(
        new Set(
          (documentosData ?? [])
            .map((d) => d.tipo?.trim())
            .filter((t): t is string => Boolean(t)),
        ),
      ),
    [documentosData],
  );

  const datalistId = `tipo-documento-processo-${processoId}`;

  const resetUploadState = () => {
    setNovoFicheiro(null);
    setNovoTipo("");
    setProgresso(null);
    setUploadError(null);
  };

  const onConfirmarUpload = async () => {
    if (!novoFicheiro) return;
    setUploadError(null);
    try {
      await upload.mutateAsync({ file: novoFicheiro, tipo: novoTipo.trim(), processo_id: processoId });
      setProgresso(null);
      toast.success("Documento enviado com sucesso.");
      resetUploadState();
      setAddDocumentoModal(false);
    } catch (e) {
      setProgresso(null);
      const msg = e instanceof Error ? e.message : "Erro ao fazer upload";
      setUploadError(msg);
      toast.error(msg);
    }
  };

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <CardTitle>Documentos</CardTitle>
          {canEditDocumentos ? (
            <Dialog
              open={addDocumentoModal}
              onOpenChange={(open) => {
                setAddDocumentoModal(open);
                if (!open) resetUploadState();
              }}
            >
              <DialogTrigger asChild>
                <Button type="button" variant="outline" size="sm" className="rounded-none">
                  Adicionar Documento
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Adicionar Documento</DialogTitle>
                </DialogHeader>
                <div className="space-y-4">
                  <div className="space-y-2">
                    <Label>Ficheiro</Label>
                    <FileDropZone
                      onFileChange={(file) => setNovoFicheiro(file)}
                      onClear={() => setNovoFicheiro(null)}
                      accept="image/*,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.txt"
                      disabled={upload.isPending}
                    >
                      Arraste um ficheiro para aqui ou clique para selecionar
                    </FileDropZone>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor={datalistId}>Tipo</Label>
                    <input
                      id={datalistId}
                      list={`${datalistId}-options`}
                      className="flex h-9 w-full rounded-none border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300"
                      placeholder="Selecione ou escreva um tipo"
                      value={novoTipo}
                      onChange={(e) => setNovoTipo(e.target.value)}
                      disabled={upload.isPending}
                    />
                    <datalist id={`${datalistId}-options`}>
                      {tipoOptions.map((t) => (
                        <option key={t} value={t} />
                      ))}
                    </datalist>
                  </div>
                  {progresso !== null ? (
                    <div className="space-y-1">
                      <div className="flex justify-between text-xs text-neutral-500">
                        <span>A enviar...</span>
                        <span>{progresso}%</span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-neutral-200 dark:bg-neutral-700">
                        <div
                          className="h-2 rounded-full bg-blue-600 transition-all"
                          style={{ width: `${progresso}%` }}
                        />
                      </div>
                    </div>
                  ) : null}
                  {uploadError ? <p className="text-sm text-red-600">{uploadError}</p> : null}
                </div>
                <DialogFooter>
                  <Button
                    type="button"
                    variant="outline"
                    className="rounded-none"
                    onClick={() => {
                      setAddDocumentoModal(false);
                      resetUploadState();
                    }}
                  >
                    Cancelar
                  </Button>
                  <Button
                    type="button"
                    className="rounded-none"
                    onClick={onConfirmarUpload}
                    disabled={!novoFicheiro || upload.isPending}
                  >
                    Confirmar
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          ) : null}
        </div>
      </CardHeader>
      <CardContent>
        {list.isLoading ? (
          <p className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</p>
        ) : list.isError ? (
          <p className="text-sm text-red-600">Não foi possível carregar os documentos deste processo.</p>
        ) : documentos.length === 0 ? (
          <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento registado.</p>
        ) : (
          <DataTable columns={columns(canEditDocumentos)} data={documentos} getRowId={(d) => d.id} />
        )}
      </CardContent>
    </Card>
  );
}
