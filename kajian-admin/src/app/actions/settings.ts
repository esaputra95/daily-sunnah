"use server";

import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { createClient, verifyAdminSession } from "@/lib/supabase/server";
import {
  KajianCategory,
  KajianContentType,
  KajianFeedSortOrder,
  KajianSourceKind,
  VALID_CATEGORIES,
  VALID_CONTENT_TYPES,
} from "@/lib/types";

const FEED_SORTS: KajianFeedSortOrder[] = ["newest", "oldest", "duration_desc", "duration_asc"];

async function requireAdmin() {
  const result = await verifyAdminSession();
  if (!result.isAdmin) redirect("/login?error=unauthorized");
  return result.user!;
}

function settingsRedirect(kind: "notice" | "error", message: string): never {
  redirect(`/settings?${kind}=${encodeURIComponent(message)}`);
}

function parseSourceIdentifier(kind: KajianSourceKind, raw: string): string {
  const value = raw.trim();
  if (!value) throw new Error("Masukkan ID atau URL YouTube.");

  let identifier = value;
  if (/^https?:\/\//i.test(value)) {
    let url: URL;
    try {
      url = new URL(value);
    } catch {
      throw new Error("URL YouTube tidak valid.");
    }
    if (!/(^|\.)youtube\.com$/i.test(url.hostname)) {
      throw new Error("Gunakan URL dari youtube.com.");
    }
    if (kind === "playlist") {
      identifier = url.searchParams.get("list") ?? "";
    } else {
      const segments = url.pathname.split("/").filter(Boolean);
      const channelPosition = segments.indexOf("channel");
      identifier = channelPosition >= 0
        ? segments[channelPosition + 1] ?? ""
        : segments.find((segment) => segment.startsWith("@")) ?? "";
    }
  }

  if (kind === "playlist" && !/^[A-Za-z0-9_-]{10,100}$/.test(identifier)) {
    throw new Error("ID playlist tidak terbaca. Gunakan URL playlist atau ID playlist YouTube.");
  }
  if (kind === "channel" && !(/^UC[A-Za-z0-9_-]{22}$/.test(identifier) || /^@[A-Za-z0-9._-]{3,30}$/.test(identifier))) {
    throw new Error("Channel harus berupa URL /@handle atau ID channel UC….");
  }
  return identifier;
}

function field(formData: FormData, name: string): string {
  return String(formData.get(name) ?? "").trim();
}

function validateClassification(category: string, contentType: string) {
  if (!VALID_CATEGORIES.includes(category as KajianCategory)) throw new Error("Kategori tidak valid.");
  if (!VALID_CONTENT_TYPES.includes(contentType as KajianContentType)) throw new Error("Jenis video tidak valid.");
}

export async function saveSourceAction(formData: FormData) {
  await requireAdmin();
  const supabase = await createClient();
  let values: {
    source_kind: KajianSourceKind;
    source_identifier: string;
    display_name: string;
    category: string;
    content_type: string;
    is_active: boolean;
  };

  try {
    const kind = field(formData, "source_kind") as KajianSourceKind;
    if (kind !== "channel" && kind !== "playlist") throw new Error("Jenis sumber tidak valid.");
    const sourceIdentifier = parseSourceIdentifier(kind, field(formData, "source_identifier"));
    const category = field(formData, "category");
    const contentType = field(formData, "content_type");
    validateClassification(category, contentType);
    const displayName = field(formData, "display_name") || sourceIdentifier;
    values = {
      source_kind: kind,
      source_identifier: sourceIdentifier,
      display_name: displayName,
      category,
      content_type: contentType,
      is_active: field(formData, "is_active") === "on",
    };
  } catch (error) {
    settingsRedirect("error", error instanceof Error ? error.message : "Sumber gagal disimpan.");
  }

  const id = field(formData, "id");
  const result = id
    ? await supabase.from("kajian_sources").update(values).eq("id", id)
    : await supabase.from("kajian_sources").insert(values);
  if (result.error) settingsRedirect("error", result.error.message);

  revalidatePath("/settings");
  settingsRedirect("notice", "Sumber berhasil disimpan.");
}

export async function deleteSourceAction(formData: FormData) {
  await requireAdmin();
  const id = field(formData, "id");
  if (!id) settingsRedirect("error", "ID sumber tidak valid.");
  const supabase = await createClient();
  const { error } = await supabase.from("kajian_sources").delete().eq("id", id);
  if (error) settingsRedirect("error", error.message);
  revalidatePath("/settings");
  settingsRedirect("notice", "Sumber dihapus.");
}

function nextDailyRun(timeValue: string): string {
  const now = new Date();
  const jakarta = new Date(now.getTime() + 7 * 60 * 60 * 1000);
  const [hour, minute] = timeValue.split(":").map(Number);
  let targetUtc = Date.UTC(
    jakarta.getUTCFullYear(), jakarta.getUTCMonth(), jakarta.getUTCDate(), hour, minute,
  ) - 7 * 60 * 60 * 1000;
  if (targetUtc <= now.getTime()) targetUtc += 24 * 60 * 60 * 1000;
  return new Date(targetUtc).toISOString();
}

export async function saveIngestionSettingsAction(formData: FormData) {
  await requireAdmin();
  const mode = field(formData, "schedule_mode");
  if (mode !== "interval" && mode !== "daily") settingsRedirect("error", "Mode jadwal tidak valid.");

  const dailyTime = field(formData, "daily_time");
  if (!/^([01]\d|2[0-3]):[0-5]\d$/.test(dailyTime)) settingsRedirect("error", "Jam harian tidak valid.");
  const intervalMinutes = Number(field(formData, "interval_minutes"));
  if (mode === "interval" && (!Number.isInteger(intervalMinutes) || intervalMinutes < 15 || intervalMinutes > 43200)) {
    settingsRedirect("error", "Interval harus 15 menit sampai 30 hari.");
  }

  const nextRunAt = mode === "interval"
    ? new Date(Date.now() + intervalMinutes * 60_000).toISOString()
    : nextDailyRun(dailyTime);
  const supabase = await createClient();
  const { error } = await supabase.from("kajian_ingestion_settings").update({
    schedule_mode: mode,
    interval_minutes: mode === "interval" ? intervalMinutes : null,
    daily_time: dailyTime,
    timezone: "Asia/Jakarta",
    next_run_at: nextRunAt,
  }).eq("id", "default");
  if (error) settingsRedirect("error", error.message);

  revalidatePath("/settings");
  settingsRedirect("notice", `Jadwal disimpan. Sinkronisasi berikutnya sekitar ${new Date(nextRunAt).toLocaleString("id-ID", { timeZone: "Asia/Jakarta" })} WIB.`);
}

export async function requestIngestionNowAction() {
  const user = await requireAdmin();
  const supabase = await createClient();
  const { error } = await supabase.from("kajian_ingestion_settings").update({
    run_requested_at: new Date().toISOString(),
    run_requested_by: user.id,
  }).eq("id", "default");
  if (error) settingsRedirect("error", error.message);
  revalidatePath("/settings");
  settingsRedirect("notice", "Permintaan masuk antrean; GitHub Actions akan mengambilnya maksimal sekitar 15 menit ditambah waktu antrean GitHub.");
}

function optionalMinutes(value: string): number | null {
  if (!value) return null;
  const minutes = Number(value);
  if (!Number.isFinite(minutes) || minutes < 0 || minutes > 525600) throw new Error("Durasi harus berupa menit antara 0 dan 525.600.");
  return Math.round(minutes * 60);
}

export async function saveFeedSettingsAction(formData: FormData) {
  await requireAdmin();
  const sortOrder = field(formData, "sort_order") as KajianFeedSortOrder;
  if (!FEED_SORTS.includes(sortOrder)) settingsRedirect("error", "Urutan feed tidak valid.");
  const contentTypes = formData.getAll("content_types").map(String);
  if (!contentTypes.length || contentTypes.some((value) => !VALID_CONTENT_TYPES.includes(value as KajianContentType))) {
    settingsRedirect("error", "Pilih minimal satu jenis video.");
  }

  let minDuration: number | null;
  let maxDuration: number | null;
  try {
    minDuration = optionalMinutes(field(formData, "min_duration_minutes"));
    maxDuration = optionalMinutes(field(formData, "max_duration_minutes"));
  } catch (error) {
    settingsRedirect("error", error instanceof Error ? error.message : "Durasi tidak valid.");
  }
  if (minDuration !== null && maxDuration !== null && minDuration > maxDuration) {
    settingsRedirect("error", "Durasi minimum tidak boleh lebih besar dari maksimum.");
  }

  const supabase = await createClient();
  const { error } = await supabase.from("kajian_feed_settings").update({
    sort_order: sortOrder,
    content_types: contentTypes,
    min_duration_seconds: minDuration,
    max_duration_seconds: maxDuration,
  }).eq("id", "default");
  if (error) settingsRedirect("error", error.message);
  revalidatePath("/settings");
  revalidatePath("/catalog");
  settingsRedirect("notice", "Pengaturan feed Android berhasil disimpan.");
}
