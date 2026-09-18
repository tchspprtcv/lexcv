import { Mail } from "lucide-react";
import { AppPreview } from "@/components/app-preview";
import { Button } from "@/components/ui/button";
import { getDemoUrl } from "@/lib/contacto";

/**
 * Hero em duas colunas a partir de lg: a promessa à esquerda, a demonstração à
 * direita, ambas acima da dobra. Empilha abaixo disso, porque a animação tem
 * texto miúdo e não sobrevive a uma coluna estreita.
 *
 * A demonstração perdeu o título grande que tinha quando ocupava uma faixa só
 * para si — ao lado do h1 seriam dois títulos a disputar a mesma atenção. Fica
 * a etiqueta e uma linha de contexto; o resto conta-o a própria animação.
 */
export function HeroSection() {
  return (
    <section className="border-b border-slate-200 py-12 dark:border-slate-800 md:py-16">
      <div className="mx-auto grid max-w-7xl grid-cols-1 items-center gap-12 px-6 lg:grid-cols-[minmax(0,24rem)_minmax(0,1fr)] xl:grid-cols-[minmax(0,28rem)_minmax(0,1fr)] xl:gap-16">
        <div>
          {/* Sem marca aqui: o cabeçalho fixo mostra-a logo por cima e repeti-la
              a poucos pixéis de distância só rouba espaço ao título. Fica o
              traço, que dá um ponto de partida à coluna. */}
          <div className="mb-6 h-px w-12 bg-blue-600 dark:bg-blue-400" />

          <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
            Gestão jurídica · Cabo Verde
          </span>

          <h1 className="mt-3 text-4xl font-semibold tracking-tight text-slate-900 dark:text-slate-50 md:text-5xl lg:text-4xl xl:text-[2.6rem] xl:leading-[1.1]">
            <span className="block">Nenhum prazo perdido.</span>
            <span className="block">Nenhuma peça fora do lugar.</span>
          </h1>

          <p className="mt-6 max-w-2xl text-lg text-slate-600 dark:text-slate-300">
            Plataforma de gestão jurídica para escritórios de advogados e instituições de Cabo
            Verde. Clientes, processos, prazos, documentos e honorários num só sistema — e o seu
            escritório isolado de todos os outros.
          </p>

          {/* Um só apelo à ação: o "ver como funciona" existia para levar à
              demonstração, que agora está ao lado e dispensa a viagem. O
              cabeçalho e o rodapé mantêm a âncora, para quem quer voltar. */}
          <div className="mt-8">
            <Button asChild size="lg">
              <a href={getDemoUrl("hero")}>
                Pedir demonstração
                <Mail className="h-4 w-4" />
              </a>
            </Button>
          </div>
        </div>

        <div id="demonstracao" className="min-w-0 scroll-mt-24">
          <div className="mb-4">
            <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-slate-500 dark:text-slate-400">
              A plataforma em 40 segundos
            </span>
            <h2 className="mt-1 text-base font-semibold tracking-tight text-slate-900 dark:text-slate-50">
              Do primeiro contacto à sentença, sem sair do sistema
            </h2>
          </div>

          <AppPreview />
        </div>
      </div>
    </section>
  );
}
