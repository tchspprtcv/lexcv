"use client";

import * as React from "react";
import {
  User,
  Lock,
  Users,
  Sliders,
  Plus,
  Trash2,
  Edit,
  Check,
  X,
  Loader2,
  UserCheck,
  UserMinus,
  ShieldAlert,
  Save,
  Bell,
  AlertCircle,
  RotateCcw
} from "lucide-react";

import { apiFetch } from "@/lib/api";
import { usePermissions } from "@/hooks/use-permissions";
import { useMe } from "@/hooks/use-me";
import {
  useAdminUsers,
  useAdminRbac
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
import type { MockUser, MockRole, MockPermission } from "@/server/mock-db";
import type { NotificacaoCategoria } from "@/types/notificacoes";

type TabId = "profile" | "security" | "users" | "rbac" | "notificacoes";

export default function SettingsPage() {
  const { data: me, can } = usePermissions();
  const [activeTab, setActiveTab] = React.useState<TabId>("profile");
  
  // Admin queries
  const isAdmin = me?.roles?.includes("ADMIN");
  const hasUsersManage = can.manage("users") || isAdmin;
  const hasRbacManage = can.manage("rbac") || isAdmin;

  // Render tabs
  return (
    <div className="space-y-6 animate-in fade-in duration-300">
      <div className="flex items-start justify-between gap-6">
        <div>
          <h1 className="text-3xl font-bold text-slate-900 dark:text-white tracking-tight">Definições de Sistema</h1>
          <div className="mt-2 flex items-center text-xs font-semibold tracking-wider uppercase text-slate-500 dark:text-slate-400">
            <span>LexCV</span> <span className="mx-2 text-slate-300 dark:text-slate-700">/</span> <span className="text-blue-600 dark:text-blue-400">Configurações Gerais e Segurança</span>
          </div>
        </div>
      </div>

      {/* Modern Glassmorphic Tabs */}
      <div className="flex border-b border-slate-200 dark:border-slate-800 gap-1 pb-px overflow-x-auto">
        <button
          onClick={() => setActiveTab("profile")}
          className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${
            activeTab === "profile"
              ? "border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-400"
              : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
          }`}
        >
          <User className="h-4 w-4" />
          O Meu Perfil
        </button>

        <button
          onClick={() => setActiveTab("security")}
          className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${
            activeTab === "security"
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
            className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${
              activeTab === "users"
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
            className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${
              activeTab === "rbac"
                ? "border-blue-600 text-blue-600 dark:border-blue-500 dark:text-blue-400"
                : "border-transparent text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200"
            }`}
          >
            <Sliders className="h-4 w-4" />
            Controlo de Acesso (RBAC)
          </button>
        )}

        {can.view("notificacoes") && (
          <button
            onClick={() => setActiveTab("notificacoes")}
            className={`flex items-center gap-2 px-4 py-2.5 border-b-2 text-sm font-medium transition-all ${
              activeTab === "notificacoes"
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

  const [editingUser, setEditingUser] = React.useState<Partial<MockUser> | null>(null);
  const [isFormOpen, setIsFormOpen] = React.useState(false);
  const [selectedRoles, setSelectedRoles] = React.useState<MockRole[]>([]);
  const [selectedPermissions, setSelectedPermissions] = React.useState<MockPermission[]>([]);
  const [userPassword, setUserPassword] = React.useState("");

  const [searchTerm, setSearchTerm] = React.useState("");
  const [message, setMessage] = React.useState<{ text: string; type: "success" | "error" } | null>(null);

  // Load all system permissions to display in custom permissions overrides
  const { data: rbacData } = useAdminRbac();
  const systemPermissions = rbacData?.systemPermissions || [];

  // Indicador "X/Y utilizadores" (Phase 118 PLAN-03) — useMe() dedupe pela cache
  // partilhada ["auth","me"], nao e um segundo pedido de rede. X usa igualdade
  // estrita (=== true) para espelhar countByTenantIdAndAtivoTrue do backend;
  // deliberadamente diferente da convencao de exibicao do badge "Ativo" da
  // tabela mais abaixo, que trata utilizadores sem o campo definido como ativos.
  const { data: meData } = useMe();
  const activeUserCount = users?.filter((u) => u.ativo === true).length ?? 0;
  const tenantUserLimit = meData?.tenant_limite_utilizadores ?? null;
  const atUserLimit =
    tenantUserLimit !== null && activeUserCount >= tenantUserLimit;
  const userCountLabel =
    tenantUserLimit === null
      ? `${activeUserCount} utilizadores`
      : atUserLimit
        ? `${activeUserCount}/${tenantUserLimit} utilizadores · limite atingido`
        : `${activeUserCount}/${tenantUserLimit} utilizadores`;

  const handleEditClick = (user: MockUser) => {
    setEditingUser(user);
    setSelectedRoles(user.roles);
    setSelectedPermissions(user.permissions || []);
    setUserPassword("");
    setIsFormOpen(true);
    setMessage(null);
  };

  const handleCreateClick = () => {
    setEditingUser({
      nome: "",
      email: "",
      roles: ["ASSISTENTE"],
      ativo: true,
      permissions: [],
    });
    setSelectedRoles(["ASSISTENTE"]);
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

    const payload = {
      nome: editingUser.nome,
      email: editingUser.email,
      roles: selectedRoles,
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

  const toggleRole = (role: MockRole) => {
    if (selectedRoles.includes(role)) {
      if (selectedRoles.length > 1) {
        setSelectedRoles(selectedRoles.filter((r) => r !== role));
      }
    } else {
      setSelectedRoles([...selectedRoles, role]);
    }
  };

  const togglePermission = (perm: MockPermission) => {
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
          className={`p-3 text-sm border rounded-md ${
            message.type === "success"
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
                Lista de profissionais com credenciais de acesso ao sistema LexCV.
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
                    Utilizadores desativados são imediatamente impedidos de fazer login no LexCV.
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

              {/* Roles selection */}
              <div className="space-y-2">
                <Label>Funções / Perfis de Acesso</Label>
                <div className="grid gap-2 grid-cols-2 sm:grid-cols-4">
                  {(["ADMIN", "TECNICO", "ADVOGADO", "ASSISTENTE"] as MockRole[]).map((role) => {
                    const isChecked = selectedRoles.includes(role);
                    return (
                      <button
                        type="button"
                        key={role}
                        onClick={() => toggleRole(role)}
                        className={`flex items-center justify-between px-3 py-2 border rounded-md text-xs font-medium transition-all ${
                          isChecked
                            ? "border-blue-500 bg-blue-500/10 text-blue-600 dark:text-blue-400 shadow-sm"
                            : "border-slate-200 dark:border-slate-800 text-slate-500 dark:text-slate-400 hover:bg-slate-50/50 dark:hover:bg-slate-900/30"
                        }`}
                      >
                        {role}
                        {isChecked && <Check className="h-3.5 w-3.5" />}
                      </button>
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
                        className={`flex items-start gap-3 p-2 border rounded-md cursor-pointer transition-all ${
                          isChecked
                            ? "border-amber-500/50 bg-amber-500/5 dark:bg-amber-500/10 text-slate-900 dark:text-slate-100"
                            : "border-transparent hover:bg-slate-100/50 dark:hover:bg-slate-900/30 text-slate-600 dark:text-slate-400"
                        }`}
                      >
                        <input
                          type="checkbox"
                          checked={isChecked}
                          onChange={() => {}} // handled by click div
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
function RbacTab() {
  const { data: rbac, isLoading, isError, refetch } = useAdminRbac();
  const me = useMe();
  // Espelho de UX, nao fronteira de autorizacao: o bloqueio real vive na
  // anotacao de metodo do backend (Plan 01, AdminController.updateRbac).
  // Esta verificacao apenas evita expor, na interface, uma acao que hoje
  // resultaria num 403 confuso para quem ja nao a pode executar — na
  // linha do comentario equivalente em dashboard-shell.tsx.
  const isPlatformAdmin = me.isFetched && (me.data?.roles?.includes("PLATAFORMA_ADMIN") ?? false);
  type RolePermissionsMap = Record<MockRole, MockPermission[]>;

  const [localRolePermissions, setLocalRolePermissions] = React.useState<RolePermissionsMap | null>(null);
  const [saving, setSaving] = React.useState(false);
  const [success, setSuccess] = React.useState<string | null>(null);
  const [error, setError] = React.useState<string | null>(null);

  const effectiveRolePermissions =
    localRolePermissions ?? (rbac?.rolePermissions as RolePermissionsMap | undefined);

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

  if (!effectiveRolePermissions) {
    return (
      <div className="flex justify-center items-center h-48">
        <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
      </div>
    );
  }

  const systemPermissions = rbac?.systemPermissions || [];
  const roles: MockRole[] = ["ADMIN", "TECNICO", "ADVOGADO", "ASSISTENTE"];

  const handleCheckboxChange = (role: MockRole, permKey: MockPermission) => {
    if (role === "ADMIN") return; // Admin permissions are immutable (always enabled)
    if (!isPlatformAdmin) return; // WR-01 (121-REVIEW.md): matriz é só-leitura para quem não pode gravar

    const base = effectiveRolePermissions;
    const currentPerms = base[role] || [];
    let nextPerms: MockPermission[] = [];

    if (currentPerms.includes(permKey)) {
      nextPerms = currentPerms.filter((p: string) => p !== permKey);
    } else {
      nextPerms = [...currentPerms, permKey];
    }

    setLocalRolePermissions({
      ...base,
      [role]: nextPerms,
    });
  };

  const handleSave = async () => {
    setSaving(true);
    setSuccess(null);
    setError(null);
    try {
      await apiFetch("/admin/rbac", {
        method: "PUT",
        body: JSON.stringify({ rolePermissions: effectiveRolePermissions }),
      });
      setSuccess("Configurações do RBAC (Regras de Acesso) atualizadas com sucesso!");
      toast.success("Configurações do RBAC atualizadas com sucesso!");
      refetch();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : "Erro ao guardar definições de RBAC.";
      setError(msg);
      toast.error(msg);
    } finally {
      setSaving(false);
    }
  };

  // Group permissions by module
  const modules = Array.from(new Set(systemPermissions.map((p) => p.modulo)));

  return (
    <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
      <CardHeader className="flex flex-col sm:flex-row sm:items-center sm:justify-between space-y-2 sm:space-y-0">
        <div>
          <CardTitle className="text-xl font-semibold flex items-center gap-2">
            Matriz de Regras de Acesso (RBAC)
          </CardTitle>
          <CardDescription>
            Defina quais as permissões atribuídas globalmente a cada perfil profissional.
          </CardDescription>
        </div>
        {isPlatformAdmin ? (
          <Button
            onClick={handleSave}
            disabled={saving}
            className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm flex items-center gap-2 self-start sm:self-auto"
          >
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            Guardar Regras
          </Button>
        ) : (
          <Tooltip>
            <TooltipTrigger asChild>
              <span tabIndex={0} className="self-start sm:self-auto">
                <Badge variant="outline" className="gap-1">
                  <Lock className="h-3 w-3" />
                  Gerido pela Plataforma
                </Badge>
              </span>
            </TooltipTrigger>
            <TooltipContent>
              As regras de acesso por perfil (RBAC) passaram a ser uma configuração fixa e comum a
              toda a plataforma LexCV — já não podem ser alteradas a partir de um escritório individual.
            </TooltipContent>
          </Tooltip>
        )}
      </CardHeader>
      
      <CardContent className="space-y-4">
        {success && (
          <div className="p-3 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-sm border border-emerald-500/20 rounded-md">
            {success}
          </div>
        )}

        {error && (
          <div className="p-3 bg-red-500/10 text-red-600 dark:text-red-400 text-sm border border-red-500/20 rounded-md">
            {error}
          </div>
        )}

        <div className="p-4 bg-blue-500/5 dark:bg-blue-500/10 text-slate-700 dark:text-slate-300 text-xs border border-blue-500/10 rounded-md flex items-start gap-2.5">
          <ShieldAlert className="h-4 w-4 mt-0.5 text-blue-500 flex-shrink-0" />
          <div>
            <strong>Nota Importante:</strong> O perfil de administrador (<strong>ADMIN</strong>) possui acessos totais e incondicionais por padrão. As suas caixas de seleção estão marcadas e bloqueadas permanentemente para evitar o bloqueio acidental do painel de administração.
          </div>
        </div>

        <div className="overflow-x-auto border border-slate-200 dark:border-slate-800 rounded-md">
          <table className="w-full text-sm text-left border-collapse">
            <thead className="bg-slate-100/80 dark:bg-slate-950/80 border-b border-slate-200 dark:border-slate-800">
              <tr>
                <th className="p-3 font-semibold text-slate-600 dark:text-slate-400 min-w-[280px]">Módulo / Permissão</th>
                {roles.map((role) => (
                  <th
                    key={role}
                    className="p-3 font-bold text-center text-slate-700 dark:text-slate-300 text-xs tracking-wider"
                  >
                    {role}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-200 dark:divide-slate-800">
              {modules.map((mod) => {
                const modPerms = systemPermissions.filter((p) => p.modulo === mod);
                return (
                  <React.Fragment key={mod}>
                    {/* Module Separator header */}
                    <tr className="bg-slate-50 dark:bg-slate-900/50">
                      <td colSpan={5} className="px-3 py-2 text-xs font-bold text-slate-500 uppercase tracking-widest">
                        {mod}
                      </td>
                    </tr>
                    {modPerms.map((perm) => (
                      <tr
                        key={perm.key}
                        className="hover:bg-slate-50/30 dark:hover:bg-slate-900/10 transition-colors"
                      >
                        <td className="p-3">
                          <div className="font-medium text-slate-900 dark:text-slate-100">{perm.nome}</div>
                          <div className="text-[11px] text-slate-500 dark:text-slate-400 mt-0.5">{perm.descricao}</div>
                        </td>
                        {roles.map((role) => {
                          const isAssigned = (effectiveRolePermissions[role] || []).includes(perm.key);
                          const isAdminRow = role === "ADMIN";
                          // WR-01 (121-REVIEW.md): alarga o estado desabilitado/esbatido a toda a
                          // matriz quando quem a vê não a pode gravar — sem isto, um ADMIN de tenant
                          // via a matriz como interativa (clicava e alternava localmente) mesmo sem
                          // o botão Guardar alguma vez renderizar para si. "checked" fica ligado só a
                          // isAdminRow (não a isDisabled): a coluna ADMIN mostra-se sempre marcada por
                          // ter acessos totais implícitos, mas as restantes colunas não podem passar a
                          // "sempre marcadas" só por estarem desabilitadas para leitura.
                          const isDisabled = isAdminRow || !isPlatformAdmin;

                          return (
                            <td key={role} className="p-3 text-center">
                              <label className="inline-flex items-center justify-center cursor-pointer p-2">
                                <input
                                  type="checkbox"
                                  checked={isAssigned || isAdminRow}
                                  disabled={isDisabled}
                                  onChange={() => handleCheckboxChange(role, perm.key)}
                                  className={`h-4 w-4 text-blue-600 focus:ring-blue-500 border-slate-300 dark:border-slate-800 rounded transition-all ${
                                    isDisabled
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
      </CardContent>
    </Card>
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
                    className={`text-xs font-semibold ${
                      checked ? "text-emerald-500" : "text-slate-500"
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
