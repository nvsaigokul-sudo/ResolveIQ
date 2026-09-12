import React from "react";
import clsx from "clsx";

export interface TabItem {
  id: string;
  label: string;
  count?: number;
  icon?: React.ReactNode;
}

interface TabsProps {
  tabs: TabItem[];
  activeTab: string;
  onChange: (tabId: string) => void;
  className?: string;
}

export const Tabs: React.FC<TabsProps> = ({ tabs, activeTab, onChange, className }) => {
  return (
    <div
      role="tablist"
      className={clsx(
        "flex items-center gap-1 border-b border-slate-800 bg-slate-950/40 px-3 pt-1 overflow-x-auto select-none",
        className
      )}
    >
      {tabs.map((tab) => {
        const isActive = tab.id === activeTab;
        return (
          <button
            key={tab.id}
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(tab.id)}
            className={clsx(
              "flex items-center gap-2 px-3 py-2 text-xs font-medium border-b-2 transition-all duration-150 relative -mb-[1px] whitespace-nowrap",
              isActive
                ? "text-indigo-400 border-indigo-500 bg-slate-900/60 rounded-t"
                : "text-slate-400 border-transparent hover:text-slate-200 hover:border-slate-700"
            )}
          >
            {tab.icon && <span className="w-3.5 h-3.5">{tab.icon}</span>}
            <span>{tab.label}</span>
            {tab.count !== undefined && (
              <span
                className={clsx(
                  "text-[10px] px-1.5 py-0.2 rounded-full font-mono",
                  isActive ? "bg-indigo-500/20 text-indigo-300" : "bg-slate-800 text-slate-400"
                )}
              >
                {tab.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
};
