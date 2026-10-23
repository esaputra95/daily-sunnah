"use client";

import { useState } from "react";
import { KajianCategory, KajianContentType, KajianVideo } from "@/lib/types";
import { VideoCard } from "./VideoCard";
import { BulkReviewConfirmModal } from "./BulkReviewConfirmModal";
import { bulkUpdateEditorialAction } from "@/app/actions/editorial";
import {
  CheckCircle2,
  XCircle,
  AlertCircle,
  X,
  Film,
  CheckSquare,
  Square,
  Sparkles,
} from "lucide-react";

interface VideoListProps {
  videos: KajianVideo[];
}

export function VideoList({ videos }: VideoListProps) {
  const [selectedIds, setSelectedIds] = useState<Set<string | number>>(new Set());
  const [toast, setToast] = useState<{
    type: "success" | "error";
    message: string;
  } | null>(null);

  // Bulk Modal state
  const [bulkModalState, setBulkModalState] = useState<{
    isOpen: boolean;
    actionType: "approve" | "reject";
  }>({
    isOpen: false,
    actionType: "approve",
  });
  const [isBulkLoading, setIsBulkLoading] = useState(false);

  const showNotification = (type: "success" | "error", message: string) => {
    setToast({ type, message });
    setTimeout(() => {
      setToast((current) => (current?.message === message ? null : current));
    }, 4500);
  };

  const handleToggleSelect = (id: string | number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const isAllSelected =
    videos.length > 0 && videos.every((v) => selectedIds.has(v.id));

  const handleToggleSelectAll = () => {
    if (isAllSelected) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(videos.map((v) => v.id)));
    }
  };

  const handleClearSelection = () => {
    setSelectedIds(new Set());
  };

  const handleOpenBulkModal = (actionType: "approve" | "reject") => {
    if (selectedIds.size === 0) return;
    setBulkModalState({
      isOpen: true,
      actionType,
    });
  };

  const handleExecuteBulk = async (options?: {
    category?: KajianCategory;
    contentType?: KajianContentType;
  }) => {
    if (selectedIds.size === 0) return;

    setIsBulkLoading(true);
    const targetStatus =
      bulkModalState.actionType === "approve" ? "approved" : "rejected";

    try {
      const result = await bulkUpdateEditorialAction({
        ids: Array.from(selectedIds),
        review_status: targetStatus,
        category: options?.category,
        content_type: options?.contentType,
      });

      if (result.success) {
        showNotification(
          "success",
          result.message ||
            `Berhasil memperbarui ${selectedIds.size} video.`
        );
        setSelectedIds(new Set());
        setBulkModalState((prev) => ({ ...prev, isOpen: false }));
      } else {
        showNotification(
          "error",
          result.error || "Gagal melakukan pembaruan massal."
        );
      }
    } catch (err: unknown) {
      const errorMsg =
        err instanceof Error ? err.message : "Terjadi kesalahan sistem.";
      showNotification("error", errorMsg);
    } finally {
      setIsBulkLoading(false);
    }
  };

  if (videos.length === 0) {
    return (
      <div className="rounded-2xl border border-dashed border-slate-300 bg-white p-12 text-center">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400 mb-4">
          <Film className="h-7 w-7" />
        </div>
        <h3 className="text-sm font-semibold text-slate-900">
          Tidak ada video kajian ditemukan
        </h3>
        <p className="mt-1 text-xs text-slate-500 max-w-sm mx-auto">
          Tidak ada data yang cocok dengan kriteria filter, status, atau kata kunci pencarian yang dipilih.
        </p>
      </div>
    );
  }

  return (
    <>
      {/* Toast Notification Container */}
      {toast && (
        <div className="fixed bottom-6 right-6 z-50 animate-in slide-in-from-bottom-5 duration-200">
          <div
            className={`flex items-center gap-3 rounded-2xl p-4 shadow-xl ring-1 ${
              toast.type === "success"
                ? "bg-emerald-900 text-white ring-emerald-700/50"
                : "bg-rose-900 text-white ring-rose-700/50"
            }`}
          >
            {toast.type === "success" ? (
              <CheckCircle2 className="h-5 w-5 text-emerald-400 shrink-0" />
            ) : (
              <AlertCircle className="h-5 w-5 text-rose-400 shrink-0" />
            )}
            <span className="text-xs font-medium pr-2 max-w-md">
              {toast.message}
            </span>
            <button
              onClick={() => setToast(null)}
              className="text-white/60 hover:text-white rounded-lg p-1"
            >
              <X className="h-4 w-4" />
            </button>
          </div>
        </div>
      )}

      {/* Top Selection & Quick Control Bar */}
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-xl border border-slate-200/80 bg-white px-4 py-2.5 text-xs shadow-2xs">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={handleToggleSelectAll}
            className="inline-flex items-center gap-2 font-medium text-slate-700 hover:text-slate-900 cursor-pointer transition-colors"
          >
            {isAllSelected ? (
              <CheckSquare className="h-4 w-4 text-emerald-600" />
            ) : (
              <Square className="h-4 w-4 text-slate-400" />
            )}
            <span>
              {isAllSelected
                ? "Batalkan Pilih Semua"
                : `Pilih Semua (${videos.length} di halaman ini)`}
            </span>
          </button>

          {selectedIds.size > 0 && (
            <span className="inline-flex items-center rounded-full bg-emerald-50 px-2.5 py-0.5 text-[11px] font-semibold text-emerald-700 border border-emerald-200/80">
              {selectedIds.size} dipilih
            </span>
          )}
        </div>

        {selectedIds.size > 0 ? (
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={handleClearSelection}
              className="text-slate-500 hover:text-slate-700 px-2 py-1 text-xs font-medium cursor-pointer"
            >
              Batal
            </button>
            <button
              type="button"
              onClick={() => handleOpenBulkModal("approve")}
              className="inline-flex items-center gap-1.5 rounded-lg bg-emerald-600 px-3 py-1.5 text-xs font-semibold text-white shadow-2xs hover:bg-emerald-700 active:scale-[0.98] transition-all cursor-pointer"
            >
              <CheckCircle2 className="h-3.5 w-3.5" />
              <span>Approve Terpilih ({selectedIds.size})</span>
            </button>
            <button
              type="button"
              onClick={() => handleOpenBulkModal("reject")}
              className="inline-flex items-center gap-1.5 rounded-lg border border-rose-200 bg-rose-50 px-3 py-1.5 text-xs font-semibold text-rose-700 shadow-2xs hover:bg-rose-100 hover:border-rose-300 active:scale-[0.98] transition-all cursor-pointer"
            >
              <XCircle className="h-3.5 w-3.5" />
              <span>Reject Terpilih ({selectedIds.size})</span>
            </button>
          </div>
        ) : (
          <span className="text-[11px] text-slate-400 italic">
            Centang video untuk aksi persetujuan atau penolakan massal
          </span>
        )}
      </div>

      {/* Grid of Video Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {videos.map((video) => (
          <VideoCard
            key={video.id}
            video={video}
            onNotification={showNotification}
            isSelected={selectedIds.has(video.id)}
            onToggleSelect={handleToggleSelect}
          />
        ))}
      </div>

      {/* Floating Bottom Action Bar for Bulk Selection */}
      {selectedIds.size > 0 && (
        <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-40 animate-in slide-in-from-bottom-6 duration-200 w-[92%] max-w-xl">
          <div className="flex items-center justify-between gap-3 rounded-2xl bg-slate-900/95 text-white p-3.5 sm:px-5 shadow-2xl backdrop-blur-md border border-slate-700/60 ring-1 ring-white/10">
            <div className="flex items-center gap-2.5">
              <span className="flex h-6 w-6 items-center justify-center rounded-full bg-emerald-500 text-[11px] font-bold text-slate-950">
                {selectedIds.size}
              </span>
              <span className="text-xs font-medium text-slate-200">
                Video terpilih
              </span>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleClearSelection}
                className="rounded-xl px-3 py-1.5 text-xs font-medium text-slate-300 hover:text-white hover:bg-slate-800 transition-colors cursor-pointer"
              >
                Batal
              </button>

              <button
                type="button"
                onClick={() => handleOpenBulkModal("approve")}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-3.5 py-2 text-xs font-semibold text-white shadow-md hover:bg-emerald-500 active:scale-[0.98] transition-all cursor-pointer"
              >
                <CheckCircle2 className="h-4 w-4" />
                <span>Approve ({selectedIds.size})</span>
              </button>

              <button
                type="button"
                onClick={() => handleOpenBulkModal("reject")}
                className="inline-flex items-center gap-1.5 rounded-xl bg-rose-600/90 px-3.5 py-2 text-xs font-semibold text-white shadow-md hover:bg-rose-500 active:scale-[0.98] transition-all cursor-pointer"
              >
                <XCircle className="h-4 w-4" />
                <span>Reject ({selectedIds.size})</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Bulk Confirmation Modal */}
      <BulkReviewConfirmModal
        isOpen={bulkModalState.isOpen}
        onClose={() =>
          setBulkModalState((prev) => ({ ...prev, isOpen: false }))
        }
        onConfirm={handleExecuteBulk}
        isLoading={isBulkLoading}
        actionType={bulkModalState.actionType}
        selectedCount={selectedIds.size}
      />
    </>
  );
}
