export function formatMoneyCVE(v: number | null | undefined) {
  if (v == null) return "A confirmar";
  return v.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}

export function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}

export type HonorarioStatus = "Pendente" | "Parcialmente Pago" | "Pago";

export function calcHonorarioStatus(totalPago: number, valorTotal: number | null): HonorarioStatus {
  if (valorTotal == null) return "Pendente";
  if (totalPago <= 0) return "Pendente";
  if (totalPago < valorTotal) return "Parcialmente Pago";
  return "Pago";
}
