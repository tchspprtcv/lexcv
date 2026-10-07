/**
 * Descarrega um Blob no browser criando uma âncora temporária com URL de objeto.
 * Client-only (document / window).
 */
export function guardarFicheiro(blob: Blob, nome: string): void {
  if (typeof document === "undefined") return;
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = nome;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
