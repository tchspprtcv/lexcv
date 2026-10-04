"use client";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { Skeleton } from "@/components/ui/skeleton";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { useSeriesFiscais } from "@/hooks/use-faturacao";

// Card "Séries de numeração" (133-UI-SPEC Block 6, CFG-05). Read-Only Guarantee (vinculativa):
// este ficheiro NAO chama, importa nem referencia nenhum hook de gravacao do TanStack Query,
// nenhum botao, nenhum campo de texto, nem qualquer chamada de rede que altere dados. As séries
// são criadas pelo sistema; não existe UI de criação/edição. A ordenação (ano desc, depois tipo)
// já vem do backend e é mantida tal como recebida.

export function FaturacaoSeriesCard({ habilitado }: { habilitado: boolean }) {
  const series = useSeriesFiscais(habilitado);

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-xl font-semibold">Séries de numeração</CardTitle>
      </CardHeader>
      <CardContent>
        {series.isPending ? (
          <div className="space-y-2" aria-label="A carregar...">
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
            <Skeleton className="h-8 w-full" />
          </div>
        ) : series.isError ? (
          <div className="text-sm text-red-600">
            Não foi possível carregar as séries de numeração. Tente novamente.
          </div>
        ) : !series.data?.length ? (
          <Empty>
            <EmptyHeader>
              <EmptyTitle>Sem séries de numeração</EmptyTitle>
              <EmptyDescription>
                As séries são criadas automaticamente quando o primeiro documento for emitido.
                Ainda não existe nenhuma.
              </EmptyDescription>
            </EmptyHeader>
          </Empty>
        ) : (
          <div className="overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Tipo</TableHead>
                  <TableHead>Ano</TableHead>
                  <TableHead>Código</TableHead>
                  <TableHead className="text-right">Último número</TableHead>
                  <TableHead>Ambiente</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {series.data.map((serie) => (
                  <TableRow key={`${serie.tipoDocumento}-${serie.ano}-${serie.codigo}`}>
                    <TableCell>{serie.tipoDocumentoRotulo}</TableCell>
                    <TableCell className="tabular-nums">{serie.ano}</TableCell>
                    <TableCell className="font-mono">{serie.codigo}</TableCell>
                    <TableCell className="text-right tabular-nums">{serie.ultimoNumero}</TableCell>
                    <TableCell>
                      <Badge variant="outline">{serie.ambienteRotulo}</Badge>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        )}
      </CardContent>
    </Card>
  );
}
