import React from "react";
import clsx from "clsx";

interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  children: React.ReactNode;
  className?: string;
}

export const Card: React.FC<CardProps> = ({ children, className, ...props }) => {
  return (
    <div
      className={clsx(
        "bg-slate-900 border border-slate-800 rounded-md shadow-sm overflow-hidden",
        className
      )}
      {...props}
    >
      {children}
    </div>
  );
};

export const CardHeader: React.FC<{
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  action?: React.ReactNode;
  className?: string;
  children?: React.ReactNode;
}> = ({ title, subtitle, action, className, children }) => {
  if (children) {
    return (
      <div className={clsx("px-4 py-3 border-b border-slate-800/80 bg-slate-900/50", className)}>
        {children}
      </div>
    );
  }

  return (
    <div
      className={clsx(
        "px-4 py-3 border-b border-slate-800/80 bg-slate-900/50 flex items-center justify-between gap-4",
        className
      )}
    >
      <div>
        {title && <h3 className="text-sm font-medium text-slate-100">{title}</h3>}
        {subtitle && <p className="text-xs text-slate-400 mt-0.5">{subtitle}</p>}
      </div>
      {action && <div className="flex items-center gap-2">{action}</div>}
    </div>
  );
};

export const CardBody: React.FC<CardProps> = ({ children, className, ...props }) => {
  return (
    <div className={clsx("p-4", className)} {...props}>
      {children}
    </div>
  );
};
