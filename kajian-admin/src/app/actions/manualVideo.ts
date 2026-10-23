"use server";

import { revalidatePath } from "next/cache";
import { createClient, verifyAdminSession } from "@/lib/supabase/server";
import {
  KajianCategory,
  KajianContentType,
  ReviewStatus,
  VALID_CATEGORIES,
  VALID_CONTENT_TYPES,
  VALID_REVIEW_STATUSES,
} from "@/lib/types";
import { extractYouTubeVideoId } from "@/lib/utils";

const YOUTUBE_ID_REGEX = /^[A-Za-z0-9_-]{11}$/;

export interface VideoLookupResult {
  success: boolean;
  videoId?: string;
  title?: string;
  channelName?: string;
  channelUrl?: string;
  thumbnailUrl?: string;
  error?: string;
}

export interface SaveManualVideoParams {
  youtubeVideoId: string;
  title: string;
  channelName: string;
  channelId?: string;
  category: KajianCategory;
  contentType: KajianContentType;
  reviewStatus: ReviewStatus;
}

export interface SaveManualVideoResult {
  success: boolean;
  message?: string;
  error?: string;
}

export interface BulkLookupItem {
  input: string;
  videoId: string | null;
  title?: string;
  channelName?: string;
  thumbnailUrl?: string;
  success: boolean;
  error?: string;
}

export interface BulkLookupResult {
  success: boolean;
  items: BulkLookupItem[];
  error?: string;
}

export interface SaveBulkResult {
  success: boolean;
  savedCount: number;
  message?: string;
  error?: string;
}

/**
 * Mengambil metadata publik satu video YouTube melalui oEmbed API resmi tanpa API key.
 */
export async function lookupYouTubeVideoAction(input: string): Promise<VideoLookupResult> {
  const adminCheck = await verifyAdminSession();
  if (!adminCheck.isAdmin) {
    return { success: false, error: adminCheck.error || "Otorisasi admin diperlukan." };
  }

  const videoId = extractYouTubeVideoId(input);
  if (!videoId) {
    return {
      success: false,
      error: "Format tautan atau ID video YouTube tidak valid. Contoh: dQw4w9WgXcQ atau https://www.youtube.com/watch?v=...",
    };
  }

  try {
    const oembedUrl = `https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${videoId}&format=json`;
    const res = await fetch(oembedUrl, {
      headers: { Accept: "application/json" },
      next: { revalidate: 60 },
    });

    if (!res.ok) {
      if (res.status === 404) {
        return {
          success: false,
          error: "Video tidak ditemukan di YouTube, telah dihapus, atau disetel ke mode privat.",
        };
      }
      return {
        success: false,
        error: `Gagal memuat metadata dari YouTube oEmbed (HTTP ${res.status}).`,
      };
    }

    const data = await res.json();
    const title = String(data.title || "").trim();
    const channelName = String(data.author_name || "YouTube").trim();
    const channelUrl = String(data.author_url || "");
    const thumbnailUrl =
      data.thumbnail_url || `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`;

    return {
      success: true,
      videoId,
      title: title || `Video ${videoId}`,
      channelName,
      channelUrl,
      thumbnailUrl,
    };
  } catch (err: unknown) {
    return {
      success: false,
      error: err instanceof Error ? err.message : "Gagal menghubungi YouTube oEmbed.",
    };
  }
}

/**
 * Mengambil metadata sejumlah video YouTube secara bersamaan (bulk) menggunakan oEmbed.
 */
export async function lookupBulkYouTubeVideosAction(inputs: string[]): Promise<BulkLookupResult> {
  const adminCheck = await verifyAdminSession();
  if (!adminCheck.isAdmin) {
    return { success: false, items: [], error: adminCheck.error || "Otorisasi admin diperlukan." };
  }

  const cleanedInputs = inputs
    .map((s) => s.trim())
    .filter((s) => s.length > 0);

  if (cleanedInputs.length === 0) {
    return { success: false, items: [], error: "Masukkan minimal satu URL atau ID video YouTube." };
  }

  // Batasi maksimal 50 video sekaligus agar tidak membebani browser dan jaringan
  const cappedInputs = cleanedInputs.slice(0, 50);

  // Parse video ID dan deduplikasi
  const parsedMap = new Map<string, string>(); // videoId -> rawInput
  const invalidItems: BulkLookupItem[] = [];

  for (const raw of cappedInputs) {
    const vid = extractYouTubeVideoId(raw);
    if (!vid) {
      invalidItems.push({
        input: raw,
        videoId: null,
        success: false,
        error: "URL / ID tidak valid",
      });
    } else if (!parsedMap.has(vid)) {
      parsedMap.set(vid, raw);
    }
  }

  const uniqueVideoIds = Array.from(parsedMap.keys());

  // Ambil metadata secara paralel dengan timeout per item
  const fetchPromises = uniqueVideoIds.map(async (vid): Promise<BulkLookupItem> => {
    const raw = parsedMap.get(vid) || vid;
    try {
      const oembedUrl = `https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=${vid}&format=json`;
      const res = await fetch(oembedUrl, {
        headers: { Accept: "application/json" },
        next: { revalidate: 60 },
      });

      if (!res.ok) {
        return {
          input: raw,
          videoId: vid,
          title: `Video ${vid}`,
          channelName: "YouTube",
          thumbnailUrl: `https://img.youtube.com/vi/${vid}/hqdefault.jpg`,
          success: false,
          error: res.status === 404 ? "Tidak ditemukan / privat" : `HTTP ${res.status}`,
        };
      }

      const data = await res.json();
      return {
        input: raw,
        videoId: vid,
        title: String(data.title || `Video ${vid}`).trim(),
        channelName: String(data.author_name || "YouTube").trim(),
        thumbnailUrl: data.thumbnail_url || `https://img.youtube.com/vi/${vid}/hqdefault.jpg`,
        success: true,
      };
    } catch {
      return {
        input: raw,
        videoId: vid,
        title: `Video ${vid}`,
        channelName: "YouTube",
        thumbnailUrl: `https://img.youtube.com/vi/${vid}/hqdefault.jpg`,
        success: false,
        error: "Gagal fetch oEmbed",
      };
    }
  });

  const fetchedItems = await Promise.all(fetchPromises);
  const allResults = [...fetchedItems, ...invalidItems];

  return {
    success: true,
    items: allResults,
  };
}

/**
 * Menyimpan satu video manual langsung ke tabel public.kajian_videos di Supabase.
 */
export async function saveManualVideoAction(
  params: SaveManualVideoParams
): Promise<SaveManualVideoResult> {
  const adminCheck = await verifyAdminSession();
  if (!adminCheck.isAdmin) {
    return { success: false, error: adminCheck.error || "Otorisasi admin diperlukan." };
  }

  const {
    youtubeVideoId,
    title,
    channelName,
    channelId,
    category,
    contentType,
    reviewStatus,
  } = params;

  if (!YOUTUBE_ID_REGEX.test(youtubeVideoId)) {
    return { success: false, error: "ID video YouTube harus 11 karakter alfanumerik." };
  }
  if (!title.trim()) {
    return { success: false, error: "Judul video tidak boleh kosong." };
  }
  if (!VALID_CATEGORIES.includes(category)) {
    return { success: false, error: "Kategori tidak valid." };
  }
  if (!VALID_CONTENT_TYPES.includes(contentType)) {
    return { success: false, error: "Jenis konten tidak valid." };
  }
  if (!VALID_REVIEW_STATUSES.includes(reviewStatus)) {
    return { success: false, error: "Status review tidak valid." };
  }

  const supabase = await createClient();
  const now = new Date().toISOString();

  const row = {
    youtube_video_id: youtubeVideoId,
    title: title.trim(),
    description: "",
    channel_name: channelName.trim() || "YouTube",
    channel_id: channelId?.trim() || "manual_channel",
    category,
    content_type: contentType,
    thumbnail_url: `https://img.youtube.com/vi/${youtubeVideoId}/hqdefault.jpg`,
    duration_seconds: contentType === "short" ? 60 : 1800,
    published_at: now,
    review_status: reviewStatus,
    reviewed_at: reviewStatus === "approved" ? now : null,
    created_at: now,
    updated_at: now,
  };

  const { error } = await supabase
    .from("kajian_videos")
    .upsert(row, { onConflict: "youtube_video_id" });

  if (error) {
    return {
      success: false,
      error: `Gagal menyimpan ke database: ${error.message} (Kode: ${error.code})`,
    };
  }

  revalidatePath("/catalog");

  const statusLabel =
    reviewStatus === "approved"
      ? "Disetujui (Approved)"
      : reviewStatus === "rejected"
      ? "Ditolak (Rejected)"
      : "Pending";

  return {
    success: true,
    message: `Video "${title}" berhasil disimpan dengan status ${statusLabel}.`,
  };
}

/**
 * Menyimpan banyak video sekaligus (bulk) ke tabel public.kajian_videos di Supabase.
 */
export async function saveBulkManualVideosAction(
  items: SaveManualVideoParams[]
): Promise<SaveBulkResult> {
  const adminCheck = await verifyAdminSession();
  if (!adminCheck.isAdmin) {
    return { success: false, savedCount: 0, error: adminCheck.error || "Otorisasi admin diperlukan." };
  }

  const validItems = items.filter(
    (item) =>
      item.youtubeVideoId &&
      YOUTUBE_ID_REGEX.test(item.youtubeVideoId) &&
      VALID_CATEGORIES.includes(item.category) &&
      VALID_CONTENT_TYPES.includes(item.contentType) &&
      VALID_REVIEW_STATUSES.includes(item.reviewStatus)
  );

  if (validItems.length === 0) {
    return { success: false, savedCount: 0, error: "Tidak ada video valid yang siap disimpan." };
  }

  const supabase = await createClient();
  const now = new Date().toISOString();

  const rows = validItems.map((item) => ({
    youtube_video_id: item.youtubeVideoId,
    title: item.title.trim() || `Video ${item.youtubeVideoId}`,
    description: "",
    channel_name: item.channelName.trim() || "YouTube",
    channel_id: item.channelId?.trim() || "manual_channel",
    category: item.category,
    content_type: item.contentType,
    thumbnail_url: `https://img.youtube.com/vi/${item.youtubeVideoId}/hqdefault.jpg`,
    duration_seconds: item.contentType === "short" ? 60 : 1800,
    published_at: now,
    review_status: item.reviewStatus,
    reviewed_at: item.reviewStatus === "approved" ? now : null,
    created_at: now,
    updated_at: now,
  }));

  const { error } = await supabase
    .from("kajian_videos")
    .upsert(rows, { onConflict: "youtube_video_id" });

  if (error) {
    return {
      success: false,
      savedCount: 0,
      error: `Gagal menyimpan bulk video: ${error.message} (Kode: ${error.code})`,
    };
  }

  revalidatePath("/catalog");

  return {
    success: true,
    savedCount: rows.length,
    message: `Berhasil menambahkan dan menyimpan ${rows.length} video ke katalog database!`,
  };
}
