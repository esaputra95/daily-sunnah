"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { ChevronLeft, ChevronRight } from "lucide-react";

interface PaginationProps {
  currentPage: number;
  totalPages: number;
  totalCount: number;
  pageSize: number;
}

export function Pagination({
  currentPage,
  totalPages,
  totalCount,
  pageSize,
}: PaginationProps) {
  const router = useRouter();
  const searchParams = useSearchParams();

  if (totalPages <= 1 && totalCount <= pageSize) {
    return (
      <div className="mt-6 flex items-center justify-between text-xs text-slate-500">
        <span>Menampilkan {totalCount} video</span>
      </div>
    );
  }

  const handlePageChange = (newPage: number) => {
    if (newPage < 1 || newPage > totalPages) return;
    const params = new URLSearchParams(searchParams.toString());
    params.set("page", newPage.toString());
    router.push(`/catalog?${params.toString()}`);
  };

  const startItem = (currentPage - 1) * pageSize + 1;
  const endItem = Math.min(currentPage * pageSize, totalCount);

  return (
    <div className="mt-8 flex flex-col sm:flex-row items-center justify-between gap-4 border-t border-slate-200 pt-6">
      <div className="text-xs text-slate-500">
        Menampilkan{" "}
        <span className="font-semibold text-slate-800">{startItem}</span> -{" "}
        <span className="font-semibold text-slate-800">{endItem}</span> dari{" "}
        <span className="font-semibold text-slate-800">{totalCount}</span> video
      </div>

      <div className="flex items-center gap-1.5">
        <button
          type="button"
          onClick={() => handlePageChange(currentPage - 1)}
          disabled={currentPage <= 1}
          className="inline-flex items-center gap-1 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-medium text-slate-700 shadow-2xs hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <ChevronLeft className="h-4 w-4" />
          <span>Sebelumnya</span>
        </button>

        <div className="hidden sm:flex items-center gap-1 px-2">
          {Array.from({ length: totalPages }, (_, i) => i + 1)
            .filter((p) => {
              // Show first, last, current, and neighbours
              return (
                p === 1 ||
                p === totalPages ||
                Math.abs(p - currentPage) <= 1
              );
            })
            .map((p, index, array) => {
              const prevPage = array[index - 1];
              const showEllipsis = prevPage && p - prevPage > 1;

              return (
                <div key={p} className="flex items-center">
                  {showEllipsis && (
                    <span className="px-2 text-xs text-slate-400">...</span>
                  )}
                  <button
                    type="button"
                    onClick={() => handlePageChange(p)}
                    className={`h-8 w-8 rounded-lg text-xs font-medium transition-colors ${
                      currentPage === p
                        ? "bg-emerald-600 text-white font-semibold shadow-xs"
                        : "text-slate-600 hover:bg-slate-100"
                    }`}
                  >
                    {p}
                  </button>
                </div>
              );
            })}
        </div>

        <button
          type="button"
          onClick={() => handlePageChange(currentPage + 1)}
          disabled={currentPage >= totalPages}
          className="inline-flex items-center gap-1 rounded-xl border border-slate-200 bg-white px-3 py-2 text-xs font-medium text-slate-700 shadow-2xs hover:bg-slate-50 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          <span>Berikutnya</span>
          <ChevronRight className="h-4 w-4" />
        </button>
      </div>
    </div>
  );
}
