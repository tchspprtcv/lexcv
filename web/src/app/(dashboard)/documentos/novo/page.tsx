"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import * as React from "react";
import { useForm } from "react-hook-form";
import { ArrowLeft, Upload, X } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { NativeSelect } from "@/components/ui/native-select";
import { Progress } from "@/components/ui/progress";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { FileDropZone } from "@/components/shared/file-drop-zone";
import { useUploadDocumentoComProgresso } from "@/hooks/use-documentos";
import { toast } from "@/hooks/use-toast";
import { usePermissions } from "@/hooks/use-permissions";
import { documentoUploadFormSchema, type DocumentoUploadFormValues } from "@/schemas/documentos";

function createFileList(file: File): FileList {
  const dt = new DataTransfer();
  dt.items.add(file);
  return dt.files;
}

type PreVisualizacao = {
  tipo: "imagem" | "pdf" | "outro";
  url?: string;
  nome: string;
  tamanho: number;
};

export default function DocumentoUploadPage() {
  const router = useRouter();
  const permissions = usePermissions();
  const [serverError, setServerError] = React.useState<string | null>(null);
  const [progresso, setProgresso] = React.useState<number | null>(null);
  const [preVisualizacao, setPreVisualizacao] = React.useState<PreVisualizacao | null>(null);
  const canCreateDocumentos = permissions.can.create("documentos");

  const upload = useUploadDocumentoComProgresso({ onProgress: (pct) => setProgresso(pct) });

  const form = useForm<DocumentoUploadFormValues>({
    resolver: zodResolver(documentoUploadFormSchema),
    defaultValues: { nome: "", tipo: "", processo_id: "", cliente_id: "", confidencialidade: "PUBLICO", replace_id: "" },
  });

  const previewUrlRef = React.useRef<string | undefined>(undefined);

  React.useEffect(() => {
    return () => {
      if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
    };
  }, []); // empty deps — runs cleanup only on unmount

  const handleFicheiroSelecionado = (file: File) => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = undefined;
    }
    form.setValue("file", createFileList(file), { shouldValidate: true });

    let tipo: "imagem" | "pdf" | "outro";
    if (file.type.startsWith("image/")) {
      tipo = "imagem";
    } else if (file.type === "application/pdf") {
      tipo = "pdf";
    } else {
      tipo = "outro";
    }

    const url = tipo !== "outro" ? URL.createObjectURL(file) : undefined;
    previewUrlRef.current = url;
    setPreVisualizacao({ tipo, url, nome: file.name, tamanho: file.size });
  };

  const handleFicheiroLimpo = () => {
    if (previewUrlRef.current) {
      URL.revokeObjectURL(previewUrlRef.current);
      previewUrlRef.current = undefined;
    }
    setPreVisualizacao(null);
    form.setValue("file", undefined as unknown as FileList, { shouldValidate: true });
  };

  const onSubmit = async (values: DocumentoUploadFormValues) => {
    setServerError(null);
    const file = values.file.item(0);
    if (!file) return;
    if (!canCreateDocumentos) {
      setServerError("Não tem permissão para enviar documentos");
      return;
    }

    try {
      const res = await upload.mutateAsync({
        file,
        nome: values.nome,
        tipo: values.tipo,
        processo_id: values.processo_id,
        cliente_id: values.cliente_id,
        confidencialidade: values.confidencialidade,
        replace_id: values.replace_id,
      });
      setProgresso(null);
      if (previewUrlRef.current) {
        URL.revokeObjectURL(previewUrlRef.current);
        previewUrlRef.current = undefined;
      }
      toast.success("Documento enviado com sucesso.");
      router.push(`/documentos/${encodeURIComponent(res.id)}`);
    } catch (e) {
      setProgresso(null);
      const msg = e instanceof Error ? e.message : "Erro ao fazer upload";
      setServerError(msg);
      toast.error(msg);
    }
  };

  if (permissions.isFetched && !canCreateDocumentos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para enviar documentos."
        backHref="/documentos"
      />
    );
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold">Upload de documento</h1>
          <p className="text-sm text-neutral-500 dark:text-neutral-400">
            Enviar um ficheiro e associá-lo opcionalmente a um processo/cliente.
          </p>
        </div>

        <Button asChild variant="outline">
          <Link href="/documentos">
            <ArrowLeft className="h-4 w-4" />
            Voltar
          </Link>
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Dados</CardTitle>
        </CardHeader>
        <CardContent>
          {/* handleSubmit(onSubmit) is called inside this wrapper (not inline as the onSubmit
              value itself) so it runs at submit time, not at render time — onSubmit reads
              previewUrlRef.current, and calling it eagerly during render would violate
              react-hooks/refs ("Cannot access refs during render"). */}
          <form className="space-y-4" onSubmit={(event) => form.handleSubmit(onSubmit)(event)}>
            <div className="space-y-2">
              <Label>Ficheiro</Label>
              <FileDropZone
                onFileChange={handleFicheiroSelecionado}
                onClear={handleFicheiroLimpo}
                accept="image/*,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document,.txt"
                disabled={form.formState.isSubmitting || upload.isPending}
              >
                Arraste um ficheiro para aqui ou clique para selecionar
              </FileDropZone>

              {preVisualizacao?.tipo === "imagem" && preVisualizacao.url ? (
                <img
                  src={preVisualizacao.url}
                  alt="Pré-visualização"
                  className="mt-2 max-h-48 rounded border object-contain"
                />
              ) : preVisualizacao?.tipo === "pdf" && preVisualizacao.url ? (
                <iframe
                  src={preVisualizacao.url}
                  title="Pré-visualização PDF"
                  className="mt-2 h-72 w-full rounded border"
                />
              ) : preVisualizacao?.tipo === "outro" ? (
                <p className="mt-2 text-sm text-neutral-600 dark:text-neutral-400">
                  {preVisualizacao.nome} ({(preVisualizacao.tamanho / 1024).toFixed(1)} KB)
                </p>
              ) : null}

              {progresso !== null ? (
                <div className="space-y-1">
                  <div className="flex justify-between text-xs text-neutral-500">
                    <span>A enviar...</span>
                    <span>{progresso}%</span>
                  </div>
                  <Progress value={progresso ?? 0} />
                </div>
              ) : null}

              {form.formState.errors.file ? (
                <p className="text-sm text-red-600">{form.formState.errors.file.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="nome">Nome (opcional)</Label>
              <Input id="nome" {...form.register("nome")} placeholder="Ex.: Procuração" />
              {form.formState.errors.nome ? (
                <p className="text-sm text-red-600">{form.formState.errors.nome.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="tipo">Tipo (opcional)</Label>
              <Input id="tipo" {...form.register("tipo")} placeholder="Ex.: PDF / Contrato" />
              {form.formState.errors.tipo ? (
                <p className="text-sm text-red-600">{form.formState.errors.tipo.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="processo_id">Processo ID (opcional)</Label>
              <Input id="processo_id" {...form.register("processo_id")} />
              {form.formState.errors.processo_id ? (
                <p className="text-sm text-red-600">{form.formState.errors.processo_id.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="cliente_id">Cliente ID (opcional)</Label>
              <Input id="cliente_id" {...form.register("cliente_id")} />
              {form.formState.errors.cliente_id ? (
                <p className="text-sm text-red-600">{form.formState.errors.cliente_id.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="confidencialidade">Confidencialidade</Label>
              <NativeSelect id="confidencialidade" size="default" className="w-full" {...form.register("confidencialidade")}>
                <option value="PUBLICO">Público</option>
                <option value="INTERNO">Interno</option>
                <option value="CONFIDENCIAL">Confidencial</option>
                <option value="RESTRITO">Restrito</option>
              </NativeSelect>
              {form.formState.errors.confidencialidade ? (
                <p className="text-sm text-red-600">{form.formState.errors.confidencialidade.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="replace_id">ID a substituir (opcional, para Nova Versão)</Label>
              <Input id="replace_id" {...form.register("replace_id")} placeholder="UUID do documento a substituir" />
              {form.formState.errors.replace_id ? (
                <p className="text-sm text-red-600">{form.formState.errors.replace_id.message}</p>
              ) : null}
            </div>

            {serverError ? <p className="text-sm text-red-600">{serverError}</p> : null}

            <div className="flex gap-2">
              <Button
                type="submit"
                disabled={form.formState.isSubmitting || upload.isPending || permissions.isLoading || !canCreateDocumentos}
              >
                <Upload className="h-4 w-4" />
                {form.formState.isSubmitting || upload.isPending ? "A enviar..." : "Enviar"}
              </Button>
              <Button asChild type="button" variant="outline">
                <Link href="/documentos">
                  <X className="h-4 w-4" />
                  Cancelar
                </Link>
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
