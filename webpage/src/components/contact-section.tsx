import { Mail } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";

export function ContactSection() {
  return (
    <section id="contacto" className="border-t border-slate-200 py-12 dark:border-slate-800 md:py-16 lg:py-16">
      <div className="mx-auto max-w-3xl px-6 text-center">
        <Card>
          <CardHeader>
            <Badge variant="secondary" className="px-3 py-1 text-sm font-semibold uppercase tracking-[0.2em]">
              CONTACTO
            </Badge>
          </CardHeader>
          <CardContent>
            <h2 className="text-2xl font-semibold text-slate-900 dark:text-slate-50">Pronto para começar?</h2>
            <p className="mt-6 text-base text-slate-600 dark:text-slate-300">
              Fale com a nossa equipa e conheça a plataforma em detalhe.
            </p>
            <div className="mt-8 flex justify-center">
              <Button asChild className="rounded-none">
                <a href="mailto:contacto@lexcv.cv?subject=Pedido%20de%20Demonstra%C3%A7%C3%A3o%20%E2%80%94%20LexCV">
                  Pedir Demonstração<Mail className="h-4 w-4" />
                </a>
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>
    </section>
  );
}
