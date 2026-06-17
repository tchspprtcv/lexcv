"use client";

import Link from "next/link";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useCliente, useClienteContaCorrente } from "@/hooks/use-clientes";
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
import { usePermissions } from "@/hooks/use-permissions";
import type { ClienteContacto } from "@/types/clientes-contactos";
import type { ClienteNota } from "@/types/clientes-notas";

type PageProps = {
  params: Promise<{ id: string }>;
};

function formatMoneyCVE(v: number) {
  return v.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

export default function ClienteDetailPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canViewClientes = permissions.can.view("clientes");
  const canEditClientes = permissions.can.edit("clientes");

  if (!permissions.isLoading && !canViewClientes) {
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
  const cliente = useCliente(id);
  const conta = useClienteContaCorrente(id);
  const contactos = useClienteContactos(id);
  const notas = useClienteNotas(id);
  const isLoading = cliente.isLoading || conta.isLoading;
  const isError = cliente.isError || conta.isError;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Cliente</h1>
          <div className="text-sm text-neutral-500 dark:text-neutral-400">
            <Link href="/clientes" className="hover:underline">
              Clientes
            </Link>{" "}
            <span>/</span> <span className="text-neutral-900 dark:text-neutral-50">{id}</span>
          </div>
        </div>

        <div className="flex gap-2">
          <Button asChild variant="outline">
            <Link href="/clientes">Voltar</Link>
          </Button>
          {canEditClientes ? (
            <Button asChild>
              <Link href={`/clientes/${encodeURIComponent(id)}/editar`}>Editar</Link>
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
          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Dados</CardTitle>
              </CardHeader>
              <CardContent>
                <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                  <dt className="text-neutral-500 dark:text-neutral-400">Nome</dt>
                  <dd className="col-span-2 font-medium">{cliente.data.nome}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">NIF</dt>
                  <dd className="col-span-2">{cliente.data.nif ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Tipo</dt>
                  <dd className="col-span-2">{cliente.data.tipo ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Email</dt>
                  <dd className="col-span-2">{cliente.data.email ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Telefone</dt>
                  <dd className="col-span-2">{cliente.data.telefone ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Localidade</dt>
                  <dd className="col-span-2">{cliente.data.localidade ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Morada</dt>
                  <dd className="col-span-2">{cliente.data.morada ?? "—"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Ativo</dt>
                  <dd className="col-span-2">{cliente.data.ativo === false ? "Não" : "Sim"}</dd>

                  <dt className="text-neutral-500 dark:text-neutral-400">Criado</dt>
                  <dd className="col-span-2">{new Date(cliente.data.created_at).toLocaleString("pt-CV")}</dd>
                </dl>
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

              <Card className="rounded-none">
                <CardHeader>
                  <CardTitle>Informações Adicionais</CardTitle>
                </CardHeader>
                <CardContent>
                  <dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm">
                    <dt className="text-neutral-500 dark:text-neutral-400">Tipo de Documento</dt>
                    <dd className="col-span-2">{cliente.data.documento_tipo ?? cliente.data.documentoTipo ?? "—"}</dd>

                    <dt className="text-neutral-500 dark:text-neutral-400">Número do Documento</dt>
                    <dd className="col-span-2">{cliente.data.documento_numero ?? cliente.data.documentoNumero ?? "—"}</dd>

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
                </CardContent>
              </Card>
            </div>
          </div>

          <div className="grid gap-4 lg:grid-cols-2">
            <ClienteContactosCard
              clienteId={id}
              canEditClientes={canEditClientes}
              data={contactos.data}
              isLoading={contactos.isLoading}
              isError={contactos.isError}
            />
            <ClienteNotasCard
              clienteId={id}
              canEditClientes={canEditClientes}
              data={notas.data}
              isLoading={notas.isLoading}
              isError={notas.isError}
            />
          </div>
        </div>
      ) : (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">
          Cliente não encontrado.
        </div>
      )}
    </div>
  );
}

function ClienteContactosCard({
  clienteId,
  canEditClientes,
  data,
  isLoading,
  isError,
}: {
  clienteId: string;
  canEditClientes: boolean;
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

  const onCreate = async () => {
    if (!canEditClientes) return;
    const valor = newValor.trim();
    if (!valor) return;
    await create.mutateAsync({ tipo: newTipo, valor });
    setNewValor("");
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
    if (!canEditClientes) return;
    if (!editingId) return;
    const valor = editValor.trim();
    if (!valor) return;
    await update.mutateAsync({ contactoId: editingId, payload: { tipo: editTipo || undefined, valor } });
    onCancelEdit();
  };

  const onDelete = async (id: string) => {
    if (!canEditClientes) return;
    const ok = window.confirm("Remover este contacto?");
    if (!ok) return;
    await del.mutateAsync(id);
    if (editingId === id) onCancelEdit();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Contactos</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {canEditClientes ? (
          <div className="grid gap-2 sm:grid-cols-[140px_1fr_auto]">
            <select
              value={newTipo}
              onChange={(e) => setNewTipo(e.target.value)}
              className="h-10 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
            >
              <option value="EMAIL">Email</option>
              <option value="TELEFONE">Telefone</option>
              <option value="OUTRO">Outro</option>
            </select>
            <input
              value={newValor}
              onChange={(e) => setNewValor(e.target.value)}
              placeholder="Valor do contacto"
              className="h-10 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
            />
            <Button type="button" onClick={onCreate} disabled={create.isPending || !newValor.trim()}>
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
                        <select
                          value={editTipo}
                          onChange={(e) => setEditTipo(e.target.value)}
                          className="h-9 w-full bg-white dark:bg-neutral-950 rounded-md border border-neutral-200 dark:border-neutral-800 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300"
                        >
                          <option value="">—</option>
                          <option value="EMAIL">Email</option>
                          <option value="TELEFONE">Telefone</option>
                          <option value="OUTRO">Outro</option>
                        </select>
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

                  {canEditClientes ? (
                    <div className="flex items-center gap-2">
                      {isEditing ? (
                        <>
                          <Button
                            type="button"
                            variant="secondary"
                            onClick={onSaveEdit}
                            disabled={update.isPending || !editValor.trim()}
                          >
                            Guardar
                          </Button>
                          <Button type="button" variant="outline" onClick={onCancelEdit}>
                            Cancelar
                          </Button>
                        </>
                      ) : (
                        <>
                          <Button type="button" variant="secondary" onClick={() => onStartEdit(c)}>
                            Editar
                          </Button>
                          <Button type="button" variant="outline" onClick={() => onDelete(c.id)} disabled={del.isPending}>
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
  data,
  isLoading,
  isError,
}: {
  clienteId: string;
  canEditClientes: boolean;
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

  const onCreate = async () => {
    if (!canEditClientes) return;
    const conteudo = newConteudo.trim();
    if (!conteudo) return;
    await create.mutateAsync({ titulo: newTitulo.trim() || undefined, conteudo });
    setNewTitulo("");
    setNewConteudo("");
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
    if (!canEditClientes) return;
    if (!editingId) return;
    const conteudo = editConteudo.trim();
    if (!conteudo) return;
    await update.mutateAsync({ notaId: editingId, payload: { titulo: editTitulo.trim() || undefined, conteudo } });
    onCancelEdit();
  };

  const onDelete = async (id: string) => {
    if (!canEditClientes) return;
    const ok = window.confirm("Remover esta nota?");
    if (!ok) return;
    await del.mutateAsync(id);
    if (editingId === id) onCancelEdit();
  };

  return (
    <Card>
      <CardHeader>
        <CardTitle>Notas</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {canEditClientes ? (
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

                    {canEditClientes ? (
                      <div className="flex items-center gap-2">
                        {isEditing ? (
                          <>
                            <Button
                              type="button"
                              variant="secondary"
                              onClick={onSaveEdit}
                              disabled={update.isPending || !editConteudo.trim()}
                            >
                              Guardar
                            </Button>
                            <Button type="button" variant="outline" onClick={onCancelEdit}>
                              Cancelar
                            </Button>
                          </>
                        ) : (
                          <>
                            <Button type="button" variant="secondary" onClick={() => onStartEdit(n)}>
                              Editar
                            </Button>
                            <Button type="button" variant="outline" onClick={() => onDelete(n.id)} disabled={del.isPending}>
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
