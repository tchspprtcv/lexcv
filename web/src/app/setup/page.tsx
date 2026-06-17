"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ArrowRight, Building2, ShieldCheck, Upload } from "lucide-react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import * as React from "react";
import { useForm } from "react-hook-form";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { apiFetch } from "@/lib/api";
import { setupSchema, type SetupFormValues } from "@/schemas/setup";
import type { SetupInitializeRequest, SetupInitializeResponse } from "@/types/setup";

const MAX_LOGO_SIZE_BYTES = 2 * 1024 * 1024;

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ""));
    reader.onerror = () => reject(new Error("Não foi possível ler o ficheiro do logo."));
    reader.readAsDataURL(file);
  });
}

export default function SetupPage() {
  const router = useRouter();
  const [serverError, setServerError] = React.useState<string | null>(null);
  const [successMessage, setSuccessMessage] = React.useState<string | null>(null);
  const [logoFile, setLogoFile] = React.useState<File | null>(null);
  const [logoError, setLogoError] = React.useState<string | null>(null);
  const [logoPreview, setLogoPreview] = React.useState<string | null>(null);

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
    setLogoFile(null);
    setLogoError(null);
    setLogoPreview(null);

    if (!file) {
      return;
    }

    if (!file.type.startsWith("image/")) {
      setLogoError("O logo deve ser uma imagem válida.");
      event.target.value = "";
      return;
    }

    if (file.size > MAX_LOGO_SIZE_BYTES) {
      setLogoError("O logo deve ter no máximo 2MB.");
      event.target.value = "";
      return;
    }

    try {
      const preview = await readFileAsDataUrl(file);
      setLogoFile(file);
      setLogoPreview(preview);
    } catch (error) {
      setLogoError(error instanceof Error ? error.message : "Erro ao carregar o logo.");
      event.target.value = "";
    }
  };

  const onSubmit = async (values: SetupFormValues) => {
    setServerError(null);
    setSuccessMessage(null);

    try {
      const payload: SetupInitializeRequest = {
        clientName: values.clientName.trim(),
        adminEmail: values.adminEmail.trim().toLowerCase(),
        adminPassword: values.adminPassword,
        logo: logoFile ? await readFileAsDataUrl(logoFile) : null,
      };

      const response = await apiFetch<SetupInitializeResponse>("/setup/initialize", {
        method: "POST",
        body: JSON.stringify(payload),
      });

      setSuccessMessage(response.message);
      window.setTimeout(() => {
        router.replace("/login");
      }, 1200);
    } catch (error) {
      setServerError(error instanceof Error ? error.message : "Não foi possível concluir a configuração.");
    }
  };

  return (
    <div className="min-h-screen overflow-y-auto bg-white text-slate-950 dark:bg-black dark:text-slate-50">
      <div className="mx-auto flex min-h-screen max-w-7xl flex-col justify-center px-6 py-10 lg:px-10">
        <div className="mb-8 grid gap-6 border-b border-slate-200 pb-8 dark:border-slate-800 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="space-y-5">
            <div className="inline-flex items-center gap-2 border border-slate-300 px-3 py-1 text-[11px] font-bold uppercase tracking-[0.28em] text-slate-600 dark:border-slate-700 dark:text-slate-300">
              LexCV
              <span className="text-slate-400">First-Time Setup</span>
            </div>
            <div className="space-y-3">
              <h1 className="max-w-3xl text-3xl font-semibold tracking-tight sm:text-5xl">
                Configure o ambiente institucional antes do primeiro acesso.
              </h1>
              <p className="max-w-2xl text-sm leading-6 text-slate-600 dark:text-slate-400 sm:text-base">
                Este assistente cria o primeiro tenant e o utilizador administrador inicial do sistema.
                Depois da conclusão, a rota de setup fica bloqueada automaticamente.
              </p>
            </div>
          </div>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-1">
            <div className="border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-[#020617]">
              <div className="mb-3 flex items-center gap-3">
                <Building2 className="h-5 w-5 text-slate-900 dark:text-slate-100" />
                <h2 className="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
                  Tenant Inicial
                </h2>
              </div>
              <p className="text-sm leading-6 text-slate-600 dark:text-slate-400">
                O nome da empresa e o logo alimentam a identidade base do primeiro cliente/tenant.
              </p>
            </div>

            <div className="border border-slate-200 bg-slate-50 p-4 dark:border-slate-800 dark:bg-[#020617]">
              <div className="mb-3 flex items-center gap-3">
                <ShieldCheck className="h-5 w-5 text-slate-900 dark:text-slate-100" />
                <h2 className="text-sm font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
                  Admin Seguro
                </h2>
              </div>
              <p className="text-sm leading-6 text-slate-600 dark:text-slate-400">
                A palavra-passe deve ser forte. O backend grava apenas o hash seguro e impede reinicializações.
              </p>
            </div>
          </div>
        </div>

        <Card className="border-slate-200 shadow-none dark:border-slate-800">
          <CardHeader className="border-b border-slate-200 pb-5 dark:border-slate-800">
            <CardTitle className="text-2xl">Primeiros Passos</CardTitle>
            <CardDescription>
              Preencha os dados mínimos para disponibilizar o ambiente e iniciar sessão.
            </CardDescription>
          </CardHeader>
          <CardContent className="pt-6">
            <form className="grid gap-8 lg:grid-cols-[1fr_0.72fr]" onSubmit={form.handleSubmit(onSubmit)}>
              <div className="space-y-5">
                <div className="grid gap-5 md:grid-cols-2">
                  <div className="space-y-2 md:col-span-2">
                    <Label htmlFor="clientName">Nome da Empresa/Cliente</Label>
                    <Input
                      id="clientName"
                      placeholder="Ex.: Sociedade Exemplo, Lda."
                      className="h-11 rounded-none"
                      {...form.register("clientName")}
                    />
                    {form.formState.errors.clientName ? (
                      <p className="text-sm text-red-600">{form.formState.errors.clientName.message}</p>
                    ) : null}
                  </div>

                  <div className="space-y-2 md:col-span-2">
                    <Label htmlFor="logo">Logo (opcional)</Label>
                    <div className="grid gap-4 md:grid-cols-[1fr_180px]">
                      <label
                        htmlFor="logo"
                        className="flex min-h-32 cursor-pointer flex-col items-center justify-center gap-3 border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center dark:border-slate-700 dark:bg-[#020617]"
                      >
                        <Upload className="h-5 w-5 text-slate-500 dark:text-slate-400" />
                        <div className="space-y-1">
                          <div className="text-sm font-medium">Selecionar ficheiro</div>
                          <div className="text-xs text-slate-500 dark:text-slate-400">
                            PNG, JPG, WEBP até 2MB
                          </div>
                        </div>
                      </label>
                      <div className="border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-[#020617]">
                        {logoPreview ? (
                          <Image
                            src={logoPreview}
                            alt="Pré-visualização do logo"
                            width={160}
                            height={96}
                            unoptimized
                            className="h-24 w-full object-contain"
                          />
                        ) : (
                          <div className="flex h-24 items-center justify-center text-xs uppercase tracking-[0.22em] text-slate-400">
                            Sem logo
                          </div>
                        )}
                      </div>
                    </div>
                    <Input id="logo" type="file" accept="image/*" className="hidden" onChange={handleLogoChange} />
                    {logoFile ? (
                      <p className="text-xs text-slate-500 dark:text-slate-400">{logoFile.name}</p>
                    ) : null}
                    {logoError ? <p className="text-sm text-red-600">{logoError}</p> : null}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="adminEmail">Email do Administrador</Label>
                    <Input
                      id="adminEmail"
                      type="email"
                      autoComplete="email"
                      placeholder="admin@empresa.cv"
                      className="h-11 rounded-none"
                      {...form.register("adminEmail")}
                    />
                    {form.formState.errors.adminEmail ? (
                      <p className="text-sm text-red-600">{form.formState.errors.adminEmail.message}</p>
                    ) : null}
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="adminPassword">Password</Label>
                    <Input
                      id="adminPassword"
                      type="password"
                      autoComplete="new-password"
                      className="h-11 rounded-none"
                      {...form.register("adminPassword")}
                    />
                    {form.formState.errors.adminPassword ? (
                      <p className="text-sm text-red-600">{form.formState.errors.adminPassword.message}</p>
                    ) : null}
                  </div>

                  <div className="space-y-2 md:col-span-2">
                    <Label htmlFor="confirmPassword">Confirmar Password</Label>
                    <Input
                      id="confirmPassword"
                      type="password"
                      autoComplete="new-password"
                      className="h-11 rounded-none"
                      {...form.register("confirmPassword")}
                    />
                    {form.formState.errors.confirmPassword ? (
                      <p className="text-sm text-red-600">{form.formState.errors.confirmPassword.message}</p>
                    ) : null}
                  </div>
                </div>
              </div>

              <div className="space-y-5 border-l-0 border-slate-200 lg:border-l lg:pl-8 dark:border-slate-800">
                <div className="space-y-3 border border-slate-200 bg-slate-50 p-5 dark:border-slate-800 dark:bg-[#020617]">
                  <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                    Checklist
                  </div>
                  <div className="space-y-3 text-sm leading-6 text-slate-600 dark:text-slate-400">
                    <p>1. Criação do primeiro tenant institucional.</p>
                    <p>2. Criação do utilizador administrador com password hasheada.</p>
                    <p>3. Fecho definitivo do wizard após sucesso.</p>
                  </div>
                </div>

                <div className="space-y-2 border border-slate-200 p-5 dark:border-slate-800">
                  <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
                    Regras da Password
                  </div>
                  <div className="text-sm leading-6 text-slate-600 dark:text-slate-400">
                    Use pelo menos 8 caracteres com maiúscula, minúscula, número e símbolo.
                  </div>
                </div>

                {serverError ? (
                  <div className="border border-red-300 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900/60 dark:bg-red-950/40 dark:text-red-300">
                    {serverError}
                  </div>
                ) : null}

                {successMessage ? (
                  <div className="border border-emerald-300 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-900/60 dark:bg-emerald-950/40 dark:text-emerald-300">
                    {successMessage} Redirecionando para o login...
                  </div>
                ) : null}

                <Button
                  type="submit"
                  className="h-11 w-full rounded-none bg-slate-950 text-white hover:bg-slate-800 dark:bg-white dark:text-black dark:hover:bg-slate-200"
                  disabled={form.formState.isSubmitting}
                >
                  {form.formState.isSubmitting ? "A configurar ambiente..." : "Concluir configuração"}
                  <ArrowRight className="h-4 w-4" />
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
