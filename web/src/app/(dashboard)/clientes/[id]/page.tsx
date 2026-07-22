"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import * as React from "react";
import { ArrowLeft, Check, Download, Pencil, Plus, Printer, Save, Trash2, Upload, X } from "lucide-react";
import { Controller, useForm } from "react-hook-form";

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
import { Progress } from "@/components/ui/progress";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Combobox } from "@/components/shared/combobox";
import { FileDropZone } from "@/components/shared/file-drop-zone";
import {
  useAddAdministrativo,
  useAddAdvogado,
  useCliente,
  useClienteAdministrativos,
  useClienteAdvogados,
  useClienteContaCorrente,
  useDeleteProcuracao,
  useDownloadProcuracao,
  useRemoveAdministrativo,
  useRemoveAdvogado,
  useUpdateCliente,
  useUploadProcuracao,
} from "@/hooks/use-clientes";
import {
  useClienteContactos,
  useCreateClienteContacto,
  useDeleteClienteContacto,
  useUpdateClienteContacto,
} from "@/hooks/use-cliente-contactos";
import {
  useClienteNotas,
  useCreateClienteNota,
  useDeleteClienteNota,
  useUpdateClienteNota,
} from "@/hooks/use-cliente-notas";
import { useAdminUsers } from "@/hooks/use-admin";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { usePareceres } from "@/hooks/use-pareceres";
import {
  useDeleteDocumento,
  useDocumentos,
  useDownloadDocumento,
  useUploadDocumentoComProgresso,
} from "@/hooks/use-documentos";
import {
  getDocumentoTipoLabel,
  getDocumentoTipoOptions,
  toDocumentoTipo,
} from "@/lib/cliente-documento-tipo";
import { buildClienteFormSchema, type ClienteFormValues } from "@/schemas/clientes";
import type { ClienteContacto } from "@/types/clientes-contactos";
import type { ClienteNota } from "@/types/clientes-notas";
import type {
  Cliente,
  ClienteAdvogadoUser,
  ClienteUpdateRequest,
  Deslocacao,
  DocumentoATratar,
} from "@/types/clientes";
import type { ParecerStatus } from "@/types/pareceres";
import type { Documento } from "@/types/documentos";
import { toast } from "@/hooks/use-toast";

type PageProps = {
  params: Promise<{ id: string }>;
};

type TabKey =
  | "dados"
  | "contactosNotas"
  | "processos"
  | "pareceres"
  | "documentosEntregues"
  | "documentosATratar"
  | "deslocacoes";

function formatMoneyCVE(v: number) {
  return v.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
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

const textareaClassName =
  "flex min-h-24 w-full border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

export default function ClienteDetailPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canViewClientes = permissions.can.view("clientes");
  const canEditClientes = permissions.can.edit("clientes");

  if (permissions.isFetched && !canViewClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este cliente."
        backHref="/clientes"
      />
    );
  }

  return <ClienteDetailContent id={id} canEditClientes={canEditClientes} />;
}

function ClienteDetailContent({ id, canEditClientes }: { id: string; canEditClientes: boolean }) {
  const permissions = usePermissions();
  const canViewProcessos = permissions.can.view("processos");
  const canViewPareceres = permissions.can.view("pareceres");
  const canViewDocumentos = permissions.can.view("documentos");
  const canEditDocumentos = permissions.can.edit("documentos");
  const cliente = useCliente(id);
  const conta = useClienteContaCorrente(id);
  const contactos = useClienteContactos(id);
  const notas = useClienteNotas(id);
  const isLoading = cliente.isLoading || conta.isLoading;
  const isError = cliente.isError || conta.isError;

  const update = useUpdateCliente(id);

  const searchParams = useSearchParams();
  // Deep-link into edit mode via ?edit=1 (Clientes list mobile "Editar" quick action).
  // Gated on canEditClientes so a view-only user can never have the edit form forced open.
  const [isEditing, setIsEditing] = React.useState(
    () => canEditClientes && searchParams.get("edit") === "1",
  );
  const [tab, setTab] = React.useState<TabKey>("dados");
  const [serverError, setServerError] = React.useState<string | null>(null);
  const [legacyDocumentoTipo, setLegacyDocumentoTipo] = React.useState<string | null>(null);

  // Rebuilt whenever the loaded legacy documento_tipo changes so the resolver exempts exactly
  // that one value from the per-tipo membership check (see buildClienteFormSchema docblock).
  const clienteFormSchema = React.useMemo(
    () => buildClienteFormSchema(legacyDocumentoTipo ?? undefined),
    [legacyDocumentoTipo],
  );

  const form = useForm<ClienteFormValues>({
    resolver: zodResolver(clienteFormSchema),
    defaultValues: {
      nome: "",
      nif: "",
      tipo: undefined,
      avencado: false,
      email: "",
      telefone: "",
      localidade: "",
      morada: "",
      documento_tipo: "",
      documento_numero: "",
      ramo_atividade: "",
      detalhes_adicionais: "",
      descricao_caso: "",
      honorarios_propostos: {
        total: undefined,
        totalPorExtenso: "",
        previsao: "",
      },
    },
  });

  const [pendingTipo, setPendingTipo] = React.useState<"PARTICULAR" | "EMPRESA" | null>(null);

  function onTipoChange(newTipo: "PARTICULAR" | "EMPRESA") {
    const current = form.getValues("tipo");
    if (current && current !== newTipo) {
      setPendingTipo(newTipo);
    } else {
      form.setValue("tipo", newTipo, { shouldValidate: true });
    }
  }

  function confirmTipoChange() {
    if (!pendingTipo) return;
    const currentDocumentoTipo = form.getValues("documento_tipo");
    if (toDocumentoTipo(currentDocumentoTipo, pendingTipo) === undefined) {
      form.setValue("documento_tipo", "");
      form.setValue("documento_numero", "");
      setLegacyDocumentoTipo(null);
    }
    form.setValue("tipo", pendingTipo, { shouldValidate: true });
    setPendingTipo(null);
  }

  // Intake list state — managed outside react-hook-form, synced into the PUT payload on submit.
  const [documentosATratar, setDocumentosATratar] = React.useState<DocumentoATratar[]>([]);
  const [deslocacoes, setDeslocacoes] = React.useState<Deslocacao[]>([]);

  const [addDocATratarModal, setAddDocATratarModal] = React.useState(false);
  const [newDocATratar, setNewDocATratar] = React.useState<{ descricao: string }>({ descricao: "" });

  const [addDeslocacaoModal, setAddDeslocacaoModal] = React.useState(false);
  const [newDeslocacao, setNewDeslocacao] = React.useState<{ descricao: string; local: string; data: string }>({
    descricao: "",
    local: "",
    data: "",
  });

  // The three "Adicionar" dialogs above are only rendered inside the "Dados" tab's JSX, but their
  // open/draft state is hoisted here so it isn't lost across a tab round-trip. Because the dialogs
  // are unmounted (not just hidden) when the user leaves "Dados", a controlled `open={true}` left
  // over from before the switch would otherwise cause the dialog to reopen automatically — with
  // stale draft text — the moment the user navigates back to "Dados". Close and clear them whenever
  // the user is not on "Dados", mirroring the reset effect used for AdvogadosResponsaveisCard-style
  // sub-components below (see the `if (!editable) setModalOpen(false)` pattern).
  React.useEffect(() => {
    if (tab !== "documentosATratar") {
      setAddDocATratarModal(false);
      setNewDocATratar({ descricao: "" });
    }
    if (tab !== "deslocacoes") {
      setAddDeslocacaoModal(false);
      setNewDeslocacao({ descricao: "", local: "", data: "" });
    }
  }, [tab]);

  const buildDefaultValues = React.useCallback((data: Cliente) => {
    const loadedTipo = (data.tipo as "PARTICULAR" | "EMPRESA" | undefined) ?? undefined;
    const loadedDocumentoTipo = data.documento_tipo ?? data.documentoTipo ?? "";
    return {
      nome: data.nome ?? "",
      nif: data.nif ?? "",
      tipo: loadedTipo,
      avencado: data.avencado ?? false,
      email: data.email ?? "",
      telefone: data.telefone ?? "",
      localidade: data.localidade ?? "",
      morada: data.morada ?? "",
      documento_tipo: loadedDocumentoTipo,
      documento_numero: data.documento_numero ?? data.documentoNumero ?? "",
      ramo_atividade: data.ramo_atividade ?? data.ramoAtividade ?? "",
      detalhes_adicionais: data.detalhes_adicionais ?? data.detalhesAdicionais ?? "",
      descricao_caso: data.descricao_caso ?? "",
      honorarios_propostos: {
        total: data.honorarios_propostos?.total ?? undefined,
        totalPorExtenso: data.honorarios_propostos?.totalPorExtenso ?? "",
        previsao: data.honorarios_propostos?.previsao ?? "",
      },
    };
  }, []);

  // Detect a pre-existing documento_tipo that isn't valid for the loaded tipo (e.g. legacy
  // data not covered by the 74-cleanup-nif-documento-tipo.sql migration). If we silently fed
  // this into the native select element, the browser would fall back to the first option
  // ("Nenhum", value "") since there's no matching option, and an unrelated save would then
  // clear the field as a side effect. Instead, flag it and keep the raw value selected below.
  // Shared by the load effect AND onCancel so that cancelling a tipo-change confirmation
  // restores this flag in lockstep with the form values it resets alongside (CR-01 review fix:
  // previously onCancel only reset the form, leaving legacyDocumentoTipo out of sync and
  // silently dropping the legacy value on the next unrelated save).
  const computeLegacyDocumentoTipo = React.useCallback((data: Cliente) => {
    const loadedTipo = (data.tipo as "PARTICULAR" | "EMPRESA" | undefined) ?? undefined;
    const loadedDocumentoTipo = data.documento_tipo ?? data.documentoTipo ?? "";
    const isValidCombo =
      !loadedDocumentoTipo ||
      getDocumentoTipoOptions(loadedTipo).some((opt) => opt.value === loadedDocumentoTipo);
    return isValidCombo ? null : loadedDocumentoTipo;
  }, []);

  React.useEffect(() => {
    if (!cliente.data) return;
    setLegacyDocumentoTipo(computeLegacyDocumentoTipo(cliente.data));
    form.reset(buildDefaultValues(cliente.data));
    setDocumentosATratar(cliente.data.documentos_a_tratar ?? []);
    setDeslocacoes(cliente.data.deslocacoes ?? []);
  }, [cliente.data, form, buildDefaultValues, computeLegacyDocumentoTipo]);

  function confirmAddDocATratar() {
    if (!newDocATratar.descricao.trim()) return;
    setDocumentosATratar((prev) => [...prev, { ...newDocATratar }]);
    setNewDocATratar({ descricao: "" });
    setAddDocATratarModal(false);
  }

  function confirmAddDeslocacao() {
    if (!newDeslocacao.descricao.trim()) return;
    setDeslocacoes((prev) => [...prev, { ...newDeslocacao }]);
    setNewDeslocacao({ descricao: "", local: "", data: "" });
    setAddDeslocacaoModal(false);
  }

  const onSubmit = async (values: ClienteFormValues) => {
    setServerError(null);
    try {
      // Preserve a legacy documento_tipo/tipo combo verbatim when the user hasn't touched the
      // field (see legacyDocumentoTipo banner above) -- toDocumentoTipo() would otherwise return
      // undefined for it since it isn't in the valid option set for the current tipo, silently
      // clearing a value the user never intended to change.
      const documentoTipo =
        legacyDocumentoTipo && values.documento_tipo === legacyDocumentoTipo
          ? (values.documento_tipo as ClienteUpdateRequest["documento_tipo"])
          : toDocumentoTipo(values.documento_tipo, values.tipo);

      const payload: ClienteUpdateRequest = {
        ...values,
        tipo: values.tipo,
        avencado: values.avencado,
        documento_tipo: documentoTipo,
        documentoTipo: documentoTipo,
        documentoNumero: values.documento_numero || undefined,
        ramoAtividade: values.ramo_atividade || undefined,
        detalhesAdicionais: values.detalhes_adicionais || undefined,
        descricaoCaso: values.descricao_caso || undefined,
        honorariosPropostos: values.honorarios_propostos,
        documentosATratar: documentosATratar,
        deslocacoes: deslocacoes,
      };

      await update.mutateAsync(payload);
      toast.success("Cliente atualizado com sucesso.");
      setNewDocATratar({ descricao: "" });
      setNewDeslocacao({ descricao: "", local: "", data: "" });
      setAddDocATratarModal(false);
      setAddDeslocacaoModal(false);
      setIsEditing(false);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao atualizar cliente";
      setServerError(msg);
      toast.error(msg);
    }
  };

  const onCancel = () => {
    if (cliente.data) {
      form.reset(buildDefaultValues(cliente.data));
      setLegacyDocumentoTipo(computeLegacyDocumentoTipo(cliente.data));
      setDocumentosATratar(cliente.data.documentos_a_tratar ?? []);
      setDeslocacoes(cliente.data.deslocacoes ?? []);
    }
    setNewDocATratar({ descricao: "" });
    setNewDeslocacao({ descricao: "", local: "", data: "" });
    setAddDocATratarModal(false);
    setAddDeslocacaoModal(false);
    setPendingTipo(null);
    setServerError(null);
    setIsEditing(false);
  };

  const tipoValue = form.watch("tipo");
  const nomeLabel = tipoValue === "EMPRESA" ? "Nome Comercial" : "Nome";
  const moradaLabel = tipoValue === "EMPRESA" ? "Sede" : "Morada";

  const isSaving = form.formState.isSubmitting || update.isPending;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Cliente</h1>
          <Breadcrumb>
            <BreadcrumbList>
              <BreadcrumbItem>
                <BreadcrumbLink asChild>
                  <Link href="/clientes">Clientes</Link>
                </BreadcrumbLink>
              </BreadcrumbItem>
              <BreadcrumbSeparator />
              <BreadcrumbItem>
                <BreadcrumbPage>
                  {cliente.data?.numero_cliente ?? cliente.data?.nome ?? "…"}
                </BreadcrumbPage>
              </BreadcrumbItem>
            </BreadcrumbList>
          </Breadcrumb>
        </div>

        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href="/clientes">
              <ArrowLeft className="h-4 w-4" />
              Voltar
            </Link>
          </Button>
          <Button asChild variant="outline">
            <Link
              href={`/clientes/${encodeURIComponent(id)}/ficha`}
              target="_blank"
              rel="noopener noreferrer"
            >
              <Printer className="h-4 w-4 mr-2" />
              Imprimir Ficha
            </Link>
          </Button>
          {isEditing ? (
            <>
              <Button type="button" variant="outline" onClick={onCancel} disabled={isSaving}>
                <X className="h-4 w-4" />
                Cancelar
              </Button>
              <Button
                type="button"
                onClick={form.handleSubmit(onSubmit, () => {
                  // Every editable field lives in the "Dados" tab; if validation fails while the
                  // user is parked on another tab, the inline errors would render nowhere on
                  // screen. Switch back to "Dados" so the errors become visible.
                  setTab("dados");
                  toast.error("Existem campos por corrigir no separador Dados.");
                })}
                disabled={isSaving}
              >
                <Save className="h-4 w-4" />
                {isSaving ? "A guardar..." : "Guardar"}
              </Button>
            </>
          ) : canEditClientes ? (
            <Button type="button" onClick={() => { setIsEditing(true); setTab("dados"); }}>
              <Pencil className="h-4 w-4" />
              Editar
            </Button>
          ) : null}
        </div>
      </div>

      {isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : isError ? (
        <div className="text-sm text-red-600">
          {cliente.error instanceof Error
            ? cliente.error.message
            : conta.error instanceof Error
              ? conta.error.message
              : "Erro ao carregar"}
        </div>
      ) : cliente.data ? (
        <div className="space-y-4">
          <Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}>
          <div className="overflow-x-auto">
            <TabsList variant="default">
              <TabsTrigger value="dados">Dados</TabsTrigger>
              <TabsTrigger value="contactosNotas">Contactos e Notas</TabsTrigger>
              {canViewProcessos ? <TabsTrigger value="processos">Processos</TabsTrigger> : null}
              {canViewPareceres ? <TabsTrigger value="pareceres">Pareceres</TabsTrigger> : null}
              <TabsTrigger value="documentosEntregues">Documentos Entregues</TabsTrigger>
              <TabsTrigger value="documentosATratar">Documentos a Tratar</TabsTrigger>
              <TabsTrigger value="deslocacoes">Deslocações</TabsTrigger>
            </TabsList>
          </div>

          <TabsContent value="dados" className="space-y-4">
          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Dados</CardTitle>
              </CardHeader>
              <CardContent>
                {isEditing ? (
                  <div className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="nome">{nomeLabel}</Label>
                      <Input id="nome" className="max-sm:h-12 max-sm:text-base" {...form.register("nome")} />
                      {form.formState.errors.nome ? (
                        <p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>
                      ) : null}
                    </div>

                    <div className="space-y-2">
                      <Label>Tipo de Cliente</Label>
                      <Controller
                        control={form.control}
                        name="tipo"
                        render={({ field }) => (
                          <RadioGroup
                            value={field.value ?? ""}
                            onValueChange={(val) => onTipoChange(val as "PARTICULAR" | "EMPRESA")}
                            className="flex gap-6"
                          >
                            <div className="flex items-center gap-2">
                              <RadioGroupItem value="PARTICULAR" id="tipo-particular" />
                              <Label htmlFor="tipo-particular" className="cursor-pointer font-normal">Particular</Label>
                            </div>
                            <div className="flex items-center gap-2">
                              <RadioGroupItem value="EMPRESA" id="tipo-empresa" />
                              <Label htmlFor="tipo-empresa" className="cursor-pointer font-normal">Empresa</Label>
                            </div>
                          </RadioGroup>
                        )}
                      />
                      {form.formState.errors.tipo ? (
                        <p className="text-sm text-red-600">{form.formState.errors.tipo.message}</p>
                      ) : null}
                    </div>

                    <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                      <div className="space-y-2">
                        <Label htmlFor="email">Email</Label>
                        <Input id="email" className="max-sm:h-12 max-sm:text-base" {...form.register("email")} />
                        {form.formState.errors.email ? (
                          <p className="text-sm text-red-600">{form.formState.errors.email.message}</p>
                        ) : null}
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="telefone">Telefone</Label>
                        <Input id="telefone" className="max-sm:h-12 max-sm:text-base" {...form.register("telefone")} />
                        {form.formState.errors.telefone ? (
                          <p className="text-sm text-red-600">{form.formState.errors.telefone.message}</p>
                        ) : null}
                      </div>
                    </div>

                    <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                      <div className="space-y-2">
                        <Label htmlFor="localidade">Localidade</Label>
                        <Input id="localidade" className="max-sm:h-12 max-sm:text-base" {...form.register("localidade")} />
                        {form.formState.errors.localidade ? (
                          <p className="text-sm text-red-600">{form.formState.errors.localidade.message}</p>
                        ) : null}
                      </div>
                      <div className="space-y-2">
                        <Label htmlFor="morada">{moradaLabel}</Label>
                        <Input id="morada" className="max-sm:h-12 max-sm:text-base" {...form.register("morada")} />
                        {form.formState.errors.morada ? (
                          <p className="text-sm text-red-600">{form.formState.errors.morada.message}</p>
                        ) : null}
                      </div>
                    </div>

                    <div className="flex items-center gap-3">
                      <Controller
                        control={form.control}
                        name="avencado"
                        render={({ field }) => (
                          <Switch
                            id="avencado"
                            checked={field.value ?? false}
                            onCheckedChange={field.onChange}
                          />
                        )}
                      />
                      <Label htmlFor="avencado" className="cursor-pointer">Avençado</Label>
                    </div>

                    <div className="pt-4 border-t border-neutral-200 dark:border-neutral-800 space-y-4">
                      <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Identificação</h4>

                      <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                        <div className="space-y-2">
                          <Label htmlFor="nif">NIF</Label>
                          <Input id="nif" className="max-sm:h-12 max-sm:text-base" {...form.register("nif")} />
                          {form.formState.errors.nif ? (
                            <p className="text-sm text-red-600">{form.formState.errors.nif.message}</p>
                          ) : null}
                        </div>
                      </div>

                      <div className="grid gap-6 grid-cols-1 md:grid-cols-2">
                        <div className="space-y-2">
                          <Label htmlFor="documento_tipo">Tipo de Documento</Label>
                          <NativeSelect
                            id="documento_tipo"
                            size="default"
                            className="w-full"
                            {...form.register("documento_tipo")}
                          >
                            <option value="">Nenhum</option>
                            {getDocumentoTipoOptions(form.watch("tipo")).map((opt) => (
                              <option key={opt.value} value={opt.value}>
                                {opt.label}
                              </option>
                            ))}
                            {legacyDocumentoTipo ? (
                              <option value={legacyDocumentoTipo}>
                                {legacyDocumentoTipo} (inválido para o tipo atual)
                              </option>
                            ) : null}
                          </NativeSelect>
                          {legacyDocumentoTipo ? (
                            <p className="text-sm text-amber-600">
                              Este cliente tem um tipo de documento (&quot;{legacyDocumentoTipo}&quot;) que já não é
                              válido para o tipo de cliente selecionado. Selecione um tipo de documento válido para
                              corrigir, ou guarde sem alterar este campo para manter o valor legado.
                            </p>
                          ) : null}
                          {form.formState.errors.documento_tipo ? (
                            <p className="text-sm text-red-600">{form.formState.errors.documento_tipo.message}</p>
                          ) : null}
                        </div>

                        <div className="space-y-2">
                          <Label htmlFor="documento_numero">Número do Documento</Label>
                          <Input
                            id="documento_numero"
                            className="max-sm:h-12 max-sm:text-base"
                            placeholder="Introduza o número do documento"
                            {...form.register("documento_numero")}
                          />
                          {form.formState.errors.documento_numero ? (
                            <p className="text-sm text-red-600">{form.formState.errors.documento_numero.message}</p>
                          ) : null}
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                    <dt className="text-neutral-500 dark:text-neutral-400">Nome</dt>
                    <dd className="col-span-2 font-medium flex items-center gap-2 flex-wrap">
                      {cliente.data.nome}
                      {cliente.data.numero_cliente ? (
                        <Badge variant="blue" className="font-mono font-bold text-[10px]">
                          {cliente.data.numero_cliente}
                        </Badge>
                      ) : null}
                      {cliente.data.avencado ? (
                        <Badge variant="green" className="font-bold text-[10px]">
                          Avençado
                        </Badge>
                      ) : null}
                    </dd>

                    <dt className="text-neutral-500 dark:text-neutral-400">Tipo</dt>
                    <dd className="col-span-2">{cliente.data.tipo ?? "—"}</dd>

                    <dt className="text-neutral-500 dark:text-neutral-400">Email</dt>
                    <dd className="col-span-2">{cliente.data.email ?? "—"}</dd>

                    <dt className="text-neutral-500 dark:text-neutral-400">Telefone</dt>
                    <dd className="col-span-2">{cliente.data.telefone ?? "—"}</dd>

                    <dt className="text-neutral-500 dark:text-neutral-400">Localidade</dt>
                    <dd className="col-span-2">{cliente.data.localidade ?? "—"}</dd>

                    <dt className="text-neutral-500 dark:text-neutral-400">{cliente.data.tipo === "EMPRESA" ? "Sede" : "Morada"}</dt>
                    <dd className="col-span-2">{cliente.data.morada ?? "—"}</dd>

                    <dt className="text-neutral-500 dark:text-neutral-400">Ativo</dt>
                    <dd className="col-span-2">{cliente.data.ativo === false ? "Não" : "Sim"}</dd>

                    <dt className="text-neutral-500 dark:text-neutral-400">Criado</dt>
                    <dd className="col-span-2">{new Date(cliente.data.created_at).toLocaleString("pt-CV")}</dd>
                  </dl>
                )}

                {!isEditing ? (
                  <div className="pt-4 border-t border-neutral-200 dark:border-neutral-800 space-y-4">
                    <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Identificação</h4>
                    <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                      <dt className="text-neutral-500 dark:text-neutral-400">NIF</dt>
                      <dd className="col-span-2">{cliente.data.nif ?? "—"}</dd>

                      <dt className="text-neutral-500 dark:text-neutral-400">Tipo de Documento</dt>
                      <dd className="col-span-2">{getDocumentoTipoLabel(cliente.data.documento_tipo ?? cliente.data.documentoTipo) ?? "—"}</dd>

                      <dt className="text-neutral-500 dark:text-neutral-400">Número do Documento</dt>
                      <dd className="col-span-2">{cliente.data.documento_numero ?? cliente.data.documentoNumero ?? "—"}</dd>
                    </dl>
                  </div>
                ) : null}
              </CardContent>
            </Card>

            <div className="space-y-4">
              <Card>
                <CardHeader>
                  <CardTitle>Conta-corrente</CardTitle>
                </CardHeader>
                <CardContent className="space-y-2">
                  <div className="flex items-baseline justify-between gap-4">
                    <div className="text-sm text-neutral-500 dark:text-neutral-400">Saldo</div>
                    <div className="text-2xl font-semibold">
                      {conta.data ? formatMoneyCVE(conta.data.saldo) : "—"}
                    </div>
                  </div>
                  <div className="text-sm text-neutral-500 dark:text-neutral-400">
                    Atualizado em:{" "}
                    <span className="text-neutral-900 dark:text-neutral-50">
                      {conta.data ? new Date(conta.data.updated_at).toLocaleString("pt-CV") : "—"}
                    </span>
                  </div>
                </CardContent>
              </Card>

              <Card className="">
                <CardHeader>
                  <CardTitle>Informações Adicionais</CardTitle>
                </CardHeader>
                <CardContent>
                  {isEditing ? (
                    <div className="space-y-4">
                      <div className="space-y-2">
                        <Label htmlFor="ramo_atividade">Ramo de Atividade</Label>
                        <NativeSelect
                          id="ramo_atividade"
                          size="default"
                          className="w-full"
                          {...form.register("ramo_atividade")}
                        >
                          <option value="">Selecione o ramo de atividade</option>
                          <option value="Banca">Banca</option>
                          <option value="Telecom">Telecom</option>
                          <option value="Construção">Construção</option>
                          <option value="Serviços">Serviços</option>
                          <option value="Comércio">Comércio</option>
                          <option value="Outros">Outros</option>
                        </NativeSelect>
                        {form.formState.errors.ramo_atividade ? (
                          <p className="text-sm text-red-600">{form.formState.errors.ramo_atividade.message}</p>
                        ) : null}
                      </div>

                      <div className="space-y-2">
                        <Label htmlFor="detalhes_adicionais">Detalhes Adicionais</Label>
                        <textarea
                          id="detalhes_adicionais"
                          className={textareaClassName}
                          placeholder="Observações e detalhes adicionais sobre o cliente"
                          {...form.register("detalhes_adicionais")}
                        />
                        {form.formState.errors.detalhes_adicionais ? (
                          <p className="text-sm text-red-600">{form.formState.errors.detalhes_adicionais.message}</p>
                        ) : null}
                      </div>
                    </div>
                  ) : (
                    <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                      <dt className="text-neutral-500 dark:text-neutral-400">Ramo de Atividade</dt>
                      <dd className="col-span-2">{cliente.data.ramo_atividade ?? cliente.data.ramoAtividade ?? "—"}</dd>

                      <dt className="text-neutral-500 dark:text-neutral-400">Detalhes Adicionais</dt>
                      <dd className="col-span-2">
                        {cliente.data.detalhes_adicionais || cliente.data.detalhesAdicionais ? (
                          <span className="whitespace-pre-wrap">{cliente.data.detalhes_adicionais ?? cliente.data.detalhesAdicionais}</span>
                        ) : (
                          <span className="italic text-neutral-400 dark:text-neutral-500">Sem observações</span>
                        )}
                      </dd>
                    </dl>
                  )}
                </CardContent>
              </Card>
            </div>
          </div>

          {isEditing ? (
            <Card>
              <CardHeader>
                <CardTitle>Intake do Caso</CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="descricao_caso">Descrição do Caso</Label>
                  <textarea
                    id="descricao_caso"
                    className={textareaClassName}
                    placeholder="Descreva o caso do cliente"
                    {...form.register("descricao_caso")}
                  />
                  {form.formState.errors.descricao_caso ? (
                    <p className="text-sm text-red-600">{form.formState.errors.descricao_caso.message}</p>
                  ) : null}
                </div>

                <div className="space-y-4 p-4 border border-neutral-200 dark:border-neutral-800 bg-neutral-50/50 dark:bg-neutral-900/20">
                  <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Honorários Propostos</h4>
                  <div className="grid gap-4 grid-cols-1 md:grid-cols-3">
                    <div className="space-y-2">
                      <Label htmlFor="honorarios_propostos.total">Total</Label>
                      <Input
                        id="honorarios_propostos.total"
                        type="number"
                        step="0.01"
                        className=""
                        {...form.register("honorarios_propostos.total", { valueAsNumber: true })}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="honorarios_propostos.totalPorExtenso">Valor por Extenso</Label>
                      <Input
                        id="honorarios_propostos.totalPorExtenso"
                        className=""
                        {...form.register("honorarios_propostos.totalPorExtenso")}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="honorarios_propostos.previsao">Previsão</Label>
                      <Input
                        id="honorarios_propostos.previsao"
                        className=""
                        placeholder="Ex.: 6 meses"
                        {...form.register("honorarios_propostos.previsao")}
                      />
                    </div>
                  </div>
                </div>

                {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}
              </CardContent>
            </Card>
          ) : null}

          <div className="grid gap-4 lg:grid-cols-3">
            <ProcuracaoCard
              clienteId={id}
              canEditClientes={canEditClientes}
              editable={isEditing}
              procuracaoKey={cliente.data.procuracao_key}
            />
            <ResponsaveisCard
              title="Advogados"
              clienteId={id}
              canEditClientes={canEditClientes}
              editable={isEditing}
              useList={useClienteAdvogados}
              useAdd={useAddAdvogado}
              useRemove={useRemoveAdvogado}
            />
            <ResponsaveisCard
              title="Administrativos"
              clienteId={id}
              canEditClientes={canEditClientes}
              editable={isEditing}
              useList={useClienteAdministrativos}
              useAdd={useAddAdministrativo}
              useRemove={useRemoveAdministrativo}
            />
          </div>
          </TabsContent>

          <TabsContent value="contactosNotas">
          <div className="grid gap-4 lg:grid-cols-2">
            <ClienteContactosCard
              clienteId={id}
              canEditClientes={canEditClientes}
              editable={isEditing}
              data={contactos.data}
              isLoading={contactos.isLoading}
              isError={contactos.isError}
            />
            <ClienteNotasCard
              clienteId={id}
              canEditClientes={canEditClientes}
              editable={isEditing}
              data={notas.data}
              isLoading={notas.isLoading}
              isError={notas.isError}
            />
          </div>
          </TabsContent>

          <TabsContent value="processos">
            {!permissions.isFetched ? (
              <div className="p-6 text-sm text-neutral-500">A carregar...</div>
            ) : canViewProcessos ? (
              <ClienteProcessosTab clienteId={id} />
            ) : (
              <AccessDeniedState description="Não tem permissão para consultar os processos deste cliente." />
            )}
          </TabsContent>

          <TabsContent value="pareceres">
            {!permissions.isFetched ? (
              <div className="p-6 text-sm text-neutral-500">A carregar...</div>
            ) : canViewPareceres ? (
              <ClienteParecerTab clienteId={id} />
            ) : (
              <AccessDeniedState description="Não tem permissão para consultar os pareceres deste cliente." />
            )}
          </TabsContent>

          <TabsContent value="documentosEntregues">
            {!permissions.isFetched ? (
              <div className="p-6 text-sm text-neutral-500">A carregar...</div>
            ) : canViewDocumentos ? (
              <ClienteDocumentosEntreguesTab
                clienteId={id}
                editable={isEditing}
                canEditDocumentos={canEditDocumentos}
              />
            ) : (
              <AccessDeniedState description="Não tem permissão para consultar os documentos deste cliente." />
            )}
          </TabsContent>

          <TabsContent value="documentosATratar">
            <Card>
              <CardContent className="space-y-2 pt-6">
                <div className="flex items-center justify-between">
                  <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Documentos a Tratar</h4>
                  {canEditClientes && isEditing ? (
                    <Dialog open={addDocATratarModal} onOpenChange={setAddDocATratarModal}>
                      <DialogTrigger asChild>
                        <Button type="button" variant="outline" size="sm">
                          <Plus className="h-4 w-4" />
                          Adicionar
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>Adicionar Documento a Tratar</DialogTitle>
                        </DialogHeader>
                        <div className="space-y-4">
                          <div className="space-y-2">
                            <Label htmlFor="new-doc-tratar-descricao">Descrição</Label>
                            <Input
                              id="new-doc-tratar-descricao"
                              className=""
                              value={newDocATratar.descricao}
                              onChange={(e) => setNewDocATratar({ descricao: e.target.value })}
                            />
                          </div>
                        </div>
                        <DialogFooter>
                          <Button type="button" variant="outline" onClick={() => setAddDocATratarModal(false)}>
                            <X className="h-4 w-4" />
                            Cancelar
                          </Button>
                          <Button type="button" onClick={confirmAddDocATratar}>
                            <Check className="h-4 w-4" />
                            Confirmar
                          </Button>
                        </DialogFooter>
                      </DialogContent>
                    </Dialog>
                  ) : null}
                </div>
                {documentosATratar.length === 0 ? (
                  <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento a tratar registado.</p>
                ) : (
                  <ul className="space-y-1">
                    {documentosATratar.map((doc, index) => (
                      <li key={index} className="flex items-center justify-between border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm">
                        <span>{doc.descricao}</span>
                        {canEditClientes && isEditing ? (
                          <button
                            type="button"
                            className="text-neutral-500 hover:text-red-600"
                            onClick={() => setDocumentosATratar((prev) => prev.filter((_, i) => i !== index))}
                            aria-label="Remover"
                          >
                            ✕
                          </button>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="deslocacoes">
            <Card>
              <CardContent className="space-y-2 pt-6">
                <div className="flex items-center justify-between">
                  <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Deslocações</h4>
                  {canEditClientes && isEditing ? (
                    <Dialog open={addDeslocacaoModal} onOpenChange={setAddDeslocacaoModal}>
                      <DialogTrigger asChild>
                        <Button type="button" variant="outline" size="sm">
                          <Plus className="h-4 w-4" />
                          Adicionar
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>Adicionar Deslocação</DialogTitle>
                        </DialogHeader>
                        <div className="space-y-4">
                          <div className="space-y-2">
                            <Label htmlFor="new-deslocacao-descricao">Descrição</Label>
                            <Input
                              id="new-deslocacao-descricao"
                              className=""
                              value={newDeslocacao.descricao}
                              onChange={(e) => setNewDeslocacao((prev) => ({ ...prev, descricao: e.target.value }))}
                            />
                          </div>
                          <div className="space-y-2">
                            <Label htmlFor="new-deslocacao-local">Local</Label>
                            <Input
                              id="new-deslocacao-local"
                              className=""
                              value={newDeslocacao.local}
                              onChange={(e) => setNewDeslocacao((prev) => ({ ...prev, local: e.target.value }))}
                            />
                          </div>
                          <div className="space-y-2">
                            <Label htmlFor="new-deslocacao-data">Data</Label>
                            <Input
                              id="new-deslocacao-data"
                              type="date"
                              className=""
                              value={newDeslocacao.data}
                              onChange={(e) => setNewDeslocacao((prev) => ({ ...prev, data: e.target.value }))}
                            />
                          </div>
                        </div>
                        <DialogFooter>
                          <Button type="button" variant="outline" onClick={() => setAddDeslocacaoModal(false)}>
                            <X className="h-4 w-4" />
                            Cancelar
                          </Button>
                          <Button type="button" onClick={confirmAddDeslocacao}>
                            <Check className="h-4 w-4" />
                            Confirmar
                          </Button>
                        </DialogFooter>
                      </DialogContent>
                    </Dialog>
                  ) : null}
                </div>
                {deslocacoes.length === 0 ? (
                  <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhuma deslocação registada.</p>
                ) : (
                  <ul className="space-y-1">
                    {deslocacoes.map((d, index) => (
                      <li key={index} className="flex items-center justify-between border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm">
                        <span>
                          {d.descricao}
                          {d.local ? ` (${d.local})` : ""}
                          {d.data ? ` — ${d.data}` : ""}
                        </span>
                        {canEditClientes && isEditing ? (
                          <button
                            type="button"
                            className="text-neutral-500 hover:text-red-600"
                            onClick={() => setDeslocacoes((prev) => prev.filter((_, i) => i !== index))}
                            aria-label="Remover"
                          >
                            ✕
                          </button>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>
          </TabsContent>
          </Tabs>
        </div>
      ) : (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">
          Cliente não encontrado.
        </div>
      )}

      <Dialog open={!!pendingTipo} onOpenChange={(open) => { if (!open) setPendingTipo(null); }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Mudar tipo de cliente</DialogTitle>
            <DialogDescription>
              Mudar o tipo irá limpar os dados de {pendingTipo === "PARTICULAR" ? "Empresa" : "Particular"}. Continuar?
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPendingTipo(null)}>
              <X className="h-4 w-4" />
              Cancelar
            </Button>
            <Button onClick={confirmTipoChange}>
              <Check className="h-4 w-4" />
              Continuar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}

function ClienteProcessosTab({ clienteId }: { clienteId: string }) {
  const processos = useProcessos({ cliente_id: clienteId });

  return (
    <Card>
      <CardContent className="p-0 bg-white dark:bg-[#020617]">
        {processos.isLoading ? (
          <div className="p-6 text-sm text-slate-500">A carregar...</div>
        ) : processos.isError ? (
          <div className="p-6 text-sm text-red-600">
            Não foi possível carregar os processos deste cliente.
          </div>
        ) : !processos.data?.length ? (
          <div className="p-6 text-sm text-slate-500">Nenhum processo associado a este cliente.</div>
        ) : (
          <Table>
            <TableHeader className="bg-slate-50/50 dark:bg-slate-900/50">
              <TableRow className="border-b border-slate-200 dark:border-slate-800 hover:bg-transparent">
                <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">NÚMERO</TableHead>
                <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">ESTADO</TableHead>
                <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">ÁREA JURÍDICA</TableHead>
                <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">DATA DE INÍCIO</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {processos.data.map((p) => {
                const estado = (p.estado ?? "").toUpperCase();
                const estadoVariant =
                  estado === "ATIVO"
                    ? "green"
                    : estado === "SUSPENSO"
                      ? "amber"
                      : estado === "TRIAGEM"
                        ? "purple"
                        : estado === "CONCLUIDO" || estado === "ENCERRADO"
                          ? "gray"
                          : "secondary";
                const estadoLabel = estado === "TRIAGEM" ? "EM TRIAGEM" : (p.estado ?? "—");

                return (
                  <TableRow key={p.id} className="border-slate-100 dark:border-slate-800/50 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors">
                    <TableCell>
                      <Link
                        href={`/processos/${encodeURIComponent(p.id)}`}
                        className="font-bold text-slate-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                      >
                        {p.numero || p.titulo || "Sem número"}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <Badge variant={estadoVariant as "green" | "amber" | "gray" | "purple" | "secondary"} className="font-bold tracking-wide">
                        {estadoLabel}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-slate-500 dark:text-slate-400 font-medium">
                      {p.area_juridica ?? "—"}
                    </TableCell>
                    <TableCell className="text-slate-500 dark:text-slate-400 font-medium">
                      {p.data_inicio ? new Date(p.data_inicio).toLocaleDateString("pt-CV") : "—"}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function parecerStatusVariant(status: ParecerStatus) {
  return status === "PENDENTE"
    ? "gray"
    : status === "EM_ELABORACAO"
      ? "blue"
      : status === "EM_REVISAO"
        ? "amber"
        : status === "CONCLUIDO"
          ? "green"
          : "secondary";
}

function formatParecerDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

function ClienteParecerTab({ clienteId }: { clienteId: string }) {
  const pareceres = usePareceres({ clienteId: clienteId });
  const permissions = usePermissions();
  const isAdmin = Boolean(permissions.data?.roles?.includes("ADMIN"));
  const adminUsers = useAdminUsers({ enabled: isAdmin });
  const advogados = React.useMemo(
    () => (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO")),
    [adminUsers.data],
  );
  const advogadoNomeById = React.useMemo(
    () => new Map(advogados.map((u) => [u.id, u.nome] as const)),
    [advogados],
  );

  return (
    <Card>
      <CardContent className="p-0 bg-white dark:bg-[#020617]">
        {pareceres.isLoading ? (
          <div className="p-6 text-sm text-slate-500">A carregar...</div>
        ) : pareceres.isError ? (
          <div className="p-6 text-sm text-red-600">
            Não foi possível carregar os pareceres deste cliente.
          </div>
        ) : !pareceres.data?.length ? (
          <div className="p-6 text-sm text-slate-500">Nenhum parecer associado a este cliente.</div>
        ) : (
          <Table>
            <TableHeader className="bg-slate-50/50 dark:bg-slate-900/50">
              <TableRow className="border-b border-slate-200 dark:border-slate-800 hover:bg-transparent">
                <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">NÚMERO/TÍTULO</TableHead>
                <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">ESTADO</TableHead>
                <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">ADVOGADO RESPONSÁVEL</TableHead>
                <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">DATA DE CRIAÇÃO</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {pareceres.data.map((s) => (
                <TableRow key={s.id} className="border-slate-100 dark:border-slate-800/50 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors">
                  <TableCell className="font-bold text-slate-700 dark:text-slate-300">
                    <Link
                      href={`/pareceres/${encodeURIComponent(s.id)}`}
                      className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                    >
                      {s.descricao}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <Badge variant={parecerStatusVariant(s.status)} className="font-bold tracking-wide">
                      {s.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-slate-500 dark:text-slate-400 font-medium">
                    {advogadoNomeById.get(s.advogadoId ?? "") ?? "—"}
                  </TableCell>
                  <TableCell className="text-slate-500 dark:text-slate-400 font-medium">
                    {formatParecerDate(s.createdAt)}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  );
}

function formatDocumentoSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDocumentoDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return "—";
  return d.toLocaleDateString("pt-CV");
}

function ClienteDocumentosEntreguesTab({
  clienteId,
  editable,
  canEditDocumentos,
}: {
  clienteId: string;
  editable: boolean;
  canEditDocumentos: boolean;
}) {
  const list = useDocumentos({ cliente_id: clienteId });
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

  const datalistId = `tipo-documento-entregue-${clienteId}`;

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
      await upload.mutateAsync({ file: novoFicheiro, tipo: novoTipo.trim(), cliente_id: clienteId });
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
      <CardContent className="space-y-2 pt-6">
        <div className="flex items-center justify-between">
          <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Documentos Entregues</h4>
          {editable && canEditDocumentos ? (
            <Dialog
              open={addDocumentoModal}
              onOpenChange={(open) => {
                setAddDocumentoModal(open);
                if (!open) resetUploadState();
              }}
            >
              <DialogTrigger asChild>
                <Button type="button" variant="outline" size="sm" className="">
                  <Plus className="h-4 w-4" />
                  Adicionar
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Adicionar Documento Entregue</DialogTitle>
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
                    <Combobox
                      id={datalistId}
                      value={novoTipo}
                      onChange={setNovoTipo}
                      options={tipoOptions.map((t) => ({ value: t, label: t }))}
                      creatable
                      emptyMessage="Nenhuma sugestão."
                      placeholder="Selecionar ou escrever tipo..."
                      searchPlaceholder="Pesquisar ou escrever novo tipo..."
                      triggerClassName=""
                      disabled={upload.isPending}
                    />
                  </div>
                  {progresso !== null ? (
                    <div className="space-y-1">
                      <div className="flex justify-between text-xs text-neutral-500">
                        <span>A enviar...</span>
                        <span>{progresso}%</span>
                      </div>
                      <Progress value={progresso ?? 0} />
                    </div>
                  ) : null}
                  {uploadError ? <p className="text-sm text-red-600">{uploadError}</p> : null}
                </div>
                <DialogFooter>
                  <Button
                    type="button"
                    variant="outline"
                    className=""
                    onClick={() => {
                      setAddDocumentoModal(false);
                      resetUploadState();
                    }}
                  >
                    <X className="h-4 w-4" />
                    Cancelar
                  </Button>
                  <Button
                    type="button"
                    className=""
                    onClick={onConfirmarUpload}
                    disabled={!novoFicheiro || upload.isPending}
                  >
                    <Upload className="h-4 w-4" />
                    Confirmar
                  </Button>
                </DialogFooter>
              </DialogContent>
            </Dialog>
          ) : null}
        </div>

        {list.isLoading ? (
          <p className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</p>
        ) : list.isError ? (
          <p className="text-sm text-red-600">
            {list.error instanceof Error ? list.error.message : "Não foi possível carregar os documentos entregues deste cliente."}
          </p>
        ) : documentos.length === 0 ? (
          <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento entregue registado.</p>
        ) : (
          <ul className="space-y-1">
            {documentos.map((doc: Documento) => (
              <ClienteDocumentoEntregueRow
                key={doc.id}
                documento={doc}
                editable={editable}
                canEditDocumentos={canEditDocumentos}
              />
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

function ClienteDocumentoEntregueRow({
  documento,
  editable,
  canEditDocumentos,
}: {
  documento: Documento;
  editable: boolean;
  canEditDocumentos: boolean;
}) {
  const del = useDeleteDocumento(documento.id);
  const download = useDownloadDocumento(documento.id);

  // WR-01: the backend serializes the raw Documento entity with its Java field
  // names (tamanho, createdAt), not the snake_case/renamed shape declared on the
  // shared frontend `Documento` type (size, created_at). Read the actual wire
  // fields here rather than widening the shared type (which is also consumed by
  // the generic /documentos pages outside this phase's scope).
  const wireDocumento = documento as unknown as { tamanho?: number; createdAt?: string };
  const tamanho = wireDocumento.tamanho ?? 0;
  const criadoEm = wireDocumento.createdAt;

  const onDelete = async () => {
    const ok = window.confirm("Apagar este documento?");
    if (!ok) return;
    try {
      await del.mutateAsync();
      toast.success("Documento apagado com sucesso.");
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao apagar documento";
      toast.error(msg);
    }
  };

  const onDownload = async () => {
    try {
      const res = await download.mutateAsync();
      window.open(res.url, "_blank", "noopener,noreferrer");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao gerar link de download.");
    }
  };

  return (
    <li className="flex items-center justify-between border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm">
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-medium">{documento.nome}</span>
          <span className="text-neutral-500 dark:text-neutral-400">{documento.tipo || "—"}</span>
        </div>
        <div className="text-xs text-neutral-500 dark:text-neutral-400">
          {formatDocumentoSize(tamanho)} · {formatDocumentoDate(criadoEm)}
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-3">
        <button
          type="button"
          onClick={onDownload}
          disabled={download.isPending}
          className="text-blue-600 dark:text-blue-400 hover:underline text-xs font-medium disabled:opacity-50"
        >
          Download
        </button>
        {editable && canEditDocumentos ? (
          <button
            type="button"
            className="text-neutral-500 hover:text-red-600"
            onClick={onDelete}
            aria-label="Remover"
            disabled={del.isPending}
          >
            ✕
          </button>
        ) : null}
      </div>
    </li>
  );
}

function ProcuracaoCard({
  clienteId,
  canEditClientes,
  editable,
  procuracaoKey,
}: {
  clienteId: string;
  canEditClientes: boolean;
  editable: boolean;
  procuracaoKey?: string;
}) {
  const upload = useUploadProcuracao(clienteId);
  const download = useDownloadProcuracao();
  const del = useDeleteProcuracao(clienteId);
  const hasProcuracao = Boolean(procuracaoKey);

  const onFileChange = async (file: File) => {
    if (!canEditClientes || !editable) return;
    const formData = new FormData();
    formData.append("file", file);
    try {
      await upload.mutateAsync(formData);
      toast.success(hasProcuracao ? "Procuração substituída com sucesso." : "Procuração carregada com sucesso.");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao carregar procuração.");
    }
  };

  const onView = async () => {
    try {
      const res = await download.mutateAsync(clienteId);
      window.open(res.url, "_blank", "noopener,noreferrer");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao gerar link de download.");
    }
  };

  const onRemove = async () => {
    if (!canEditClientes || !editable) return;
    const ok = window.confirm("Remover a procuração deste cliente?");
    if (!ok) return;
    try {
      await del.mutateAsync();
      toast.success("Procuração removida com sucesso.");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao remover procuração.");
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          Procuração
          {!hasProcuracao ? (
            <Badge variant="amber" className="font-normal">Procuração em falta</Badge>
          ) : null}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {hasProcuracao ? (
          <div className="space-y-3">
            <div className="flex items-center gap-2 text-sm text-neutral-700 dark:text-neutral-200">
              <Badge variant="green" className="">Carregada</Badge>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="button" variant="secondary" onClick={onView} disabled={download.isPending}>
                <Download className="h-4 w-4" />
                {download.isPending ? "A preparar..." : "Ver / Download"}
              </Button>
              {canEditClientes && editable ? (
                <Button type="button" variant="outline" onClick={onRemove} disabled={del.isPending}>
                  <Trash2 className="h-4 w-4" />
                  Remover
                </Button>
              ) : null}
            </div>
            {canEditClientes && editable ? (
              <div className="pt-2">
                <Label className="mb-2 block text-xs text-neutral-500 dark:text-neutral-400">
                  Substituir ficheiro
                </Label>
                <FileDropZone
                  onFileChange={onFileChange}
                  accept="application/pdf,image/*,.doc,.docx"
                  disabled={upload.isPending}
                >
                  Arraste a nova procuração para aqui ou clique para selecionar
                </FileDropZone>
              </div>
            ) : null}
          </div>
        ) : canEditClientes && editable ? (
          <FileDropZone
            onFileChange={onFileChange}
            accept="application/pdf,image/*,.doc,.docx"
            disabled={upload.isPending}
          >
            Arraste a procuração para aqui ou clique para selecionar
          </FileDropZone>
        ) : (
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            Sem procuração carregada.
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function ResponsaveisCard({
  title,
  clienteId,
  canEditClientes,
  editable,
  useList,
  useAdd,
  useRemove,
}: {
  title: string;
  clienteId: string;
  canEditClientes: boolean;
  editable: boolean;
  useList: (clienteId: string) => { data?: ClienteAdvogadoUser[]; isLoading: boolean; isError: boolean };
  useAdd: (clienteId: string) => { mutateAsync: (userId: string) => Promise<unknown>; isPending: boolean };
  useRemove: (clienteId: string) => { mutateAsync: (userId: string) => Promise<unknown>; isPending: boolean };
}) {
  const list = useList(clienteId);
  const add = useAdd(clienteId);
  const remove = useRemove(clienteId);
  const adminUsers = useAdminUsers();

  const [modalOpen, setModalOpen] = React.useState(false);
  const [selectedUserId, setSelectedUserId] = React.useState("");

  // Close the add-modal if it was open when the parent exits edit mode, so an action that is no
  // longer permitted doesn't remain visibly open with no explanation.
  React.useEffect(() => {
    if (!editable) setModalOpen(false);
  }, [editable]);

  const existingIds = new Set((list.data ?? []).map((u) => u.id));
  const candidateUsers = (adminUsers.data ?? []).filter(
    (u) => !existingIds.has((u as { id: string }).id),
  ) as Array<{ id: string; nome?: string; email?: string }>;

  const onAdd = async () => {
    if (!canEditClientes || !editable || !selectedUserId) return;
    try {
      await add.mutateAsync(selectedUserId);
      toast.success("Adicionado com sucesso.");
      setModalOpen(false);
      setSelectedUserId("");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao adicionar.");
    }
  };

  const onRemove = async (userId: string) => {
    if (!canEditClientes || !editable) return;
    const ok = window.confirm("Remover este utilizador?");
    if (!ok) return;
    try {
      await remove.mutateAsync(userId);
      toast.success("Removido com sucesso.");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao remover.");
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {canEditClientes && editable ? (
          <Button type="button" variant="secondary" onClick={() => setModalOpen(true)}>
            <Plus className="h-4 w-4" />
            Adicionar
          </Button>
        ) : null}

        {list.isLoading ? (
          <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
        ) : list.isError ? (
          <div className="text-sm text-red-600">Erro ao carregar.</div>
        ) : !list.data?.length ? (
          <div className="text-sm text-neutral-500 dark:text-neutral-400">Sem registos.</div>
        ) : (
          <div className="space-y-2">
            {list.data.map((u) => (
              <div
                key={u.id}
                className="flex items-center justify-between gap-3 rounded-md border border-neutral-200 dark:border-neutral-800 p-2"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <Avatar size="sm">
                    <AvatarFallback>{deriveInitials(u.nome)}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0">
                    <div className="font-medium text-sm truncate">{u.nome}</div>
                    <div className="text-xs text-neutral-500 dark:text-neutral-400 truncate space-x-2">
                      {u.numeroCedula ? <span>Cédula: {u.numeroCedula}</span> : null}
                      {u.telefone ? <span>Tel: {u.telefone}</span> : null}
                      {u.email ? <span>{u.email}</span> : null}
                    </div>
                  </div>
                </div>
                {canEditClientes && editable ? (
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() => onRemove(u.id)}
                    disabled={remove.isPending}
                  >
                    <Trash2 className="h-4 w-4" />
                    Remover
                  </Button>
                ) : null}
              </div>
            ))}
          </div>
        )}
      </CardContent>

      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Adicionar a {title}</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <Label>Utilizador</Label>
            <NativeSelect
              value={selectedUserId}
              onChange={(e) => setSelectedUserId(e.target.value)}
              size="default"
              className="w-full"
            >
              <option value="">Selecionar utilizador...</option>
              {candidateUsers.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.nome ?? u.email ?? u.id}
                </option>
              ))}
            </NativeSelect>
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>
              <X className="h-4 w-4" />
              Cancelar
            </Button>
            <Button type="button" onClick={onAdd} disabled={!selectedUserId || add.isPending}>
              <Plus className="h-4 w-4" />
              Adicionar
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  );
}

function ClienteContactosCard({
  clienteId,
  canEditClientes,
  editable,
  data,
  isLoading,
  isError,
}: {
  clienteId: string;
  canEditClientes: boolean;
  editable: boolean;
  data: ClienteContacto[] | undefined;
  isLoading: boolean;
  isError: boolean;
}) {
  const create = useCreateClienteContacto(clienteId);
  const update = useUpdateClienteContacto(clienteId);
  const del = useDeleteClienteContacto(clienteId);

  const [newTipo, setNewTipo] = React.useState("EMAIL");
  const [newValor, setNewValor] = React.useState("");

  const [editingId, setEditingId] = React.useState<string | null>(null);
  const [editTipo, setEditTipo] = React.useState("");
  const [editValor, setEditValor] = React.useState("");

  // Reset any in-progress row edit when the parent exits edit mode (Save or Cancel), so stale
  // editingId/editTipo/editValor never resurface if the parent re-enters edit mode later.
  React.useEffect(() => {
    if (!editable) {
      setEditingId(null);
      setEditTipo("");
      setEditValor("");
    }
  }, [editable]);

  const onCreate = async () => {
    if (!canEditClientes || !editable) return;
    const valor = newValor.trim();
    if (!valor) return;
    try {
      await create.mutateAsync({ tipo: newTipo, valor });
      toast.success("Contacto adicionado com sucesso.");
      setNewValor("");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao adicionar contacto.");
    }
  };

  const onStartEdit = (c: ClienteContacto) => {
    setEditingId(c.id);
    setEditTipo(c.tipo ?? "");
    setEditValor(c.valor);
  };

  const onCancelEdit = () => {
    setEditingId(null);
    setEditTipo("");
    setEditValor("");
  };

  const onSaveEdit = async () => {
    if (!canEditClientes || !editable) return;
    if (!editingId) return;
    const valor = editValor.trim();
    if (!valor) return;
    try {
      await update.mutateAsync({ contactoId: editingId, payload: { tipo: editTipo || undefined, valor } });
      toast.success("Contacto atualizado com sucesso.");
      onCancelEdit();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao atualizar contacto.");
    }
  };

  const onDelete = async (id: string) => {
    if (!canEditClientes || !editable) return;
    const ok = window.confirm("Remover este contacto?");
    if (!ok) return;
    try {
      await del.mutateAsync(id);
      toast.success("Contacto removido com sucesso.");
      if (editingId === id) onCancelEdit();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao remover contacto.");
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Contactos</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {canEditClientes && editable ? (
          <div className="grid gap-2 sm:grid-cols-[140px_1fr_auto]">
            <NativeSelect
              value={newTipo}
              onChange={(e) => setNewTipo(e.target.value)}
              size="default"
              className="w-full"
            >
              <option value="EMAIL">Email</option>
              <option value="TELEFONE">Telefone</option>
              <option value="OUTRO">Outro</option>
            </NativeSelect>
            <input
              value={newValor}
              onChange={(e) => setNewValor(e.target.value)}
              placeholder="Valor do contacto"
              className="h-10 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
            />
            <Button type="button" onClick={onCreate} disabled={create.isPending || !newValor.trim()}>
              <Plus className="h-4 w-4" />
              Adicionar
            </Button>
          </div>
        ) : null}

        {isLoading ? (
          <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
        ) : isError ? (
          <div className="text-sm text-red-600">Erro ao carregar contactos.</div>
        ) : !data?.length ? (
          <div className="text-sm text-neutral-500 dark:text-neutral-400">Sem contactos registados.</div>
        ) : (
          <div className="space-y-2">
            {data.map((c) => {
              const isEditing = editingId === c.id;
              return (
                <div
                  key={c.id}
                  className="flex items-start justify-between gap-3 rounded-md border border-neutral-200 dark:border-neutral-800 p-3"
                >
                  <div className="min-w-0 flex-1">
                    {isEditing ? (
                      <div className="grid gap-2 sm:grid-cols-[140px_1fr]">
                        <NativeSelect
                          value={editTipo}
                          onChange={(e) => setEditTipo(e.target.value)}
                          size="default"
                          className="w-full"
                        >
                          <option value="">—</option>
                          <option value="EMAIL">Email</option>
                          <option value="TELEFONE">Telefone</option>
                          <option value="OUTRO">Outro</option>
                        </NativeSelect>
                        <input
                          value={editValor}
                          onChange={(e) => setEditValor(e.target.value)}
                          className="h-9 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
                        />
                      </div>
                    ) : (
                      <div className="space-y-1">
                        <div className="text-xs font-semibold uppercase tracking-wider text-neutral-500 dark:text-neutral-400">
                          {c.tipo ?? "CONTACTO"}
                        </div>
                        <div className="font-medium text-neutral-900 dark:text-neutral-50 truncate">{c.valor}</div>
                      </div>
                    )}
                    <div className="mt-1 text-xs text-neutral-500 dark:text-neutral-400">
                      {new Date(c.created_at).toLocaleString("pt-CV")}
                    </div>
                  </div>

                  {canEditClientes && editable ? (
                    <div className="flex items-center gap-2">
                      {isEditing ? (
                        <>
                          <Button
                            type="button"
                            variant="secondary"
                            onClick={onSaveEdit}
                            disabled={update.isPending || !editValor.trim()}
                          >
                            <Save className="h-4 w-4" />
                            Guardar
                          </Button>
                          <Button type="button" variant="outline" onClick={onCancelEdit}>
                            <X className="h-4 w-4" />
                            Cancelar
                          </Button>
                        </>
                      ) : (
                        <>
                          <Button type="button" variant="secondary" onClick={() => onStartEdit(c)}>
                            <Pencil className="h-4 w-4" />
                            Editar
                          </Button>
                          <Button type="button" variant="outline" onClick={() => onDelete(c.id)} disabled={del.isPending}>
                            <Trash2 className="h-4 w-4" />
                            Remover
                          </Button>
                        </>
                      )}
                    </div>
                  ) : null}
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function ClienteNotasCard({
  clienteId,
  canEditClientes,
  editable,
  data,
  isLoading,
  isError,
}: {
  clienteId: string;
  canEditClientes: boolean;
  editable: boolean;
  data: ClienteNota[] | undefined;
  isLoading: boolean;
  isError: boolean;
}) {
  const create = useCreateClienteNota(clienteId);
  const update = useUpdateClienteNota(clienteId);
  const del = useDeleteClienteNota(clienteId);

  const [newTitulo, setNewTitulo] = React.useState("");
  const [newConteudo, setNewConteudo] = React.useState("");

  const [editingId, setEditingId] = React.useState<string | null>(null);
  const [editTitulo, setEditTitulo] = React.useState("");
  const [editConteudo, setEditConteudo] = React.useState("");

  // Reset any in-progress row edit when the parent exits edit mode (Save or Cancel), so stale
  // editingId/editTitulo/editConteudo never resurface if the parent re-enters edit mode later.
  React.useEffect(() => {
    if (!editable) {
      setEditingId(null);
      setEditTitulo("");
      setEditConteudo("");
    }
  }, [editable]);

  const onCreate = async () => {
    if (!canEditClientes || !editable) return;
    const conteudo = newConteudo.trim();
    if (!conteudo) return;
    try {
      await create.mutateAsync({ titulo: newTitulo.trim() || undefined, conteudo });
      toast.success("Nota adicionada com sucesso.");
      setNewTitulo("");
      setNewConteudo("");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao adicionar nota.");
    }
  };

  const onStartEdit = (n: ClienteNota) => {
    setEditingId(n.id);
    setEditTitulo(n.titulo ?? "");
    setEditConteudo(n.conteudo);
  };

  const onCancelEdit = () => {
    setEditingId(null);
    setEditTitulo("");
    setEditConteudo("");
  };

  const onSaveEdit = async () => {
    if (!canEditClientes || !editable) return;
    if (!editingId) return;
    const conteudo = editConteudo.trim();
    if (!conteudo) return;
    try {
      await update.mutateAsync({ notaId: editingId, payload: { titulo: editTitulo.trim() || undefined, conteudo } });
      toast.success("Nota atualizada com sucesso.");
      onCancelEdit();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao atualizar nota.");
    }
  };

  const onDelete = async (id: string) => {
    if (!canEditClientes || !editable) return;
    const ok = window.confirm("Remover esta nota?");
    if (!ok) return;
    try {
      await del.mutateAsync(id);
      toast.success("Nota removida com sucesso.");
      if (editingId === id) onCancelEdit();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Erro ao remover nota.");
    }
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Notas</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {canEditClientes && editable ? (
          <div className="space-y-2">
            <input
              value={newTitulo}
              onChange={(e) => setNewTitulo(e.target.value)}
              placeholder="Título (opcional)"
              className="h-10 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
            />
            <textarea
              value={newConteudo}
              onChange={(e) => setNewConteudo(e.target.value)}
              placeholder="Escrever nota..."
              className="min-h-24 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
            />
            <div className="flex justify-end">
              <Button type="button" onClick={onCreate} disabled={create.isPending || !newConteudo.trim()}>
                <Plus className="h-4 w-4" />
                Adicionar nota
              </Button>
            </div>
          </div>
        ) : null}

        {isLoading ? (
          <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
        ) : isError ? (
          <div className="text-sm text-red-600">Erro ao carregar notas.</div>
        ) : !data?.length ? (
          <div className="text-sm text-neutral-500 dark:text-neutral-400">Sem notas registadas.</div>
        ) : (
          <div className="space-y-2">
            {data.map((n) => {
              const isEditing = editingId === n.id;
              return (
                <div
                  key={n.id}
                  className="rounded-md border border-neutral-200 dark:border-neutral-800 p-3 space-y-2"
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      {isEditing ? (
                        <input
                          value={editTitulo}
                          onChange={(e) => setEditTitulo(e.target.value)}
                          placeholder="Título (opcional)"
                          className="h-9 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
                        />
                      ) : (
                        <div className="font-semibold text-neutral-900 dark:text-neutral-50 truncate">
                          {n.titulo ?? "Nota"}
                        </div>
                      )}
                      <div className="mt-1 text-xs text-neutral-500 dark:text-neutral-400">
                        {new Date(n.updated_at ?? n.created_at).toLocaleString("pt-CV")}
                      </div>
                    </div>

                    {canEditClientes && editable ? (
                      <div className="flex items-center gap-2">
                        {isEditing ? (
                          <>
                            <Button
                              type="button"
                              variant="secondary"
                              onClick={onSaveEdit}
                              disabled={update.isPending || !editConteudo.trim()}
                            >
                              <Save className="h-4 w-4" />
                              Guardar
                            </Button>
                            <Button type="button" variant="outline" onClick={onCancelEdit}>
                              <X className="h-4 w-4" />
                              Cancelar
                            </Button>
                          </>
                        ) : (
                          <>
                            <Button type="button" variant="secondary" onClick={() => onStartEdit(n)}>
                              <Pencil className="h-4 w-4" />
                              Editar
                            </Button>
                            <Button type="button" variant="outline" onClick={() => onDelete(n.id)} disabled={del.isPending}>
                              <Trash2 className="h-4 w-4" />
                              Remover
                            </Button>
                          </>
                        )}
                      </div>
                    ) : null}
                  </div>

                  {isEditing ? (
                    <textarea
                      value={editConteudo}
                      onChange={(e) => setEditConteudo(e.target.value)}
                      className="min-h-24 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
                    />
                  ) : (
                    <div className="text-sm text-neutral-700 dark:text-neutral-200 whitespace-pre-wrap">
                      {n.conteudo}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
