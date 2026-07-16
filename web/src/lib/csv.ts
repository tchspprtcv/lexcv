function parseCsvLine(line: string, delimiter: string) {
  const out: string[] = [];
  let cur = "";
  let inQuotes = false;

  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (inQuotes) {
      if (ch === '"') {
        const next = line[i + 1];
        if (next === '"') {
          cur += '"';
          i++;
        } else {
          inQuotes = false;
        }
      } else {
        cur += ch;
      }
    } else {
      if (ch === '"') {
        inQuotes = true;
      } else if (ch === delimiter) {
        out.push(cur);
        cur = "";
      } else {
        cur += ch;
      }
    }
  }

  out.push(cur);
  return out.map((v) => v.trim());
}

const FORMULA_TRIGGER_CHARS = ["=", "+", "-", "@", "\t", "\r"];

/**
 * Neutralizes spreadsheet formula execution on a single value (OWASP
 * CSV-injection guidance). Apply this ONLY to genuinely free-text,
 * attacker-influenced field values (e.g. a cliente's `nome`) before
 * building the `rows` array passed to `toCsv` — do not apply it to every
 * exported field. Structured data such as phone numbers legitimately
 * starts with `+` (e.g. this app's own "+238 000 0000" placeholder), and
 * this app's own CSV importer does not strip the guard back out, so
 * blanket-applying it corrupts data on export/import round-trips.
 */
export function guardCsvFormula(value: string) {
  if (FORMULA_TRIGGER_CHARS.some((c) => value.startsWith(c))) {
    return "'" + value;
  }
  return value;
}

function escapeCsvValue(value: string, delimiter: string) {
  const needsQuote =
    value.includes('"') || value.includes("\n") || value.includes("\r") || value.includes(delimiter);
  if (!needsQuote) return value;
  return `"${value.replaceAll('"', '""')}"`;
}

export function detectCsvDelimiter(headerLine: string) {
  const commas = (headerLine.match(/,/g) ?? []).length;
  const semis = (headerLine.match(/;/g) ?? []).length;
  return semis > commas ? ";" : ",";
}

export function parseCsv(text: string) {
  const normalized = text.replace(/\r\n/g, "\n").replace(/\r/g, "\n");
  const lines = normalized.split("\n").filter((l) => l.trim().length > 0);
  if (!lines.length) return { delimiter: ",", headers: [] as string[], rows: [] as string[][] };

  const delimiter = detectCsvDelimiter(lines[0]);
  const headers = parseCsvLine(lines[0], delimiter).map((h) => h.trim());
  const rows = lines.slice(1).map((l) => parseCsvLine(l, delimiter));
  return { delimiter, headers, rows };
}

export function toCsv(headers: string[], rows: Array<Array<string | number | boolean | null | undefined>>, delimiter = ",") {
  const headerLine = headers.map((h) => escapeCsvValue(String(h), delimiter)).join(delimiter);
  const bodyLines = rows.map((r) =>
    r.map((v) => escapeCsvValue(v === null || v === undefined ? "" : String(v), delimiter)).join(delimiter),
  );
  return [headerLine, ...bodyLines].join("\n");
}

