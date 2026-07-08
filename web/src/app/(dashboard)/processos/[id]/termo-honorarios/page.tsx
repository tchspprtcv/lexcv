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

function TermoHonorarios({
  processo: _processo,
  cliente: _cliente,
  honorario: _honorario,
  tenantNome: _tenantNome,
}: {
  processo: Processo;
  cliente: Cliente;
  honorario: Honorario;
  tenantNome: string;
}) {
  // Implemented in Task 2 (document body: SectionTitle/Field sections).
  return null;
}
