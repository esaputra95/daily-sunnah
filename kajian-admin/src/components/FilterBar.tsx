"use client";

import { useEffect, useState, useTransition } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { Search, Filter, X, ArrowDownWideNarrow } from "lucide-react";
import { VALID_CATEGORIES, VALID_CONTENT_TYPES } from "@/lib/types";

export function FilterBar() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [isPending, startTransition] = useTransition();

  const currentSearch = searchParams.get("q") || "";
  const currentCategory = searchParams.get("category") || "";
  const currentContentType = searchParams.get("content_type") || "";
  const currentSort = searchParams.get("sort") || "newest";
  const currentMinDuration = searchParams.get("min_minutes") || "";
  const currentMaxDuration = searchParams.get("max_minutes") || "";

  const [searchTerm, setSearchTerm] = useState(currentSearch);
  const [minDuration, setMinDuration] = useState(currentMinDuration);
  const [maxDuration, setMaxDuration] = useState(currentMaxDuration);
  const [durationError, setDurationError] = useState("");

  useEffect(() => {
    setSearchTerm(currentSearch);
    setMinDuration(currentMinDuration);
    setMaxDuration(currentMaxDuration);
  }, [currentSearch, currentMinDuration, currentMaxDuration]);

  const applyFilters = (updates: Record<string, string | null>) => {
    startTransition(() => {
      const params = new URLSearchParams(searchParams.toString());
      Object.entries(updates).forEach(([key, value]) => {
        if (!value || value === "all") {
          params.delete(key);
        } else {
          params.set(key, value);
        }
      });
      params.delete("page"); // Reset ke page 1
      router.push(`/catalog?${params.toString()}`);
    });
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    applyFilters({ q: searchTerm.trim() });
  };

  const applyDuration = () => {
    const min = minDuration === "" ? null : Number(minDuration);
    const max = maxDuration === "" ? null : Number(maxDuration);
    if ((min !== null && (!Number.isInteger(min) || min < 0)) || (max !== null && (!Number.isInteger(max) || max < 0))) {
      setDurationError("Durasi harus berupa menit nol atau lebih.");
      return;
    }
    if (min !== null && max !== null && min > max) {
      setDurationError("Durasi minimum tidak boleh melebihi maksimum.");
      return;
    }
    setDurationError("");
    applyFilters({ min_minutes: min === null ? null : String(min), max_minutes: max === null ? null : String(max) });
  };

  const handleReset = () => {
    setSearchTerm("");
    startTransition(() => {
      const params = new URLSearchParams();
      const status = searchParams.get("status");
      if (status) params.set("status", status);
      router.push(`/catalog?${params.toString()}`);
    });
  };

  const hasActiveFilters = Boolean(
    currentSearch || currentCategory || currentContentType || currentMinDuration || currentMaxDuration || (currentSort && currentSort !== "newest")
  );

  return (
    <div className="bg-white p-4 rounded-2xl border border-slate-200/80 shadow-xs mb-6">
      <div className="flex flex-col lg:flex-row gap-3">
        {/* Search Bar */}
        <form onSubmit={handleSearchSubmit} className="relative flex-1">
          <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5">
            <Search className="h-4 w-4 text-slate-400" />
          </div>
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Cari judul video atau nama channel..."
            className="w-full rounded-xl border border-slate-200 bg-slate-50/50 pl-10 pr-20 py-2.5 text-xs sm:text-sm text-slate-900 placeholder:text-slate-400 focus:border-emerald-500 focus:bg-white focus:outline-hidden focus:ring-2 focus:ring-emerald-500/20"
          />
          {searchTerm && (
            <button
              type="button"
              onClick={() => {
                setSearchTerm("");
                applyFilters({ q: null });
              }}
              className="absolute inset-y-0 right-12 flex items-center pr-2 text-slate-400 hover:text-slate-600"
            >
              <X className="h-4 w-4" />
            </button>
          )}
          <button
            type="submit"
            disabled={isPending}
            className="absolute right-1.5 top-1.5 bottom-1.5 rounded-lg bg-emerald-600 px-3 text-xs font-medium text-white hover:bg-emerald-700 transition-colors disabled:opacity-50"
          >
            {isPending ? "..." : "Cari"}
          </button>
        </form>

        {/* Filters */}
        <div className="flex flex-wrap sm:flex-nowrap items-center gap-2 sm:gap-3">
          {/* Category Dropdown */}
          <div className="relative min-w-[150px] flex-1 sm:flex-none">
            <select
              value={currentCategory}
              onChange={(e) => applyFilters({ category: e.target.value })}
              className="w-full appearance-none rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 pr-8 text-xs sm:text-sm text-slate-700 hover:border-slate-300 focus:border-emerald-500 focus:outline-hidden focus:ring-2 focus:ring-emerald-500/20 cursor-pointer"
            >
              <option value="">Semua Kategori</option>
              {VALID_CATEGORIES.map((cat) => (
                <option key={cat} value={cat}>
                  {cat}
                </option>
              ))}
            </select>
            <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
              <Filter className="h-3.5 w-3.5" />
            </div>
          </div>

          {/* Content Type Dropdown */}
          <div className="relative min-w-[130px] flex-1 sm:flex-none">
            <select
              value={currentContentType}
              onChange={(e) => applyFilters({ content_type: e.target.value })}
              className="w-full appearance-none rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 pr-8 text-xs sm:text-sm text-slate-700 hover:border-slate-300 focus:border-emerald-500 focus:outline-hidden focus:ring-2 focus:ring-emerald-500/20 cursor-pointer"
            >
              <option value="">Semua Jenis</option>
              {VALID_CONTENT_TYPES.map((type) => (
                <option key={type} value={type}>
                  {type === "kajian" ? "Kajian Panjang" : "Kajian Short"}
                </option>
              ))}
            </select>
            <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
              <Filter className="h-3.5 w-3.5" />
            </div>
          </div>

          {/* Sort Order */}
          <div className="relative min-w-[150px] flex-1 sm:flex-none">
            <select
              value={currentSort}
              onChange={(e) => applyFilters({ sort: e.target.value })}
              className="w-full appearance-none rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 pr-8 text-xs sm:text-sm text-slate-700 hover:border-slate-300 focus:border-emerald-500 focus:outline-hidden focus:ring-2 focus:ring-emerald-500/20 cursor-pointer"
            >
              <option value="newest">Terbaru Rilis (YouTube)</option>
              <option value="oldest">Terlama Rilis (YouTube)</option>
              <option value="created_desc">Terbaru Masuk Sistem</option>
              <option value="duration_desc">Durasi Terpanjang</option>
              <option value="duration_asc">Durasi Terpendek</option>
            </select>
            <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2.5 text-slate-400">
              <ArrowDownWideNarrow className="h-3.5 w-3.5" />
            </div>
          </div>

          {/* Reset Filters */}
          {hasActiveFilters && (
            <button
              onClick={handleReset}
              className="inline-flex items-center gap-1.5 rounded-xl border border-rose-200 bg-rose-50/50 px-3 py-2.5 text-xs font-medium text-rose-700 hover:bg-rose-100 transition-colors"
              title="Reset semua filter dan pencarian"
            >
              <X className="h-3.5 w-3.5" />
              <span>Reset</span>
            </button>
          )}
        </div>
      </div>

      <div className="mt-3 flex flex-wrap items-end gap-2 border-t border-slate-100 pt-3">
        <div>
          <label htmlFor="min-duration" className="mb-1 block text-[11px] font-medium text-slate-500">Durasi minimum (menit)</label>
          <input id="min-duration" type="number" min="0" step="1" value={minDuration} onChange={(event) => setMinDuration(event.target.value)} placeholder="Tanpa batas" className="w-36 rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-700 outline-none focus:border-emerald-500" />
        </div>
        <div>
          <label htmlFor="max-duration" className="mb-1 block text-[11px] font-medium text-slate-500">Durasi maksimum (menit)</label>
          <input id="max-duration" type="number" min="0" step="1" value={maxDuration} onChange={(event) => setMaxDuration(event.target.value)} placeholder="Tanpa batas" className="w-36 rounded-xl border border-slate-200 px-3 py-2 text-xs text-slate-700 outline-none focus:border-emerald-500" />
        </div>
        <button type="button" onClick={applyDuration} disabled={isPending} className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50">Terapkan durasi</button>
        {durationError && <span role="alert" className="text-xs text-rose-700">{durationError}</span>}
      </div>
    </div>
  );
}
