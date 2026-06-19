"use client";

import * as React from "react";

export type FileDropZoneProps = {
  onFileChange: (file: File) => void;
  accept?: string;
  disabled?: boolean;
  children?: React.ReactNode;
};

export function FileDropZone({ onFileChange, accept, disabled, children }: FileDropZoneProps) {
  const inputRef = React.useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = React.useState(false);

  const handleDragEnter = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (!disabled) setIsDragging(true);
  };

  const handleDragOver = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (!disabled) setIsDragging(true);
  };

  const handleDragLeave = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    // Only clear when leaving the drop zone entirely, not when crossing child elements
    if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
    setIsDragging(false);
  };

  const handleDrop = (e: React.DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    e.stopPropagation();
    setIsDragging(false);
    if (disabled) return;
    const file = e.dataTransfer.files[0];
    if (file) onFileChange(file);
  };

  return (
    <div
      onDragEnter={handleDragEnter}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={[
        "border-2 border-dashed rounded-lg p-6 text-center transition-colors cursor-pointer",
        isDragging
          ? "border-blue-500 bg-blue-50 dark:bg-blue-950"
          : "border-neutral-300 dark:border-neutral-700",
        disabled ? "opacity-50 cursor-not-allowed" : "",
      ]
        .filter(Boolean)
        .join(" ")}
    >
      <input
        type="file"
        ref={inputRef}
        className="sr-only"
        accept={accept}
        disabled={disabled}
        onChange={(e) => {
          const f = e.target.files?.[0];
          if (f) onFileChange(f);
        }}
      />
      <button
        type="button"
        disabled={disabled}
        onClick={() => inputRef.current?.click()}
        className="text-sm text-blue-600 dark:text-blue-400 hover:underline disabled:cursor-not-allowed"
      >
        Clique para selecionar
      </button>
      {children ? (
        <p className="mt-1 text-sm text-neutral-500 dark:text-neutral-400">{children}</p>
      ) : (
        <p className="mt-1 text-sm text-neutral-500 dark:text-neutral-400">
          ou arraste um ficheiro para aqui
        </p>
      )}
    </div>
  );
}
