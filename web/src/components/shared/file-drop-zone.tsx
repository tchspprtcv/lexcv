"use client";

import * as React from "react";
import { Paperclip, X } from "lucide-react";

export type FileDropZoneProps = {
  onFileChange: (file: File) => void;
  /** Called when the user clears the selected file via the "Remover" control. */
  onClear?: () => void;
  accept?: string;
  disabled?: boolean;
  children?: React.ReactNode;
};

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function FileDropZone({ onFileChange, onClear, accept, disabled, children }: FileDropZoneProps) {
  const inputRef = React.useRef<HTMLInputElement>(null);
  const [isDragging, setIsDragging] = React.useState(false);
  const [selectedFile, setSelectedFile] = React.useState<File | null>(null);

  const selectFile = (file: File) => {
    setSelectedFile(file);
    onFileChange(file);
  };

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
    if (file) selectFile(file);
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const f = e.target.files?.[0];
    if (f) selectFile(f);
  };

  const handleClear = (e: React.MouseEvent) => {
    e.stopPropagation();
    setSelectedFile(null);
    if (inputRef.current) inputRef.current.value = "";
    onClear?.();
  };

  const hiddenInput = (
    <input
      type="file"
      ref={inputRef}
      className="sr-only"
      accept={accept}
      disabled={disabled}
      onChange={handleInputChange}
    />
  );

  if (selectedFile) {
    return (
      <div
        className={[
          "flex items-center justify-between gap-3 rounded-lg border-2 border-emerald-300 bg-emerald-50 p-4 transition-colors",
          "dark:border-emerald-800 dark:bg-emerald-950/30",
          disabled ? "opacity-50" : "",
        ]
          .filter(Boolean)
          .join(" ")}
      >
        <div className="flex min-w-0 items-center gap-2">
          <Paperclip className="h-4 w-4 shrink-0 text-emerald-700 dark:text-emerald-400" />
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-emerald-900 dark:text-emerald-200">
              {selectedFile.name}
            </p>
            <p className="text-xs text-emerald-700/80 dark:text-emerald-400/80">
              {formatFileSize(selectedFile.size)}
            </p>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          <button
            type="button"
            disabled={disabled}
            onClick={() => inputRef.current?.click()}
            className="text-xs font-medium text-emerald-700 hover:underline disabled:cursor-not-allowed dark:text-emerald-400"
          >
            Trocar
          </button>
          <button
            type="button"
            disabled={disabled}
            onClick={handleClear}
            aria-label="Remover ficheiro selecionado"
            className="rounded-full p-1 text-emerald-700 hover:bg-emerald-100 disabled:cursor-not-allowed dark:text-emerald-400 dark:hover:bg-emerald-900/40"
          >
            <X className="h-4 w-4" />
          </button>
        </div>
        {hiddenInput}
      </div>
    );
  }

  return (
    <div
      onDragEnter={handleDragEnter}
      onDragOver={handleDragOver}
      onDragLeave={handleDragLeave}
      onDrop={handleDrop}
      className={[
        "border-2 border-dashed rounded-lg p-6 text-center transition-colors cursor-pointer",
        isDragging
          ? "border-neutral-400 bg-neutral-100 dark:bg-neutral-800"
          : "border-neutral-300 dark:border-neutral-700",
        disabled ? "opacity-50 cursor-not-allowed" : "",
      ]
        .filter(Boolean)
        .join(" ")}
    >
      {hiddenInput}
      <button
        type="button"
        disabled={disabled}
        onClick={() => inputRef.current?.click()}
        className="text-sm text-neutral-700 dark:text-neutral-300 hover:underline disabled:cursor-not-allowed"
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
