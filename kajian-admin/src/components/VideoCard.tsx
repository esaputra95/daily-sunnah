"use client";

import { useState } from "react";
import Image from "next/image";
import {
  Play,
  ExternalLink,
  Clock,
  Calendar,
  CheckCircle2,
  XCircle,
  AlertCircle,
  ChevronDown,
  ChevronUp,
  Tag,
  Video,
  Sparkles,
} from "lucide-react";
import {
  KajianVideo,
  KajianCategory,
  KajianContentType,
  ReviewStatus,
  VALID_CATEGORIES,
} from "@/lib/types";
import { formatDuration, formatDate } from "@/lib/utils";
import { updateEditorialAction } from "@/app/actions/editorial";
import { YouTubePreviewModal } from "./YouTubePreviewModal";
import { ReviewConfirmModal } from "./ReviewConfirmModal";

interface VideoCardProps {
  video: KajianVideo;
  onNotification?: (type: "success" | "error", message: string) => void;
  isSelected?: boolean;
  onToggleSelect?: (id: string | number) => void;
}

export function VideoCard({
  video,
  onNotification,
  isSelected,
  onToggleSelect,
}: VideoCardProps) {
  const [selectedCategory, setSelectedCategory] = useState<KajianCategory>(
    video.category
  );
  const [selectedContentType, setSelectedContentType] =
    useState<KajianContentType>(video.content_type);
  const [currentStatus, setCurrentStatus] = useState<ReviewStatus>(
    video.review_status
  );
  const [reviewedAt, setReviewedAt] = useState<string | null>(
    video.reviewed_at
  );

  const [isDescExpanded, setIsDescExpanded] = useState(false);
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);

  // Modal konfirmasi
  const [confirmModalState, setConfirmModalState] = useState<{
    isOpen: boolean;
    actionType: "approve" | "reject";
  }>({
    isOpen: false,
    actionType: "approve",
  });
  const [isLoading, setIsLoading] = useState(false);

  // Fallback thumbnail jika thumbnail_url kosong
  const thumbnailSrc =
    video.thumbnail_url ||
    `https://img.youtube.com/vi/${video.youtube_video_id}/hqdefault.jpg`;

  const handleOpenConfirm = (actionType: "approve" | "reject") => {
    setConfirmModalState({
      isOpen: true,
      actionType,
    });
  };

  const handleExecuteReview = async () => {
    setIsLoading(true);
    const targetStatus: ReviewStatus =
      confirmModalState.actionType === "approve" ? "approved" : "rejected";

    try {
      const result = await updateEditorialAction({
        id: video.id,
        category: selectedCategory,
        content_type: selectedContentType,
        review_status: targetStatus,
      });

      if (result.success) {
        setCurrentStatus(targetStatus);
        if (result.reviewedAt) {
          setReviewedAt(result.reviewedAt);
        }
        setConfirmModalState((prev) => ({ ...prev, isOpen: false }));
        onNotification?.(
          "success",
          result.message || "Status berhasil diperbarui."
        );
      } else {
        onNotification?.(
          "error",
          result.error || "Gagal memperbarui status video."
        );
      }
    } catch (err: unknown) {
      const errorMsg =
        err instanceof Error ? err.message : "Terjadi kesalahan sistem.";
      onNotification?.("error", errorMsg);
    } finally {
      setIsLoading(false);
    }
  };

  const statusConfig = {
    pending: {
      label: "Pending Review",
      badgeClass: "bg-amber-100 text-amber-800 border-amber-200",
      icon: Clock,
    },
    approved: {
      label: "Disetujui (Approved)",
      badgeClass: "bg-emerald-100 text-emerald-800 border-emerald-200",
      icon: CheckCircle2,
    },
    rejected: {
      label: "Ditolak (Rejected)",
      badgeClass: "bg-rose-100 text-rose-800 border-rose-200",
      icon: XCircle,
    },
  }[currentStatus] || {
    label: currentStatus,
    badgeClass: "bg-slate-100 text-slate-800 border-slate-200",
    icon: AlertCircle,
  };

  const StatusIcon = statusConfig.icon;

  return (
    <div
      className={`group relative rounded-2xl border transition-all flex flex-col justify-between p-4 sm:p-5 shadow-xs hover:shadow-md ${
        isSelected
          ? "border-emerald-500 ring-2 ring-emerald-500/30 bg-emerald-50/15"
          : "border-slate-200/90 bg-white"
      }`}
    >
      <div>
        {/* Thumbnail & Preview Player Overlay */}
        <div className="relative aspect-16/9 w-full overflow-hidden rounded-xl bg-slate-900 shadow-inner">
          <Image
            src={thumbnailSrc}
            alt={video.title}
            fill
            sizes="(max-width: 768px) 100vw, (max-width: 1200px) 50vw, 33vw"
            className="object-cover transition-transform duration-300 group-hover:scale-105"
            unoptimized
          />

          {/* Overlay gradient */}
          <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/20 to-transparent pointer-events-none" />

          {/* Duration Badge */}
          {video.duration_seconds && (
            <div className="absolute bottom-2.5 right-2.5 rounded-md bg-black/80 backdrop-blur-xs px-2 py-0.5 text-[11px] font-mono font-medium text-white shadow-sm">
              {formatDuration(video.duration_seconds)}
            </div>
          )}

          {/* Content Type Pill on Thumbnail */}
          <div className="absolute top-2.5 left-2.5">
            <span
              className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-semibold tracking-wide uppercase shadow-sm ${
                video.content_type === "short"
                  ? "bg-purple-600 text-white"
                  : "bg-blue-600 text-white"
              }`}
            >
              {video.content_type === "short" ? "Short" : "Kajian"}
            </span>
          </div>

          {/* Selection Checkbox */}
          {onToggleSelect && (
            <div
              className="absolute top-2.5 right-2.5 z-10"
              onClick={(e) => e.stopPropagation()}
            >
              <label
                className={`flex items-center justify-center h-7 w-7 rounded-lg backdrop-blur-xs cursor-pointer border transition-all shadow-sm ${
                  isSelected
                    ? "bg-emerald-600 border-emerald-500 text-white"
                    : "bg-black/60 hover:bg-black/80 border-white/20 text-transparent"
                }`}
                title={isSelected ? "Batalkan pilihan" : "Pilih video ini"}
              >
                <input
                  type="checkbox"
                  checked={isSelected}
                  onChange={() => onToggleSelect(video.id)}
                  className="sr-only"
                />
                <CheckCircle2
                  className={`h-4 w-4 transition-transform ${
                    isSelected ? "scale-100 fill-current" : "scale-75 opacity-0"
                  }`}
                />
              </label>
            </div>
          )}

          {/* Quick Play Preview Button */}
          <button
            type="button"
            onClick={() => setIsPreviewOpen(true)}
            className="absolute inset-0 m-auto flex h-12 w-12 items-center justify-center rounded-full bg-white/90 text-slate-900 shadow-lg backdrop-blur-xs transition-transform hover:scale-110 hover:bg-white focus:outline-hidden"
            title="Tonton Preview YouTube"
          >
            <Play className="h-5 w-5 fill-current ml-0.5 text-emerald-600" />
          </button>
        </div>

        {/* Video Info */}
        <div className="mt-4">
          {/* Status Badge & Channel */}
          <div className="flex items-center justify-between gap-2 mb-2">
            <span
              className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold ${statusConfig.badgeClass}`}
            >
              <StatusIcon className="h-3.5 w-3.5" />
              <span>{statusConfig.label}</span>
            </span>

            <a
              href={`https://www.youtube.com/watch?v=${video.youtube_video_id}`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-[11px] font-medium text-slate-500 hover:text-emerald-600 transition-colors"
              title="Buka langsung di YouTube"
            >
              <span>YouTube</span>
              <ExternalLink className="h-3 w-3" />
            </a>
          </div>

          {/* Title */}
          <h3
            className="font-semibold text-slate-900 text-sm sm:text-base leading-snug line-clamp-2 hover:line-clamp-none transition-all cursor-pointer"
            onClick={() => setIsPreviewOpen(true)}
            title={video.title}
          >
            {video.title}
          </h3>

          {/* Channel & Date */}
          <div className="mt-2 flex flex-wrap items-center gap-y-1 gap-x-3 text-xs text-slate-500">
            {video.channel_name && (
              <span className="font-medium text-slate-700 truncate max-w-[200px]">
                {video.channel_name}
              </span>
            )}
            <span className="flex items-center gap-1 text-slate-400">
              <Calendar className="h-3 w-3" />
              <span>{formatDate(video.published_at)}</span>
            </span>
          </div>

          {/* Description */}
          {video.description && (
            <div className="mt-2.5">
              <p
                className={`text-xs text-slate-600 leading-relaxed ${
                  isDescExpanded ? "" : "line-clamp-2"
                }`}
              >
                {video.description}
              </p>
              {video.description.length > 100 && (
                <button
                  type="button"
                  onClick={() => setIsDescExpanded(!isDescExpanded)}
                  className="mt-1 inline-flex items-center gap-0.5 text-[11px] font-medium text-emerald-700 hover:underline"
                >
                  <span>{isDescExpanded ? "Sembunyikan" : "Baca selengkapnya"}</span>
                  {isDescExpanded ? (
                    <ChevronUp className="h-3 w-3" />
                  ) : (
                    <ChevronDown className="h-3 w-3" />
                  )}
                </button>
              )}
            </div>
          )}

          {/* Reviewed At info */}
          {reviewedAt && (
            <div className="mt-2 text-[11px] text-slate-400 italic">
              Ditinjau pada: {formatDate(reviewedAt)}
            </div>
          )}
        </div>
      </div>

      {/* Editorial Controls Section */}
      <div className="mt-5 pt-4 border-t border-slate-100 bg-slate-50/60 -mx-4 -mb-4 sm:-mx-5 sm:-mb-5 p-4 sm:p-5 rounded-b-2xl">
        <div className="text-[11px] font-semibold uppercase tracking-wider text-slate-400 mb-2.5 flex items-center gap-1.5">
          <Sparkles className="h-3.5 w-3.5 text-emerald-600" />
          <span>Pengaturan Editorial</span>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
          {/* Category Select */}
          <div>
            <label className="block text-[11px] font-medium text-slate-600 mb-1">
              Kategori Kajian
            </label>
            <div className="relative">
              <select
                value={selectedCategory}
                onChange={(e) =>
                  setSelectedCategory(e.target.value as KajianCategory)
                }
                className="w-full appearance-none rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 pr-7 text-xs text-slate-800 font-medium focus:border-emerald-500 focus:outline-hidden focus:ring-1 focus:ring-emerald-500"
              >
                {VALID_CATEGORIES.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
              <Tag className="pointer-events-none absolute right-2 top-2 h-3.5 w-3.5 text-slate-400" />
            </div>
          </div>

          {/* Content Type Radio / Toggle */}
          <div>
            <label className="block text-[11px] font-medium text-slate-600 mb-1">
              Jenis Konten (Editorial)
            </label>
            <div className="grid grid-cols-2 gap-1 rounded-lg bg-slate-200/80 p-0.5">
              <button
                type="button"
                onClick={() => setSelectedContentType("kajian")}
                className={`rounded-md py-1 text-xs font-medium transition-all ${
                  selectedContentType === "kajian"
                    ? "bg-white text-slate-900 shadow-2xs font-semibold"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                Kajian
              </button>
              <button
                type="button"
                onClick={() => setSelectedContentType("short")}
                className={`rounded-md py-1 text-xs font-medium transition-all ${
                  selectedContentType === "short"
                    ? "bg-white text-slate-900 shadow-2xs font-semibold"
                    : "text-slate-600 hover:text-slate-900"
                }`}
              >
                Short
              </button>
            </div>
          </div>
        </div>

        {/* Action Buttons: Approve vs Reject */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            disabled={isLoading}
            onClick={() => handleOpenConfirm("approve")}
            className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-xl bg-emerald-600 px-3 py-2 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 active:scale-[0.98] transition-all disabled:opacity-50"
          >
            <CheckCircle2 className="h-4 w-4" />
            <span>Approve</span>
          </button>

          <button
            type="button"
            disabled={isLoading}
            onClick={() => handleOpenConfirm("reject")}
            className="inline-flex items-center justify-center gap-1.5 rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-xs font-semibold text-rose-700 shadow-2xs hover:bg-rose-100 hover:border-rose-300 active:scale-[0.98] transition-all disabled:opacity-50"
          >
            <XCircle className="h-4 w-4" />
            <span>Reject</span>
          </button>
        </div>
      </div>

      {/* YouTube Preview Modal */}
      <YouTubePreviewModal
        isOpen={isPreviewOpen}
        onClose={() => setIsPreviewOpen(false)}
        videoId={video.youtube_video_id}
        title={video.title}
        channelName={video.channel_name}
        contentType={selectedContentType}
      />

      {/* Review Confirmation Modal */}
      <ReviewConfirmModal
        isOpen={confirmModalState.isOpen}
        onClose={() =>
          setConfirmModalState((prev) => ({ ...prev, isOpen: false }))
        }
        onConfirm={handleExecuteReview}
        isLoading={isLoading}
        actionType={confirmModalState.actionType}
        videoTitle={video.title}
        category={selectedCategory}
        contentType={selectedContentType}
      />
    </div>
  );
}
