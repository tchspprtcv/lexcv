"use client";

import Link from "next/link";
import * as React from "react";
import { ArrowLeft, ArrowUpRight, Info } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyContent, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Separator } from "@/components/ui/separator";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { podeLerDocumentosFiscais, useDocumentoFiscal } from "@/hooks/use-faturacao";
import { usePermissions } from "@/hooks/use-permissions";
import { isApiError } from "@/lib/api";
import type { DocumentoFiscalDetalhe } from "@/types/faturacao";

// Detalhe de um documento fiscal (134-UI-SPEC Surface 4; D-18, EMIS-08, EMIS-11). Só de leitura:
// mostra o snapshot gravado na emissão e os valores calculados pelo backend. Este ecrã não tem
// nenhuma ação que altere o documento. Um id de outro escritório devolve o mesmo 404 do que um
// id inexistente.

type PageProps = {
  params: Promise<{ id: string }>;
};

const NOTICE_CLASSES =
  "rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-300";
const LINK_CLASSES =
  "inline-flex items-center gap-1 text-sm font-medium text-blue-600 hover:underline dark:text-blue-400";
const AJUDA = "text-xs text-slate-500 dark:text-slate-400";

function formatarCVE(valor: number) {
  return valor.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

function formatarData(valor: string) {
  const d = new Date(valor.includes("T") ? valor : `${valor}T00:00:00`);
  if (Number.isNaN(d.getTime())) return valor;
  return d.toLocaleDateString("pt-CV");
}

function Campo({ rotulo, children, mono = false }: { rotulo: string; children: React.ReactNode; mono?: boolean }) {
  return (
    <>
      <dt className="text-sm font-semibold">{rotulo}</dt>
      <dd className={mono ? "font-mono text-sm" : "text-sm"}>{children}</dd>
    </>
  );
}

export default function DocumentoFiscalPage(props: PageProps) {
  const { id } = React.use(props.params);
  const permissions = usePermissions();
  const podeLer = podeLerDocumentosFiscais(permissions.permissions);
  const canViewClientes = permissions.can.view("clientes");
  const documento = useDocumentoFiscal(id, podeLer);

  if (!permissions.isFetched) {
    return null;
  }

  if (!podeLer) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este documento fiscal."
        backHref="/financeiro"
      />
    );
  }

  const naoEncontrado = documento.isError && isApiError(documento.error) && documento.error.status === 404;

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4">
        <Breadcrumb>
          <BreadcrumbList>
            <BreadcrumbItem>
              <BreadcrumbLink asChild>
                <Link href="/financeiro">Financeiro</Link>
              </BreadcrumbLink>
            </BreadcrumbItem>
            <BreadcrumbSeparator />
            <BreadcrumbItem>
              <BreadcrumbLink asChild>
                <Link href="/financeiro/documentos-fiscais">Documentos fiscais</Link>
              </BreadcrumbLink>
            </BreadcrumbItem>
            <BreadcrumbSeparator />
            <BreadcrumbItem>
              <BreadcrumbPage className="font-mono">{documento.data?.numeroFormatado ?? "…"}</BreadcrumbPage>
            </BreadcrumbItem>
          </BreadcrumbList>
        </Breadcrumb>
        <Button asChild variant="outline">
          <Link href="/financeiro/documentos-fiscais">
            <ArrowLeft className="h-4 w-4" />
            Voltar aos documentos fiscais
          </Link>
        </Button>
      </div>

      {naoEncontrado ? (
        <Empty>
          <EmptyHeader>
            <EmptyTitle>Documento fiscal não encontrado</EmptyTitle>
            <EmptyDescription>O documento não existe ou não está disponível.</EmptyDescription>
          </EmptyHeader>
          <EmptyContent>
            <Button asChild variant="outline">
              <Link href="/financeiro/documentos-fiscais">Voltar aos documentos fiscais</Link>
            </Button>
          </EmptyContent>
        </Empty>
      ) : documento.isError ? (
        <div className="space-y-3">
          <p className="text-sm text-red-600 dark:text-red-400">
            Não foi possível carregar o documento fiscal. Tente novamente.
          </p>
          <Button type="button" variant="outline" onClick={() => documento.refetch()}>
            Tentar novamente
          </Button>
        </div>
      ) : !documento.data ? (
        <CarregandoDocumento />
      ) : (
        <DetalheDocumento documento={documento.data} canViewClientes={canViewClientes} />
      )}
    </div>
  );
}

function CarregandoDocumento() {
  return (
    <div className="space-y-6" aria-label="A carregar...">
      <Skeleton className="h-8 w-64" />
      <div className="grid gap-6 lg:grid-cols-2">
        {[0, 1].map((i) => (
          <Card key={i}>
            <CardContent className="space-y-2 pt-6">
              <Skeleton className="h-4 w-full" />
              <Skeleton className="h-4 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
            </CardContent>
          </Card>
        ))}
      </div>
      {[0, 1, 2].map((i) => (
        <Card key={i}>
          <CardContent className="space-y-2 pt-6">
            <Skeleton className="h-4 w-full" />
            <Skeleton className="h-4 w-2/3" />
          </CardContent>
        </Card>
      ))}
    </div>
  );
}

function DetalheDocumento({
  documento: d,
  canViewClientes,
}: {
  documento: DocumentoFiscalDetalhe;
  canViewClientes: boolean;
}) {
  const isento = d.emitenteRegimeIva === "ISENTO";

  return (
    <>
      <div className="space-y-2">
        <h1 className="font-mono text-2xl font-semibold">{d.numeroFormatado}</h1>
        <div className="flex flex-wrap items-center gap-2">
          <Badge variant="secondary">{d.tipoRotulo}</Badge>
          {d.estadoComunicacao === "PENDENTE" ? <Badge variant="outline">Pendente</Badge> : null}
          <Badge variant="outline" className="gap-1">
            <Info className="h-3 w-3" aria-hidden="true" />
            Simulação — sem validade fiscal
          </Badge>
        </div>
        {d.estadoComunicacao === "PENDENTE" ? (
          <p className={AJUDA}>Comunicação à administração fiscal ainda não efetuada.</p>
        ) : null}
      </div>

      <div className={NOTICE_CLASSES}>
        Este documento é simulado e não tem validade fiscal. Fica registado como pendente de comunicação; a
        comunicação à administração fiscal chega numa versão futura.
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle className="text-xl font-semibold">Emitente</CardTitle>
          </CardHeader>
          <CardContent>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2">
              <Campo rotulo="Firma">{d.emitenteFirma}</Campo>
              <Campo rotulo="NIF" mono>
                {d.emitenteNif}
              </Campo>
              <Campo rotulo="Morada">{d.emitenteMorada}</Campo>
              <Campo rotulo="Localidade">{d.emitenteLocalidade ?? "—"}</Campo>
              <Campo rotulo="Regime de IVA">{isento ? "Isento" : "Normal"}</Campo>
              {isento ? (
                <Campo rotulo="Motivo de isenção">
                  {d.emitenteMotivoIsencaoCodigo} — {d.emitenteMotivoIsencaoDescricao}
                </Campo>
              ) : null}
            </dl>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-xl font-semibold">Adquirente</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2">
              <Campo rotulo="Nome">{d.adquirenteNome}</Campo>
              <Campo rotulo="NIF" mono>
                {d.adquirenteNif}
              </Campo>
              <Campo rotulo="Morada">{d.adquirenteMorada}</Campo>
            </dl>
            <p className={AJUDA}>
              Dados do cliente no momento da emissão. Alterações posteriores ao cliente não afetam este documento.
            </p>
          </CardContent>
        </Card>
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-xl font-semibold">Linha do documento</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Descrição</TableHead>
                <TableHead className="text-right">Base</TableHead>
                <TableHead className="text-right">IVA / Isenção</TableHead>
                <TableHead className="text-right">Total</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {d.linhas.map((l) => (
                <TableRow key={l.numeroLinha}>
                  <TableCell className="whitespace-normal">{l.descricao}</TableCell>
                  <TableCell className="text-right tabular-nums">{formatarCVE(l.valorBase)}</TableCell>
                  <TableCell className="text-right tabular-nums">
                    {l.motivoIsencaoCodigo
                      ? `Isento (${l.motivoIsencaoCodigo})`
                      : `${formatarCVE(l.valorIva)} (${l.taxaIva}%)`}
                  </TableCell>
                  <TableCell className="text-right tabular-nums">{formatarCVE(l.totalLinha)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-xl font-semibold">Valores</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm">
            <dt>Base tributável</dt>
            <dd className="text-right tabular-nums">{formatarCVE(d.totalBase)}</dd>
            {isento ? (
              <>
                <dt>Motivo de isenção</dt>
                <dd className="text-right">
                  {d.emitenteMotivoIsencaoCodigo} — {d.emitenteMotivoIsencaoDescricao}
                </dd>
              </>
            ) : (
              <>
                <dt>IVA ({d.taxaIva}%)</dt>
                <dd className="text-right tabular-nums">{formatarCVE(d.totalIva)}</dd>
              </>
            )}
            {d.totalRetencao > 0 ? (
              <>
                <dt>Retenção na fonte ({d.taxaRetencao}%)</dt>
                <dd className="text-right tabular-nums">- {formatarCVE(d.totalRetencao)}</dd>
              </>
            ) : null}
          </dl>
          <Separator />
          <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm font-semibold">
            <dt>Total</dt>
            <dd className="text-right tabular-nums">{formatarCVE(d.totalDocumento)}</dd>
            <dt>Líquido recebido</dt>
            <dd className="text-right tabular-nums">{formatarCVE(d.valorLiquido)}</dd>
          </dl>
          <p className={AJUDA}>Conta corrente creditada do total.</p>
          <Separator />
          <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm">
            <dt>Método de pagamento</dt>
            <dd className="text-right">{d.metodoPagamentoRotulo}</dd>
            <dt>Data de emissão</dt>
            <dd className="text-right">{formatarData(d.dataEmissao)}</dd>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle className="text-xl font-semibold">Ligações</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="flex flex-col gap-2 sm:flex-row sm:gap-6">
            <li>
              <Link
                href={`/financeiro/${encodeURIComponent(String(d.honorarioId))}#pagamento-${d.pagamentoId}`}
                className={LINK_CLASSES}
              >
                Ver pagamento
                <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
              </Link>
            </li>
            <li>
              <Link href={`/financeiro/${encodeURIComponent(String(d.honorarioId))}`} className={LINK_CLASSES}>
                Ver honorário
                <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
              </Link>
            </li>
            {canViewClientes ? (
              <li>
                <Link href={`/clientes/${encodeURIComponent(d.clienteId)}`} className={LINK_CLASSES}>
                  Ver cliente
                  <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
                </Link>
              </li>
            ) : null}
          </ul>
        </CardContent>
      </Card>

      <p className={AJUDA}>Documento imutável: não pode ser alterado nem apagado.</p>
    </>
  );
}
