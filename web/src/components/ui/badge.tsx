import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-neutral-900 text-neutral-50 dark:bg-neutral-50 dark:text-neutral-900",
        secondary:
          "border-transparent bg-neutral-100 text-neutral-900 dark:bg-neutral-800 dark:text-neutral-50",
        outline:
          "border-neutral-200 text-neutral-900 dark:border-neutral-800 dark:text-neutral-50",
        blue: "border-transparent bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-400",
        green: "border-transparent bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-400",
        amber: "border-transparent bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-400",
        red: "border-transparent bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-400",
        purple: "border-transparent bg-purple-100 text-purple-700 dark:bg-purple-500/20 dark:text-purple-400",
        gray: "border-transparent bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-400",
      },
    },
    defaultVariants: {
      variant: "secondary",
    },
  },
);

export function Badge({
  className,
  variant,
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return <span data-slot="badge" className={cn(badgeVariants({ variant }), className)} {...props} />;
}
