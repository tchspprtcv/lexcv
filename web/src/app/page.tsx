"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { fetchSetupStatus } from "@/lib/setup";
import { useMe } from "@/hooks/use-me";

export default function Home() {
  const router = useRouter();
  const me = useMe();
  const [setupChecked, setSetupChecked] = useState(false);

  useEffect(() => {
    async function checkStatus() {
      try {
        const status = await fetchSetupStatus();
        if (!status.initialized) {
          router.replace("/setup");
          return;
        }
        setSetupChecked(true);
      } catch (error) {
        console.error("Erro ao verificar o estado de inicialização:", error);
        setSetupChecked(true); // Fallback: try to proceed
      }
    }
    
    checkStatus();
  }, [router]);

  useEffect(() => {
    if (!setupChecked) return;
    
    if (me.isError) {
      router.replace("/login?returnUrl=/dashboard");
      return;
    }

    if (me.isSuccess) {
      router.replace("/dashboard");
    }
  }, [setupChecked, me.isError, me.isSuccess, router]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-50 dark:bg-[#020617]">
      <div className="h-8 w-8 animate-spin rounded-full border-b-2 border-slate-900 dark:border-white"></div>
    </div>
  );
}
