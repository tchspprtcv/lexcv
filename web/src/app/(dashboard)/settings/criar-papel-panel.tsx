"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { X } from "lucide-react";
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
import { criarPapelSchema, type CriarPapelFormValues } from "@/schemas/papeis-escritorio";
import type { OfficePermissao, PapelCreateRequest } from "@/types/office-rbac";

/**
 * Painel inline de criacao de papel de escritorio -- mesmo padrao de
 * `CriarMoldePanel` (plataforma/moldes): troca inline do Card principal
 * (ver page.tsx), sem ser um modal.
 *
 * Puramente de apresentacao e validacao. Quem detem a mutacao, o toast de
 * sucesso/erro e o fecho do painel e o chamador (RbacTab em page.tsx),
 * atraves das props `onSubmit`/`isSubmitting` -- este componente nao chama
 * nenhum hook de mutacao nem faz nenhum pedido de rede.
 */
export function CriarPapelPanel({
  onCancel,
  onSubmit,
  isSubmitting,
  permissoes,
}: {
  onCancel: () => void;
  onSubmit: (payload: PapelCreateRequest) => void | Promise<void>;
  isSubmitting: boolean;
  permissoes: OfficePermissao[];
}) {
  const form = useForm<CriarPapelFormValues>({
    resolver: zodResolver(criarPapelSchema),
    defaultValues: {
      nome: "",
      permissoes: [],
    },
  });

  const permissoesSelecionadas = form.watch("permissoes");

  const togglePermissao = (key: string) => {
    const atual = form.getValues("permissoes");
    const seguinte = atual.includes(key) ? atual.filter((k) => k !== key) : [...atual, key];
    form.setValue("permissoes", seguinte, { shouldDirty: true });
  };

  const modulos = Array.from(new Set(permissoes.map((p) => p.modulo)));

  const handleFormSubmit = async (values: CriarPapelFormValues) => {
    const payload: PapelCreateRequest = {
      nome: values.nome,
      permissoes: values.permissoes,
    };

    await onSubmit(payload);
  };

  return (
    <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
      <form onSubmit={form.handleSubmit(handleFormSubmit)}>
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle className="text-xl font-semibold">Criar Papel</CardTitle>
            <CardDescription>
              Defina o nome e as permissões iniciais deste papel. Fica disponível de imediato
              para atribuir a utilizadores do seu escritório.
            </CardDescription>
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
            <Label htmlFor="papelNome">Nome do Papel</Label>
            <Input
              id="papelNome"
              placeholder="Ex.: Recepção"
              aria-invalid={!!form.formState.errors.nome}
              className="bg-slate-50 dark:bg-slate-950"
              {...form.register("nome")}
            />
            <p className="text-xs text-slate-500 dark:text-slate-400">
              Escolha um nome claro para a sua equipa — pode alterá-lo mais tarde.
            </p>
            {form.formState.errors.nome ? (
              <p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>
            ) : null}
          </div>

          <div className="space-y-2">
            <Label>Permissões</Label>
            <div className="grid gap-3 grid-cols-1 max-h-72 overflow-y-auto p-3 border border-slate-200 dark:border-slate-800 rounded-md bg-slate-50/30 dark:bg-slate-950/10">
              {modulos.map((mod) => (
                <div key={mod} className="space-y-2">
                  <div className="text-xs font-bold text-slate-500 uppercase tracking-widest">
                    {mod}
                  </div>
                  {permissoes
                    .filter((p) => p.modulo === mod)
                    .map((perm) => {
                      const isChecked = permissoesSelecionadas.includes(perm.key);
                      return (
                        <div
                          key={perm.key}
                          onClick={() => togglePermissao(perm.key)}
                          className={`flex items-start gap-3 p-2 border rounded-md cursor-pointer transition-all ${
                            isChecked
                              ? "border-blue-500/50 bg-blue-500/5 dark:bg-blue-500/10 text-slate-900 dark:text-slate-100"
                              : "border-transparent hover:bg-slate-100/50 dark:hover:bg-slate-900/30 text-slate-600 dark:text-slate-400"
                          }`}
                        >
                          <input
                            type="checkbox"
                            checked={isChecked}
                            onChange={() => {}}
                            aria-label={perm.nome}
                            className="mt-0.5 text-blue-600 focus:ring-blue-500 rounded h-3.5 w-3.5 pointer-events-none"
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
              ))}
            </div>
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
            {isSubmitting ? "A criar..." : "Criar Papel"}
          </Button>
        </CardFooter>
      </form>
    </Card>
  );
}
