import React from "react";
import clsx from "clsx";
import { Loader2 } from "lucide-react";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "secondary" | "danger" | "ghost" | "outline";
  size?: "xs" | "sm" | "md" | "lg";
  isLoading?: boolean;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  (
    {
      children,
      variant = "primary",
      size = "sm",
      isLoading = false,
      leftIcon,
      rightIcon,
      disabled,
      className,
      ...props
    },
    ref
  ) => {
    const baseStyles =
      "inline-flex items-center justify-center font-medium font-sans transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-offset-1 focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed select-none rounded";

    const variantStyles = {
      primary:
        "bg-indigo-600 hover:bg-indigo-500 text-white focus:ring-indigo-500 border border-indigo-500/50 shadow-sm",
      secondary:
        "bg-slate-800 hover:bg-slate-700 text-slate-200 focus:ring-slate-400 border border-slate-700 shadow-sm",
      danger:
        "bg-rose-600 hover:bg-rose-500 text-white focus:ring-rose-500 border border-rose-500/50 shadow-sm",
      ghost:
        "bg-transparent hover:bg-slate-800 text-slate-300 hover:text-white focus:ring-slate-500",
      outline:
        "bg-transparent hover:bg-slate-800 text-slate-200 border border-slate-700 focus:ring-slate-500",
    };

    const sizeStyles = {
      xs: "text-xs px-2 py-1 gap-1.5",
      sm: "text-xs px-3 py-1.5 gap-1.5",
      md: "text-sm px-4 py-2 gap-2",
      lg: "text-base px-5 py-2.5 gap-2.5",
    };

    return (
      <button
        ref={ref}
        disabled={disabled || isLoading}
        className={clsx(baseStyles, variantStyles[variant], sizeStyles[size], className)}
        {...props}
      >
        {isLoading ? (
          <Loader2 className="w-3.5 h-3.5 animate-spin" />
        ) : (
          leftIcon && <span className="flex-shrink-0">{leftIcon}</span>
        )}
        <span>{children}</span>
        {!isLoading && rightIcon && <span className="flex-shrink-0">{rightIcon}</span>}
      </button>
    );
  }
);

Button.displayName = "Button";
