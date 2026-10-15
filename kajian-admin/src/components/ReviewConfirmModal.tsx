"use client";

import { CheckCircle2, XCircle, AlertTriangle } from "lucide-react";
import { KajianCategory, KajianContentType, ReviewStatus } from "@/lib/types";

interface ReviewConfirmModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConfirm: () => void;
  isLoading: boolean;
  actionType: "approve" | "reject";
  videoTitle: string;
  category: KajianCategory;
  contentType: KajianContentType;
}

export function ReviewConfirmModal({
  isOpen,
  onClose,
  onConfirm,
  isLoading,
  actionType,
  videoTitle,
  category,
  contentType,
}: ReviewConfirmModalProps) {
  if (!isOpen) return null;

  const isApprove = actionType === "approve";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/60 backdrop-blur-xs animate-in fade-in duration-150">
      <div className="fixed inset-0" onClick={isLoading ? undefined : onClose} />

      <div className="relative z-10 w-full max-w-md overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-slate-900/10">
        <div className="p-6">
          <div className="flex items-start gap-4">
            <div
              className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${
                isApprove ? "bg-emerald-100 text-emerald-600" : "bg-rose-100 text-rose-600"
              }`}
            >
              {isApprove ? (
                <CheckCircle2 className="h-6 w-6" />
              ) : (
                <XCircle className="h-6 w-6" />
              )}
            </div>
            <div>
              <h3 className="text-base font-semibold text-slate-900">
                {isApprove
                  ? "Setujui Video (Approve)?"
                  : "Tolak Video (Reject)?"}
              </h3>
              <p className="mt-1 text-xs text-slate-500 leading-relaxed">
                {isApprove
                  ? "Video akan disetujui dan langsung dapat dilihat oleh publik di aplikasi Android Daily Sunnah."
                  : "Video akan ditandai Ditolak dan tidak akan ditampilkan di aplikasi Android Daily Sunnah."}
              </p>
            </div>
          </div>

          <div className="mt-4 rounded-xl bg-slate-50 p-3.5 border border-slate-100 text-xs space-y-2">
            <div>
              <span className="text-slate-400 block text-[11px]">Judul Video:</span>
              <span className="font-medium text-slate-800 line-clamp-2">
                {videoTitle}
              </span>
            </div>
            <div className="grid grid-cols-2 gap-2 pt-2 border-t border-slate-200/60">
              <div>
                <span className="text-slate-400 block text-[11px]">Kategori Terpilih:</span>
                <span className="font-semibold text-emerald-700">{category}</span>
              </div>
              <div>
                <span className="text-slate-400 block text-[11px]">Jenis Konten:</span>
                <span className="font-semibold text-slate-800">
                  {contentType === "kajian" ? "Kajian Panjang" : "Kajian Short"}
                </span>
              </div>
            </div>
          </div>

          <div className="mt-3 flex items-center gap-1.5 text-[11px] text-amber-700 bg-amber-50 rounded-lg p-2 border border-amber-200/60">
            <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
            <span>Timestamp <code>reviewed_at</code> akan otomatis diisi dengan waktu saat ini.</span>
          </div>

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
              onClick={onConfirm}
              className={`inline-flex items-center gap-1.5 rounded-xl px-4 py-2 text-xs font-semibold text-white shadow-xs transition-colors disabled:opacity-50 ${
                isApprove
                  ? "bg-emerald-600 hover:bg-emerald-700"
                  : "bg-rose-600 hover:bg-rose-700"
              }`}
            >
              {isLoading ? (
                <>
                  <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  <span>Memproses...</span>
                </>
              ) : (
                <span>
                  {isApprove ? "Ya, Setujui Video" : "Ya, Tolak Video"}
                </span>
              )}
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
