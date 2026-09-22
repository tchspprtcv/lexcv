"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import {
  User,
  Lock,
  Users,
  Sliders,
  Plus,
  Trash2,
  Edit,
  X,
  Loader2,
  UserCheck,
  UserMinus,
  ShieldAlert,
  Save,
  Bell,
  AlertCircle,
  RotateCcw,
  History
} from "lucide-react";

import { apiFetch } from "@/lib/api";
import { usePermissions } from "@/hooks/use-permissions";
import { useMe } from "@/hooks/use-me";
import {
  useAdminUsers,
  useOfficeRbac,
  useSaveOfficeRbac,
  useCreateOfficeRole,
  useRenameOfficeRole,
  useDeleteOfficeRole,
} from "@/hooks/use-admin";
import {
  useNotificacaoPreferencias,
  useReativarCategoria,
  useSilenciarCategoria,
} from "@/hooks/use-notificacao-preferencias";
import { toast } from "@/hooks/use-toast";
import { NOTIFICACAO_CATEGORIA_SILENCIAVEIS_OPTIONS } from "@/lib/notificacao-categoria";
import { UserProfileForm } from "@/components/profile/user-profile-form";
import { UserPasswordForm } from "@/components/profile/user-password-form";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
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
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { renomearPapelSchema, type RenomearPapelFormValues } from "@/schemas/papeis-escritorio";
import {
  mesclarEstadoLocal,
  papeisComAlteracoesPorGravar,
  rotuloPapeisPorGravar,
  type LocalPermissoesPapeis,
} from "./merge-local-papeis";
import { CriarPapelPanel } from "./criar-papel-panel";
import { PapelAcoesMenu } from "./papel-acoes-menu";
import { AuditoriaTab } from "./auditoria-tab";
import type { AdminUser, AdminUserSavePayload } from "@/types/admin-users";
import type { OfficePapel, OfficeRbac, PapelCreateRequest } from "@/types/office-rbac";
import type { NotificacaoCategoria } from "@/types/notificacoes";

type TabId = "profile" | "security" | "users" | "rbac" | "auditoria" | "notificacoes";

export default function SettingsPage() {
  const { data: me, can } = usePermissions();
  const [activeTab, setActiveTab] = React.useState<TabId>("profile");

  // Admin queries
  // WR-02 (127-REVIEW.md): a antiga guarda `me?.roles?.includes("ADMIN")` comparava pelo NOME
  // efectivo do papel -- exactamente o que PAPEL-04 torna editavel nesta fase. Um escritorio que
  // renomeasse o seu papel de administrador via PUT /admin/rbac/roles/{id} perdia, em silencio,
  // esta segunda via de visibilidade das abas (nunca a real, ja que can.manage(...) nao depende
  // do nome). Removida por inteiro -- a permissao efectiva (rbac:manage/users:manage) ja cobre a
  // visibilidade pretendida, na mesma disciplina de proveniencia (nunca nome) que o resto desta
  // fase aplica em ParecerController/OfficeRolesController/AdminController.
  const hasUsersManage = can.manage("users");
  const hasRbacManage = can.manage("rbac");

  // Render tabs
  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex items-start justify-between gap-6">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">Definições de Sistema</h1>
          <div className="mt-2 flex items-center text-xs font-semibold tracking-wider uppercase text-slate-500 dark:text-slate-400">
            <span>ALCv</span> <span className="mx-2 text-slate-300 dark:text-slate-700">/</span> <span className="text-blue-600 dark:text-blue-400">Configurações Gerais e Segurança</span>
          </div>
        </div>
      </div>

      {/* Modern Glassmorphic Tabs */}
      <div className="flex border-b border-slate-200 dark:border-slate-800 gap-1 pb-px overflow-x-auto">
        <button
          onClick={() => setActiveTab("profile")}
          className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${activeTab === "profile"
              ? "border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-400"
              : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
            }`}
        >
          <User className="h-4 w-4" />
          O Meu Perfil
        </button>

        <button
          onClick={() => setActiveTab("security")}
          className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${activeTab === "security"
              ? "border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-400"
              : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
            }`}
        >
          <Lock className="h-4 w-4" />
          Segurança
        </button>

        {hasUsersManage && (
          <button
            onClick={() => setActiveTab("users")}
            className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${activeTab === "users"
                ? "border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-400"
                : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
              }`}
          >
            <Users className="h-4 w-4" />
            Gestão de Utilizadores
          </button>
        )}

        {hasRbacManage && (
          <button
            onClick={() => setActiveTab("rbac")}
            className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${activeTab === "rbac"
                ? "border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-400"
                : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
              }`}
          >
            <Sliders className="h-4 w-4" />
            Controlo de Acesso (RBAC)
          </button>
        )}

        {hasRbacManage && (
          <button
            onClick={() => setActiveTab("auditoria")}
            className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${activeTab === "auditoria"
                ? "border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-400"
                : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
              }`}
          >
            <History className="h-4 w-4" />
            Auditoria
          </button>
        )}

        {can.view("notificacoes") && (
          <button
            onClick={() => setActiveTab("notificacoes")}
            className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${activeTab === "notificacoes"
                ? "border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-400"
                : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
              }`}
          >
            <Bell className="h-4 w-4" />
            Notificações
          </button>
        )}
      </div>

      {/* Tab Panels */}
      <div className="space-y-6">
        {activeTab === "profile" && (
          <div className="animate-in fade-in duration-200">
            <UserProfileForm />
          </div>
        )}

        {activeTab === "security" && (
          <div className="animate-in fade-in duration-200">
            <UserPasswordForm />
          </div>
        )}

        {activeTab === "users" && hasUsersManage && (
          <div className="animate-in fade-in duration-200">
            <UserManagementTab currentUserId={me?.id} />
          </div>
        )}

        {activeTab === "rbac" && hasRbacManage && (
          <div className="animate-in fade-in duration-200">
            <RbacTab />
          </div>
        )}

        {activeTab === "auditoria" && hasRbacManage && (
          <div className="animate-in fade-in duration-200">
            <AuditoriaTab />
          </div>
        )}

        {activeTab === "notificacoes" && can.view("notificacoes") && (
          <div className="animate-in fade-in duration-200">
            <NotificationPreferencesTab />
          </div>
        )}
      </div>
    </div>
  );
}

// ==========================================
// USER MANAGEMENT TAB SUB-COMPONENT
// ==========================================
function UserManagementTab({ currentUserId }: { currentUserId?: string }) {
  const { data: users, isLoading, isError, refetch } = useAdminUsers();

  const [editingUser, setEditingUser] = React.useState<Partial<AdminUser> | null>(null);
  const [isFormOpen, setIsFormOpen] = React.useState(false);
  const [selectedRoles, setSelectedRoles] = React.useState<string[]>([]);
  const [selectedPermissions, setSelectedPermissions] = React.useState<string[]>([]);
  const [userPassword, setUserPassword] = React.useState("");

  const [searchTerm, setSearchTerm] = React.useState("");
  const [message, setMessage] = React.useState<{ text: string; type: "success" | "error" } | null>(null);

  // Load all system permissions to display in custom permissions overrides, and the office's
  // own roles to display in the role picker below (PAPEL-06) -- ambos vem do mesmo payload de
  // /admin/rbac, ja tenant-scoped no servidor.
  const { data: rbacData } = useOfficeRbac();
  const systemPermissions = rbacData?.permissoes || [];
  const officePapeis = rbacData?.papeis || [];

  // Indicador "X/Y utilizadores" (Phase 118 PLAN-03, fonte trocada na Phase 124)
  // — useMe() dedupe pela cache partilhada ["auth","me"], nao e um segundo
  // pedido de rede. X vem diretamente de tenant_utilizadores_ativos, calculado
  // no backend pela funcao unica de contagem da Phase 117, fechando a
  // duplicacao encontrada pela auditoria do marco v2.16 (Phase 124). A lista
  // de utilizadores carregada acima mantem-se apenas para alimentar a tabela
  // de gestao abaixo. A convencao de exibicao do badge "Ativo" dessa tabela e
  // separada e permanece inalterada.
  const { data: meData } = useMe();
  const activeUserCount = meData?.tenant_utilizadores_ativos ?? 0;
  const tenantUserLimit = meData?.tenant_limite_utilizadores ?? null;
  const atUserLimit =
    tenantUserLimit !== null && activeUserCount >= tenantUserLimit;
  const userCountLabel =
    tenantUserLimit === null
      ? `${activeUserCount} utilizadores`
      : atUserLimit
        ? `${activeUserCount}/${tenantUserLimit} utilizadores · limite atingido`
        : `${activeUserCount}/${tenantUserLimit} utilizadores`;

  const handleEditClick = (user: AdminUser) => {
    setEditingUser(user);
    // tenant_role_ids, nao roles -- so o id sobrevive a uma renomeacao de papel (Phase 127,
    // Plano 05, Decisao 6; ver o doc-comment de AdminUser em types/admin-users.ts).
    setSelectedRoles(user.tenant_role_ids);
    setSelectedPermissions(user.permissions || []);
    setUserPassword("");
    setIsFormOpen(true);
    setMessage(null);
  };

  const handleCreateClick = () => {
    setEditingUser({
      nome: "",
      email: "",
      ativo: true,
      permissions: [],
    });
    // Papeis ja nao sao um union fixo (ADMIN/TECNICO/ADVOGADO/ASSISTENTE) -- o primeiro papel
    // do escritorio serve de valor por omissao sensato para um novo utilizador, sujeito a ser
    // trocado pelo administrador no picker abaixo.
    setSelectedRoles(officePapeis[0] ? [officePapeis[0].id] : []);
    setSelectedPermissions([]);
    setUserPassword("");
    setIsFormOpen(true);
    setMessage(null);
  };

  const handleDeleteClick = async (id: string) => {
    if (id === currentUserId) {
      setMessage({ text: "Não é permitido apagar a sua própria conta.", type: "error" });
      toast.error("Não é permitido apagar a sua própria conta.");
      return;
    }

    if (confirm("Tem a certeza que deseja eliminar este utilizador?")) {
      try {
        await apiFetch(`/admin/users/${encodeURIComponent(id)}`, { method: "DELETE" });
        setMessage({ text: "Utilizador removido com sucesso!", type: "success" });
        toast.success("Utilizador removido com sucesso!");
        setTimeout(() => {
          window.location.reload();
        }, 1000);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : "Erro ao apagar utilizador";
        setMessage({ text: msg, type: "error" });
        toast.error(msg);
      }
    }
  };

  const handleFormSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingUser) return;

    if (!editingUser.nome?.trim() || !editingUser.email?.trim()) {
      setMessage({ text: "Nome e email são obrigatórios.", type: "error" });
      toast.error("Nome e email são obrigatórios.");
      return;
    }

    if (!editingUser.id && !userPassword) {
      setMessage({ text: "Password é obrigatória para novos utilizadores.", type: "error" });
      toast.error("Password é obrigatória para novos utilizadores.");
      return;
    }

    // tenantRoleIds, nunca roles -- o backend recusa com 400 qualquer corpo que inclua `roles`
    // (Phase 127, Plano 05, Decisao 6): a atribuicao de papeis passa a ser feita
    // exclusivamente por id, nunca por nome. Este e o par frontend dessa decisao; enviar
    // `roles` aqui voltaria a partir todas as gravacoes de utilizador.
    const payload: Partial<AdminUserSavePayload> = {
      nome: editingUser.nome,
      email: editingUser.email,
      tenantRoleIds: selectedRoles,
      permissions: selectedPermissions,
      ativo: editingUser.ativo !== false,
      telefone: editingUser.telefone || "",
      avatar_url: editingUser.avatar_url || "",
      ...(userPassword ? { password: userPassword } : {}),
    };

    try {
      if (editingUser.id) {
        await apiFetch(`/admin/users/${encodeURIComponent(editingUser.id)}`, {
          method: "PUT",
          body: JSON.stringify(payload),
        });
        setMessage({ text: "Utilizador atualizado com sucesso!", type: "success" });
        toast.success("Utilizador atualizado com sucesso!");
      } else {
        await apiFetch("/admin/users", {
          method: "POST",
          body: JSON.stringify(payload),
        });
        setMessage({ text: "Novo utilizador criado com sucesso!", type: "success" });
        toast.success("Novo utilizador criado com sucesso!");
      }

      setIsFormOpen(false);
      setEditingUser(null);
      // reload to sync mock database
      setTimeout(() => {
        window.location.reload();
      }, 1000);
    } catch (err: unknown) {
      const msg =
        err instanceof Error
          ? err.message.replace(/^API \d{3}: /, "")
          : "Erro ao gravar dados.";
      setMessage({ text: msg || "Erro ao gravar dados.", type: "error" });
      toast.error(msg || "Erro ao gravar dados.");
    }
  };

  const toggleRole = (papelId: string) => {
    // Guarda de "pelo menos um papel", mantida verbatim -- so a comparacao mudou de nome
    // (string literal) para id, ja que o nome deixa de ser uma chave estavel (papeis podem ser
    // renomeados nesta fase).
    if (selectedRoles.includes(papelId)) {
      if (selectedRoles.length > 1) {
        setSelectedRoles(selectedRoles.filter((id) => id !== papelId));
      }
    } else {
      setSelectedRoles([...selectedRoles, papelId]);
    }
  };

  const togglePermission = (perm: string) => {
    if (selectedPermissions.includes(perm)) {
      setSelectedPermissions(selectedPermissions.filter((p) => p !== perm));
    } else {
      setSelectedPermissions([...selectedPermissions, perm]);
    }
  };

  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-48">
        <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 h-48 text-center px-4">
        <AlertCircle className="h-6 w-6 text-red-500" />
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Não foi possível carregar a lista de utilizadores.
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RotateCcw className="h-4 w-4" />
          Tentar novamente
        </Button>
      </div>
    );
  }

  const filteredUsers = users?.filter(
    (u) =>
      u.nome.toLowerCase().includes(searchTerm.toLowerCase()) ||
      u.email.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {message && (
        <div
          className={`p-3 text-sm border rounded-md ${message.type === "success"
              ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 border-emerald-500/20"
              : "bg-red-500/10 text-red-600 dark:text-red-400 border-red-500/20"
            }`}
        >
          {message.text}
        </div>
      )}

      {/* Desktop user list Card */}
      {!isFormOpen || !editingUser ? (
        <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
          <CardHeader className="flex flex-row items-center justify-between space-y-0">
            <div>
              <CardTitle className="text-xl font-semibold">Utilizadores Registados</CardTitle>
              <CardDescription>
                Lista de profissionais com credenciais de acesso ao sistema ALCv.
              </CardDescription>
            </div>
            <div className="flex flex-col items-end gap-2">
              <span
                className={
                  atUserLimit
                    ? "text-xs font-semibold text-red-600 dark:text-red-400"
                    : "text-xs text-slate-500 dark:text-slate-400"
                }
              >
                {userCountLabel}
              </span>
              {atUserLimit ? (
                <Tooltip>
                  <TooltipTrigger asChild>
                    <span tabIndex={0}>
                      <Button
                        disabled
                        className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
                      >
                        <Plus className="h-4 w-4" />
                        Novo Utilizador
                      </Button>
                    </span>
                  </TooltipTrigger>
                  <TooltipContent>
                    Limite de utilizadores atingido. Desative um utilizador para libertar uma vaga.
                  </TooltipContent>
                </Tooltip>
              ) : (
                <Button
                  onClick={handleCreateClick}
                  className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
                >
                  <Plus className="h-4 w-4" />
                  Novo Utilizador
                </Button>
              )}
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="relative">
              <Input
                placeholder="Pesquisar utilizador por nome ou email..."
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="bg-slate-50 dark:bg-slate-950 pr-8"
              />
              {searchTerm && (
                <button
                  type="button"
                  onClick={() => setSearchTerm("")}
                  aria-label="Limpar pesquisa"
                  className="absolute right-2.5 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  <X className="h-4 w-4" />
                </button>
              )}
            </div>

            <div className="overflow-x-auto border border-slate-200 dark:border-slate-800 rounded-md">
              <table className="w-full text-sm text-left border-collapse">
                <thead className="bg-slate-100/80 dark:bg-slate-950/80 text-slate-600 dark:text-slate-400 font-semibold border-b border-slate-200 dark:border-slate-800">
                  <tr>
                    <th className="p-3">Nome / Email</th>
                    <th className="p-3">Funções (Roles)</th>
                    <th className="p-3">Permissões Custom</th>
                    <th className="p-3">Estado</th>
                    <th className="p-3 text-right">Ações</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                  {filteredUsers && filteredUsers.length > 0 ? (
                    filteredUsers.map((user) => (
                      <tr
                        key={user.id}
                        className="hover:bg-slate-50/50 dark:hover:bg-slate-900/30 transition-colors"
                      >
                        <td className="p-3">
                          <div className="flex items-center gap-3">
                            <div className="h-9 w-9 rounded-full bg-slate-200 dark:bg-slate-800 flex items-center justify-center font-bold text-slate-600 dark:text-slate-300 uppercase text-xs overflow-hidden">
                              {user.avatar_url ? (
                                <img src={user.avatar_url} alt={user.nome} className="h-full w-full object-cover" />
                              ) : (
                                user.nome.substring(0, 2)
                              )}
                            </div>
                            <div>
                              <div className="font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-1.5">
                                {user.nome}
                                {user.id === currentUserId && (
                                  <Badge className="bg-slate-100 text-slate-800 border-none text-[9px] px-1 py-0 font-normal">
                                    Tu
                                  </Badge>
                                )}
                              </div>
                              <div className="text-xs text-slate-500 dark:text-slate-400">{user.email}</div>
                            </div>
                          </div>
                        </td>
                        <td className="p-3">
                          <div className="flex flex-wrap gap-1">
                            {user.roles.map((r) => (
                              <Badge
                                key={r}
                                className="bg-blue-500/10 text-blue-600 dark:text-blue-400 border-none text-xs font-semibold px-2 py-0.5"
                              >
                                {r}
                              </Badge>
                            ))}
                          </div>
                        </td>
                        <td className="p-3">
                          {user.permissions && user.permissions.length > 0 ? (
                            <span className="text-xs font-medium text-amber-600 dark:text-amber-400">
                              {user.permissions.length} overrides
                            </span>
                          ) : (
                            <span className="text-xs text-slate-400 dark:text-slate-600">Nenhuma</span>
                          )}
                        </td>
                        <td className="p-3">
                          {user.ativo !== false ? (
                            <span className="inline-flex items-center gap-1 text-xs font-medium text-emerald-600 dark:text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full">
                              <UserCheck className="h-3 w-3" />
                              Ativo
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 text-xs font-medium text-slate-500 dark:text-slate-400 bg-slate-500/10 px-2 py-0.5 rounded-full">
                              <UserMinus className="h-3 w-3" />
                              Desativado
                            </span>
                          )}
                        </td>
                        <td className="p-3 text-right">
                          <div className="flex items-center justify-end gap-1.5">
                            <Tooltip>
                              <TooltipTrigger asChild>
                                <Button
                                  variant="ghost"
                                  aria-label="Editar"
                                  onClick={() => handleEditClick(user)}
                                  className="h-8 w-8 p-0 text-slate-500 hover:text-blue-500 dark:text-slate-400 dark:hover:text-blue-400"
                                >
                                  <Edit className="h-4 w-4" />
                                </Button>
                              </TooltipTrigger>
                              <TooltipContent>Editar</TooltipContent>
                            </Tooltip>
                            {user.id !== currentUserId && (
                              <Tooltip>
                                <TooltipTrigger asChild>
                                  <Button
                                    variant="ghost"
                                    aria-label="Eliminar"
                                    onClick={() => handleDeleteClick(user.id)}
                                    className="h-8 w-8 p-0 text-slate-500 hover:text-red-500 dark:text-slate-400 dark:hover:text-red-400"
                                  >
                                    <Trash2 className="h-4 w-4" />
                                  </Button>
                                </TooltipTrigger>
                                <TooltipContent>Eliminar</TooltipContent>
                              </Tooltip>
                            )}
                          </div>
                        </td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={5} className="p-6 text-center text-slate-400 dark:text-slate-600">
                        Nenhum utilizador encontrado.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      ) : (
        /* Edit/Create Form Panel */
        <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
          <form onSubmit={handleFormSubmit}>
            <CardHeader className="flex flex-row items-center justify-between space-y-0">
              <div>
                <CardTitle className="text-xl font-semibold">
                  {editingUser.id ? "Editar Utilizador" : "Registar Novo Utilizador"}
                </CardTitle>
                <CardDescription>
                  Configure dados pessoais, perfis de acesso e permissões individuais de override.
                </CardDescription>
              </div>
              <Button
                type="button"
                variant="ghost"
                onClick={() => setIsFormOpen(false)}
                className="h-8 w-8 p-0 rounded-full border border-slate-200 dark:border-slate-800"
                aria-label="Fechar"
              >
                <X className="h-4 w-4" />
              </Button>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="userName">Nome Completo</Label>
                  <Input
                    id="userName"
                    value={editingUser.nome || ""}
                    onChange={(e) => setEditingUser({ ...editingUser, nome: e.target.value })}
                    placeholder="ex: João Miguel"
                    className="bg-slate-50 dark:bg-slate-950"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="userEmail">Email de Acesso</Label>
                  <Input
                    id="userEmail"
                    type="email"
                    value={editingUser.email || ""}
                    onChange={(e) => setEditingUser({ ...editingUser, email: e.target.value })}
                    placeholder="ex: joao@lexcv.cv"
                    className="bg-slate-50 dark:bg-slate-950"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="userPhone">Telefone (Opcional)</Label>
                  <Input
                    id="userPhone"
                    value={editingUser.telefone || ""}
                    onChange={(e) => setEditingUser({ ...editingUser, telefone: e.target.value })}
                    placeholder="+238 000 0000"
                    className="bg-slate-50 dark:bg-slate-950"
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="userPass">
                    {editingUser.id ? "Redefinir Password (Opcional)" : "Palavra-passe de Acesso"}
                  </Label>
                  <Input
                    id="userPass"
                    type="password"
                    value={userPassword}
                    onChange={(e) => setUserPassword(e.target.value)}
                    placeholder={editingUser.id ? "Deixe em branco para manter a atual" : "Mínimo 6 caracteres"}
                    className="bg-slate-50 dark:bg-slate-950"
                  />
                </div>
              </div>

              {/* Toggle Status switch */}
              <div className="flex items-center justify-between p-3 rounded-lg border border-slate-200 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-950/20">
                <div>
                  <h4 className="text-sm font-semibold text-slate-900 dark:text-slate-100">Status de Utilizador</h4>
                  <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                    Utilizadores desativados são imediatamente impedidos de fazer login no ALCv.
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <span className={`text-xs font-semibold ${editingUser.ativo !== false ? "text-emerald-500" : "text-slate-500"}`}>
                    {editingUser.ativo !== false ? "ATIVADO" : "DESATIVADO"}
                  </span>
                  <input
                    type="checkbox"
                    checked={editingUser.ativo !== false}
                    onChange={(e) => setEditingUser({ ...editingUser, ativo: e.target.checked })}
                    className="w-10 h-5 bg-slate-200 rounded-full appearance-none cursor-pointer relative checked:bg-emerald-500 transition-colors after:content-[''] after:w-4 after:h-4 after:bg-white after:rounded-full after:absolute after:top-0.5 after:left-0.5 checked:after:translate-x-5 after:transition-transform"
                  />
                </div>
              </div>

              {/* Roles selection -- lista dinamica dos papeis do proprio escritorio (PAPEL-06),
                  reusando o padrao visual exato do picker "Permissões Customizadas" abaixo:
                  container com scroll, uma linha clicavel por item, checkbox pointer-events-none
                  dentro dela, estados neutros de hover (nao o azul da aba RBAC). */}
              <div className="space-y-2">
                <Label>Papéis do Escritório</Label>
                <div className="grid gap-3 grid-cols-1 md:grid-cols-2 max-h-56 overflow-y-auto p-3 border border-slate-200 dark:border-slate-800 rounded-md bg-slate-50/30 dark:bg-slate-950/10">
                  {officePapeis.map((papel) => {
                    const isChecked = selectedRoles.includes(papel.id);
                    return (
                      <div
                        key={papel.id}
                        onClick={() => toggleRole(papel.id)}
                        className={`flex items-center justify-between gap-2 p-2 border rounded-md cursor-pointer transition-all ${isChecked
                            ? "border-slate-300 bg-slate-100/50 dark:border-slate-700 dark:bg-slate-800/50 text-slate-900 dark:text-slate-100"
                            : "border-transparent hover:bg-slate-100/50 dark:hover:bg-slate-900/30 text-slate-600 dark:text-slate-400"
                          }`}
                      >
                        <div className="flex items-center gap-2 min-w-0">
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => { }} // handled by click div
                            className="rounded h-3.5 w-3.5 pointer-events-none flex-shrink-0"
                          />
                          <span className="text-xs font-semibold truncate">{papel.nome}</span>
                        </div>
                        <Badge variant={papel.sistema ? "gray" : "outline"} className="flex-shrink-0">
                          {papel.sistema ? "Predefinido" : "Criado por si"}
                        </Badge>
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Custom Overrides selection */}
              <div className="space-y-2">
                <Label className="flex items-center gap-1.5">
                  Permissões Customizadas (Overrides)
                  <Badge variant="outline" className="text-[10px] py-0 font-normal">
                    Opcional
                  </Badge>
                </Label>
                <p className="text-xs text-slate-500 dark:text-slate-400">
                  Adicione permissões específicas de override a este utilizador individualmente, além das que ele já herda de seus perfis (Roles) do RBAC.
                </p>

                <div className="grid gap-3 grid-cols-1 grid-cols-1 md:grid-cols-2 max-h-56 overflow-y-auto p-3 border border-slate-200 dark:border-slate-800 rounded-md bg-slate-50/30 dark:bg-slate-950/10">
                  {systemPermissions.map((perm) => {
                    const isChecked = selectedPermissions.includes(perm.key);
                    return (
                      <div
                        key={perm.key}
                        onClick={() => togglePermission(perm.key)}
                        className={`flex items-start gap-3 p-2 border rounded-md cursor-pointer transition-all ${isChecked
                            ? "border-amber-500/50 bg-amber-500/5 dark:bg-amber-500/10 text-slate-900 dark:text-slate-100"
                            : "border-transparent hover:bg-slate-100/50 dark:hover:bg-slate-900/30 text-slate-600 dark:text-slate-400"
                          }`}
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => { }} // handled by click div
                          className="mt-0.5 text-amber-500 focus:ring-amber-500 rounded h-3.5 w-3.5 pointer-events-none"
                        />
                        <div className="space-y-0.5 text-left">
                          <div className="text-xs font-semibold">{perm.nome}</div>
                          <div className="text-[10px] text-slate-500 dark:text-slate-400 leading-tight">
                            {perm.descricao}
                          </div>
                        </div>
                      </div>
                    );
                  })}
                </div>
              </div>
            </CardContent>
            <CardFooter className="flex justify-end gap-3 pt-6 border-t border-slate-200 dark:border-slate-800">
              <Button
                type="button"
                variant="outline"
                onClick={() => setIsFormOpen(false)}
                className="border-slate-200 dark:border-slate-700"
              >
                <X className="h-4 w-4" />
                Cancelar
              </Button>
              <Button
                type="submit"
                className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
              >
                <Save className="h-4 w-4" />
                Guardar Utilizador
              </Button>
            </CardFooter>
          </form>
        </Card>
      )}
    </div>
  );
}

// ==========================================
// RBAC SYSTEM CONFIGURATION MATRIX SUB-COMPONENT
// ==========================================

// Fix 1 (127-UI-REVIEW.md, Top 3 Priority Fixes #1): rascunho da matriz por gravar, guardado
// FORA de React (variavel de modulo), nunca em estado do componente nem em prop. RbacTab tem de
// manter a assinatura literal `function RbacTab() {` -- e o marcador de bloco que
// scripts/verify-papeis-escritorio.mjs usa para delimitar o componente -- por isso este rascunho
// nao pode viajar por props. Uma variavel de modulo e a unica via que sobrevive ao
// desmontar/remontar de RbacTab quando o administrador muda de separador em Definicoes e volta,
// sem manter as outras abas sempre montadas (o que alargaria o raio de impacto a
// UserProfileForm/UserPasswordForm/NotificationPreferencesTab, fora do escopo desta correcao).
// Sincronizada a cada render de RbacTab, no mesmo padrao de "ajustar estado durante o render" ja
// usado abaixo para `appliedData` -- nunca em `useEffect`, que o ESLint deste projecto rejeita
// (`react-hooks/set-state-in-effect`) e que aqui seria superfluo (a atribuicao e sincrona e
// idempotente, nao dispara um novo render).
let rbacRascunhoPermissoes: LocalPermissoesPapeis | null = null;
let rbacRascunhoTocados: Set<string> = new Set();

function RbacTab() {
  // Regra dos Hooks (UI-SPEC §0): TODOS os hooks abaixo -- a query, as quatro
  // mutacoes, e todo o useState/useMemo/useForm -- tem de ser chamados
  // incondicionalmente, antes do primeiro early return (isLoading/isError).
  // Uma futura edicao NAO pode inserir um hook novo depois desses returns.
  const { data: rbac, isLoading, isError, refetch } = useOfficeRbac();
  const guardarPermissoes = useSaveOfficeRbac();
  const criarPapel = useCreateOfficeRole();
  const renomearPapel = useRenameOfficeRole();
  const apagarPapel = useDeleteOfficeRole();

  // Estado local de edicao, mais a referencia do ultimo payload ja aplicado
  // a ele -- mesmo padrao de ajuste de estado em render de
  // plataforma/moldes/page.tsx (comparar uma referencia do payload aplicado
  // e reagir quando mudou), nao `useEffect` com `setState`, que o ESLint
  // deste projecto rejeita (`react-hooks/set-state-in-effect`).
  const [appliedData, setAppliedData] = React.useState<OfficeRbac | null>(null);
  // Fix 1: inicializados a partir do rascunho de modulo (nao de `null`/`new Set()`), para que um
  // remontar de RbacTab apos o administrador mudar de separador recupere as edicoes nao gravadas
  // em vez de as perder -- ver comentario junto de `rbacRascunhoPermissoes` acima.
  const [localPermissoes, setLocalPermissoes] = React.useState<LocalPermissoesPapeis | null>(
    () => rbacRascunhoPermissoes,
  );
  const [touchedPapelIds, setTouchedPapelIds] = React.useState<Set<string>>(
    () => rbacRascunhoTocados,
  );
  const [isFormOpen, setIsFormOpen] = React.useState(false);
  const [papelEmRenomeacao, setPapelEmRenomeacao] = React.useState<OfficePapel | null>(null);
  const [papelEmEliminacao, setPapelEmEliminacao] = React.useState<OfficePapel | null>(null);

  const renameForm = useForm<RenomearPapelFormValues>({
    resolver: zodResolver(renomearPapelSchema),
    defaultValues: { nome: "" },
  });

  const data = rbac ?? null;
  if (data && data !== appliedData) {
    setAppliedData(data);
    // Reconciliado (mesclarEstadoLocal), nunca reinicializado -- preserva a
    // edicao local de qualquer papel tocado, mesmo quando este mesmo payload
    // fresco vem de uma mutacao sem nenhuma relacao com a matriz (criar,
    // renomear ou apagar outro papel). Ver o doc-comment de
    // mesclarEstadoLocal em merge-local-papeis.ts para a decisao completa.
    setLocalPermissoes((prevLocal) => mesclarEstadoLocal(data, prevLocal, touchedPapelIds));
  }

  const papeis = React.useMemo(() => data?.papeis ?? [], [data]);
  const permissoes = React.useMemo(() => data?.permissoes ?? [], [data]);

  const modulos = React.useMemo(
    () => Array.from(new Set(permissoes.map((p) => p.modulo))),
    [permissoes],
  );

  // Diff entre o estado local e o ultimo payload obtido, por papel,
  // comparando conjuntos de chaves (nao ordem de array) -- mesma tecnica de
  // moldesAlterados em plataforma/moldes/page.tsx. Extraida para
  // merge-local-papeis.ts (Fix 1) para ter prova automatizada fora do componente.
  const papeisAlterados = React.useMemo(
    () => papeisComAlteracoesPorGravar(papeis, localPermissoes),
    [localPermissoes, papeis],
  );

  const existeDiff = papeisAlterados.length > 0;

  // Fix 1: mantem o rascunho de modulo alinhado com o estado local depois de cada commit --
  // reatribuir uma variavel externa DURANTE o render e um efeito lateral que o React Compiler
  // deste projecto rejeita (regra `react-hooks/globals`, "Cannot reassign variables declared
  // outside of the component/hook"); a propria mensagem da regra aponta para um efeito quando a
  // variavel nao e usada na renderizacao em si, que e exactamente este caso -- o rascunho so e
  // lido no `useState(() => ...)` de um FUTURO mount de RbacTab, nunca no render actual.
  React.useEffect(() => {
    rbacRascunhoPermissoes = localPermissoes;
    rbacRascunhoTocados = touchedPapelIds;
  }, [localPermissoes, touchedPapelIds]);

  // Fix 1: mesmo com o rascunho de modulo acima a sobreviver a troca de separador dentro da
  // aplicacao, sair da pagina de facto (fechar o separador, recarregar, navegar para outro URL)
  // perde essa variavel -- o processo JS e reiniciado. Aviso nativo do browser antes de descartar
  // edicoes nao gravadas. Nao e uma chamada de negocio (nenhum pedido de rede/mutacao), por isso
  // nao cai na proibicao de useEffect para chamadas de negocio deste projecto -- e o idiom padrao
  // do React para sincronizar com um sistema externo (o evento beforeunload do browser).
  React.useEffect(() => {
    if (!existeDiff) return;
    const handleBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", handleBeforeUnload);
    return () => window.removeEventListener("beforeunload", handleBeforeUnload);
  }, [existeDiff]);

  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-48">
        <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 h-48 text-center px-4">
        <AlertCircle className="h-6 w-6 text-red-500" />
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Não foi possível carregar a matriz de permissões (RBAC).
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RotateCcw className="h-4 w-4" />
          Tentar novamente
        </Button>
      </div>
    );
  }

  const handleToggle = (papelId: string, permKey: string) => {
    setTouchedPapelIds((prev) => {
      if (prev.has(papelId)) return prev;
      const seguinte = new Set(prev);
      seguinte.add(papelId);
      return seguinte;
    });
    setLocalPermissoes((prev) => {
      if (!prev) return prev;
      const atual = prev[papelId] ?? new Set<string>();
      const seguinte = new Set(atual);
      if (seguinte.has(permKey)) {
        seguinte.delete(permKey);
      } else {
        seguinte.add(permKey);
      }
      return { ...prev, [papelId]: seguinte };
    });
  };

  const handleSave = async () => {
    const idsGravados = papeisAlterados.map((papel) => papel.id);
    try {
      await guardarPermissoes.mutateAsync({
        papeis: idsGravados.map((id) => ({
          id,
          permissoes: Array.from(localPermissoes?.[id] ?? new Set<string>()),
        })),
      });
      toast.success("Permissões atualizadas com sucesso.");
      // Os papeis que acabaram de ser gravados deixam de estar "por gravar"
      // -- mesclarEstadoLocal volta a aceitar o valor do servidor para eles
      // no proximo payload fresco, em vez de continuar a preservar
      // indefinidamente um valor local que ja foi gravado.
      setTouchedPapelIds((prev) => {
        if (prev.size === 0) return prev;
        const seguinte = new Set(prev);
        for (const id of idsGravados) {
          seguinte.delete(id);
        }
        return seguinte;
      });
    } catch {
      // O wrapper de fetch partilhado (apiFetch) ja mostrou o toast com a
      // mensagem do backend.
    }
  };

  const handleCreateSubmit = async (payload: PapelCreateRequest) => {
    try {
      const criado = await criarPapel.mutateAsync(payload);
      toast.success(`Papel "${criado.nome}" criado com sucesso.`);
      setIsFormOpen(false);
    } catch {
      // apiFetch ja mostrou o toast com a mensagem do backend (ex.: nome
      // duplicado). Mantemos o painel aberto com o input intacto.
    }
  };

  const openRename = (papel: OfficePapel) => {
    renameForm.reset({ nome: papel.nome });
    setPapelEmRenomeacao(papel);
  };

  const handleRenameSubmit = async (values: RenomearPapelFormValues) => {
    if (!papelEmRenomeacao) return;
    try {
      const atualizado = await renomearPapel.mutateAsync({
        id: papelEmRenomeacao.id,
        nome: values.nome,
      });
      toast.success(`Papel renomeado para "${atualizado.nome}".`);
      setPapelEmRenomeacao(null);
    } catch {
      // apiFetch ja mostrou o toast; mantemos o Dialog aberto.
    }
  };

  const openDelete = (papel: OfficePapel) => setPapelEmEliminacao(papel);

  const handleConfirmDelete = async () => {
    if (!papelEmEliminacao) return;
    const { id, nome } = papelEmEliminacao;
    try {
      await apagarPapel.mutateAsync(id);
      toast.success(`Papel "${nome}" apagado.`);
      setPapelEmEliminacao(null);
      setTouchedPapelIds((prev) => {
        if (!prev.has(id)) return prev;
        const seguinte = new Set(prev);
        seguinte.delete(id);
        return seguinte;
      });
    } catch {
      // apiFetch ja mostrou o toast; mantemos o AlertDialog aberto.
    }
  };

  return (
    <>
      {isFormOpen ? (
        <CriarPapelPanel
          onCancel={() => setIsFormOpen(false)}
          onSubmit={handleCreateSubmit}
          isSubmitting={criarPapel.isPending}
          permissoes={permissoes}
        />
      ) : (
        <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
          <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between space-y-2 sm:space-y-0">
            <div>
              <CardTitle className="text-xl font-semibold">Papéis do Escritório</CardTitle>
              <CardDescription>
                Crie, edite, renomeie e atribua os papéis do seu escritório. Estas definições
                pertencem apenas ao seu escritório — não afetam outros escritórios da
                plataforma.
              </CardDescription>
            </div>
            <div className="flex items-center gap-2 self-start sm:self-auto">
              {existeDiff && (
                // Fix 1 (127-UI-REVIEW.md achado #1): indicador de dirty-state sempre visivel,
                // nao so a mudanca de opacidade do botao. Cor neutra (nao azul) e peso 600 --
                // dentro dos valores ja declarados pela UI-SPEC (Color §Secondary,
                // Typography's 12px/text-xs 400/600 para prosa micro), sem abrir uma nova
                // excecao ao mesmo contrato que os achados #2/#3 apontam como incompleto.
                <span
                  className="text-xs font-semibold text-slate-700 dark:text-slate-300"
                  aria-live="polite"
                >
                  {rotuloPapeisPorGravar(papeisAlterados.length)}
                </span>
              )}
              <Button
                variant="outline"
                onClick={() => setIsFormOpen(true)}
                className="flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
              >
                <Plus className="h-4 w-4" />
                Criar Papel
              </Button>
              <Button
                onClick={handleSave}
                disabled={!existeDiff || guardarPermissoes.isPending}
                className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto"
              >
                {guardarPermissoes.isPending ? (
                  <Loader2 className="h-4 w-4 animate-spin" />
                ) : (
                  <Save className="h-4 w-4" />
                )}
                Guardar Alterações
              </Button>
            </div>
          </CardHeader>

          <CardContent className="space-y-4">
            <div className="p-4 bg-blue-500/5 dark:bg-blue-500/10 text-slate-700 dark:text-slate-300 text-xs border border-blue-500/10 rounded-md flex items-start gap-2.5">
              <ShieldAlert className="h-4 w-4 mt-0.5 text-blue-500 flex-shrink-0" />
              <div>
                <strong>O papel de administrador do escritório</strong> mantém sempre as
                permissões que o tornam administrador — não é possível removê-las nem apagar
                esse papel. As alterações a qualquer papel aplicam-se de imediato às sessões já
                abertas dos utilizadores afetados; não é preciso iniciar sessão novamente.
              </div>
            </div>

            {papeis.length === 0 ? (
              <Empty>
                <EmptyHeader>
                  <EmptyTitle>Nenhum papel definido</EmptyTitle>
                  <EmptyDescription>
                    Crie o primeiro papel do seu escritório para poder atribuí-lo a
                    utilizadores.
                  </EmptyDescription>
                </EmptyHeader>
                <EmptyContent>
                  <Button
                    variant="outline"
                    onClick={() => setIsFormOpen(true)}
                    className="flex items-center gap-1.5"
                  >
                    <Plus className="h-4 w-4" />
                    Criar Papel
                  </Button>
                </EmptyContent>
              </Empty>
            ) : (
              <div className="overflow-x-auto border border-slate-200 dark:border-slate-800 rounded-md">
                <table className="w-full text-sm text-left border-collapse">
                  <thead className="bg-slate-100/80 dark:bg-slate-950/80 border-b border-slate-200 dark:border-slate-800">
                    <tr>
                      <th
                        scope="col"
                        className="p-3 font-semibold text-slate-600 dark:text-slate-400 min-w-[280px]"
                      >
                        Módulo / Permissão
                      </th>
                      {papeis.map((papel) => (
                        <th
                          key={papel.id}
                          scope="col"
                          className="p-3 font-bold text-center text-slate-700 dark:text-slate-300 text-xs tracking-wider min-w-[140px]"
                        >
                          <div className="flex flex-col items-center gap-1">
                            <div className="flex items-center gap-1">
                              {papel.protegido && (
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <Lock className="h-3 w-3 text-slate-400" aria-label="Papel protegido" />
                                  </TooltipTrigger>
                                  <TooltipContent>
                                    Este é o papel de administrador do escritório. Não pode
                                    ser apagado nem perder as permissões que o tornam
                                    administrador.
                                  </TooltipContent>
                                </Tooltip>
                              )}
                              <span>{papel.nome}</span>
                              <PapelAcoesMenu
                                papel={papel}
                                onRenomear={() => openRename(papel)}
                                onApagar={() => openDelete(papel)}
                              />
                            </div>
                            <div className="flex flex-wrap items-center justify-center gap-1">
                              <Badge variant={papel.sistema ? "gray" : "outline"}>
                                {papel.sistema ? "Predefinido" : "Criado por si"}
                              </Badge>
                              <Badge variant="gray">
                                {papel.utilizadoresAtribuidos} utilizador
                                {papel.utilizadoresAtribuidos === 1 ? "" : "es"}
                              </Badge>
                            </div>
                          </div>
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
                    {modulos.map((mod) => {
                      const permissoesDoModulo = permissoes.filter((p) => p.modulo === mod);
                      return (
                        <React.Fragment key={mod}>
                          <tr className="bg-slate-50 dark:bg-slate-900/50">
                            <td
                              colSpan={papeis.length + 1}
                              className="px-3 py-2 text-xs font-bold text-slate-500 uppercase tracking-widest"
                            >
                              {mod}
                            </td>
                          </tr>
                          {permissoesDoModulo.map((permissao) => (
                            <tr
                              key={permissao.key}
                              className="hover:bg-slate-50/30 dark:hover:bg-slate-900/10 transition-colors"
                            >
                              <th scope="row" className="p-3 text-left">
                                <div className="font-medium text-slate-900 dark:text-slate-100">
                                  {permissao.nome}
                                </div>
                                <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">
                                  {permissao.descricao}
                                </div>
                              </th>
                              {papeis.map((papel) => {
                                const isChecked =
                                  localPermissoes?.[papel.id]?.has(permissao.key) ?? false;
                                // Bloqueio de piso: a chave e sempre `protegido`, nunca a
                                // string literal "ADMIN" -- o nome deste papel passa a ser
                                // editavel a partir deste mesmo ecra nesta fase, por isso
                                // qualquer comparacao pelo literal deixaria de proteger o
                                // papel assim que o escritorio o renomeasse. O conjunto
                                // fixo vem de `papel.permissoes` (o payload persistido no
                                // servidor), nunca do estado local editado.
                                const isLockedFloor =
                                  papel.protegido && papel.permissoes.includes(permissao.key);
                                return (
                                  <td key={papel.id} className="p-3 text-center">
                                    <label className="inline-flex items-center justify-center cursor-pointer p-2">
                                      <input
                                        type="checkbox"
                                        checked={isChecked || isLockedFloor}
                                        disabled={isLockedFloor}
                                        onChange={() => handleToggle(papel.id, permissao.key)}
                                        aria-label={`${permissao.nome} — ${papel.nome}`}
                                        className={`h-4 w-4 text-blue-600 focus:ring-blue-500 border-slate-300 dark:border-slate-800 rounded transition-all ${
                                          isLockedFloor
                                            ? "cursor-not-allowed text-blue-500/55 opacity-60"
                                            : "cursor-pointer"
                                        }`}
                                      />
                                    </label>
                                  </td>
                                );
                              })}
                            </tr>
                          ))}
                        </React.Fragment>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </CardContent>
        </Card>
      )}

      <Dialog
        open={!!papelEmRenomeacao}
        onOpenChange={(open) => {
          if (!open) setPapelEmRenomeacao(null);
        }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Renomear papel</DialogTitle>
            <DialogDescription>
              Isto altera apenas o nome apresentado — não muda as permissões nem os
              utilizadores atualmente atribuídos a este papel. O novo nome fica visível de
              imediato em toda a aplicação.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={renameForm.handleSubmit(handleRenameSubmit)}>
            <div className="space-y-2">
              <Label htmlFor="roleRename">Novo nome</Label>
              <Input
                id="roleRename"
                aria-invalid={!!renameForm.formState.errors.nome}
                className="bg-slate-50 dark:bg-slate-950"
                {...renameForm.register("nome")}
              />
              {renameForm.formState.errors.nome ? (
                <p className="text-sm text-red-600">{renameForm.formState.errors.nome.message}</p>
              ) : null}
              <p className="text-xs text-slate-500 dark:text-slate-400">
                Nome atual: {papelEmRenomeacao?.nome}
              </p>
            </div>
            <DialogFooter className="mt-6">
              <DialogClose asChild>
                <Button type="button" variant="outline">
                  Cancelar
                </Button>
              </DialogClose>
              <Button
                type="submit"
                className="bg-blue-600 hover:bg-blue-700 text-white"
                disabled={renomearPapel.isPending}
              >
                {renomearPapel.isPending ? "A gravar..." : "Guardar Nome"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog
        open={!!papelEmEliminacao}
        onOpenChange={(open) => {
          if (!open) setPapelEmEliminacao(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Apagar o papel &quot;{papelEmEliminacao?.nome}&quot;?</AlertDialogTitle>
            <AlertDialogDescription>
              Esta ação não pode ser desfeita. O papel deixa de existir e as suas permissões
              deixam de estar disponíveis para atribuição. Nenhum utilizador tem este papel
              atribuído atualmente, por isso é seguro apagá-lo.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={apagarPapel.isPending}>Cancelar</AlertDialogCancel>
            <AlertDialogAction
              disabled={apagarPapel.isPending}
              className="bg-red-600 hover:bg-red-700 text-white"
              onClick={(e) => {
                e.preventDefault();
                void handleConfirmDelete();
              }}
            >
              {apagarPapel.isPending ? "A apagar..." : "Apagar Papel"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

// ==========================================
// NOTIFICATION PREFERENCES TAB SUB-COMPONENT
// ==========================================
function NotificationPreferencesTab() {
  const { data, isLoading, isError, refetch } = useNotificacaoPreferencias();
  const silenciar = useSilenciarCategoria();
  const reativar = useReativarCategoria();

  if (isLoading) {
    return (
      <div className="flex justify-center items-center h-48">
        <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 h-48 text-center px-4">
        <AlertCircle className="h-6 w-6 text-red-500" />
        <p className="text-sm text-slate-600 dark:text-slate-400">
          Não foi possível carregar as preferências de notificação.
        </p>
        <Button variant="outline" size="sm" onClick={() => refetch()}>
          <RotateCcw className="h-4 w-4" />
          Tentar novamente
        </Button>
      </div>
    );
  }

  const silenciadas = data?.silenciadas ?? [];
  const categoriasSilenciaveis = NOTIFICACAO_CATEGORIA_SILENCIAVEIS_OPTIONS;

  const handleToggle = async (categoria: NotificacaoCategoria, entregar: boolean) => {
    try {
      if (entregar) {
        await reativar.mutateAsync(categoria);
        toast.success("Categoria reativada. Voltará a receber estas notificações.");
      } else {
        await silenciar.mutateAsync(categoria);
        toast.success("Categoria silenciada. Deixa de receber estas notificações.");
      }
    } catch {
      // Erro já reportado pelo toast automático do apiFetch (exceto 401/403).
    }
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
      <CardHeader>
        <CardTitle className="text-xl font-semibold flex items-center gap-2">
          <Bell className="h-5 w-5 text-blue-500" />
          Preferências de Notificação
        </CardTitle>
        <CardDescription>
          Escolha que categorias de notificação pretende receber. O silenciamento aplica-se apenas
          a si — não afeta outros utilizadores do escritório.
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="p-4 bg-blue-500/5 dark:bg-blue-500/10 text-slate-700 dark:text-slate-300 text-xs border border-blue-500/10 rounded-md flex items-start gap-2.5">
          <ShieldAlert className="h-4 w-4 mt-0.5 text-blue-500 flex-shrink-0" />
          <div>
            A categoria crítica <strong>&quot;Prazo vencido&quot;</strong> é sempre entregue e por
            isso não aparece nesta lista.
          </div>
        </div>

        <div className="divide-y divide-slate-200 dark:divide-slate-800 border border-slate-200 dark:border-slate-800 rounded-md">
          {categoriasSilenciaveis.map((o) => {
            const checked = !silenciadas.includes(o.value);
            const isPending =
              (silenciar.isPending && silenciar.variables === o.value) ||
              (reativar.isPending && reativar.variables === o.value);

            return (
              <div
                key={o.value}
                className="flex items-center justify-between p-3 bg-white/50 dark:bg-transparent"
              >
                <span className="text-sm font-medium text-slate-900 dark:text-slate-100">
                  {o.label}
                </span>
                <div className="flex items-center gap-2">
                  <span
                    className={`text-xs font-semibold ${checked ? "text-emerald-500" : "text-slate-500"
                      }`}
                  >
                    {checked ? "A ENTREGAR" : "SILENCIADA"}
                  </span>
                  <input
                    type="checkbox"
                    checked={checked}
                    disabled={isPending}
                    onChange={(e) => handleToggle(o.value, e.target.checked)}
                    aria-label={`Alternar entrega de notificações de ${o.label}`}
                    className="w-10 h-5 bg-slate-200 rounded-full appearance-none cursor-pointer relative checked:bg-emerald-500 transition-colors after:content-[''] after:w-4 after:h-4 after:bg-white after:rounded-full after:absolute after:top-0.5 after:left-0.5 checked:after:translate-x-5 after:transition-transform disabled:cursor-not-allowed disabled:opacity-60"
                  />
                </div>
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
}
