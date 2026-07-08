"use client";

import Link from "next/link";
import * as React from "react";
import { Printer } from "lucide-react";

import { Button } from "@/components/ui/button";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { useCliente } from "@/hooks/use-clientes";
import { useHonorarios } from "@/hooks/use-financeiro";
import { useMe } from "@/hooks/use-me";
import { usePermissions } from "@/hooks/use-permissions";
import { useProcesso } from "@/hooks/use-processos";
import { origemProcessoToLabel } from "@/lib/origem-processo";
import type { Cliente } from "@/types/clientes";
import type { Honorario } from "@/types/financeiro";
import type { Processo } from "@/types/processos";

type PageProps = {
  params: Promise<{ id: string }>;
};

const PRINT_CSS = `
  @media print {
    aside, header, [data-print-hide], .bottom-nav, .ficha-print-btn {
      display: none !important;
    }
    body {
      background: white !important;
    }
  }
  @page {
    size: A4;
    margin: 2cm;
  }
`;

const BLANK = "___________";

function fmt(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === "") return BLANK;
  return String(value);
}

function fmtMoney(value: number | null | undefined): string {
  if (value === null || value === undefined) return BLANK;
  return value.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

export default function TermoHonorariosPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canViewProcessos = permissions.can.view("processos");

  if (!permissions.isLoading && !canViewProcessos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este processo."
        backHref="/processos"
      />
    );
  }

  return <TermoHonorariosContent id={id} />;
}

function TermoHonorariosContent({ id }: { id: string }) {
  const processo = useProcesso(id);
  const cliente = useCliente(processo.data?.cliente_id ?? "");
  const honorarios = useHonorarios({ processoId: id });
  const me = useMe();

  const honorario = honorarios.data?.[0];

  const isLoading = processo.isLoading || cliente.isLoading || honorarios.isLoading;
  const isError = processo.isError || cliente.isError || honorarios.isError;
  const isBlocked = !honorario || honorario.valorTotal === null;

  return (
    <div>
      {/* eslint-disable-next-line react/no-danger */}
      <style dangerouslySetInnerHTML={{ __html: PRINT_CSS }} />

      <div className="flex items-center justify-between gap-4 mb-6" data-print-hide>
        <Link href={`/processos/${encodeURIComponent(id)}`} className="text-sm hover:underline">
          &larr; Voltar ao processo
        </Link>
        <Button
          type="button"
          className="ficha-print-btn rounded-none"
          data-print-hide
          disabled={isBlocked}
          onClick={() => window.print()}
        >
          <Printer className="h-4 w-4 mr-2" />
          Imprimir
        </Button>
      </div>

      {isBlocked && honorario ? (
        <p className="text-sm text-red-600 mt-2" data-print-hide>
          O valor dos honorários ainda não foi preenchido. Preencha o valor em{" "}
          <Link href={`/financeiro/${honorario.id}`} className="text-blue-600 hover:underline">
            Financeiro
          </Link>{" "}
          antes de gerar o termo.
        </p>
      ) : null}

      {isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : isError ? (
        <div className="text-sm text-red-600">Erro ao carregar o termo.</div>
      ) : !honorario ? (
        <div className="text-sm text-red-600">
          Não foi possível gerar o termo: nenhum honorário associado a este processo.
        </div>
      ) : (
        <TermoHonorarios
          processo={processo.data!}
          cliente={cliente.data!}
          honorario={honorario}
          tenantNome={me.data?.tenant_nome ?? "LexCV"}
        />
      )}
    </div>
  );
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return (
    <h2 className="uppercase font-semibold text-sm border-b border-gray-300 pb-1 mb-3 mt-6">
      {children}
    </h2>
  );
}

function Field({ label, value }: { label: string; value: string }) {
  const isBlank = value === BLANK;
  return (
    <div className="grid grid-cols-2 gap-2 py-1 text-sm">
      <span className="text-gray-600">{label}</span>
      <span className={isBlank ? "font-mono underline" : ""}>{value}</span>
    </div>
  );
}

function TermoHonorarios({
  processo,
  cliente,
  honorario,
  tenantNome,
}: {
  processo: Processo;
  cliente: Cliente;
  honorario: Honorario;
  tenantNome: string;
}) {
  const dataFormatada = new Date().toLocaleDateString("pt-CV", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });

  return (
    <div className="max-w-[210mm] mx-auto p-8 bg-white text-black">
      <div className="text-center mb-6">
        <div className="font-bold text-lg">{tenantNome}</div>
        <div className="font-semibold text-base uppercase mt-1">Termo de Honorários</div>
        <div className="text-xs text-gray-600 mt-1">{dataFormatada}</div>
      </div>

      <SectionTitle>Identificação do Cliente</SectionTitle>
      <Field label="Nome" value={fmt(cliente.nome)} />
      <Field label="NIF" value={fmt(cliente.nif)} />
      <Field label="Morada" value={fmt(cliente.morada)} />

      <SectionTitle>Identificação do Processo</SectionTitle>
      <Field label="Número" value={fmt(processo.numero)} />
      <Field label="Tipo" value={fmt(processo.tipo_processo)} />
      <Field label="Tribunal" value={fmt(processo.tribunal)} />
      <Field label="Juízo" value={fmt(processo.juizo)} />
      <Field
        label="Origem"
        value={fmt(processo.origem ? origemProcessoToLabel(processo.origem) : undefined)}
      />

      <SectionTitle>Honorários</SectionTitle>
      <Field label="Valor Total" value={fmtMoney(honorario.valorTotal)} />
      <Field label="Total Pago" value={fmtMoney(honorario.totalPago)} />
      <Field label="Data do Acordo" value={fmt(honorario.dataAcordo)} />
      <Field label="Descrição" value={fmt(honorario.descricao)} />

      <SectionTitle>Data e Assinaturas</SectionTitle>
      <div className="text-sm mb-6">Data: ___/___/______</div>
      <div className="grid grid-cols-2 gap-8 mt-8">
        <div>
          <div className="border-b border-black min-w-[200px] h-8" />
          <div className="text-center text-sm mt-2">O Advogado</div>
        </div>
        <div>
          <div className="border-b border-black min-w-[200px] h-8" />
          <div className="text-center text-sm mt-2">O Cliente</div>
        </div>
      </div>
    </div>
  );
}
