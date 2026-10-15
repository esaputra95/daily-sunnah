"use client";

import { useState } from "react";
import { CheckCircle2, XCircle, AlertTriangle, Sparkles, Tag, Video } from "lucide-react";
import { KajianCategory, KajianContentType, VALID_CATEGORIES, VALID_CONTENT_TYPES } from "@/lib/types";

interface BulkReviewConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: (options?: {
    category?: KajianCategory;
    contentType?: KajianContentType;
  }) => Promise<void>;
  isLoading: boolean;
  actionType: "approve" | "reject";
  selectedCount: number;
}

export function BulkReviewConfirmModal({
  isOpen,
  onClose,
  onConfirm,
  isLoading,
  actionType,
  selectedCount,
}: BulkReviewConfirmModalProps) {
  const [applyCategory, setApplyCategory] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState<KajianCategory>("Tazkiyatun Nufus");

  const [applyContentType, setApplyContentType] = useState(false);
  const [selectedContentType, setSelectedContentType] = useState<KajianContentType>("kajian");

  if (!isOpen) return null;

  const isApprove = actionType === "approve";

  const handleConfirm = async () => {
    await onConfirm({
      category: applyCategory ? selectedCategory : undefined,
      contentType: applyContentType ? selectedContentType : undefined,
    });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-in fade-in duration-150">
      <div className="fixed inset-0" onClick={isLoading ? undefined : onClose} />

      <div className="relative z-10 w-full max-w-lg overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-slate-900/10">
        <div className="p-6">
          {/* Header */}
          <div className="flex items-start gap-4">
            <div
              className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl ${
                isApprove ? "bg-emerald-100 text-emerald-600" : "bg-rose-100 text-rose-600"
              }`}
            >
              {isApprove ? (
                <CheckCircle2 className="h-7 w-7" />
              ) : (
                <XCircle className="h-7 w-7" />
              )}
            </div>
            <div>
              <h3 className="text-base font-bold text-slate-900">
                {isApprove
                  ? `Setujui ${selectedCount} Video Sekaligus?`
                  : `Tolak ${selectedCount} Video Sekaligus?`}
              </h3>
              <p className="mt-1 text-xs text-slate-500 leading-relaxed">
                {isApprove
                  ? `Semua ${selectedCount} video terpilih akan diubah statusnya menjadi Approved dan langsung dapat dilihat oleh pengguna aplikasi Android Daily Sunnah.`
                  : `Semua ${selectedCount} video terpilih akan ditandai Rejected dan tidak akan muncul di aplikasi Android Daily Sunnah.`}
              </p>
            </div>
          </div>

          {/* Optional Batch Overrides */}
          <div className="mt-5 rounded-xl border border-slate-200/80 bg-slate-50/70 p-4 space-y-3">
            <div className="text-[11px] font-semibold text-slate-500 uppercase tracking-wider flex items-center gap-1.5">
              <Sparkles className="h-3.5 w-3.5 text-emerald-600" />
              <span>Opsi Penyesuaian Massal (Opsional)</span>
            </div>

            {/* Override Category */}
            <div className="space-y-1.5">
              <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-slate-700">
                <input
                  type="checkbox"
                  checked={applyCategory}
                  onChange={(e) => setApplyCategory(e.target.checked)}
                  disabled={isLoading}
                  className="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500 cursor-pointer"
                />
                <span>Ubah kategori semua video terpilih menjadi:</span>
              </label>
              {applyCategory && (
                <div className="pl-6 animate-in fade-in duration-100">
                  <div className="relative">
                    <select
                      value={selectedCategory}
                      onChange={(e) => setSelectedCategory(e.target.value as KajianCategory)}
                      disabled={isLoading}
                      className="w-full appearance-none rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 pr-7 text-xs text-slate-800 font-medium focus:border-emerald-500 focus:ring-1 focus:ring-emerald-500"
                    >
                      {VALID_CATEGORIES.map((cat) => (
                        <option key={cat} value={cat}>
                          {cat}
                        </option>
                      ))}
                    </select>
                    <Tag className="pointer-events-none absolute right-2.5 top-2 h-3.5 w-3.5 text-slate-400" />
                  </div>
                </div>
              )}
            </div>

            {/* Override Content Type */}
            <div className="space-y-1.5 pt-2 border-t border-slate-200/60">
              <label className="flex items-center gap-2 cursor-pointer text-xs font-medium text-slate-700">
                <input
                  type="checkbox"
                  checked={applyContentType}
                  onChange={(e) => setApplyContentType(e.target.checked)}
                  disabled={isLoading}
                  className="h-4 w-4 rounded border-slate-300 text-emerald-600 focus:ring-emerald-500 cursor-pointer"
                />
                <span>Ubah jenis konten semua video terpilih menjadi:</span>
              </label>
              {applyContentType && (
                <div className="pl-6 grid grid-cols-2 gap-2 animate-in fade-in duration-100">
                  {VALID_CONTENT_TYPES.map((type) => (
                    <button
                      key={type}
                      type="button"
                      disabled={isLoading}
                      onClick={() => setSelectedContentType(type)}
                      className={`rounded-lg border py-1.5 px-3 text-xs font-medium transition-all ${
                        selectedContentType === type
                          ? "border-emerald-500 bg-emerald-50 text-emerald-800 font-semibold shadow-2xs"
                          : "border-slate-200 bg-white text-slate-600 hover:bg-slate-50"
                      }`}
                    >
                      {type === "kajian" ? "Kajian" : "Short"}
                    </button>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Info note */}
          <div className="mt-3 flex items-center gap-2 text-[11px] text-amber-700 bg-amber-50 rounded-lg p-2.5 border border-amber-200/70">
            <AlertTriangle className="h-4 w-4 shrink-0" />
            <span>
              Kolom <code>reviewed_at</code> pada semua {selectedCount} video akan otomatis diperbarui ke waktu sekarang.
            </span>
          </div>

          {/* Actions */}
          <div className="mt-6 flex items-center justify-end gap-3">
            <button
              type="button"
              disabled={isLoading}
              onClick={onClose}
              className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-medium text-slate-700 hover:bg-slate-50 transition-colors disabled:opacity-50"
            >
              Batal
            </button>
            <button
              type="button"
              disabled={isLoading}
              onClick={handleConfirm}
              className={`inline-flex items-center gap-2 rounded-xl px-4 py-2 text-xs font-semibold text-white shadow-xs transition-colors disabled:opacity-50 ${
                isApprove
                  ? "bg-emerald-600 hover:bg-emerald-700 active:scale-[0.98]"
                  : "bg-rose-600 hover:bg-rose-700 active:scale-[0.98]"
              }`}
            >
              {isLoading ? (
                <>
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  <span>Memproses ({selectedCount} video)...</span>
                </>
              ) : (
                <span>
                  {isApprove
                    ? `Ya, Setujui ${selectedCount} Video`
                    : `Ya, Tolak ${selectedCount} Video`}
                </span>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
