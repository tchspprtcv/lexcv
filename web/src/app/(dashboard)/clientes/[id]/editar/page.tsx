"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { Controller, useForm } from "react-hook-form";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Switch } from "@/components/ui/switch";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useCliente, useUpdateCliente } from "@/hooks/use-clientes";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { clienteFormSchema, type ClienteFormValues } from "@/schemas/clientes";
import type {
  ClienteUpdateRequest,
  Deslocacao,
  DocumentoATratar,
  DocumentoEntregue,
  DocumentoTipo,
} from "@/types/clientes";

const DOCUMENTO_TIPOS: readonly DocumentoTipo[] = ["NIF", "CNI", "PASSAPORTE", "REG_COMERCIAL"];

function toDocumentoTipo(value: string | undefined): DocumentoTipo | undefined {
  return DOCUMENTO_TIPOS.includes(value as DocumentoTipo) ? (value as DocumentoTipo) : undefined;
}

type PageProps = {
  params: Promise<{ id: string }>;
};

export default function ClienteEditPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canEditClientes = permissions.can.edit("clientes");

  if (!permissions.isLoading && !canEditClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para editar clientes."
        backHref={`/clientes/${encodeURIComponent(id)}`}
      />
    );
  }

  return <ClienteEditContent id={id} />;
}

const selectClassName =
  "flex h-9 w-full rounded-none border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300";

const textareaClassName =
  "flex min-h-24 w-full rounded-none border border-neutral-200 bg-white px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-neutral-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:placeholder:text-neutral-400 dark:focus-visible:ring-neutral-300";

function ClienteEditContent({ id }: { id: string }) {
  const router = useRouter();
  const cliente = useCliente(id);
  const update = useUpdateCliente(id);
  const permissions = usePermissions();
  const canEditClientes = permissions.can.edit("clientes");
  const [serverError, setServerError] = React.useState<string | null>(null);

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
    form.setValue("tipo", pendingTipo, { shouldValidate: true });
    setPendingTipo(null);
  }

  // Intake list state — managed outside react-hook-form, synced into the PUT payload on submit.
  const [documentosEntregues, setDocumentosEntregues] = React.useState<DocumentoEntregue[]>([]);
  const [documentosATratar, setDocumentosATratar] = React.useState<DocumentoATratar[]>([]);
  const [deslocacoes, setDeslocacoes] = React.useState<Deslocacao[]>([]);

  const [addDocEntreModal, setAddDocEntreModal] = React.useState(false);
  const [newDocEntre, setNewDocEntre] = React.useState<{ descricao: string; data: string }>({ descricao: "", data: "" });

  const [addDocATratarModal, setAddDocATratarModal] = React.useState(false);
  const [newDocATratar, setNewDocATratar] = React.useState<{ descricao: string }>({ descricao: "" });

  const [addDeslocacaoModal, setAddDeslocacaoModal] = React.useState(false);
  const [newDeslocacao, setNewDeslocacao] = React.useState<{ descricao: string; local: string; data: string }>({
    descricao: "",
    local: "",
    data: "",
  });

  React.useEffect(() => {
    if (!cliente.data) return;
    form.reset({
      nome: cliente.data.nome ?? "",
      nif: cliente.data.nif ?? "",
      tipo: (cliente.data.tipo as "PARTICULAR" | "EMPRESA" | undefined) ?? undefined,
      avencado: cliente.data.avencado ?? false,
      email: cliente.data.email ?? "",
      telefone: cliente.data.telefone ?? "",
      localidade: cliente.data.localidade ?? "",
      morada: cliente.data.morada ?? "",
      documento_tipo: cliente.data.documento_tipo ?? cliente.data.documentoTipo ?? "",
      documento_numero: cliente.data.documento_numero ?? cliente.data.documentoNumero ?? "",
      ramo_atividade: cliente.data.ramo_atividade ?? cliente.data.ramoAtividade ?? "",
      detalhes_adicionais: cliente.data.detalhes_adicionais ?? cliente.data.detalhesAdicionais ?? "",
      descricao_caso: cliente.data.descricao_caso ?? "",
      honorarios_propostos: {
        total: cliente.data.honorarios_propostos?.total ?? undefined,
        totalPorExtenso: cliente.data.honorarios_propostos?.totalPorExtenso ?? "",
        previsao: cliente.data.honorarios_propostos?.previsao ?? "",
      },
    });
    setDocumentosEntregues(cliente.data.documentos_entregues ?? []);
    setDocumentosATratar(cliente.data.documentos_a_tratar ?? []);
    setDeslocacoes(cliente.data.deslocacoes ?? []);
  }, [cliente.data, form]);

  function confirmAddDocEntre() {
    if (!newDocEntre.descricao.trim()) return;
    setDocumentosEntregues((prev) => [...prev, { ...newDocEntre }]);
    setNewDocEntre({ descricao: "", data: "" });
    setAddDocEntreModal(false);
  }

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
      const documentoTipo = toDocumentoTipo(values.documento_tipo);

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
        documentosEntregues: documentosEntregues,
        documentosATratar: documentosATratar,
        deslocacoes: deslocacoes,
      };

      // Sincronizar NIF se tipo for NIF
      if (values.documento_tipo === "NIF") {
        payload.nif = values.documento_numero;
      }

      await update.mutateAsync(payload);
      toast.success("Cliente atualizado com sucesso.");
      router.push(`/clientes/${encodeURIComponent(id)}`);
    } catch (e) {
      const msg = e instanceof Error ? e.message : "Erro ao atualizar cliente";
      setServerError(msg);
      toast.error(msg);
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Editar cliente</h1>
          {cliente.data?.numero_cliente && (
            <Badge variant="blue" className="rounded-none font-mono font-bold text-[10px] w-fit">
              {cliente.data.numero_cliente}
            </Badge>
          )}
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            <Link href={`/clientes/${encodeURIComponent(id)}`} className="hover:underline">
              Voltar ao detalhe
            </Link>
          </div>
        </div>

        <Button asChild variant="outline">
          <Link href={`/clientes/${encodeURIComponent(id)}`}>Cancelar</Link>
        </Button>
      </div>

      {cliente.isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : cliente.isError ? (
        <div className="text-sm text-red-600">
          {cliente.error instanceof Error ? cliente.error.message : "Erro ao carregar cliente"}
        </div>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>Dados</CardTitle>
          </CardHeader>
          <CardContent>
            <form className="space-y-6" onSubmit={form.handleSubmit(onSubmit)}>
              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="nome">Nome</Label>
                  <Input id="nome" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("nome")} />
                  {form.formState.errors.nome ? (
                    <p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>
                  ) : null}
                </div>

                <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="nif">NIF (Legado)</Label>
                    <Input id="nif" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("nif")} />
                    {form.formState.errors.nif ? (
                      <p className="text-sm text-red-600">{form.formState.errors.nif.message}</p>
                    ) : null}
                  </div>

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
                    <Input id="email" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("email")} />
                    {form.formState.errors.email ? (
                      <p className="text-sm text-red-600">{form.formState.errors.email.message}</p>
                    ) : null}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="telefone">Telefone</Label>
                    <Input id="telefone" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("telefone")} />
                    {form.formState.errors.telefone ? (
                      <p className="text-sm text-red-600">{form.formState.errors.telefone.message}</p>
                    ) : null}
                  </div>
                </div>

                <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                  <div className="space-y-2">
                    <Label htmlFor="localidade">Localidade</Label>
                    <Input id="localidade" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("localidade")} />
                    {form.formState.errors.localidade ? (
                      <p className="text-sm text-red-600">{form.formState.errors.localidade.message}</p>
                    ) : null}
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="morada">Morada</Label>
                    <Input id="morada" className="rounded-none max-sm:h-12 max-sm:text-base" {...form.register("morada")} />
                    {form.formState.errors.morada ? (
                      <p className="text-sm text-red-600">{form.formState.errors.morada.message}</p>
                    ) : null}
                  </div>
                </div>
              </div>

              <hr className="border-neutral-200 dark:border-neutral-800" />

              <div className="space-y-4">
                <h3 className="text-lg font-medium text-neutral-900 dark:text-neutral-100">Informações Adicionais</h3>

                <div className="grid gap-6 grid-cols-1 md:grid-cols-2">
                  {/* Coluna 1: Tipo de Documento e Número de Documento */}
                  <div className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="documento_tipo">Tipo de Documento</Label>
                      <select
                        id="documento_tipo"
                        className={selectClassName}
                        {...form.register("documento_tipo")}
                      >
                        <option value="">Nenhum</option>
                        <option value="NIF">NIF</option>
                        <option value="CNI">CNI</option>
                        <option value="PASSAPORTE">Passaporte</option>
                      </select>
                      {form.formState.errors.documento_tipo ? (
                        <p className="text-sm text-red-600">{form.formState.errors.documento_tipo.message}</p>
                      ) : null}
                    </div>

                    <div className="space-y-2">
                      <Label htmlFor="documento_numero">Número do Documento</Label>
                      <Input
                        id="documento_numero"
                        className="rounded-none max-sm:h-12 max-sm:text-base"
                        placeholder="Introduza o número do documento"
                        {...form.register("documento_numero")}
                      />
                      {form.formState.errors.documento_numero ? (
                        <p className="text-sm text-red-600">{form.formState.errors.documento_numero.message}</p>
                      ) : null}
                    </div>
                  </div>

                  {/* Coluna 2: Ramo de Atividade e Detalhes Adicionais */}
                  <div className="space-y-4">
                    <div className="space-y-2">
                      <Label htmlFor="ramo_atividade">Ramo de Atividade</Label>
                      <select
                        id="ramo_atividade"
                        className={selectClassName}
                        {...form.register("ramo_atividade")}
                      >
                        <option value="">Selecione o ramo de atividade</option>
                        <option value="Banca">Banca</option>
                        <option value="Telecom">Telecom</option>
                        <option value="Construção">Construção</option>
                        <option value="Serviços">Serviços</option>
                        <option value="Comércio">Comércio</option>
                        <option value="Outros">Outros</option>
                      </select>
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

              <hr className="border-neutral-200 dark:border-neutral-800" />

              <div className="space-y-4">
                <h3 className="text-lg font-medium text-neutral-900 dark:text-neutral-100">Intake do Caso</h3>

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
                        className="rounded-none"
                        {...form.register("honorarios_propostos.total", { valueAsNumber: true })}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="honorarios_propostos.totalPorExtenso">Valor por Extenso</Label>
                      <Input
                        id="honorarios_propostos.totalPorExtenso"
                        className="rounded-none"
                        {...form.register("honorarios_propostos.totalPorExtenso")}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="honorarios_propostos.previsao">Previsão</Label>
                      <Input
                        id="honorarios_propostos.previsao"
                        className="rounded-none"
                        placeholder="Ex.: 6 meses"
                        {...form.register("honorarios_propostos.previsao")}
                      />
                    </div>
                  </div>
                </div>

                {/* Documentos Entregues */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Documentos Entregues</h4>
                    <Dialog open={addDocEntreModal} onOpenChange={setAddDocEntreModal}>
                      <DialogTrigger asChild>
                        <Button type="button" variant="outline" size="sm">Adicionar</Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>Adicionar Documento Entregue</DialogTitle>
                        </DialogHeader>
                        <div className="space-y-4">
                          <div className="space-y-2">
                            <Label htmlFor="new-doc-entre-descricao">Descrição</Label>
                            <Input
                              id="new-doc-entre-descricao"
                              className="rounded-none"
                              value={newDocEntre.descricao}
                              onChange={(e) => setNewDocEntre((prev) => ({ ...prev, descricao: e.target.value }))}
                            />
                          </div>
                          <div className="space-y-2">
                            <Label htmlFor="new-doc-entre-data">Data</Label>
                            <Input
                              id="new-doc-entre-data"
                              type="date"
                              className="rounded-none"
                              value={newDocEntre.data}
                              onChange={(e) => setNewDocEntre((prev) => ({ ...prev, data: e.target.value }))}
                            />
                          </div>
                        </div>
                        <DialogFooter>
                          <Button type="button" variant="outline" onClick={() => setAddDocEntreModal(false)}>Cancelar</Button>
                          <Button type="button" onClick={confirmAddDocEntre}>Confirmar</Button>
                        </DialogFooter>
                      </DialogContent>
                    </Dialog>
                  </div>
                  {documentosEntregues.length === 0 ? (
                    <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento entregue registado.</p>
                  ) : (
                    <ul className="space-y-1">
                      {documentosEntregues.map((doc, index) => (
                        <li key={index} className="flex items-center justify-between border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm">
                          <span>
                            {doc.descricao}
                            {doc.data ? ` — ${doc.data}` : ""}
                          </span>
                          <button
                            type="button"
                            className="text-neutral-500 hover:text-red-600"
                            onClick={() => setDocumentosEntregues((prev) => prev.filter((_, i) => i !== index))}
                            aria-label="Remover"
                          >
                            ✕
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                {/* Documentos a Tratar */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Documentos a Tratar</h4>
                    <Dialog open={addDocATratarModal} onOpenChange={setAddDocATratarModal}>
                      <DialogTrigger asChild>
                        <Button type="button" variant="outline" size="sm">Adicionar</Button>
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
                              className="rounded-none"
                              value={newDocATratar.descricao}
                              onChange={(e) => setNewDocATratar({ descricao: e.target.value })}
                            />
                          </div>
                        </div>
                        <DialogFooter>
                          <Button type="button" variant="outline" onClick={() => setAddDocATratarModal(false)}>Cancelar</Button>
                          <Button type="button" onClick={confirmAddDocATratar}>Confirmar</Button>
                        </DialogFooter>
                      </DialogContent>
                    </Dialog>
                  </div>
                  {documentosATratar.length === 0 ? (
                    <p className="text-sm text-neutral-500 dark:text-neutral-400">Nenhum documento a tratar registado.</p>
                  ) : (
                    <ul className="space-y-1">
                      {documentosATratar.map((doc, index) => (
                        <li key={index} className="flex items-center justify-between border border-neutral-200 dark:border-neutral-800 px-3 py-2 text-sm">
                          <span>{doc.descricao}</span>
                          <button
                            type="button"
                            className="text-neutral-500 hover:text-red-600"
                            onClick={() => setDocumentosATratar((prev) => prev.filter((_, i) => i !== index))}
                            aria-label="Remover"
                          >
                            ✕
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>

                {/* Deslocações */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <h4 className="text-sm font-medium text-neutral-700 dark:text-neutral-300">Deslocações</h4>
                    <Dialog open={addDeslocacaoModal} onOpenChange={setAddDeslocacaoModal}>
                      <DialogTrigger asChild>
                        <Button type="button" variant="outline" size="sm">Adicionar</Button>
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
                              className="rounded-none"
                              value={newDeslocacao.descricao}
                              onChange={(e) => setNewDeslocacao((prev) => ({ ...prev, descricao: e.target.value }))}
                            />
                          </div>
                          <div className="space-y-2">
                            <Label htmlFor="new-deslocacao-local">Local</Label>
                            <Input
                              id="new-deslocacao-local"
                              className="rounded-none"
                              value={newDeslocacao.local}
                              onChange={(e) => setNewDeslocacao((prev) => ({ ...prev, local: e.target.value }))}
                            />
                          </div>
                          <div className="space-y-2">
                            <Label htmlFor="new-deslocacao-data">Data</Label>
                            <Input
                              id="new-deslocacao-data"
                              type="date"
                              className="rounded-none"
                              value={newDeslocacao.data}
                              onChange={(e) => setNewDeslocacao((prev) => ({ ...prev, data: e.target.value }))}
                            />
                          </div>
                        </div>
                        <DialogFooter>
                          <Button type="button" variant="outline" onClick={() => setAddDeslocacaoModal(false)}>Cancelar</Button>
                          <Button type="button" onClick={confirmAddDeslocacao}>Confirmar</Button>
                        </DialogFooter>
                      </DialogContent>
                    </Dialog>
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
                          <button
                            type="button"
                            className="text-neutral-500 hover:text-red-600"
                            onClick={() => setDeslocacoes((prev) => prev.filter((_, i) => i !== index))}
                            aria-label="Remover"
                          >
                            ✕
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>

              {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

              <div className="flex gap-2">
                <Button
                  type="submit"
                  className="max-sm:min-h-[48px]"
                  disabled={form.formState.isSubmitting || update.isPending || permissions.isLoading || !canEditClientes}
                >
                  {form.formState.isSubmitting || update.isPending ? "A guardar..." : "Guardar"}
                </Button>
                <Button asChild type="button" variant="outline">
                  <Link href={`/clientes/${encodeURIComponent(id)}`}>Cancelar</Link>
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
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
            <Button variant="outline" onClick={() => setPendingTipo(null)}>Cancelar</Button>
            <Button onClick={confirmTipoChange}>Continuar</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
