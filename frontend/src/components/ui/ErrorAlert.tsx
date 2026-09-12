import React, { useState } from "react";
import { AlertTriangle, RefreshCw, ChevronDown, ChevronUp } from "lucide-react";
import { Button } from "./Button";
import clsx from "clsx";

interface ErrorAlertProps {
  title?: string;
  message: string;
  code?: string;
  traceId?: string;
  details?: Record<string, any>;
  onRetry?: () => void;
  className?: string;
}

export const ErrorAlert: React.FC<ErrorAlertProps> = ({
  title = "Service Interruption",
  message,
  code,
  traceId,
  details,
  onRetry,
  className,
}) => {
  const [showDetails, setShowDetails] = useState(false);

  return (
    <div
      role="alert"
      className={clsx(
        "p-4 rounded-md border border-rose-500/30 bg-rose-950/20 text-slate-200",
        className
      )}
    >
      <div className="flex items-start gap-3">
        <AlertTriangle className="w-5 h-5 text-rose-400 mt-0.5 flex-shrink-0" />
        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <h4 className="text-sm font-semibold text-rose-300">{title}</h4>
            {code && (
              <span className="font-mono text-[10px] px-1.5 py-0.5 rounded bg-rose-900/40 text-rose-300 border border-rose-700/50">
                {code}
              </span>
            )}
          </div>
          <p className="text-xs text-slate-300 mt-1">{message}</p>

          {(traceId || details) && (
            <div className="mt-2">
              <button
                type="button"
                onClick={() => setShowDetails(!showDetails)}
                className="text-[11px] text-slate-400 hover:text-slate-200 flex items-center gap-1 font-mono"
              >
                {showDetails ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                {showDetails ? "Hide diagnostics" : "Show diagnostics"}
              </button>

              {showDetails && (
                <div className="mt-2 p-2.5 rounded bg-slate-950 border border-slate-800 text-[11px] font-mono space-y-1">
                  {traceId && (
                    <div className="text-slate-400">
                      <span className="text-slate-500">Trace ID:</span> {traceId}
                    </div>
                  )}
                  {details && (
                    <pre className="text-slate-300 overflow-x-auto text-[10px] p-1 bg-slate-900 rounded">
                      {JSON.stringify(details, null, 2)}
                    </pre>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {onRetry && (
          <Button
            variant="secondary"
            size="xs"
            onClick={onRetry}
            leftIcon={<RefreshCw className="w-3 h-3" />}
            className="flex-shrink-0"
          >
            Retry
          </Button>
        )}
      </div>
    </div>
  );
};
