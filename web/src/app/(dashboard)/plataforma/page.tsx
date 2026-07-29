"use client";

import * as React from "react";
import { Lock, Pencil, Plus, Unlock } from "lucide-react";

import { AccessDeniedState } from "@/components/shared/access-denied-state";
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
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { DataTable } from "@/components/shared/data-table/data-table";
import { toast } from "@/hooks/use-toast";
import { useMe } from "@/hooks/use-me";
import {
  useCreateTenant,
  useSetTenantAtivo,
  useTenantsAdmin,
  useUpdateTenant,
} from "@/hooks/use-platform-admin";
import { cn } from "@/lib/utils";
import { tenantInitials } from "@/lib/tenant-initials";
import type { TenantAdminSummary, TenantPlano, TenantUpdateRequest } from "@/types/platform-admin";
import type { SetupInitializeRequest } from "@/types/setup";

import { columns, TENANT_RESERVADO } from "./columns";
import { CriarTenantPanel } from "./criar-tenant-panel";

const PLANO_OPTIONS: TenantPlano[] = ["STARTER", "STANDARD", "ENTERPRISE"];

/**
 * Ecra `/plataforma` — consola de administracao de tenants, acessivel apenas
 * a utilizadores com o papel PLATAFORMA_ADMIN. Compoe as pecas ja construidas
 * pelos planos anteriores desta fase (tipos/hooks, colunas, painel de criacao)
 * num unico ecra: lista com pesquisa, criacao inline, edicao de plano/limite
 * e alternancia de suspenso/ativo.
 */
export default function PlataformaPage() {
  // Guarda de pagina (defesa em profundidade). A camada autoritativa de
  // autorizacao e o gate de papel de classe do controlador de administracao
  // de plataforma (backend, Phase 119) — este guard e apenas espelho de UX.
  //
  // WR-03 (Phase 120 code review): ao contrario do padrao de condicao unica
  // `isFetched && !canX` usado no resto do codebase (ver `clientes/page.tsx`),
  // este ecra precisa de um branch extra para `!me.isFetched` que ainda nao
  // renderize nada. Sem ele, ANTES do primeiro resolve de useMe() (o estado
  // normal na primeira navegacao direta para /plataforma), a condicao unica
  // avaliava sempre a falso independentemente do papel real do chamador, e
  // PlataformaPageContent renderizava incondicionalmente para QUALQUER
  // utilizador autenticado -- disparando de imediato o GET /platform/tenants
  // de useTenantsAdmin() antes sequer de se saber se o chamador e mesmo
  // PLATAFORMA_ADMIN. O backend continua a recusar com 403 (defesa em
  // profundidade real, sem fuga de dados), mas o proprio guard de pagina
  // falhava aberto, nao fechado, durante essa janela.
  const me = useMe();

  if (!me.isFetched) {
    return null;
  }

  if (!me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
    return (
      <AccessDeniedState
        description="Não tem permissão para aceder à consola de administração de tenants."
        backHref="/dashboard"
      />
    );
  }

  return <PlataformaPageContent />;
}

function PlataformaPageContent() {
  const [isFormOpen, setIsFormOpen] = React.useState(false);
  const [searchTerm, setSearchTerm] = React.useState("");
  const [tenantEmEdicao, setTenantEmEdicao] = React.useState<TenantAdminSummary | null>(null);
  const [tenantEmAlteracaoDeEstado, setTenantEmAlteracaoDeEstado] =
    React.useState<TenantAdminSummary | null>(null);

  const tenants = useTenantsAdmin();
  const criarTenant = useCreateTenant();
  const atualizarTenant = useUpdateTenant();
  const alterarEstado = useSetTenantAtivo();

  const tenantsFiltrados = React.useMemo(() => {
    const termo = searchTerm.trim().toLowerCase();
    const lista = tenants.data ?? [];
    if (!termo) return lista;
    return lista.filter((t) => t.nome.toLowerCase().includes(termo));
  }, [tenants.data, searchTerm]);

  // Ambos os dialogos sao controlados a partir da pagina (nao das linhas da
  // tabela) — o estado de "que tenant esta a ser editado/alterado" vive aqui,
  // as colunas e os cards mobile apenas notificam por callback.
  const onEdit = React.useCallback((tenant: TenantAdminSummary) => {
    setTenantEmEdicao(tenant);
  }, []);

  const onToggleAtivo = React.useCallback((tenant: TenantAdminSummary) => {
    setTenantEmAlteracaoDeEstado(tenant);
  }, []);

  const tenantColumns = React.useMemo(
    () => columns({ onEdit, onToggleAtivo }),
    [onEdit, onToggleAtivo],
  );

  const handleCreateSubmit = async (payload: SetupInitializeRequest) => {
    try {
      await criarTenant.mutateAsync(payload);
      toast.success("Tenant criado com sucesso.");
      setIsFormOpen(false);
    } catch {
      // O wrapper de fetch partilhado ja mostrou o toast com a mensagem do
      // backend (ex.: "Já existe um utilizador com este email."). Mantemos o
      // painel aberto para o operador nao perder o que escreveu.
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">
          Administração de Tenants
        </h1>
        <div className="mt-2 flex items-center text-xs font-semibold tracking-wider uppercase text-slate-500 dark:text-slate-400">
          <span>LexCV</span>
          <span className="mx-2 text-slate-300 dark:text-slate-700">/</span>
          <span className="text-blue-600 dark:text-blue-400">Consola de Plataforma</span>
        </div>
      </div>

      {isFormOpen ? (
        <CriarTenantPanel
          onCancel={() => setIsFormOpen(false)}
          onSubmit={handleCreateSubmit}
          isSubmitting={criarTenant.isPending}
        />
      ) : (
        <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
          <CardHeader className="flex flex-row items-center justify-between space-y-0">
            <div>
              <CardTitle className="text-xl font-semibold">Tenants Registados</CardTitle>
              <CardDescription>Lista de organizações com acesso à plataforma LexCV.</CardDescription>
            </div>
            <Button
              onClick={() => setIsFormOpen(true)}
              className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
            >
              <Plus className="h-4 w-4" />
              Criar Tenant
            </Button>
          </CardHeader>
          <CardContent className="space-y-4">
            <Input
              placeholder="Pesquisar tenant por nome..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="bg-slate-50 dark:bg-slate-950"
            />

            {tenants.isLoading ? (
              <div className="p-6 text-sm text-slate-500">A carregar...</div>
            ) : tenants.isError ? (
              <div className="p-6 text-sm text-red-600">
                {tenants.error instanceof Error ? tenants.error.message : "Erro ao carregar tenants."}
              </div>
            ) : (
              <>
                {/* Mobile: cards empilhados */}
                <div className="md:hidden divide-y divide-slate-200 dark:divide-slate-800">
                  {tenantsFiltrados.length === 0 ? (
                    <div className="p-6 text-center text-sm text-slate-500 dark:text-slate-400">
                      Sem resultados para os filtros aplicados.
                    </div>
                  ) : (
                    tenantsFiltrados.map((tenant) => {
                      const initials = tenantInitials(tenant.nome);
                      const suspenderDesativado = tenant.nome === TENANT_RESERVADO && tenant.ativo;

                      return (
                        <div key={tenant.id} className="p-4">
                          <div className="flex items-center gap-3">
                            <div className="h-10 w-10 rounded-md flex-shrink-0 bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold">
                              {initials}
                            </div>
                            <div className="min-w-0 flex-1">
                              <span className="font-bold text-slate-900 dark:text-white text-sm">
                                {tenant.nome}
                              </span>
                              {tenant.nome === TENANT_RESERVADO ? (
                                <div className="mt-1">
                                  <Badge variant="outline">Plataforma</Badge>
                                </div>
                              ) : null}
                            </div>
                            <Badge
                              variant={
                                tenant.plano === "STARTER"
                                  ? "gray"
                                  : tenant.plano === "STANDARD"
                                    ? "purple"
                                    : "amber"
                              }
                              className="font-bold text-[10px] flex-shrink-0 tracking-wide"
                            >
                              {tenant.plano}
                            </Badge>
                            <Badge
                              variant={tenant.ativo ? "green" : "red"}
                              className="font-bold text-[10px] flex-shrink-0"
                            >
                              {tenant.ativo ? "Ativo" : "Suspenso"}
                            </Badge>
                          </div>
                          <div className="mt-3 pl-[52px] flex items-center gap-1">
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="ghost"
                                  aria-label="Editar tenant"
                                  className="h-12 w-12 p-0 text-slate-500 hover:text-emerald-600 dark:hover:text-emerald-400 transition-colors"
                                  onClick={() => onEdit(tenant)}
                                >
                                  <Pencil className="h-4 w-4" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>Editar</TooltipContent>
                            </Tooltip>

                            {suspenderDesativado ? (
                              // buttonVariants embute disabled:pointer-events-none, por isso um
                              // TooltipTrigger colocado diretamente sobre o Button desativado
                              // nunca dispararia — o span tabIndex={0} e o alvo real do tooltip
                              // (composicao Phase 118), tal como em columns.tsx (desktop).
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <span tabIndex={0}>
                                    <Button
                                      type="button"
                                      size="sm"
                                      variant="ghost"
                                      aria-label="Suspender tenant"
                                      disabled
                                      className="h-12 w-12 p-0 text-slate-500"
                                    >
                                      <Lock className="h-4 w-4" />
                                    </Button>
                                  </span>
                                </TooltipTrigger>
                                <TooltipContent>
                                  Não é possível suspender o tenant da plataforma (LexCV).
                                </TooltipContent>
                              </Tooltip>
                            ) : (
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <Button
                                    type="button"
                                    size="sm"
                                    variant="ghost"
                                    aria-label={tenant.ativo ? "Suspender tenant" : "Reativar tenant"}
                                    className={cn(
                                      "h-12 w-12 p-0 text-slate-500 transition-colors",
                                      tenant.ativo
                                        ? "hover:text-red-600 dark:hover:text-red-400"
                                        : "hover:text-emerald-600 dark:hover:text-emerald-400",
                                    )}
                                    onClick={() => onToggleAtivo(tenant)}
                                  >
                                    {tenant.ativo ? (
                                      <Lock className="h-4 w-4" />
                                    ) : (
                                      <Unlock className="h-4 w-4" />
                                    )}
                                  </Button>
                                </TooltipTrigger>
                                <TooltipContent>{tenant.ativo ? "Suspender" : "Reativar"}</TooltipContent>
                              </Tooltip>
                            )}
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>

                {/* Desktop: DataTable */}
                <div className="hidden md:block">
                  <DataTable columns={tenantColumns} data={tenantsFiltrados} getRowId={(t) => t.id} />
                </div>
              </>
            )}
          </CardContent>
        </Card>
      )}

      <EditarTenantDialog
        tenant={tenantEmEdicao}
        isSubmitting={atualizarTenant.isPending}
        onOpenChange={(open) => {
          if (!open) setTenantEmEdicao(null);
        }}
        onSubmit={async (payload) => {
          if (!tenantEmEdicao) return;
          try {
            await atualizarTenant.mutateAsync({ id: tenantEmEdicao.id, payload });
            toast.success("Tenant atualizado com sucesso.");
            setTenantEmEdicao(null);
          } catch {
            // O wrapper de fetch partilhado ja mostrou o toast do backend;
            // mantemos o Dialog aberto para o operador poder corrigir.
          }
        }}
      />

      {/* AlertDialog de suspensao/reativacao — nunca fundido no Dialog de
          edicao acima: sao dois componentes distintos, com dois gatilhos
          distintos (edicao e um rascunho adiado ate "Guardar"; alterar o
          estado e uma accao direta e imediata). */}
      <AlertDialog
        open={tenantEmAlteracaoDeEstado !== null}
        onOpenChange={(open) => {
          if (!open) setTenantEmAlteracaoDeEstado(null);
        }}
      >
        <AlertDialogContent>
          {tenantEmAlteracaoDeEstado ? (
            <AlteracaoEstadoConteudo
              tenant={tenantEmAlteracaoDeEstado}
              isPending={alterarEstado.isPending}
              onConfirm={async () => {
                try {
                  await alterarEstado.mutateAsync({
                    id: tenantEmAlteracaoDeEstado.id,
                    ativo: !tenantEmAlteracaoDeEstado.ativo,
                  });
                  toast.success(
                    tenantEmAlteracaoDeEstado.ativo
                      ? "Tenant suspenso com sucesso."
                      : "Tenant reativado com sucesso.",
                  );
                  setTenantEmAlteracaoDeEstado(null);
                } catch {
                  // O wrapper de fetch partilhado ja mostrou o toast do
                  // backend (ex.: tentativa de suspender a tenant reservada);
                  // mantemos o AlertDialog aberto.
                }
              }}
            />
          ) : null}
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}

/**
 * Dialog "Editar Tenant" — formulario reversivel (Dialog, nao AlertDialog)
 * para ajustar plano/limite de utilizadores em conjunto. Controlado a partir
 * da pagina: `tenant` nulo fecha o dialogo.
 */
function EditarTenantDialog({
  tenant,
  onOpenChange,
  onSubmit,
  isSubmitting,
}: {
  tenant: TenantAdminSummary | null;
  onOpenChange: (open: boolean) => void;
  onSubmit: (payload: TenantUpdateRequest) => Promise<void>;
  isSubmitting: boolean;
}) {
  return (
    <Dialog open={tenant !== null} onOpenChange={onOpenChange}>
      <DialogContent className="max-sm:fixed max-sm:bottom-0 max-sm:left-0 max-sm:right-0 max-sm:top-auto max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-t-xl max-sm:rounded-b-none max-sm:w-full max-sm:max-w-none">
        {/* O tenant so e nulo enquanto o dialogo esta fechado — quando null,
            o Dialog acima ja recebeu open=false, pelo que renderizar nada e
            seguro. Cada abertura re-monta este formulario (a transicao passa
            sempre por um fecho antes de reabrir), pelo que o estado local
            comeca sempre sincronizado com o tenant selecionado. */}
        {tenant ? (
          <EditarTenantForm tenant={tenant} onSubmit={onSubmit} isSubmitting={isSubmitting} />
        ) : null}
      </DialogContent>
    </Dialog>
  );
}

function EditarTenantForm({
  tenant,
  onSubmit,
  isSubmitting,
}: {
  tenant: TenantAdminSummary;
  onSubmit: (payload: TenantUpdateRequest) => Promise<void>;
  isSubmitting: boolean;
}) {
  // CR-01 (Phase 120 code review): fallback de defesa em profundidade -- o backend agora
  // garante que `plano` nunca fica null (Tenant.java tem @Builder.Default = STARTER, e a
  // migracao 120b-backfill-tenant-plano.sql corrige linhas antigas), mas seed-ar o estado
  // diretamente com `tenant.plano` sem esta rede de seguranca deixava o <NativeSelect> abaixo
  // funcionalmente nao controlado sempre que `plano` fosse null (o browser mostra visualmente a
  // 1ª opcao, "STARTER", sem o estado React alguma vez sincronizar com esse valor) -- o operador
  // podia gravar sem tocar no dropdown e submeter `{ plano: null, ... }`, que o backend recusa
  // com 400 "O plano e obrigatorio.". `?? PLANO_OPTIONS[0]` garante que o valor inicial e sempre
  // uma das opcoes reais do <select>, para a selecao visivel e o valor submetido nunca poderem
  // divergir, mesmo perante dados antigos/pre-migracao.
  const [plano, setPlano] = React.useState<TenantPlano>(tenant.plano ?? PLANO_OPTIONS[0]);
  const [limiteInput, setLimiteInput] = React.useState(
    tenant.limiteUtilizadores !== null ? String(tenant.limiteUtilizadores) : "",
  );
  const [limiteErro, setLimiteErro] = React.useState<string | null>(null);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    const valor = limiteInput.trim();

    if (valor === "") {
      setLimiteErro(null);
      await onSubmit({ plano, limiteUtilizadores: null });
      return;
    }

    const numero = Number(valor);
    if (!Number.isInteger(numero) || numero < 1) {
      setLimiteErro("O limite deve ser um número inteiro igual ou superior a 1.");
      return;
    }

    setLimiteErro(null);
    await onSubmit({ plano, limiteUtilizadores: numero });
  };

  return (
    <form onSubmit={handleSubmit}>
      <DialogHeader>
        <DialogTitle>Editar Tenant</DialogTitle>
        <DialogDescription>Ajuste o plano e o limite de utilizadores de {tenant.nome}.</DialogDescription>
      </DialogHeader>
      <div className="space-y-4 py-4">
        <div className="space-y-2">
          <Label htmlFor="tenant-edit-plano">Plano</Label>
          <NativeSelect
            id="tenant-edit-plano"
            value={plano}
            onChange={(event) => setPlano(event.target.value as TenantPlano)}
            className="w-full"
          >
            {PLANO_OPTIONS.map((opcao) => (
              <option key={opcao} value={opcao}>
                {opcao}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="space-y-2">
          <Label htmlFor="tenant-edit-limite">Limite de Utilizadores</Label>
          <Input
            id="tenant-edit-limite"
            type="number"
            inputMode="numeric"
            min={1}
            step={1}
            placeholder="Sem limite"
            value={limiteInput}
            onChange={(event) => setLimiteInput(event.target.value)}
          />
          <p className="text-xs text-slate-500 dark:text-slate-400">
            Deixe em branco para não aplicar limite de utilizadores.
          </p>
          {limiteErro ? <p className="text-sm text-red-600">{limiteErro}</p> : null}
        </div>
      </div>
      <DialogFooter>
        <DialogClose asChild>
          <Button type="button" variant="outline">
            Cancelar
          </Button>
        </DialogClose>
        <Button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white" disabled={isSubmitting}>
          {isSubmitting ? "A guardar..." : "Guardar"}
        </Button>
      </DialogFooter>
    </form>
  );
}

/**
 * Conteudo do AlertDialog de alteracao de estado — o titulo, a descricao e a
 * cor/rotulo do botao de confirmacao dependem da direcao (suspender vs.
 * reativar), derivados de `tenant.ativo`.
 */
function AlteracaoEstadoConteudo({
  tenant,
  isPending,
  onConfirm,
}: {
  tenant: TenantAdminSummary;
  isPending: boolean;
  onConfirm: () => Promise<void>;
}) {
  const vaiSuspender = tenant.ativo;

  return (
    <>
      <AlertDialogHeader>
        <AlertDialogTitle>{vaiSuspender ? "Suspender Tenant" : "Reativar Tenant"}</AlertDialogTitle>
        <AlertDialogDescription>
          {vaiSuspender
            ? `Esta ação bloqueia de imediato o acesso de todos os utilizadores de ${tenant.nome}, incluindo sessões já iniciadas. Pode reativar o tenant a qualquer momento.`
            : `Os utilizadores de ${tenant.nome} recuperam o acesso de imediato.`}
        </AlertDialogDescription>
      </AlertDialogHeader>
      <AlertDialogFooter>
        <AlertDialogCancel disabled={isPending}>Cancelar</AlertDialogCancel>
        <AlertDialogAction
          disabled={isPending}
          onClick={(e) => {
            e.preventDefault();
            void onConfirm();
          }}
          className={
            vaiSuspender
              ? "bg-red-600 hover:bg-red-700 text-white"
              : "bg-emerald-600 hover:bg-emerald-700 text-white"
          }
        >
          {isPending ? "A processar..." : vaiSuspender ? "Suspender" : "Reativar"}
        </AlertDialogAction>
      </AlertDialogFooter>
    </>
  );
}
