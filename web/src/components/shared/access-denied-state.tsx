import Link from "next/link";
import { Lock } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

type AccessDeniedStateProps = {
  title?: string;
  description?: string;
  backHref?: string;
  backLabel?: string;
};

export function AccessDeniedState({
  title = "Acesso negado",
  description = "Não tem permissões suficientes para aceder a esta área.",
  backHref,
  backLabel = "Voltar",
}: AccessDeniedStateProps) {
  return (
    <Card className="border-slate-200 dark:border-slate-800">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-slate-900 dark:text-white">
          <Lock className="h-5 w-5 text-red-600 dark:text-red-400" />
          {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <p className="text-sm text-slate-500 dark:text-slate-400">{description}</p>
        {backHref ? (
          <Button asChild variant="outline">
            <Link href={backHref}>{backLabel}</Link>
          </Button>
        ) : null}
      </CardContent>
    </Card>
  );
}
