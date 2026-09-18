import { ArrowRight, Mail } from "lucide-react";
import { Button } from "@/components/ui/button";
import { EMAIL_CONTACTO, getDemoUrl } from "@/lib/contacto";
import { getLoginUrl } from "@/lib/get-login-url";

/**
 * O ponto de conversão da página.
 *
 * Antes era um cartão centrado com "Fale com a nossa equipa" e um botão. Passa
 * a dizer o que acontece a seguir: quem não sabe o que vem depois de carregar
 * hesita. O acesso para quem já é cliente fica aqui em baixo, como ligação
 * discreta, para não competir com o pedido de demonstração.
 */
export function ContactSection() {
  return (
    <section
      id="contacto"
      className="scroll-mt-20 border-t border-slate-200 py-12 dark:border-slate-800 md:py-16"
    >
      <div className="mx-auto max-w-7xl px-6">
        <div className="rounded-lg border border-slate-200 bg-white p-8 shadow-sm dark:border-slate-800 dark:bg-[#020617] md:p-10">
          <div className="grid grid-cols-1 gap-8 md:grid-cols-[1.2fr_1fr] md:gap-12">
            <div>
              <span className="text-[11px] font-semibold uppercase tracking-[0.2em] text-blue-600 dark:text-blue-400">
                Demonstração
              </span>
              <h2 className="mt-3 text-2xl font-semibold tracking-tight text-slate-900 dark:text-slate-50 md:text-3xl">
                Veja a plataforma com os seus próprios processos em mente
              </h2>
              <p className="mt-4 text-base text-slate-600 dark:text-slate-300">
                Mostramos o sistema a funcionar e respondemos às perguntas do seu escritório —
                incluindo as de segurança e as de preço.
              </p>

              <div className="mt-8 flex flex-wrap items-center gap-3">
                <Button asChild size="lg">
                  <a href={getDemoUrl("contacto")}>
                    Pedir demonstração
                    <Mail className="h-4 w-4" />
                  </a>
                </Button>
                <a
                  href={`mailto:${EMAIL_CONTACTO}`}
                  className="text-sm font-medium text-slate-600 underline-offset-4 hover:text-blue-600 hover:underline dark:text-slate-300 dark:hover:text-blue-400"
                >
                  {EMAIL_CONTACTO}
                </a>
              </div>
            </div>

            <div className="md:border-l md:border-slate-200 md:pl-12 md:dark:border-slate-800">
              <span className="text-[11px] font-semibold uppercase tracking-[0.18em] text-slate-500 dark:text-slate-400">
                O que acontece a seguir
              </span>
              <ol className="mt-4 space-y-4">
                {[
                  "Responde-nos com o nome do escritório e quantas pessoas o usariam.",
                  "Combinamos uma sessão e mostramos o sistema a trabalhar.",
                  "Recebe uma proposta ajustada à dimensão do escritório.",
                ].map((passo, i) => (
                  <li key={passo} className="flex gap-3">
                    <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-slate-100 text-xs font-semibold text-slate-600 dark:bg-slate-800 dark:text-slate-300">
                      {i + 1}
                    </span>
                    <span className="text-sm text-slate-600 dark:text-slate-400">{passo}</span>
                  </li>
                ))}
              </ol>

              <div className="mt-8 border-t border-slate-200 pt-4 dark:border-slate-800">
                <p className="text-sm text-slate-500 dark:text-slate-400">
                  Já é cliente?{" "}
                  <a
                    href={getLoginUrl()}
                    className="font-medium text-blue-600 underline-offset-4 hover:underline dark:text-blue-400"
                  >
                    Entrar na plataforma
                    <ArrowRight className="ml-0.5 inline h-3 w-3" />
                  </a>
                </p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
