import type { ParecerStatus } from "@/types/pareceres";

export function formatDate(v: string | undefined) {
  if (!v) return "—";
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(v);
  const d = m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

export function statusVariant(status: ParecerStatus) {
  return status === "PENDENTE"
    ? "gray"
    : status === "EM_ELABORACAO"
      ? "blue"
      : status === "EM_REVISAO"
        ? "amber"
        : status === "CONCLUIDO"
          ? "green"
          : "secondary";
}
