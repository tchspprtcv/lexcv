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
import { ComunicacaoEstadoBadge } from "@/components/shared/comunicacao-estado-badge";
import { ModoSimuladoBanner } from "@/components/shared/modo-simulado-banner";
import {
  podeEmitirNotaCredito,
  podeLerDocumentosFiscais,
  podeReprocessarComunicacao,
  useDocumentoFiscal,
  useEstadoEmissao,
} from "@/hooks/use-faturacao";
import { usePermissions } from "@/hooks/use-permissions";
import { isApiError } from "@/lib/api";
import { descricaoEstadoComunicacao } from "@/lib/comunicacao-fiscal";
import type { DocumentoFiscalDetalhe } from "@/types/faturacao";

import { ComunicacaoFiscalCard } from "./comunicacao-fiscal-card";
import { NotaCreditoDialog } from "./nota-credito-dialog";

// Detalhe de um documento fiscal (134-UI-SPEC Surface 4; D-18, EMIS-08, EMIS-11). Só de leitura:
// mostra o snapshot gravado na emissão e os valores calculados pelo backend. Este ecrã não tem
// nenhuma ação que altere o documento. Um id de outro escritório devolve o mesmo 404 do que um
// id inexistente.
//
// Phase 135 (135-UI-SPEC Surface 2; NCRD-01, NCRD-02): numa Fatura-Recibo mostra as notas de
// crédito emitidas, o total creditado e o valor ainda creditável (todos vindos do backend) e, para
// quem tem EXATAMENTE financeiro:manage, o botão "Emitir Nota de Crédito". A NC é um documento
// NOVO, emitido pelo diálogo (nota-credito-dialog.tsx, que concentra os pedidos); a FR nunca é
// alterada. Numa Nota de Crédito mostra o documento original, o motivo e as ligações ao
// honorário e ao estorno.
//
// Phase 136 (136-UI-SPEC Surface 2; DFE-04, DFE-05, DFE-06): o cabeçalho mostra UM badge de
// comunicação (comunicacao.estado do backend) e o cartão "Comunicação fiscal" fica depois de
// "Valores" (e de "Notas de crédito" numa FR), antes de "Ligações". O reprocessamento vive em
// comunicacao-fiscal-card.tsx / reprocessar-comunicacao.tsx: esta página continua sem pedidos de
// alteração. O banner "Modo simulado" fica no topo do conteúdo autorizado.

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
  const estadoEmissao = useEstadoEmissao(podeLer);
  const podeEmitirNc = podeEmitirNotaCredito(permissions.permissions);
  const podeReprocessar = podeReprocessarComunicacao(permissions.permissions);
  const faturacaoDesligada = estadoEmissao.data?.ativa === false;
  const modoComunicacao = estadoEmissao.data?.modoComunicacao ?? null;

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
      <ModoSimuladoBanner />

      <div className="flex flex-wrap items-start justify-between gap-4">
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
        <DetalheDocumento
          documento={documento.data}
          canViewClientes={canViewClientes}
          podeEmitirNc={podeEmitirNc}
          podeReprocessar={podeReprocessar}
          faturacaoDesligada={faturacaoDesligada}
          modoComunicacao={modoComunicacao}
        />
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
      {[0, 1, 2, 3].map((i) => (
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
  podeEmitirNc,
  podeReprocessar,
  faturacaoDesligada,
  modoComunicacao,
}: {
  documento: DocumentoFiscalDetalhe;
  canViewClientes: boolean;
  podeEmitirNc: boolean;
  podeReprocessar: boolean;
  faturacaoDesligada: boolean;
  modoComunicacao: string | null;
}) {
  const isento = d.emitenteRegimeIva === "ISENTO";
  const isFr = d.tipo === "FR";
  const isNc = d.tipo === "NC";
  const tituloRef = React.useRef<HTMLHeadingElement>(null);
  // Gate exato (financeiro:manage) + FR com valor ainda creditável (do backend) + faturação não
  // desligada. Sem estas condições o botão não é renderizado (nem desativado).
  const mostrarEmissaoNc = isFr && podeEmitirNc && (d.valorCreditavelRestante ?? 0) > 0 && !faturacaoDesligada;
  const totalmenteCreditada = isFr && podeEmitirNc && d.valorCreditavelRestante === 0;
  const hrefHonorario = `/financeiro/${encodeURIComponent(String(d.honorarioId))}`;

  return (
    <>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="space-y-2">
          <h1 ref={tituloRef} tabIndex={-1} className="font-mono text-2xl font-semibold outline-none">
            {d.numeroFormatado}
          </h1>
          <div className="flex flex-wrap items-center gap-2">
            <Badge variant="secondary">{d.tipoRotulo}</Badge>
            {d.comunicacao ? (
              <ComunicacaoEstadoBadge
                estado={d.comunicacao.estado}
                descricao={descricaoEstadoComunicacao(d.comunicacao.estado, podeReprocessar)}
              />
            ) : null}
            <Badge variant="outline" className="gap-1">
              <Info className="h-3 w-3" aria-hidden="true" />
              Simulação — sem validade fiscal
            </Badge>
          </div>
          {d.comunicacao ? (
            <p className={AJUDA}>{descricaoEstadoComunicacao(d.comunicacao.estado, podeReprocessar)}</p>
          ) : null}
        </div>
        {mostrarEmissaoNc ? (
          <NotaCreditoDialog documento={d} tituloPaginaRef={tituloRef} />
        ) : totalmenteCreditada ? (
          <p className={AJUDA}>Esta fatura-recibo já foi totalmente creditada.</p>
        ) : null}
      </div>

      <div className={NOTICE_CLASSES}>
        {isNc
          ? "Esta nota de crédito é simulada e não tem validade fiscal. A comunicação é feita a um serviço de simulação, não à administração fiscal."
          : "Este documento é simulado e não tem validade fiscal. A comunicação é feita a um serviço de simulação, não à administração fiscal."}
      </div>

      {isNc && d.documentoOrigem ? (
        <Card>
          <CardHeader>
            <CardTitle className="text-xl font-semibold">Documento original</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="flex flex-wrap items-center gap-2 text-sm">
              <span>Corrige a fatura-recibo</span>
              <span className="font-mono">{d.documentoOrigem.numeroFormatado}</span>
            </p>
            <Link
              href={`/financeiro/documentos-fiscais/${encodeURIComponent(d.documentoOrigem.id)}`}
              className={`${LINK_CLASSES} font-mono`}
            >
              <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
              Ver fatura-recibo original
            </Link>
            <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2">
              <Campo rotulo="Motivo">{d.motivoRotulo ?? "—"}</Campo>
              <dt className="text-sm font-semibold">Descrição do motivo</dt>
              <dd className="text-sm break-words">{d.motivoTexto ?? "—"}</dd>
            </dl>
          </CardContent>
        </Card>
      ) : null}

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
            <dt>{isNc ? "Total creditado" : "Total"}</dt>
            <dd className="text-right tabular-nums">{formatarCVE(d.totalDocumento)}</dd>
            <dt>Líquido recebido</dt>
            <dd className="text-right tabular-nums">{formatarCVE(d.valorLiquido)}</dd>
          </dl>
          <p className={AJUDA}>
            {isNc ? "Conta corrente debitada do total creditado." : "Conta corrente creditada do total."}
          </p>
          <Separator />
          <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm">
            <dt>Método de pagamento</dt>
            <dd className="text-right">{d.metodoPagamentoRotulo}</dd>
            <dt>Data de emissão</dt>
            <dd className="text-right">{formatarData(d.dataEmissao)}</dd>
          </dl>
        </CardContent>
      </Card>

      {isFr ? <NotasCreditoCard documento={d} /> : null}

      <ComunicacaoFiscalCard documento={d} modoComunicacao={modoComunicacao} />

      <Card>
        <CardHeader>
          <CardTitle className="text-xl font-semibold">Ligações</CardTitle>
        </CardHeader>
        <CardContent>
          <ul className="flex flex-col gap-2 sm:flex-row sm:gap-6">
            {isNc ? null : (
              <li>
                <Link href={`${hrefHonorario}#pagamento-${d.pagamentoId}`} className={LINK_CLASSES}>
                  Ver pagamento
                  <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
                </Link>
              </li>
            )}
            <li>
              <Link href={hrefHonorario} className={LINK_CLASSES}>
                Ver honorário
                <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
              </Link>
            </li>
            {isNc ? (
              <li>
                {/* Numa NC, pagamentoId é o id do estorno (pagamento negativo). */}
                <Link href={`${hrefHonorario}#pagamento-${d.pagamentoId}`} className={LINK_CLASSES}>
                  Ver estorno
                  <ArrowUpRight className="h-4 w-4" aria-hidden="true" />
                </Link>
              </li>
            ) : null}
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

/** FR: notas de crédito emitidas, total creditado e valor ainda creditável (valores do backend). */
function NotasCreditoCard({ documento: d }: { documento: DocumentoFiscalDetalhe }) {
  const notas = d.notasCredito ?? [];

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl font-semibold">Notas de crédito</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm">
          <dt>Total creditado</dt>
          <dd className="text-right tabular-nums">{formatarCVE(d.totalCreditado ?? 0)}</dd>
          <dt className="font-semibold">Valor ainda creditável</dt>
          <dd className="text-right font-semibold tabular-nums">{formatarCVE(d.valorCreditavelRestante ?? 0)}</dd>
        </dl>
        {notas.length === 0 ? (
          <p className="text-sm text-slate-500 dark:text-slate-400">
            Ainda não foram emitidas notas de crédito para esta fatura-recibo.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Número</TableHead>
                  <TableHead>Data</TableHead>
                  <TableHead>Motivo</TableHead>
                  <TableHead className="text-right">Total</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {notas.map((nc) => (
                  <TableRow key={nc.id}>
                    <TableCell>
                      <Link
                        href={`/financeiro/documentos-fiscais/${encodeURIComponent(nc.id)}`}
                        className={`${LINK_CLASSES} font-mono`}
                      >
                        {nc.numeroFormatado}
                      </Link>
                    </TableCell>
                    <TableCell>{formatarData(nc.dataEmissao)}</TableCell>
                    <TableCell>{nc.motivoRotulo}</TableCell>
                    <TableCell className="text-right tabular-nums">{formatarCVE(nc.totalDocumento)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
        <p className={AJUDA}>A soma das notas de crédito nunca pode exceder o total da fatura-recibo.</p>
      </CardContent>
    </Card>
  );
}
