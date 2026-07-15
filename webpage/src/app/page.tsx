import { ThemeToggle } from "@/components/theme-toggle";

export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-4">
      <ThemeToggle />
      <p className="text-base text-slate-500 dark:text-slate-400">
        LexCV — landing em construção (placeholder, substituído no plano 99-04)
      </p>
    </main>
  );
}
