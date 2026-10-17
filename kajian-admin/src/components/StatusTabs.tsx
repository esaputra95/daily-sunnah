"use client";

import { useState, useTransition, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Clock, CheckCircle2, XCircle, Layers, Loader2 } from "lucide-react";
import { StatusCounts } from "@/lib/types";

interface StatusTabsProps {
  currentStatus: string;
  counts: StatusCounts;
}

export function StatusTabs({ currentStatus, counts }: StatusTabsProps) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [optimisticStatus, setOptimisticStatus] = useState<string | null>(null);
  const [isPending, startTransition] = useTransition();

  // Reset optimistic state when currentStatus prop syncs from server
  useEffect(() => {
    setOptimisticStatus(null);
  }, [currentStatus]);

  const activeStatus = optimisticStatus ?? currentStatus ?? "pending";

  const handleTabChange = (status: string) => {
    if (status === activeStatus) return;
    setOptimisticStatus(status);
    const params = new URLSearchParams(searchParams.toString());
    if (status === "all") {
      params.delete("status");
    } else {
      params.set("status", status);
    }
    params.delete("page"); // Reset ke halaman pertama saat berganti tab
    startTransition(() => {
      router.push(`/catalog?${params.toString()}`);
    });
  };

  const tabs = [
    {
      id: "pending",
      label: "Pending Review",
      icon: Clock,
      count: counts.pending,
      activeColor: "border-amber-500 text-amber-700 bg-amber-50/70",
      badgeColor: "bg-amber-100 text-amber-800",
    },
    {
      id: "approved",
      label: "Disetujui (Approved)",
      icon: CheckCircle2,
      count: counts.approved,
      activeColor: "border-emerald-600 text-emerald-700 bg-emerald-50/70",
      badgeColor: "bg-emerald-100 text-emerald-800",
    },
    {
      id: "rejected",
      label: "Ditolak (Rejected)",
      icon: XCircle,
      count: counts.rejected,
      activeColor: "border-rose-500 text-rose-700 bg-rose-50/70",
      badgeColor: "bg-rose-100 text-rose-800",
    },
    {
      id: "all",
      label: "Semua Katalog",
      icon: Layers,
      count: counts.all,
      activeColor: "border-slate-800 text-slate-900 bg-slate-100",
      badgeColor: "bg-slate-200 text-slate-800",
    },
  ];

  return (
    <div className="border-b border-slate-200">
      <nav className="-mb-px flex space-x-2 sm:space-x-4 overflow-x-auto pb-1" aria-label="Tabs">
        {tabs.map((tab) => {
          const isActive =
            activeStatus === tab.id ||
            (!activeStatus && tab.id === "pending");
          const Icon = tab.icon;

          return (
            <button
              key={tab.id}
              onClick={() => handleTabChange(tab.id)}
              className={`group inline-flex items-center gap-2 whitespace-nowrap rounded-t-xl border-b-2 px-3 sm:px-4 py-3 text-xs sm:text-sm font-medium transition-all ${
                isActive
                  ? `${tab.activeColor} border-b-2 font-semibold`
                  : "border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-700 hover:bg-slate-50"
              }`}
            >
              {isActive && isPending ? (
                <Loader2 className="h-4 w-4 animate-spin text-current" />
              ) : (
                <Icon
                  className={`h-4 w-4 ${
                    isActive ? "text-current" : "text-slate-400 group-hover:text-slate-600"
                  }`}
                />
              )}
              <span>{tab.label}</span>
              <span
                className={`ml-1 rounded-full px-2 py-0.5 text-xs font-semibold ${
                  isActive ? tab.badgeColor : "bg-slate-100 text-slate-600 group-hover:bg-slate-200"
                }`}
              >
                {tab.count}
              </span>
            </button>
          );
        })}
      </nav>
    </div>
  );
}
