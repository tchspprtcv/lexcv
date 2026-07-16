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

function escapeCsvValue(value: string, delimiter: string) {
  let v = value;
  if (FORMULA_TRIGGER_CHARS.some((c) => v.startsWith(c))) {
    v = "'" + v; // neutralize formula interpretation, mirrors OWASP CSV-injection guidance
  }
  const needsQuote =
    v.includes('"') || v.includes("\n") || v.includes("\r") || v.includes(delimiter);
  if (!needsQuote) return v;
  return `"${v.replaceAll('"', '""')}"`;
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

