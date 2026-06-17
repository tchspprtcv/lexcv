"use client";

import * as React from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useMutation } from "@tanstack/react-query";
import { Loader2, ShieldCheck, KeyRound } from "lucide-react";

import { apiFetch } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, "Palavra-passe atual é obrigatória"),
    newPassword: z.string().min(6, "A nova palavra-passe deve ter pelo menos 6 caracteres"),
    confirmPassword: z.string().min(1, "Confirmação da palavra-passe é obrigatória"),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "As palavras-passe não coincidem",
    path: ["confirmPassword"],
  });

type PasswordFormValues = z.infer<typeof passwordSchema>;

export function UserPasswordForm() {
  const [successMsg, setSuccessMsg] = React.useState<string | null>(null);
  const [errorMsg, setErrorMsg] = React.useState<string | null>(null);

  const form = useForm<PasswordFormValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: {
      currentPassword: "",
      newPassword: "",
      confirmPassword: "",
    },
  });

  const mutation = useMutation({
    mutationFn: (values: PasswordFormValues) => {
      return apiFetch<{ message: string }>("/auth/change-password", {
        method: "POST",
        body: JSON.stringify({
          currentPassword: values.currentPassword,
          newPassword: values.newPassword,
        }),
      });
    },
    onSuccess: (data) => {
      setSuccessMsg(data.message || "Palavra-passe alterada com sucesso!");
      setErrorMsg(null);
      form.reset();
    },
    onError: (error: unknown) => {
      let msg = "Erro ao alterar palavra-passe. Tente novamente.";
      if (error instanceof Error) {
        msg = error.message.replace("API 400: ", "");
      }
      setErrorMsg(msg);
      setSuccessMsg(null);
    },
  });

  const onSubmit = (values: PasswordFormValues) => {
    mutation.mutate(values);
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <form onSubmit={form.handleSubmit(onSubmit)}>
        <CardHeader>
          <CardTitle className="text-xl font-semibold text-slate-900 dark:text-slate-100 flex items-center gap-2">
            <KeyRound className="h-5 w-5 text-blue-500" />
            Alterar Palavra-passe
          </CardTitle>
          <CardDescription className="text-slate-500 dark:text-slate-400">
            Garanta a segurança da sua conta alterando a palavra-passe periodicamente.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {successMsg && (
            <div className="p-3 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 text-sm border border-emerald-500/20 rounded-md flex items-center gap-2 animate-fade-in">
              <ShieldCheck className="h-4 w-4" />
              {successMsg}
            </div>
          )}

          {errorMsg && (
            <div className="p-3 bg-red-500/10 text-red-600 dark:text-red-400 text-sm border border-red-500/20 rounded-md">
              {errorMsg}
            </div>
          )}

          <div className="space-y-2">
            <Label htmlFor="currentPassword">Palavra-passe Atual</Label>
            <Input
              id="currentPassword"
              type="password"
              {...form.register("currentPassword")}
              className="bg-slate-50 dark:bg-slate-950"
              placeholder="Digite a palavra-passe atual"
            />
            {form.formState.errors.currentPassword && (
              <p className="text-xs text-red-500">{form.formState.errors.currentPassword.message}</p>
            )}
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="newPassword">Nova Palavra-passe</Label>
              <Input
                id="newPassword"
                type="password"
                {...form.register("newPassword")}
                className="bg-slate-50 dark:bg-slate-950"
                placeholder="Mínimo 6 caracteres"
              />
              {form.formState.errors.newPassword && (
                <p className="text-xs text-red-500">{form.formState.errors.newPassword.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirmPassword">Confirmar Nova Palavra-passe</Label>
              <Input
                id="confirmPassword"
                type="password"
                {...form.register("confirmPassword")}
                className="bg-slate-50 dark:bg-slate-950"
                placeholder="Confirme a nova palavra-passe"
              />
              {form.formState.errors.confirmPassword && (
                <p className="text-xs text-red-500">{form.formState.errors.confirmPassword.message}</p>
              )}
            </div>
          </div>
        </CardContent>
        <CardFooter className="flex justify-end gap-3 pt-6 border-t border-slate-200 dark:border-slate-800">
          <Button
            type="button"
            variant="outline"
            onClick={() => {
              form.reset();
              setErrorMsg(null);
              setSuccessMsg(null);
            }}
            disabled={mutation.isPending}
            className="border-slate-200 dark:border-slate-700"
          >
            Limpar
          </Button>
          <Button 
            type="submit" 
            disabled={mutation.isPending}
            className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm"
          >
            {mutation.isPending ? (
              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
            ) : (
              "Atualizar Palavra-passe"
            )}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
