"use client";

import { useState, useTransition } from "react";
import Image from "next/image";
import {
  Plus,
  X,
  Search,
  CheckCircle2,
  AlertCircle,
  ExternalLink,
  Tag,
  Sparkles,
  Play,
  Layers,
  FileText,
  Trash2,
} from "lucide-react";
import {
  KajianCategory,
  KajianContentType,
  ReviewStatus,
  VALID_CATEGORIES,
  VALID_CONTENT_TYPES,
} from "@/lib/types";
import {
  lookupYouTubeVideoAction,
  lookupBulkYouTubeVideosAction,
  saveManualVideoAction,
  saveBulkManualVideosAction,
  SaveManualVideoParams,
} from "@/app/actions/manualVideo";

interface AddVideoModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess?: (message: string) => void;
}

interface BulkEditableItem {
  id: string; // unique key for state
  videoId: string;
  title: string;
  channelName: string;
  thumbnailUrl: string;
  category: KajianCategory;
  contentType: KajianContentType;
  reviewStatus: ReviewStatus;
  status: "success" | "error";
  errorMessage?: string;
}

export function AddVideoModal({ isOpen, onClose, onSuccess }: AddVideoModalProps) {
  const [activeTab, setActiveTab] = useState<"single" | "bulk">("single");

  // Single mode state
  const [singleInput, setSingleInput] = useState("");
  const [isSingleLookingUp, startSingleLookup] = useTransition();
  const [isSingleSaving, startSingleSaving] = useTransition();
  const [singleLookupError, setSingleLookupError] = useState<string | null>(null);
  const [singleSaveError, setSingleSaveError] = useState<string | null>(null);
  const [singleVideoData, setSingleVideoData] = useState<{
    videoId: string;
    title: string;
    channelName: string;
    thumbnailUrl: string;
  } | null>(null);
  const [singleCategory, setSingleCategory] = useState<KajianCategory>("Akidah");
  const [singleContentType, setSingleContentType] = useState<KajianContentType>("kajian");
  const [singleStatus, setSingleStatus] = useState<ReviewStatus>("pending");

  // Bulk mode state
  const [bulkInputText, setBulkInputText] = useState("");
  const [isBulkLookingUp, startBulkLookup] = useTransition();
  const [isBulkSaving, startBulkSaving] = useTransition();
  const [bulkGlobalCategory, setBulkGlobalCategory] = useState<KajianCategory>("Akidah");
  const [bulkGlobalContentType, setBulkGlobalContentType] = useState<KajianContentType>("kajian");
  const [bulkGlobalStatus, setBulkGlobalStatus] = useState<ReviewStatus>("pending");
  const [bulkItems, setBulkItems] = useState<BulkEditableItem[]>([]);
  const [bulkLookupError, setBulkLookupError] = useState<string | null>(null);
  const [bulkSaveError, setBulkSaveError] = useState<string | null>(null);

  if (!isOpen) return null;

  // Single lookup handler
  const handleSingleLookup = (e: React.FormEvent) => {
    e.preventDefault();
    if (!singleInput.trim()) return;

    setSingleLookupError(null);
    setSingleSaveError(null);

    startSingleLookup(async () => {
      const res = await lookupYouTubeVideoAction(singleInput);
      if (res.success && res.videoId && res.title) {
        setSingleVideoData({
          videoId: res.videoId,
          title: res.title,
          channelName: res.channelName || "YouTube",
          thumbnailUrl:
            res.thumbnailUrl || `https://img.youtube.com/vi/${res.videoId}/hqdefault.jpg`,
        });
      } else {
        setSingleLookupError(res.error || "Gagal mengambil metadata video YouTube.");
        setSingleVideoData(null);
      }
    });
  };

  // Single save handler
  const handleSingleSave = () => {
    if (!singleVideoData) return;
    setSingleSaveError(null);

    startSingleSaving(async () => {
      const res = await saveManualVideoAction({
        youtubeVideoId: singleVideoData.videoId,
        title: singleVideoData.title,
        channelName: singleVideoData.channelName,
        category: singleCategory,
        contentType: singleContentType,
        reviewStatus: singleStatus,
      });

      if (res.success) {
        onSuccess?.(res.message || "Video berhasil disimpan!");
        handleClose();
      } else {
        setSingleSaveError(res.error || "Gagal menyimpan video ke database.");
      }
    });
  };

  // Bulk lookup handler
  const handleBulkLookup = (e: React.FormEvent) => {
    e.preventDefault();
    const lines = bulkInputText
      .split("\n")
      .map((l) => l.trim())
      .filter((l) => l.length > 0);

    if (lines.length === 0) return;

    setBulkLookupError(null);
    setBulkSaveError(null);

    startBulkLookup(async () => {
      const res = await lookupBulkYouTubeVideosAction(lines);
      if (res.success && res.items.length > 0) {
        const mappedItems: BulkEditableItem[] = res.items
          .filter((item) => item.videoId != null)
          .map((item, idx) => ({
            id: `${item.videoId}-${idx}`,
            videoId: item.videoId!,
            title: item.title || `Video ${item.videoId}`,
            channelName: item.channelName || "YouTube",
            thumbnailUrl:
              item.thumbnailUrl || `https://img.youtube.com/vi/${item.videoId}/hqdefault.jpg`,
            category: bulkGlobalCategory,
            contentType: bulkGlobalContentType,
            reviewStatus: bulkGlobalStatus,
            status: item.success ? "success" : "error",
            errorMessage: item.error,
          }));

        if (mappedItems.length === 0) {
          setBulkLookupError("Tidak ditemukan URL atau ID video YouTube yang valid dari teks.");
        } else {
          setBulkItems(mappedItems);
        }
      } else {
        setBulkLookupError(res.error || "Gagal memeriksa daftar video.");
      }
    });
  };

  // Apply global defaults to all bulk items
  const handleApplyGlobalToAll = () => {
    setBulkItems((prev) =>
      prev.map((item) => ({
        ...item,
        category: bulkGlobalCategory,
        contentType: bulkGlobalContentType,
        reviewStatus: bulkGlobalStatus,
      }))
    );
  };

  // Remove single item from bulk list
  const handleRemoveBulkItem = (id: string) => {
    setBulkItems((prev) => prev.filter((item) => item.id !== id));
  };

  // Update item field in bulk list
  const handleUpdateBulkItem = <K extends keyof BulkEditableItem>(
    id: string,
    key: K,
    value: BulkEditableItem[K]
  ) => {
    setBulkItems((prev) =>
      prev.map((item) => (item.id === id ? { ...item, [key]: value } : item))
    );
  };

  // Save all bulk items
  const handleBulkSave = () => {
    const validItemsToSave: SaveManualVideoParams[] = bulkItems
      .filter((item) => item.status === "success" && item.videoId)
      .map((item) => ({
        youtubeVideoId: item.videoId,
        title: item.title,
        channelName: item.channelName,
        category: item.category,
        contentType: item.contentType,
        reviewStatus: item.reviewStatus,
      }));

    if (validItemsToSave.length === 0) {
      setBulkSaveError("Tidak ada video berstatus valid yang dapat disimpan.");
      return;
    }

    setBulkSaveError(null);

    startBulkSaving(async () => {
      const res = await saveBulkManualVideosAction(validItemsToSave);
      if (res.success) {
        onSuccess?.(res.message || `Berhasil menyimpan ${validItemsToSave.length} video.`);
        handleClose();
      } else {
        setBulkSaveError(res.error || "Gagal menyimpan batch video ke database.");
      }
    });
  };

  const handleClose = () => {
    setSingleInput("");
    setSingleVideoData(null);
    setSingleLookupError(null);
    setSingleSaveError(null);
    setBulkInputText("");
    setBulkItems([]);
    setBulkLookupError(null);
    setBulkSaveError(null);
    onClose();
  };

  const isSavingAny = isSingleSaving || isBulkSaving;
  const isLookingUpAny = isSingleLookingUp || isBulkLookingUp;

  const validBulkCount = bulkItems.filter((i) => i.status === "success").length;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/70 backdrop-blur-xs animate-in fade-in duration-150">
      <div className="fixed inset-0" onClick={isSavingAny ? undefined : handleClose} />

      <div className="relative z-10 w-full max-w-4xl overflow-hidden rounded-2xl bg-white shadow-2xl ring-1 ring-slate-900/10 flex flex-col max-h-[92vh]">
        {/* Header with Mode Tabs */}
        <div className="border-b border-slate-200 px-6 pt-4 pb-0 bg-slate-50/70">
          <div className="flex items-center justify-between pb-3">
            <div className="flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-xs">
                <Plus className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-base font-bold text-slate-900">Tambah Video Manual</h2>
                <p className="text-xs text-slate-500">
                  Cari via tautan/ID YouTube (oEmbed resmi tanpa API key) dan simpan langsung ke database
                </p>
              </div>
            </div>
            <button
              onClick={handleClose}
              disabled={isSavingAny}
              className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-200 hover:text-slate-700 transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>

          {/* Mode Tabs */}
          <div className="flex space-x-1 border-t border-slate-200/80 pt-2">
            <button
              type="button"
              onClick={() => setActiveTab("single")}
              className={`flex items-center gap-1.5 border-b-2 px-3.5 py-2 text-xs font-semibold transition-all ${
                activeTab === "single"
                  ? "border-emerald-600 text-emerald-800 bg-white rounded-t-lg shadow-2xs"
                  : "border-transparent text-slate-500 hover:text-slate-800"
              }`}
            >
              <FileText className="h-3.5 w-3.5" />
              <span>Satu Video (Single)</span>
            </button>
            <button
              type="button"
              onClick={() => setActiveTab("bulk")}
              className={`flex items-center gap-1.5 border-b-2 px-3.5 py-2 text-xs font-semibold transition-all ${
                activeTab === "bulk"
                  ? "border-emerald-600 text-emerald-800 bg-white rounded-t-lg shadow-2xs"
                  : "border-transparent text-slate-500 hover:text-slate-800"
              }`}
            >
              <Layers className="h-3.5 w-3.5" />
              <span>Import Massal (Bulk Create)</span>
              {bulkItems.length > 0 && (
                <span className="ml-1 rounded-full bg-emerald-100 text-emerald-800 px-1.5 py-0.2 text-[10px] font-bold">
                  {bulkItems.length}
                </span>
              )}
            </button>
          </div>
        </div>

        {/* Modal Content */}
        <div className="p-6 overflow-y-auto space-y-5 flex-1">
          {/* TAB 1: SINGLE MODE */}
          {activeTab === "single" && (
            <div className="space-y-5">
              <form onSubmit={handleSingleLookup} className="space-y-2">
                <label className="block text-xs font-semibold text-slate-700">
                  Tautan atau ID Video YouTube
                </label>
                <div className="flex gap-2">
                  <div className="relative flex-1">
                    <input
                      type="text"
                      value={singleInput}
                      onChange={(e) => setSingleInput(e.target.value)}
                      placeholder="https://www.youtube.com/watch?v=... atau https://youtu.be/... atau ID video"
                      className="w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-xs sm:text-sm text-slate-900 placeholder:text-slate-400 focus:border-emerald-500 focus:outline-hidden focus:ring-2 focus:ring-emerald-500/20"
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={isSingleLookingUp || !singleInput.trim()}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2.5 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50 transition-colors"
                  >
                    {isSingleLookingUp ? (
                      <>
                        <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                        <span>Mencari...</span>
                      </>
                    ) : (
                      <>
                        <Search className="h-3.5 w-3.5" />
                        <span>Cek Video</span>
                      </>
                    )}
                  </button>
                </div>
                <p className="text-[11px] text-slate-500">
                  Mendukung video biasa, shorts, maupun tautan pendek `youtu.be`.
                </p>
              </form>

              {singleLookupError && (
                <div className="flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-xs text-rose-800">
                  <AlertCircle className="h-4 w-4 text-rose-500 shrink-0 mt-0.5" />
                  <span>{singleLookupError}</span>
                </div>
              )}

              {singleVideoData && (
                <div className="rounded-2xl border border-slate-200 bg-slate-50/50 p-4 space-y-4 animate-in fade-in duration-200">
                  <div className="flex flex-col sm:flex-row gap-4">
                    <div className="relative aspect-16/9 w-full sm:w-48 overflow-hidden rounded-xl bg-slate-900 shrink-0 shadow-xs">
                      <Image
                        src={singleVideoData.thumbnailUrl}
                        alt={singleVideoData.title}
                        fill
                        sizes="200px"
                        className="object-cover"
                        unoptimized
                      />
                      <div className="absolute inset-0 flex items-center justify-center bg-black/20 pointer-events-none">
                        <Play className="h-6 w-6 text-white drop-shadow-md" />
                      </div>
                    </div>

                    <div className="flex-1 min-w-0 space-y-2">
                      <div>
                        <label className="block text-[11px] font-medium text-slate-500 mb-0.5">
                          Judul Video
                        </label>
                        <input
                          type="text"
                          value={singleVideoData.title}
                          onChange={(e) =>
                            setSingleVideoData({ ...singleVideoData, title: e.target.value })
                          }
                          className="w-full rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs font-semibold text-slate-900 focus:border-emerald-500 focus:outline-hidden"
                        />
                      </div>

                      <div>
                        <label className="block text-[11px] font-medium text-slate-500 mb-0.5">
                          Channel
                        </label>
                        <input
                          type="text"
                          value={singleVideoData.channelName}
                          onChange={(e) =>
                            setSingleVideoData({ ...singleVideoData, channelName: e.target.value })
                          }
                          className="w-full rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-700 focus:border-emerald-500 focus:outline-hidden"
                        />
                      </div>

                      <div className="flex items-center gap-2 pt-1">
                        <a
                          href={`https://www.youtube.com/watch?v=${singleVideoData.videoId}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 text-[11px] text-emerald-700 hover:text-emerald-800 font-medium"
                        >
                          <span>Buka di YouTube</span>
                          <ExternalLink className="h-3 w-3" />
                        </a>
                        <span className="text-slate-300">•</span>
                        <span className="text-[11px] text-slate-500 font-mono">
                          ID: {singleVideoData.videoId}
                        </span>
                      </div>
                    </div>
                  </div>

                  <div className="pt-3 border-t border-slate-200 grid grid-cols-1 sm:grid-cols-3 gap-3">
                    <div>
                      <label className="block text-[11px] font-medium text-slate-600 mb-1">
                        Kategori
                      </label>
                      <div className="relative">
                        <select
                          value={singleCategory}
                          onChange={(e) => setSingleCategory(e.target.value as KajianCategory)}
                          className="w-full appearance-none rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 pr-7 text-xs text-slate-800 font-medium focus:border-emerald-500 focus:outline-hidden"
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

                    <div>
                      <label className="block text-[11px] font-medium text-slate-600 mb-1">
                        Jenis Konten
                      </label>
                      <div className="grid grid-cols-2 gap-1 rounded-lg bg-slate-200/80 p-0.5">
                        <button
                          type="button"
                          onClick={() => setSingleContentType("kajian")}
                          className={`rounded-md py-1 text-xs font-medium transition-all ${
                            singleContentType === "kajian"
                              ? "bg-white text-slate-900 shadow-2xs font-semibold"
                              : "text-slate-600 hover:text-slate-900"
                          }`}
                        >
                          Kajian
                        </button>
                        <button
                          type="button"
                          onClick={() => setSingleContentType("short")}
                          className={`rounded-md py-1 text-xs font-medium transition-all ${
                            singleContentType === "short"
                              ? "bg-white text-slate-900 shadow-2xs font-semibold"
                              : "text-slate-600 hover:text-slate-900"
                          }`}
                        >
                          Short
                        </button>
                      </div>
                    </div>

                    <div>
                      <label className="block text-[11px] font-medium text-slate-600 mb-1">
                        Status Awal
                      </label>
                      <select
                        value={singleStatus}
                        onChange={(e) => setSingleStatus(e.target.value as ReviewStatus)}
                        className="w-full appearance-none rounded-lg border border-slate-200 bg-white px-2.5 py-1.5 text-xs text-slate-800 font-medium focus:border-emerald-500 focus:outline-hidden"
                      >
                        <option value="pending">Pending (Perlu Kurasi)</option>
                        <option value="approved">Approved (Disetujui Langsung)</option>
                      </select>
                    </div>
                  </div>

                  {singleStatus === "approved" && (
                    <div className="flex items-center gap-1.5 text-[11px] text-emerald-800 bg-emerald-50 rounded-lg p-2 border border-emerald-200/60">
                      <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-emerald-600" />
                      <span>
                        Video akan langsung berstatus <strong>Approved</strong> dan dapat dibaca oleh aplikasi Android.
                      </span>
                    </div>
                  )}
                </div>
              )}

              {singleSaveError && (
                <div className="flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-xs text-rose-800">
                  <AlertCircle className="h-4 w-4 text-rose-500 shrink-0 mt-0.5" />
                  <span>{singleSaveError}</span>
                </div>
              )}
            </div>
          )}

          {/* TAB 2: BULK IMPORT MODE */}
          {activeTab === "bulk" && (
            <div className="space-y-5">
              {/* Textarea for Bulk URLs */}
              <form onSubmit={handleBulkLookup} className="space-y-3">
                <div className="flex items-center justify-between">
                  <label className="block text-xs font-semibold text-slate-700">
                    Tempelkan Daftar Tautan atau ID Video (Satu per baris)
                  </label>
                  <span className="text-[11px] text-slate-400">Maksimal 50 video sekaligus</span>
                </div>

                <textarea
                  value={bulkInputText}
                  onChange={(e) => setBulkInputText(e.target.value)}
                  rows={5}
                  placeholder={`https://www.youtube.com/watch?v=dQw4w9WgXcQ
https://youtu.be/abc123xyz89
https://www.youtube.com/shorts/uvw456rst78
dQw4w9WgXcQ`}
                  className="w-full rounded-xl border border-slate-200 bg-white p-3 font-mono text-xs text-slate-900 placeholder:text-slate-400 focus:border-emerald-500 focus:outline-hidden focus:ring-2 focus:ring-emerald-500/20 leading-relaxed"
                />

                <div className="flex flex-wrap items-center justify-between gap-3">
                  <p className="text-[11px] text-slate-500">
                    Sistem akan memvalidasi dan menduplikasi otomatis setiap tautan.
                  </p>
                  <button
                    type="submit"
                    disabled={isBulkLookingUp || !bulkInputText.trim()}
                    className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50 transition-colors"
                  >
                    {isBulkLookingUp ? (
                      <>
                        <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                        <span>Mengambil Metadata Massal...</span>
                      </>
                    ) : (
                      <>
                        <Search className="h-3.5 w-3.5" />
                        <span>Periksa Semua Video</span>
                      </>
                    )}
                  </button>
                </div>
              </form>

              {bulkLookupError && (
                <div className="flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-xs text-rose-800">
                  <AlertCircle className="h-4 w-4 text-rose-500 shrink-0 mt-0.5" />
                  <span>{bulkLookupError}</span>
                </div>
              )}

              {/* Bulk Items Controls & List */}
              {bulkItems.length > 0 && (
                <div className="space-y-4 pt-2 border-t border-slate-200">
                  {/* Global Presets Tool */}
                  <div className="rounded-xl border border-slate-200 bg-slate-50 p-3.5">
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <span className="text-xs font-semibold text-slate-800 flex items-center gap-1.5">
                        <Sparkles className="h-3.5 w-3.5 text-emerald-600" />
                        Atur Nilai Default Massal:
                      </span>
                      <button
                        type="button"
                        onClick={handleApplyGlobalToAll}
                        className="rounded-lg bg-slate-200 hover:bg-slate-300 px-2.5 py-1 text-[11px] font-semibold text-slate-700 transition-colors"
                      >
                        Terapkan ke Semua ({bulkItems.length})
                      </button>
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-3 gap-3">
                      <div>
                        <label className="block text-[11px] text-slate-500 mb-1">
                          Kategori Default
                        </label>
                        <select
                          value={bulkGlobalCategory}
                          onChange={(e) =>
                            setBulkGlobalCategory(e.target.value as KajianCategory)
                          }
                          className="w-full rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-800"
                        >
                          {VALID_CATEGORIES.map((cat) => (
                            <option key={cat} value={cat}>
                              {cat}
                            </option>
                          ))}
                        </select>
                      </div>

                      <div>
                        <label className="block text-[11px] text-slate-500 mb-1">
                          Jenis Konten Default
                        </label>
                        <select
                          value={bulkGlobalContentType}
                          onChange={(e) =>
                            setBulkGlobalContentType(e.target.value as KajianContentType)
                          }
                          className="w-full rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-800"
                        >
                          <option value="kajian">Kajian</option>
                          <option value="short">Short</option>
                        </select>
                      </div>

                      <div>
                        <label className="block text-[11px] text-slate-500 mb-1">
                          Status Awal Default
                        </label>
                        <select
                          value={bulkGlobalStatus}
                          onChange={(e) =>
                            setBulkGlobalStatus(e.target.value as ReviewStatus)
                          }
                          className="w-full rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-800"
                        >
                          <option value="pending">Pending</option>
                          <option value="approved">Approved</option>
                        </select>
                      </div>
                    </div>
                  </div>

                  {/* List of Loaded Items */}
                  <div className="space-y-2.5 max-h-[380px] overflow-y-auto pr-1">
                    {bulkItems.map((item, index) => (
                      <div
                        key={item.id}
                        className={`rounded-xl border p-3 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3 text-xs transition-colors ${
                          item.status === "error"
                            ? "bg-rose-50/50 border-rose-200"
                            : "bg-white border-slate-200 hover:border-slate-300 shadow-2xs"
                        }`}
                      >
                        <div className="flex items-center gap-3 min-w-0 flex-1">
                          <span className="font-mono text-slate-400 text-[11px] w-5 text-right shrink-0">
                            {index + 1}.
                          </span>

                          <div className="relative aspect-16/9 w-20 overflow-hidden rounded-md bg-slate-900 shrink-0">
                            <Image
                              src={item.thumbnailUrl}
                              alt={item.title}
                              fill
                              sizes="80px"
                              className="object-cover"
                              unoptimized
                            />
                          </div>

                          <div className="min-w-0 flex-1">
                            <div className="font-semibold text-slate-900 truncate">
                              {item.title}
                            </div>
                            <div className="text-[11px] text-slate-500 flex items-center gap-2">
                              <span>{item.channelName}</span>
                              <span>•</span>
                              <code className="text-slate-400">{item.videoId}</code>
                              {item.status === "error" && (
                                <span className="text-rose-600 font-semibold">
                                  ({item.errorMessage})
                                </span>
                              )}
                            </div>
                          </div>
                        </div>

                        {/* Individual Controls */}
                        {item.status === "success" && (
                          <div className="flex items-center gap-2 w-full sm:w-auto shrink-0">
                            <select
                              value={item.category}
                              onChange={(e) =>
                                handleUpdateBulkItem(
                                  item.id,
                                  "category",
                                  e.target.value as KajianCategory
                                )
                              }
                              className="rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700"
                            >
                              {VALID_CATEGORIES.map((c) => (
                                <option key={c} value={c}>
                                  {c}
                                </option>
                              ))}
                            </select>

                            <select
                              value={item.contentType}
                              onChange={(e) =>
                                handleUpdateBulkItem(
                                  item.id,
                                  "contentType",
                                  e.target.value as KajianContentType
                                )
                              }
                              className="rounded-lg border border-slate-200 bg-white px-2 py-1 text-xs text-slate-700"
                            >
                              <option value="kajian">Kajian</option>
                              <option value="short">Short</option>
                            </select>

                            <select
                              value={item.reviewStatus}
                              onChange={(e) =>
                                handleUpdateBulkItem(
                                  item.id,
                                  "reviewStatus",
                                  e.target.value as ReviewStatus
                                )
                              }
                              className={`rounded-lg border px-2 py-1 text-xs font-medium ${
                                item.reviewStatus === "approved"
                                  ? "bg-emerald-50 text-emerald-800 border-emerald-200"
                                  : "bg-slate-50 text-slate-700 border-slate-200"
                              }`}
                            >
                              <option value="pending">Pending</option>
                              <option value="approved">Approved</option>
                            </select>

                            <button
                              type="button"
                              onClick={() => handleRemoveBulkItem(item.id)}
                              className="p-1 text-slate-400 hover:text-rose-600 rounded-md transition-colors"
                              title="Hapus dari daftar ini"
                            >
                              <Trash2 className="h-4 w-4" />
                            </button>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {bulkSaveError && (
                <div className="flex items-start gap-2.5 rounded-xl border border-rose-200 bg-rose-50 p-3.5 text-xs text-rose-800">
                  <AlertCircle className="h-4 w-4 text-rose-500 shrink-0 mt-0.5" />
                  <span>{bulkSaveError}</span>
                </div>
              )}
            </div>
          )}
        </div>

        {/* Modal Footer */}
        <div className="border-t border-slate-200 px-6 py-3.5 bg-slate-50 flex items-center justify-between gap-3">
          <div className="text-xs text-slate-500">
            {activeTab === "bulk" && bulkItems.length > 0 && (
              <span>
                Siap disimpan: <strong className="text-slate-800">{validBulkCount} video</strong>
              </span>
            )}
          </div>

          <div className="flex items-center gap-3">
            <button
              type="button"
              disabled={isSavingAny}
              onClick={handleClose}
              className="rounded-xl border border-slate-200 bg-white px-4 py-2 text-xs font-medium text-slate-700 hover:bg-slate-100 transition-colors disabled:opacity-50"
            >
              Batal
            </button>

            {activeTab === "single" ? (
              <button
                type="button"
                disabled={!singleVideoData || isSingleSaving}
                onClick={handleSingleSave}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50 transition-colors"
              >
                {isSingleSaving ? (
                  <>
                    <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                    <span>Menyimpan ke Database...</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="h-3.5 w-3.5" />
                    <span>Simpan ke Database</span>
                  </>
                )}
              </button>
            ) : (
              <button
                type="button"
                disabled={validBulkCount === 0 || isBulkSaving}
                onClick={handleBulkSave}
                className="inline-flex items-center gap-1.5 rounded-xl bg-emerald-600 px-4 py-2 text-xs font-semibold text-white shadow-xs hover:bg-emerald-700 disabled:opacity-50 transition-colors"
              >
                {isBulkSaving ? (
                  <>
                    <div className="h-3.5 w-3.5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                    <span>Menyimpan Massal ({validBulkCount})...</span>
                  </>
                ) : (
                  <>
                    <Sparkles className="h-3.5 w-3.5" />
                    <span>Simpan Massal ({validBulkCount} Video)</span>
                  </>
                )}
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
