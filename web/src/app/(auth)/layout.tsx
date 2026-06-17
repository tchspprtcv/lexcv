export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="flex-1 flex items-center justify-center p-6 bg-neutral-50 dark:bg-black">
      <div className="w-full max-w-md">{children}</div>
    </div>
  );
}
