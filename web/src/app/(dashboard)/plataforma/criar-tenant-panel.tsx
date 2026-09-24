"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
import Image from "next/image";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import {
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { setupSchema, type SetupFormValues } from "@/schemas/setup";
import type { SetupInitializeRequest } from "@/types/setup";

const MAX_LOGO_SIZE_BYTES = 2 * 1024 * 1024;
const ALLOWED_LOGO_TYPES = ["image/png", "image/jpeg", "image/webp"];

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(new Error("Não foi possível ler o ficheiro do logo."));
    reader.readAsDataURL(file);
  });
}

/**
 * Painel inline de criacao de tenant — nao e um modal (ver UI-SPEC: o upload
 * de logo precisa de largura, sem precedente de "modal + upload" neste app).
 *
 * Este componente e puramente de apresentacao e validacao. Quem detem a
 * mutacao, as mensagens de sucesso/erro e o fecho do painel e o chamador
 * (page.tsx, Plan 05), atraves das props `onSubmit`/`isSubmitting` —
 * mantendo esse controlo de estado todo num so sitio.
 */
export function CriarTenantPanel({
  onCancel,
  onSubmit,
  isSubmitting,
}: {
  onCancel: () => void;
  onSubmit: (payload: SetupInitializeRequest) => Promise<void>;
  isSubmitting: boolean;
}) {
  const [logoDataUrl, setLogoDataUrl] = React.useState<string | null>(null);
  const [logoName, setLogoName] = React.useState<string | null>(null);
  const [logoError, setLogoError] = React.useState<string | null>(null);

  const form = useForm<SetupFormValues>({
    resolver: zodResolver(setupSchema),
    defaultValues: {
      clientName: "",
      adminEmail: "",
      adminPassword: "",
      confirmPassword: "",
    },
  });

  const handleLogoChange = async (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0] ?? null;
    setLogoDataUrl(null);
    setLogoName(null);
    setLogoError(null);

    if (!file) {
      return;
    }

    if (!ALLOWED_LOGO_TYPES.includes(file.type)) {
      setLogoError("O logo deve ser PNG, JPG ou WEBP.");
      event.target.value = "";
      return;
    }

    if (file.size > MAX_LOGO_SIZE_BYTES) {
      setLogoError("O logo deve ter no máximo 2MB.");
      event.target.value = "";
      return;
    }

    try {
      const dataUrl = await readFileAsDataUrl(file);
      setLogoDataUrl(dataUrl);
      setLogoName(file.name);
    } catch (error) {
      setLogoError(error instanceof Error ? error.message : "Erro ao carregar o logo.");
      event.target.value = "";
    }
  };

  const handleRemoveLogo = () => {
    setLogoDataUrl(null);
    setLogoName(null);
    setLogoError(null);
  };

  const handleFormSubmit = async (values: SetupFormValues) => {
    const payload: SetupInitializeRequest = {
      clientName: values.clientName,
      adminEmail: values.adminEmail.toLowerCase(),
      adminPassword: values.adminPassword,
      logo: logoDataUrl ?? null,
    };

    await onSubmit(payload);
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
      <form onSubmit={form.handleSubmit(handleFormSubmit)}>
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle className="text-xl font-semibold">Criar Tenant</CardTitle>
            <CardDescription>Registe uma nova organização com acesso à plataforma LexCV.</CardDescription>
          </div>
          <Button
            type="button"
            variant="ghost"
            onClick={onCancel}
            className="h-8 w-8 p-0 rounded-full border border-slate-200 dark:border-slate-800"
            aria-label="Fechar"
          >
            <X className="h-4 w-4" />
          </Button>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-2">
            <Label htmlFor="tenantClientName">Nome da Empresa/Cliente</Label>
            <Input
              id="tenantClientName"
              placeholder="Ex.: Sociedade Exemplo, Lda."
              aria-invalid={!!form.formState.errors.clientName}
              className="bg-slate-50 dark:bg-slate-950"
              {...form.register("clientName")}
            />
            {form.formState.errors.clientName ? (
              <p className="text-sm text-red-600">{form.formState.errors.clientName.message}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="tenantLogo">Logo (opcional)</Label>
            <input
              id="tenantLogo"
              type="file"
              accept="image/*"
              onChange={handleLogoChange}
              className="block w-full text-sm text-slate-600 dark:text-slate-400 file:mr-3 file:rounded-md file:border-0 file:bg-slate-100 file:px-3 file:py-1.5 file:text-sm file:font-medium file:text-slate-900 dark:file:bg-slate-800 dark:file:text-slate-50"
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">PNG, JPG, WEBP até 2MB</p>
            {logoDataUrl ? (
              <div className="flex items-center gap-3 rounded-md border border-slate-200 p-3 dark:border-slate-800">
                <Image
                  src={logoDataUrl}
                  alt="Pré-visualização do logo"
                  width={48}
                  height={48}
                  unoptimized
                  className="h-12 w-12 object-contain"
                />
                <span className="flex-1 truncate text-xs text-slate-500 dark:text-slate-400">{logoName}</span>
                <Button type="button" variant="ghost" size="sm" onClick={handleRemoveLogo} className="text-xs">
                  Remover
                </Button>
              </div>
            ) : null}
            {logoError ? <p className="text-sm text-red-600">{logoError}</p> : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="tenantAdminEmail">Email do Administrador</Label>
            <Input
              id="tenantAdminEmail"
              type="email"
              placeholder="admin@empresa.cv"
              aria-invalid={!!form.formState.errors.adminEmail}
              className="bg-slate-50 dark:bg-slate-950"
              {...form.register("adminEmail")}
            />
            {form.formState.errors.adminEmail ? (
              <p className="text-sm text-red-600">{form.formState.errors.adminEmail.message}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="tenantAdminPassword">Password</Label>
            <Input
              id="tenantAdminPassword"
              type="password"
              aria-invalid={!!form.formState.errors.adminPassword}
              className="bg-slate-50 dark:bg-slate-950"
              {...form.register("adminPassword")}
            />
            {form.formState.errors.adminPassword ? (
              <p className="text-sm text-red-600">{form.formState.errors.adminPassword.message}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="tenantConfirmPassword">Confirmar Password</Label>
            <Input
              id="tenantConfirmPassword"
              type="password"
              aria-invalid={!!form.formState.errors.confirmPassword}
              className="bg-slate-50 dark:bg-slate-950"
              {...form.register("confirmPassword")}
            />
            {form.formState.errors.confirmPassword ? (
              <p className="text-sm text-red-600">{form.formState.errors.confirmPassword.message}</p>
            ) : null}
          </div>
        </CardContent>
        <CardFooter className="flex justify-end gap-3 border-t border-slate-200 pt-6 dark:border-slate-800">
          <Button
            type="button"
            variant="outline"
            onClick={onCancel}
            className="border-slate-200 dark:border-slate-700"
          >
            Cancelar
          </Button>
          <Button
            type="submit"
            className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
            disabled={isSubmitting}
          >
            {isSubmitting ? "A criar..." : "Criar Tenant"}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
