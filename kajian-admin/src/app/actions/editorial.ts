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

export interface UpdateEditorialParams {
  id: string | number;
  category: KajianCategory;
  content_type: KajianContentType;
  review_status: ReviewStatus;
}

export interface EditorialActionResult {
  success: boolean;
  message?: string;
  error?: string;
  reviewedAt?: string;
}

/**
 * Server action untuk memperbarui status editorial dan metadata video kajian.
 * Eksekusi dilindungi:
 * 1. Pengecekan server-side session role ('kajian_admin').
 * 2. Menggunakan koneksi session authenticated user biasa (bukan service role).
 * 3. PostgreSQL RLS dan column-level grant membatasi modifikasi hanya pada kolom yang diizinkan.
 */
export async function updateEditorialAction(
  params: UpdateEditorialParams
): Promise<EditorialActionResult> {
  const { id, category, content_type, review_status } = params;

  // 1. Verifikasi role admin dari token session server
  const adminCheck = await verifyAdminSession();
  if (!adminCheck.isAdmin) {
    return {
      success: false,
      error: adminCheck.error ?? "Otorisasi tidak sah.",
    };
  }

  // 2. Validasi input
  if (!id) {
    return { success: false, error: "ID video tidak valid." };
  }

  if (!VALID_CATEGORIES.includes(category)) {
    return {
      success: false,
      error: `Kategori tidak valid. Pilihan: ${VALID_CATEGORIES.join(", ")}`,
    };
  }

  if (!VALID_CONTENT_TYPES.includes(content_type)) {
    return {
      success: false,
      error: `Jenis konten tidak valid. Pilihan: ${VALID_CONTENT_TYPES.join(", ")}`,
    };
  }

  if (!VALID_REVIEW_STATUSES.includes(review_status)) {
    return {
      success: false,
      error: `Status review tidak valid. Pilihan: ${VALID_REVIEW_STATUSES.join(", ")}`,
    };
  }

  const supabase = await createClient();
  const reviewedAt = new Date().toISOString();

  // 3. Eksekusi update melalui RLS session
  const { error } = await supabase
    .from("kajian_videos")
    .update({
      category,
      content_type,
      review_status,
      reviewed_at: reviewedAt,
    })
    .eq("id", id);

  if (error) {
    return {
      success: false,
      error: `Gagal memperbarui status: ${error.message} (Kode: ${error.code})`,
    };
  }

  // Revalidate cache katalog
  revalidatePath("/catalog");

  const statusLabel =
    review_status === "approved"
      ? "Disetujui (Approved)"
      : review_status === "rejected"
      ? "Ditolak (Rejected)"
      : "Pending";

  return {
    success: true,
    message: `Status video berhasil diperbarui menjadi ${statusLabel}.`,
    reviewedAt,
  };
}

export interface BulkUpdateEditorialParams {
  ids: (string | number)[];
  review_status: ReviewStatus;
  category?: KajianCategory;
  content_type?: KajianContentType;
}

export interface BulkEditorialActionResult {
  success: boolean;
  count?: number;
  message?: string;
  error?: string;
}

/**
 * Server action untuk memperbarui status editorial banyak video sekaligus (Bulk Approve / Bulk Reject).
 * Mematuhi PostgreSQL RLS & column grant: hanya mengupdate category, content_type, review_status, reviewed_at.
 */
export async function bulkUpdateEditorialAction(
  params: BulkUpdateEditorialParams
): Promise<BulkEditorialActionResult> {
  const { ids, review_status, category, content_type } = params;

  // 1. Verifikasi role admin dari session
  const adminCheck = await verifyAdminSession();
  if (!adminCheck.isAdmin) {
    return {
      success: false,
      error: adminCheck.error ?? "Otorisasi tidak sah.",
    };
  }

  // 2. Validasi input
  if (!ids || !Array.isArray(ids) || ids.length === 0) {
    return { success: false, error: "Tidak ada video yang dipilih." };
  }

  if (ids.length > 100) {
    return {
      success: false,
      error: "Maksimal 100 video yang dapat diproses sekaligus dalam satu aksi massal.",
    };
  }

  if (!VALID_REVIEW_STATUSES.includes(review_status)) {
    return {
      success: false,
      error: `Status review tidak valid. Pilihan: ${VALID_REVIEW_STATUSES.join(", ")}`,
    };
  }

  if (category && !VALID_CATEGORIES.includes(category)) {
    return {
      success: false,
      error: `Kategori tidak valid. Pilihan: ${VALID_CATEGORIES.join(", ")}`,
    };
  }

  if (content_type && !VALID_CONTENT_TYPES.includes(content_type)) {
    return {
      success: false,
      error: `Jenis konten tidak valid. Pilihan: ${VALID_CONTENT_TYPES.join(", ")}`,
    };
  }

  const supabase = await createClient();
  const reviewedAt = new Date().toISOString();

  const updatePayload: {
    review_status: ReviewStatus;
    reviewed_at: string;
    category?: KajianCategory;
    content_type?: KajianContentType;
  } = {
    review_status,
    reviewed_at: reviewedAt,
  };

  if (category) {
    updatePayload.category = category;
  }
  if (content_type) {
    updatePayload.content_type = content_type;
  }

  // 3. Eksekusi batch update dengan filter in('id', ids)
  const { error } = await supabase
    .from("kajian_videos")
    .update(updatePayload)
    .in("id", ids);

  if (error) {
    return {
      success: false,
      error: `Gagal memperbarui status massal: ${error.message} (Kode: ${error.code})`,
    };
  }

  // Revalidate cache katalog
  revalidatePath("/catalog");

  const statusLabel =
    review_status === "approved"
      ? "Disetujui (Approved)"
      : review_status === "rejected"
      ? "Ditolak (Rejected)"
      : "Pending";

  return {
    success: true,
    count: ids.length,
    message: `Berhasil memperbarui ${ids.length} video menjadi status ${statusLabel}.`,
  };
}
