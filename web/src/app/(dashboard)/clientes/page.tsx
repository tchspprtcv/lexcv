"use client";

import Link from "next/link";
import * as React from "react";
import { Eye, Filter, Pencil, Plus, Search, Trash2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { toast } from "@/hooks/use-toast";
import { useClientes, useCreateCliente, useDeleteCliente } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcessos } from "@/hooks/use-processos";
import { parseCsv, toCsv } from "@/lib/csv";
import { cn } from "@/lib/utils";
import type { ClientesListFilters, Cliente } from "@/types/clientes";

export default function ClientesPage() {
  const permissions = usePermissions();
  const canViewClientes = permissions.can.view("clientes");
  const canCreateClientes = permissions.can.create("clientes");
  const canEditClientes = permissions.can.edit("clientes");

  if (!permissions.isLoading && !canViewClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar o módulo de clientes."
        backHref="/dashboard"
      />
    );
  }

  return (
    <ClientesPageContent
      canCreateClientes={canCreateClientes}
      canEditClientes={canEditClientes}
    />
  );
}

function ClientesPageContent({
  canCreateClientes,
  canEditClientes,
}: {
  canCreateClientes: boolean;
  canEditClientes: boolean;
}) {
  const [advancedOpen, setAdvancedOpen] = React.useState(false);
  const [draftQuery, setDraftQuery] = React.useState("");
  const [draftNif, setDraftNif] = React.useState("");
  const [draftTipo, setDraftTipo] = React.useState("");
  const [draftAtivo, setDraftAtivo] = React.useState<"all" | "true" | "false">("all");
  const [draftLocalidade, setDraftLocalidade] = React.useState("");
  const [draftCreatedFrom, setDraftCreatedFrom] = React.useState("");
  const [draftCreatedTo, setDraftCreatedTo] = React.useState("");

  const [filters, setFilters] = React.useState<ClientesListFilters>({});

  const allClientes = useClientes({});
  const clientes = useClientes(filters);
  const processos = useProcessos();
  const createCliente = useCreateCliente();

  const totalClientes = allClientes.data?.length ?? 0;
  const totalSingulares = (allClientes.data ?? []).filter((c) => (c.tipo ?? "").toUpperCase() === "SINGULAR").length;
  const totalColetivas = (allClientes.data ?? []).filter((c) => (c.tipo ?? "").toUpperCase() === "COLETIVA").length;
  const processosAtivos = processos.data?.filter((p) => (p.estado ?? "").toUpperCase() !== "ENCERRADO").length ?? 0;

  const importInputRef = React.useRef<HTMLInputElement | null>(null);

  React.useEffect(() => {
    const t = window.setTimeout(() => {
      setFilters((current) => ({ ...current, q: draftQuery.trim() || undefined }));
    }, 300);
    return () => window.clearTimeout(t);
  }, [draftQuery]);

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setFilters({
      q: draftQuery.trim() || undefined,
      nif: draftNif.trim() || undefined,
      tipo: draftTipo.trim() || undefined,
      ativo: draftAtivo === "all" ? undefined : draftAtivo === "true",
      localidade: draftLocalidade.trim() || undefined,
      createdFrom: draftCreatedFrom.trim() || undefined,
      createdTo: draftCreatedTo.trim() || undefined,
    });
  };

  const onClear = () => {
    setDraftQuery("");
    setDraftNif("");
    setDraftTipo("");
    setDraftAtivo("all");
    setDraftLocalidade("");
    setDraftCreatedFrom("");
    setDraftCreatedTo("");
    setFilters({});
  };

  const onExportCsv = () => {
    const rows = (clientes.data ?? []).map((c) => [
      c.nome ?? "",
      c.tipo ?? "",
      c.nif ?? "",
      c.telefone ?? "",
      c.email ?? "",
    ]);
    const csv = toCsv(["nome", "tipo", "nif", "telefone", "email"], rows, ",");

    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `clientes-${new Date().toISOString().slice(0, 10)}.csv`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
  };

  const onImportCsv = () => {
    if (!canCreateClientes) return;
    importInputRef.current?.click();
  };

  const onImportFile = async (file: File) => {
    if (!canCreateClientes) return;

    const text = await file.text();
    const { headers, rows } = parseCsv(text);
    const h = headers.map((x) => x.trim().toLowerCase());

    const idx = (name: string) => h.findIndex((x) => x === name);
    const idxNome = idx("nome");
    if (idxNome === -1) {
      toast.error("CSV inválido: falta coluna 'nome'.");
      return;
    }

    const idxTipo = idx("tipo");
    const idxNif = idx("nif");
    const idxTelefone = idx("telefone");
    const idxEmail = idx("email");

    let created = 0;
    let failed = 0;

    for (let i = 0; i < rows.length; i++) {
      const r = rows[i] ?? [];
      const nome = (r[idxNome] ?? "").trim();
      if (!nome) {
        failed++;
        continue;
      }
      try {
        await createCliente.mutateAsync({
          nome,
          tipo: idxTipo >= 0
            ? ((r[idxTipo] ?? "").trim() || undefined) as "PARTICULAR" | "EMPRESA" | undefined
            : undefined,
          nif: idxNif >= 0 ? (r[idxNif] ?? "").trim() || undefined : undefined,
          telefone: idxTelefone >= 0 ? (r[idxTelefone] ?? "").trim() || undefined : undefined,
          email: idxEmail >= 0 ? (r[idxEmail] ?? "").trim() || undefined : undefined,
        });
        created++;
      } catch {
        failed++;
      }
    }

    if (created && !failed) {
      toast.success(`Import concluído: ${created} cliente(s) criado(s).`);
    } else if (created && failed) {
      toast.error(`Import parcial: ${created} criado(s), ${failed} falhou/invalid.`);
    } else {
      toast.error("Import falhou: nenhuma linha válida.");
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-6">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">Módulo de Clientes</h1>
          <div className="mt-2 flex items-center text-xs font-semibold tracking-wider uppercase text-slate-500 dark:text-slate-400">
            <span>LexCV</span> <span className="mx-2 text-slate-300 dark:text-slate-700">/</span> <span className="text-blue-600 dark:text-blue-400">Gestão de Clientes</span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <input
            ref={importInputRef}
            type="file"
            accept=".csv,text/csv"
            className="hidden"
            onChange={async (e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              if (!file) return;
              await onImportFile(file);
            }}
          />
          <Button type="button" variant="outline" className="rounded-none" onClick={onExportCsv}>
            Exportar CSV
          </Button>
          {canEditClientes ? (
            <Button asChild type="button" variant="outline" className="rounded-none">
              <Link href="/clientes/merge">Merge</Link>
            </Button>
          ) : null}
          {canCreateClientes ? (
            <Button type="button" variant="secondary" className="rounded-none" onClick={onImportCsv}>
              Importar CSV
            </Button>
          ) : null}
          {canCreateClientes ? (
            <Button asChild className="rounded-none font-bold tracking-wide shadow-none">
              <Link href="/clientes/novo">
                <Plus className="h-4 w-4" />
                Adicionar Novo Cliente
              </Link>
            </Button>
          ) : null}
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-4">
        <Card className="bg-blue-600 text-white border-none shadow-md">
          <CardContent className="p-5">
            <div className="text-xs font-bold uppercase tracking-wider text-blue-200">Total de Clientes</div>
            <div className="mt-2 flex items-end gap-3">
              <div className="text-4xl font-black">{totalClientes.toLocaleString("pt-CV")}</div>
              <div className="text-xs text-blue-100 font-bold bg-blue-500/50 px-2 py-0.5 rounded-sm">+12%</div>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">Pessoas Singulares</div>
            <div className="mt-2 text-3xl font-bold text-slate-900 dark:text-white">{totalSingulares.toLocaleString("pt-CV")}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">Entidades Coletivas</div>
            <div className="mt-2 text-3xl font-bold text-slate-900 dark:text-white">{totalColetivas.toLocaleString("pt-CV")}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-5">
            <div className="text-xs font-bold uppercase tracking-wider text-slate-500 dark:text-slate-400">Processos Ativos</div>
            <div className="mt-2 text-3xl font-bold text-blue-600 dark:text-blue-400">{processosAtivos.toLocaleString("pt-CV")}</div>
          </CardContent>
        </Card>
      </div>

      <Card className="bg-slate-50/50 dark:bg-slate-900/20 border-slate-200 dark:border-slate-800">
        <CardContent className="p-4">
          <form className="grid gap-3 lg:grid-cols-12 items-end" onSubmit={onSubmit}>
            <div className="lg:col-span-5">
              <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">Pesquisar</div>
              <div className="mt-2 relative">
                <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <Input
                  value={draftQuery}
                  onChange={(e) => setDraftQuery(e.target.value)}
                  placeholder="Nome, NIF, telefone ou email..."
                  className="pl-9 bg-white dark:bg-[#020617] rounded-none border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                />
              </div>
            </div>
            <div className="lg:col-span-3">
              <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">Filtrar por NIF</div>
              <div className="mt-2 relative">
                <Search className="h-4 w-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <Input
                  value={draftNif}
                  onChange={(e) => setDraftNif(e.target.value)}
                  placeholder="Ex: 123456789"
                  className="pl-9 bg-white dark:bg-[#020617] rounded-none border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                />
              </div>
            </div>
            <div className="lg:col-span-2 flex gap-2">
              <Button
                type="button"
                variant="secondary"
                className="w-full justify-center rounded-none border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] text-slate-700 dark:text-slate-300"
                onClick={() => setAdvancedOpen((v) => !v)}
              >
                <Filter className="h-4 w-4" />
                Avançados
              </Button>
            </div>
            <div className="lg:col-span-2 flex gap-2">
              <Button type="submit" className="w-full rounded-none font-bold">Aplicar</Button>
              <Button type="button" variant="ghost" className="w-full rounded-none text-slate-500 hover:text-slate-900 dark:hover:text-white" onClick={onClear}>
                Limpar
              </Button>
            </div>
            {advancedOpen ? (
              <div className="lg:col-span-12 grid gap-3 lg:grid-cols-12 pt-2 border-t border-slate-200/70 dark:border-slate-800/70">
                <div className="lg:col-span-3">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">Tipo</div>
                  <div className="mt-2">
                    <select
                      value={draftTipo}
                      onChange={(e) => setDraftTipo(e.target.value)}
                      className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    >
                      <option value="">Todos</option>
                      <option value="SINGULAR">Singular</option>
                      <option value="COLETIVA">Coletiva</option>
                    </select>
                  </div>
                </div>
                <div className="lg:col-span-3">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">Estado</div>
                  <div className="mt-2">
                    <select
                      value={draftAtivo}
                      onChange={(e) => setDraftAtivo(e.target.value as "all" | "true" | "false")}
                      className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    >
                      <option value="all">Todos</option>
                      <option value="true">Ativo</option>
                      <option value="false">Inativo</option>
                    </select>
                  </div>
                </div>
                <div className="lg:col-span-3">
                  <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">Localidade</div>
                  <div className="mt-2">
                    <Input
                      value={draftLocalidade}
                      onChange={(e) => setDraftLocalidade(e.target.value)}
                      placeholder="Ex.: Praia"
                      className="bg-white dark:bg-[#020617] rounded-none border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                    />
                  </div>
                </div>
                <div className="lg:col-span-3 grid gap-3 grid-cols-1 md:grid-cols-2">
                  <div>
                    <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">De</div>
                    <div className="mt-2">
                      <Input
                        type="date"
                        value={draftCreatedFrom}
                        onChange={(e) => setDraftCreatedFrom(e.target.value)}
                        className="bg-white dark:bg-[#020617] rounded-none border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                      />
                    </div>
                  </div>
                  <div>
                    <div className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">Até</div>
                    <div className="mt-2">
                      <Input
                        type="date"
                        value={draftCreatedTo}
                        onChange={(e) => setDraftCreatedTo(e.target.value)}
                        className="bg-white dark:bg-[#020617] rounded-none border-slate-300 dark:border-slate-700 focus-visible:ring-blue-500"
                      />
                    </div>
                  </div>
                </div>
              </div>
            ) : null}
          </form>
        </CardContent>
      </Card>

      <Card className="border-slate-200 dark:border-slate-800">
        <CardContent className="p-0">
          {clientes.isPending ? (
            <div className="p-6 text-sm text-slate-500">A carregar...</div>
          ) : clientes.isError ? (
            <div className="p-6 text-sm text-red-600">
              {clientes.error instanceof Error ? clientes.error.message : "Erro ao carregar"}
            </div>
          ) : !clientes.data?.length ? (
            <div className="p-6 text-sm text-slate-500">Nenhum cliente encontrado.</div>
          ) : (
            <>
              {/* Mobile: cards empilhados */}
              <div className="md:hidden divide-y divide-slate-200 dark:divide-slate-800">
                {clientes.data.map((c) => {
                  const initials = c.nome.split(" ").filter(Boolean).slice(0, 2).map((w: string) => w[0]).join("").toUpperCase();
                  return (
                    <div key={c.id} className="p-4">
                      <div className="flex items-center gap-3">
                        <div className="h-10 w-10 flex-shrink-0 rounded-none bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold">
                          {initials}
                        </div>
                        <div className="min-w-0 flex-1">
                          <Link href={`/clientes/${encodeURIComponent(c.id)}`} className="font-bold text-slate-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors text-sm">
                            {c.nome}
                          </Link>
                          {c.nif && (
                            <div className="text-[11px] font-medium text-slate-500 dark:text-slate-400 mt-0.5">NIF: {c.nif}</div>
                          )}
                        </div>
                        {c.ativo !== undefined && (
                          <Badge variant={c.ativo ? "green" : "gray"} className="rounded-none font-bold text-[10px] flex-shrink-0">
                            {c.ativo ? "Ativo" : "Inativo"}
                          </Badge>
                        )}
                      </div>
                      {c.telefone && (
                        <div className="mt-2 text-[11px] text-slate-500 dark:text-slate-400 pl-[52px]">Tel: {c.telefone}</div>
                      )}
                      <div className="mt-3 pl-[52px] flex items-center gap-1">
                        <Button asChild size="sm" variant="ghost" className="h-12 w-12 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
                          <Link href={`/clientes/${encodeURIComponent(c.id)}`}>
                            <Eye className="h-4 w-4" />
                          </Link>
                        </Button>
                        {canEditClientes && (
                          <Button asChild size="sm" variant="ghost" className="h-12 w-12 p-0 text-slate-500 hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors">
                            <Link href={`/clientes/${encodeURIComponent(c.id)}/editar`}>
                              <Pencil className="h-4 w-4" />
                            </Link>
                          </Button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>

              {/* Desktop: tabela */}
              <div className="hidden md:block">
                <div className="overflow-hidden">
                  <Table>
                    <TableHeader>
                      <TableRow className="bg-slate-50/50 dark:bg-slate-900/50 border-b border-slate-200 dark:border-slate-800 hover:bg-transparent">
                        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">Nome / Razão Social</TableHead>
                        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">Tipo</TableHead>
                        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">NIF</TableHead>
                        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">Contacto</TableHead>
                        <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px] text-right">Ações</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {clientes.data.map((c) => (
                        <ClienteRow key={c.id} cliente={c} canEditClientes={canEditClientes} />
                      ))}
                    </TableBody>
                  </Table>

                  <div className="flex items-center justify-between px-6 py-4 border-t border-slate-200 dark:border-slate-800 bg-slate-50/30 dark:bg-slate-900/20">
                    <div className="text-sm text-slate-500 dark:text-slate-400 font-medium">
                      A mostrar 1-{clientes.data.length} de {totalClientes.toLocaleString("pt-CV")} clientes
                    </div>
                    <div className="flex items-center gap-2">
                      <button className="h-9 w-9 rounded-none border border-slate-200 dark:border-slate-700 bg-white dark:bg-[#020617] text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors">‹</button>
                      <button className="h-9 w-9 rounded-none bg-blue-600 text-white font-bold">1</button>
                      <button className="h-9 w-9 rounded-none border border-slate-200 dark:border-slate-700 bg-white dark:bg-[#020617] text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors">2</button>
                      <button className="h-9 w-9 rounded-none border border-slate-200 dark:border-slate-700 bg-white dark:bg-[#020617] text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors">3</button>
                      <div className="px-2 text-sm text-slate-500">…</div>
                      <button className="h-9 w-9 rounded-none border border-slate-200 dark:border-slate-700 bg-white dark:bg-[#020617] text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors">›</button>
                    </div>
                  </div>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function ClienteRow({
  cliente,
  canEditClientes,
}: {
  cliente: Cliente;
  canEditClientes: boolean;
}) {
  const del = useDeleteCliente(cliente.id);

  const initials = cliente.nome
    .split(" ")
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toUpperCase();

  const idShort = cliente.id.split("-")[0]?.toUpperCase() ?? "—";
  const tipo = (cliente.tipo ?? "").toUpperCase();

  const badgeVariant = tipo === "SINGULAR" ? "blue" : tipo === "COLETIVA" ? "purple" : "gray";

  const onDelete = () => {
    if (del.isPending) return;
    const ok = window.confirm("Remover este cliente?");
    if (!ok) return;
    del.mutate();
  };

  return (
    <TableRow className="border-slate-100 dark:border-slate-800/50 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors">
      <TableCell>
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-none bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold shadow-sm">
            {initials}
          </div>
          <div className="min-w-0">
            <Link href={`/clientes/${encodeURIComponent(cliente.id)}`} className="font-bold text-slate-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
              {cliente.nome}
            </Link>
            <div className="text-[11px] font-medium tracking-wider uppercase text-slate-500 dark:text-slate-400 mt-0.5">ID: #{idShort}</div>
          </div>
        </div>
      </TableCell>
      <TableCell>
        <Badge variant={badgeVariant as "blue" | "purple" | "gray"} className="rounded-none font-bold tracking-wide">{tipo || "—"}</Badge>
      </TableCell>
      <TableCell className={cn("text-slate-800 dark:text-slate-200 font-medium", !cliente.nif && !cliente.documento_numero && !cliente.documentoNumero && "text-slate-400 dark:text-slate-600")}>
        {cliente.documento_tipo || cliente.documentoTipo ? (
          <div className="flex flex-col">
            <span>{cliente.documento_numero ?? cliente.documentoNumero}</span>
            <span className="text-[10px] text-slate-500 dark:text-slate-400 font-normal uppercase">{cliente.documento_tipo ?? cliente.documentoTipo}</span>
          </div>
        ) : (
          cliente.nif ?? "—"
        )}
      </TableCell>
      <TableCell>
        <div className="text-sm font-medium text-slate-900 dark:text-slate-200">{cliente.telefone ?? "—"}</div>
        <div className="text-[11px] text-slate-500 dark:text-slate-400">{cliente.email ?? ""}</div>
      </TableCell>
      <TableCell className="text-right">
        <div className="inline-flex items-center gap-1">
          <Button asChild size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
            <Link href={`/clientes/${encodeURIComponent(cliente.id)}`}>
              <Eye className="h-4 w-4" />
            </Link>
          </Button>
          {canEditClientes ? (
            <Button asChild size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors">
              <Link href={`/clientes/${encodeURIComponent(cliente.id)}/editar`}>
                <Pencil className="h-4 w-4" />
              </Link>
            </Button>
          ) : null}
          {canEditClientes ? (
            <Button
              type="button"
              size="sm"
              variant="ghost"
              className="h-9 w-9 p-0 text-slate-500 hover:text-red-600 dark:hover:text-red-400 transition-colors"
              onClick={onDelete}
              disabled={del.isPending}
            >
              <Trash2 className="h-4 w-4" />
            </Button>
          ) : null}
        </div>
      </TableCell>
    </TableRow>
  );
}
