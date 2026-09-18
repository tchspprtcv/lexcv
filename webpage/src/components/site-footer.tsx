import { BrandMark } from "@/components/brand-mark";
import { EMAIL_CONTACTO } from "@/lib/contacto";
import { getLoginUrl } from "@/lib/get-login-url";
import type { BrandingResponse } from "@/types/branding";

const LIGACOES = [
  { href: "#demonstracao", label: "Demonstração" },
  { href: "#funcionalidades", label: "Módulos" },
  { href: "#confianca", label: "Confiança" },
  { href: "#perguntas", label: "Perguntas frequentes" },
] as const;

export function SiteFooter({ branding }: { branding: BrandingResponse }) {
  const ano = new Date().getFullYear();
  return (
    <footer className="border-t border-slate-200 py-10 dark:border-slate-800">
      <div className="mx-auto max-w-7xl px-6">
        <div className="flex flex-col gap-6 md:flex-row md:items-start md:justify-between">
          <div>
            <BrandMark branding={branding} />
            <p className="mt-2 max-w-xs text-sm text-slate-500 dark:text-slate-400">
              Plataforma institucional de gestão jurídica para Cabo Verde.
            </p>
          </div>

          <nav className="flex flex-wrap gap-x-6 gap-y-2">
            {LIGACOES.map((l) => (
              <a
                key={l.href}
                href={l.href}
                className="text-sm text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400"
              >
                {l.label}
              </a>
            ))}
            <a
              href={`mailto:${EMAIL_CONTACTO}`}
              className="text-sm text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400"
            >
              Contacto
            </a>
            <a
              href={getLoginUrl()}
              className="text-sm text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400"
            >
              Entrar
            </a>
          </nav>
        </div>

        <p className="mt-8 border-t border-slate-200 pt-6 text-sm text-slate-500 dark:border-slate-800 dark:text-slate-400">
          © {ano} LexCV. Todos os direitos reservados.
        </p>
      </div>
    </footer>
  );
}
