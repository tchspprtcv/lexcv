import * as React from "react";

export function highlightMatch(text: string, query: string): React.ReactNode {
  if (!text) {
    return text;
  }

  const q = query.trim();
  if (!q) {
    return text;
  }

  const index = text.toLowerCase().indexOf(q.toLowerCase());
  if (index === -1) {
    return text;
  }

  const before = text.slice(0, index);
  const match = text.slice(index, index + q.length);
  const after = text.slice(index + q.length);

  return (
    <>
      {before}
      <strong>{match}</strong>
      {after}
    </>
  );
}
