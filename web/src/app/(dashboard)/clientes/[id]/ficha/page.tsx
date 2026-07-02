"use client";

import Link from "next/link";
import * as React from "react";
import { Printer } from "lucide-react";

import { Button } from "@/components/ui/button";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import {
  useCliente,
  useClienteAdministrativos,
  useClienteAdvogados,
} from "@/hooks/use-clientes";
import { useMe } from "@/hooks/use-me";
import { usePermissions } from "@/hooks/use-permissions";
import type { Cliente } from "@/types/clientes";

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

export default function FichaPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canViewClientes = permissions.can.view("clientes");

  if (!permissions.isLoading && !canViewClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar a ficha deste cliente."
        backHref="/clientes"
      />
    );
  }

  return <FichaContent id={id} />;
}

function FichaContent({ id }: { id: string }) {
  const cliente = useCliente(id);
  const me = useMe();
  const advogados = useClienteAdvogados(id);
  const administrativos = useClienteAdministrativos(id);

  const isLoading = cliente.isLoading;
  const isError = cliente.isError;

  const dataFormatada = new Date().toLocaleDateString("pt-CV", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  });

  return (
    <div>
      {/* eslint-disable-next-line react/no-danger */}
      <style dangerouslySetInnerHTML={{ __html: PRINT_CSS }} />

      <div className="flex items-center justify-between gap-4 mb-6" data-print-hide>
        <Link href={`/clientes/${encodeURIComponent(id)}`} className="text-sm hover:underline">
          &larr; Voltar ao cliente
        </Link>
        <Button
          type="button"
          className="ficha-print-btn"
          data-print-hide
          onClick={() => window.print()}
        >
          <Printer className="h-4 w-4 mr-2" />
          Imprimir
        </Button>
      </div>

      {isLoading ? (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
      ) : isError ? (
        <div className="text-sm text-red-600">
          {cliente.error instanceof Error ? cliente.error.message : "Erro ao carregar ficha."}
        </div>
      ) : cliente.data ? (
        <Ficha
          cliente={cliente.data}
          tenantNome={me.data?.tenant_nome ?? "LexCV"}
          dataFormatada={dataFormatada}
          advogadosNomes={(advogados.data ?? []).map((a) => a.nome).join(", ")}
          administrativosNomes={(administrativos.data ?? []).map((a) => a.nome).join(", ")}
        />
      ) : (
        <div className="text-sm text-neutral-500 dark:text-neutral-400">Cliente não encontrado.</div>
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

function Ficha({
  cliente,
  tenantNome,
  dataFormatada,
  advogadosNomes,
  administrativosNomes,
}: {
  cliente: Cliente;
  tenantNome: string;
  dataFormatada: string;
  advogadosNomes: string;
  administrativosNomes: string;
}) {
  const isEmpresa = cliente.tipo === "EMPRESA";

  const idade = cliente.idade;
  const sexo = cliente.sexo;
  const nacionalidade = cliente.nacionalidade;

  const documentosEntregues =
    cliente.documentos_entregues && cliente.documentos_entregues.length > 0
      ? cliente.documentos_entregues.map((d) => d.descricao).join(", ")
      : undefined;
  const documentosATratar =
    cliente.documentos_a_tratar && cliente.documentos_a_tratar.length > 0
      ? cliente.documentos_a_tratar.map((d) => d.descricao).join(", ")
      : undefined;
  const deslocacoes =
    cliente.deslocacoes && cliente.deslocacoes.length > 0
      ? cliente.deslocacoes.map((d) => d.descricao).join(", ")
      : undefined;

  const honorariosTotal = cliente.honorarios_propostos?.total;
  const honorariosPorExtenso = cliente.honorarios_propostos?.totalPorExtenso;
  const honorariosPrevisao = cliente.honorarios_propostos?.previsao;

  return (
    <div className="max-w-[210mm] mx-auto p-8 bg-white text-black">
      <div className="text-center mb-6">
        <div className="font-bold text-lg">{tenantNome}</div>
        <div className="font-semibold text-base uppercase mt-1">Ficha Cliente</div>
        <div className="text-xs text-gray-600 mt-1">{dataFormatada}</div>
      </div>

      <SectionTitle>Identificação</SectionTitle>
      <Field label="Cliente Nº" value={fmt(cliente.numero_cliente)} />
      <Field label="Nome" value={fmt(cliente.nome)} />
      <Field label="Tipo" value={fmt(cliente.tipo)} />
      <Field label="BI/Pass. Nº" value={fmt(cliente.documento_numero ?? cliente.documentoNumero)} />
      <Field label="Tipo Doc." value={fmt(cliente.documento_tipo ?? cliente.documentoTipo)} />
      {isEmpresa ? (
        <>
          <Field label="NIF" value={fmt(cliente.nif)} />
          <Field label="Nome Comercial" value={fmt(undefined)} />
          <Field label="Sede" value={fmt(undefined)} />
          <Field label="Representante Legal" value={fmt(undefined)} />
          <Field label="Cargo" value={fmt(undefined)} />
        </>
      ) : (
        <>
          <Field label="Idade" value={fmt(idade)} />
          <Field label="Sexo" value={fmt(sexo)} />
          <Field label="Nacionalidade" value={fmt(nacionalidade)} />
        </>
      )}

      <SectionTitle>Contactos</SectionTitle>
      <Field label="Morada" value={fmt(cliente.morada)} />
      <Field label="Localidade" value={fmt(cliente.localidade)} />
      <Field label="Telefone" value={fmt(cliente.telefone)} />
      <Field label="Email" value={fmt(cliente.email)} />

      <SectionTitle>Descrição do Caso</SectionTitle>
      <div className={`text-sm min-h-16 ${cliente.descricao_caso ? "" : "font-mono underline"}`}>
        {fmt(cliente.descricao_caso)}
      </div>

      <SectionTitle>Advogados e Administrativos</SectionTitle>
      <Field label="Advogados" value={fmt(advogadosNomes || undefined)} />
      <Field label="Administrativos" value={fmt(administrativosNomes || undefined)} />

      <SectionTitle>Documentos</SectionTitle>
      <Field label="Documentos entregues" value={fmt(documentosEntregues)} />
      <Field label="Documentos a tratar" value={fmt(documentosATratar)} />

      <SectionTitle>Deslocações</SectionTitle>
      <Field label="Deslocações a realizar" value={fmt(deslocacoes)} />

      <SectionTitle>Honorários</SectionTitle>
      <Field label="Totalidade" value={fmt(honorariosTotal)} />
      <Field label="Por extenso" value={fmt(honorariosPorExtenso)} />
      <Field label="Previsão" value={fmt(honorariosPrevisao)} />

      <SectionTitle>Data e Assinaturas</SectionTitle>
      <div className="text-sm mb-6">Data: ___/___/______</div>
      <div className="grid grid-cols-2 gap-8 mt-8">
        <div>
          <div className="border-b border-black min-w-[200px] h-8" />
          <div className="text-center text-sm mt-2">A Advogada</div>
        </div>
        <div>
          <div className="border-b border-black min-w-[200px] h-8" />
          <div className="text-center text-sm mt-2">O Cliente</div>
        </div>
      </div>
    </div>
  );
}
