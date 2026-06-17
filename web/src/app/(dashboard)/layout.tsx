import { Suspense } from "react";
import { DashboardShell } from "@/components/shared/dashboard-shell";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return (
    <Suspense fallback={
      <div className="h-screen w-full flex overflow-hidden bg-slate-50 dark:bg-[#020617] transition-colors duration-300">
        <aside className="w-[270px] flex-shrink-0 bg-slate-950 dark:bg-[#04091a] border-r border-slate-900/50 dark:border-slate-800/50 flex flex-col z-20"></aside>
        <main className="flex-1 min-w-0 flex flex-col items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-b-2 border-slate-900 dark:border-white"></div>
        </main>
      </div>
    }>
      <DashboardShell>{children}</DashboardShell>
    </Suspense>
  );
}
