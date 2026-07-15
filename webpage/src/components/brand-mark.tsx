import { Building2 } from "lucide-react";

import { cn } from "@/lib/utils";
import type { BrandingResponse } from "@/types/branding";

export function BrandMark({
  branding,
  className,
}: {
  branding: BrandingResponse;
  className?: string;
}) {
  const { nome, logoDataUrl } = branding;
  const hasLogo = typeof logoDataUrl === "string" && logoDataUrl.startsWith("data:image/");

  return (
    <span
      className={cn(
        "inline-flex items-center gap-2 text-sm font-semibold text-slate-900 dark:text-slate-100",
        className,
      )}
    >
      {hasLogo ? (
        <img src={logoDataUrl!} alt="" className="h-5 w-5 rounded-sm object-contain" />
      ) : (
        <Building2 className="h-4 w-4 text-slate-400" />
      )}
      <span>{nome || "LexCV"}</span>
    </span>
  );
}
