import React from "react";
import clsx from "clsx";

interface SkeletonProps extends React.HTMLAttributes<HTMLDivElement> {
  className?: string;
  lines?: number;
}

export const Skeleton: React.FC<SkeletonProps> = ({ className, lines = 1, ...props }) => {
  if (lines > 1) {
    return (
      <div className="space-y-2">
        {Array.from({ length: lines }).map((_, i) => (
          <div
            key={i}
            className={clsx(
              "animate-pulse bg-slate-800 rounded",
              i === lines - 1 ? "w-4/5" : "w-full",
              className || "h-4"
            )}
            {...props}
          />
        ))}
      </div>
    );
  }

  return (
    <div
      className={clsx("animate-pulse bg-slate-800 rounded", className || "h-4 w-full")}
      {...props}
    />
  );
};
