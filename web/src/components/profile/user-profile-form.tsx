"use client";

import * as React from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Camera, Loader2, Save, X } from "lucide-react";

import { useMe } from "@/hooks/use-me";
import { apiFetch } from "@/lib/api";
import { toast } from "@/hooks/use-toast";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from "@/components/ui/card";
import type { MeResponse } from "@/types/auth";

const profileSchema = z.object({
  nome: z.string().min(2, "Nome deve ter pelo menos 2 caracteres"),
  email: z.string().email("Email inválido"),
  telefone: z.string().optional(),
  avatar_url: z.string().optional(),
});

type ProfileFormValues = z.infer<typeof profileSchema>;

export function UserProfileForm() {
  const { data: me, isPending: isPendingMe } = useMe();
  const queryClient = useQueryClient();
  const fileInputRef = React.useRef<HTMLInputElement>(null);

  const form = useForm<ProfileFormValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: {
      nome: "",
      email: "",
      telefone: "",
      avatar_url: "",
    },
  });

  // Reset form when user data loads
  React.useEffect(() => {
    if (me) {
      form.reset({
        nome: me.nome || "",
        email: me.email || "",
        telefone: me.telefone || "",
        avatar_url: me.avatar_url || "",
      });
    }
  }, [me, form]);

  const mutation = useMutation({
    mutationFn: (values: ProfileFormValues) => {
      return apiFetch<MeResponse>("/auth/me", {
        method: "PUT",
        body: JSON.stringify(values),
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["auth", "me"] });
      toast.success("Informações atualizadas com sucesso.");
    },
    onError: (error: unknown) => {
      toast.error(error instanceof Error ? error.message : "Erro ao atualizar informações");
    }
  });

  const onSubmit = (values: ProfileFormValues) => {
    mutation.mutate(values);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      // Create a local object URL to simulate an upload
      const objectUrl = URL.createObjectURL(file);
      form.setValue("avatar_url", objectUrl, { shouldDirty: true });
    }
  };

  if (isPendingMe) {
    return (
      <div className="flex justify-center items-center h-48">
        <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
      </div>
    );
  }

  const currentAvatarUrl = form.watch("avatar_url") || me?.avatar_url;

  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <form onSubmit={form.handleSubmit(onSubmit)}>
        <CardHeader>
          <CardTitle className="text-xl font-semibold text-slate-900 dark:text-slate-100">Informações Pessoais</CardTitle>
          <CardDescription className="text-slate-500 dark:text-slate-400">
            Atualize a sua foto e os seus dados de contacto.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex flex-col sm:flex-row gap-6 items-start sm:items-center pb-6 border-b border-slate-200 dark:border-slate-800">
            <div className="relative group cursor-pointer" onClick={() => fileInputRef.current?.click()}>
              <div className="h-24 w-24 rounded-full overflow-hidden bg-slate-100 dark:bg-slate-800 border-2 border-slate-200 dark:border-slate-700 flex items-center justify-center text-slate-400">
                {currentAvatarUrl ? (
                  <img src={currentAvatarUrl} alt="Avatar" className="h-full w-full object-cover" />
                ) : (
                  <span className="text-2xl font-bold uppercase">
                    {(form.watch("nome") || me?.nome || "U").substring(0, 2)}
                  </span>
                )}
              </div>
              <div className="absolute inset-0 bg-black/40 flex items-center justify-center rounded-full opacity-0 group-hover:opacity-100 transition-opacity">
                <Camera className="h-6 w-6 text-white" />
              </div>
              <input
                type="file"
                ref={fileInputRef}
                className="hidden"
                accept="image/*"
                onChange={handleFileChange}
              />
            </div>
            <div>
              <h3 className="text-sm font-medium text-slate-900 dark:text-slate-100">Foto de Perfil</h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                Clique no avatar para alterar. Recomendado 256x256px.
              </p>
            </div>
          </div>

          <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="nome">Nome Completo</Label>
              <Input
                id="nome"
                {...form.register("nome")}
                className="bg-slate-50 dark:bg-slate-950 max-sm:h-12 max-sm:text-base"
              />
              {form.formState.errors.nome && (
                <p className="text-xs text-red-500">{form.formState.errors.nome.message}</p>
              )}
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input
                id="email"
                type="email"
                {...form.register("email")}
                className="bg-slate-50 dark:bg-slate-950 max-sm:h-12 max-sm:text-base"
              />
              {form.formState.errors.email && (
                <p className="text-xs text-red-500">{form.formState.errors.email.message}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label htmlFor="telefone">Telefone</Label>
              <Input
                id="telefone"
                {...form.register("telefone")}
                placeholder="+238 000 0000"
                className="bg-slate-50 dark:bg-slate-950 max-sm:h-12 max-sm:text-base"
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="role">Função (Role)</Label>
              <Input
                id="role"
                value={me?.roles?.join(", ") || ""}
                disabled
                className="bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 cursor-not-allowed"
              />
            </div>
          </div>
        </CardContent>
        <CardFooter className="flex justify-end gap-3 pt-6 border-t border-slate-200 dark:border-slate-800">
          <Button
            type="button"
            variant="outline"
            onClick={() => form.reset()}
            disabled={!form.formState.isDirty || mutation.isPending}
            className="border-slate-200 dark:border-slate-700"
          >
            <X className="h-4 w-4" />
            Cancelar
          </Button>
          <Button
            type="submit"
            disabled={!form.formState.isDirty || mutation.isPending}
            className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm max-sm:min-h-[48px]"
          >
            {mutation.isPending ? (
              <Loader2 className="h-4 w-4 mr-2 animate-spin" />
            ) : (
              <Save className="h-4 w-4 mr-2" />
            )}
            Guardar Alterações
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
