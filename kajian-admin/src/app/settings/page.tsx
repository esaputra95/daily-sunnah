import { redirect } from "next/navigation";
import { Activity, Clock3, Film, ListVideo, Plus, Radio, Settings2, Youtube } from "lucide-react";
import { Navbar } from "@/components/Navbar";
import {
  deleteSourceAction,
  requestIngestionNowAction,
  saveFeedSettingsAction,
  saveIngestionSettingsAction,
  saveSourceAction,
} from "@/app/actions/settings";
import { createClient, verifyAdminSession } from "@/lib/supabase/server";
import {
  KajianFeedSettings,
  KajianIngestionRun,
  KajianIngestionSettings,
  KajianSource,
  VALID_CATEGORIES,
  VALID_CONTENT_TYPES,
} from "@/lib/types";

interface SettingsPageProps {
  searchParams: Promise<{ notice?: string; error?: string }>;
}

const cardClass = "rounded-2xl border border-slate-200 bg-white p-5 shadow-xs sm:p-6";
const labelClass = "mb-1.5 block text-xs font-semibold text-slate-700";
const inputClass = "w-full rounded-xl border border-slate-200 bg-white px-3 py-2.5 text-sm text-slate-800 outline-none focus:border-emerald-500 focus:ring-2 focus:ring-emerald-500/15";
const buttonClass = "inline-flex items-center justify-center gap-2 rounded-xl bg-emerald-700 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-emerald-800 disabled:opacity-60";

function formatDateTime(value: string | null | undefined) {
  return value
    ? new Intl.DateTimeFormat("id-ID", { timeZone: "Asia/Jakarta", dateStyle: "medium", timeStyle: "short" }).format(new Date(value)) + " WIB"
    : "Belum pernah";
}

export default async function SettingsPage({ searchParams }: SettingsPageProps) {
  const admin = await verifyAdminSession();
  if (!admin.isAdmin) redirect("/login?error=unauthorized");
  const params = await searchParams;
  const supabase = await createClient();
  const [sourcesResult, ingestionResult, runsResult, feedResult] = await Promise.all([
    supabase.from("kajian_sources").select("*").order("created_at", { ascending: true }),
    supabase.from("kajian_ingestion_settings").select("*").eq("id", "default").maybeSingle(),
    supabase.from("kajian_ingestion_runs").select("*").order("started_at", { ascending: false }).limit(8),
    supabase.from("kajian_feed_settings").select("*").eq("id", "default").maybeSingle(),
  ]);

  const sources = (sourcesResult.data ?? []) as KajianSource[];
  const ingestion = ingestionResult.data as KajianIngestionSettings | null;
  const runs = (runsResult.data ?? []) as KajianIngestionRun[];
  const feed = feedResult.data as KajianFeedSettings | null;
  const errors = [sourcesResult.error, ingestionResult.error, runsResult.error, feedResult.error].filter(Boolean);

  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar userEmail={admin.user?.email} role={admin.user?.app_metadata?.role} />
      <main className="mx-auto max-w-7xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-emerald-700">Operasional katalog</p>
          <h1 className="mt-1 text-2xl font-bold tracking-tight text-slate-950">Pengaturan Kajian</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
            Kelola sumber YouTube, jadwal sinkronisasi, dan urutan feed aplikasi. Video baru tetap menunggu persetujuan editor.
          </p>
        </div>

        {params.notice && <div className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">{params.notice}</div>}
        {params.error && <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-900">{params.error}</div>}
        {errors.length > 0 && (
          <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
            Sebagian pengaturan belum dapat dimuat. Jalankan migration `202610160001_kajian_ingestion_settings.sql` di Supabase, lalu muat ulang halaman.
            <div className="mt-1 text-xs">{errors[0]?.message}</div>
          </div>
        )}

        <section className={cardClass}>
          <div className="mb-5 flex items-start gap-3">
            <div className="rounded-xl bg-emerald-50 p-2.5 text-emerald-700"><Youtube className="h-5 w-5" /></div>
            <div>
              <h2 className="font-semibold text-slate-950">Sumber YouTube</h2>
              <p className="mt-1 text-sm text-slate-500">Channel mengimpor video dari uploads channel. Playlist memberi kategori dan jenis default yang lebih spesifik.</p>
            </div>
          </div>

          <form action={saveSourceAction} className="grid gap-3 rounded-xl border border-dashed border-slate-300 bg-slate-50/70 p-4 md:grid-cols-6">
            <div>
              <label className={labelClass} htmlFor="new-source-kind">Tipe sumber</label>
              <select id="new-source-kind" name="source_kind" className={inputClass} defaultValue="channel">
                <option value="channel">Channel</option><option value="playlist">Playlist</option>
              </select>
            </div>
            <div className="md:col-span-2">
              <label className={labelClass} htmlFor="new-source-id">URL atau ID YouTube</label>
              <input id="new-source-id" name="source_identifier" className={inputClass} placeholder="https://youtube.com/@channel atau playlist URL" required />
            </div>
            <div>
              <label className={labelClass} htmlFor="new-source-name">Nama tampilan</label>
              <input id="new-source-name" name="display_name" className={inputClass} placeholder="Nama channel / playlist" />
            </div>
            <div>
              <label className={labelClass} htmlFor="new-source-category">Kategori default</label>
              <select id="new-source-category" name="category" className={inputClass} defaultValue="Tazkiyatun Nufus">
                {VALID_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}
              </select>
            </div>
            <div className="flex items-end gap-3">
              <div className="min-w-0 flex-1">
                <label className={labelClass} htmlFor="new-source-type">Jenis default</label>
                <select id="new-source-type" name="content_type" className={inputClass} defaultValue="kajian">
                  {VALID_CONTENT_TYPES.map((type) => <option key={type} value={type}>{type === "kajian" ? "Kajian" : "Short"}</option>)}
                </select>
              </div>
              <input type="hidden" name="is_active" value="on" />
              <button className={`${buttonClass} h-[42px] shrink-0 px-3`} title="Tambah sumber"><Plus className="h-4 w-4" /><span className="sr-only">Tambah</span></button>
            </div>
          </form>

          <div className="mt-4 space-y-3">
            {sources.length === 0 && <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">Belum ada sumber terdaftar.</p>}
            {sources.map((source) => (
              <div key={source.id} className="rounded-xl border border-slate-200 p-4">
                <form action={saveSourceAction} className="grid gap-3 md:grid-cols-6">
                  <input type="hidden" name="id" value={source.id} />
                  <div>
                    <label className={labelClass}>Tipe</label>
                    <select name="source_kind" className={inputClass} defaultValue={source.source_kind}>
                      <option value="channel">Channel</option><option value="playlist">Playlist</option>
                    </select>
                  </div>
                  <div className="md:col-span-2">
                    <label className={labelClass}>ID sumber</label>
                    <input name="source_identifier" className={inputClass} defaultValue={source.source_identifier} required />
                  </div>
                  <div>
                    <label className={labelClass}>Nama</label>
                    <input name="display_name" className={inputClass} defaultValue={source.display_name} required />
                  </div>
                  <div>
                    <label className={labelClass}>Kategori default</label>
                    <select name="category" className={inputClass} defaultValue={source.category}>
                      {VALID_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}
                    </select>
                  </div>
                  <div className="flex items-end gap-2">
                    <div className="min-w-0 flex-1">
                      <label className={labelClass}>Jenis default</label>
                      <select name="content_type" className={inputClass} defaultValue={source.content_type}>
                        {VALID_CONTENT_TYPES.map((type) => <option key={type} value={type}>{type === "kajian" ? "Kajian" : "Short"}</option>)}
                      </select>
                    </div>
                    <label className="mb-2 flex items-center gap-1.5 text-xs text-slate-600">
                      <input type="checkbox" name="is_active" defaultChecked={source.is_active} className="accent-emerald-700" /> Aktif
                    </label>
                    <button className={`${buttonClass} h-[42px] px-3`}>Simpan</button>
                  </div>
                </form>
                <form action={deleteSourceAction} className="mt-2 text-right">
                  <input type="hidden" name="id" value={source.id} />
                  <button className="text-xs font-medium text-rose-700 hover:text-rose-900">Hapus sumber</button>
                </form>
              </div>
            ))}
          </div>
          <p className="mt-4 text-xs leading-5 text-slate-500">Tipe `Short` tidak ditebak dari durasi. Nilai default mengikuti sumber; editor dapat mengubah klasifikasi per video sebelum menyetujui.</p>
        </section>

        <section className="grid gap-6 lg:grid-cols-[1fr_1.2fr]">
          <div className={cardClass}>
            <div className="mb-5 flex items-start gap-3">
              <div className="rounded-xl bg-sky-50 p-2.5 text-sky-700"><Clock3 className="h-5 w-5" /></div>
              <div>
                <h2 className="font-semibold text-slate-950">Jadwal sinkronisasi</h2>
                <p className="mt-1 text-sm text-slate-500">GitHub Actions memeriksa jadwal setiap 15 menit. Waktu mulai dapat bertambah karena antrean GitHub.</p>
              </div>
            </div>
            <form action={saveIngestionSettingsAction} className="space-y-4">
              <div>
                <label className={labelClass} htmlFor="schedule-mode">Mode</label>
                <select id="schedule-mode" name="schedule_mode" className={inputClass} defaultValue={ingestion?.schedule_mode ?? "daily"}>
                  <option value="daily">Setiap hari pada jam tertentu</option>
                  <option value="interval">Setiap interval</option>
                </select>
              </div>
              <div>
                <label className={labelClass} htmlFor="daily-time">Jam harian (WIB)</label>
                <input id="daily-time" name="daily_time" type="time" className={inputClass} defaultValue={(ingestion?.daily_time ?? "00:00").slice(0, 5)} required />
              </div>
              <div>
                <label className={labelClass} htmlFor="interval-minutes">Interval</label>
                <select id="interval-minutes" name="interval_minutes" className={inputClass} defaultValue={String(ingestion?.interval_minutes ?? 1440)}>
                  <option value="15">Setiap 15 menit</option><option value="30">Setiap 30 menit</option>
                  <option value="60">Setiap 1 jam</option><option value="180">Setiap 3 jam</option>
                  <option value="360">Setiap 6 jam</option><option value="720">Setiap 12 jam</option>
                  <option value="1440">Setiap 24 jam</option>
                </select>
              </div>
              <p className="text-xs text-slate-500">Zona waktu tetap Asia/Jakarta. Jadwal harian saat ini: {ingestion?.daily_time?.slice(0, 5) ?? "00:00"} WIB.</p>
              <button className={buttonClass}><Settings2 className="h-4 w-4" /> Simpan jadwal</button>
            </form>
            <div className="mt-6 border-t border-slate-100 pt-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="text-xs leading-5 text-slate-600">
                  <div>Run terakhir: <span className="font-semibold text-slate-800">{formatDateTime(ingestion?.last_run_at)}</span></div>
                  <div>Perkiraan berikutnya: <span className="font-semibold text-slate-800">{formatDateTime(ingestion?.next_run_at)}</span></div>
                  {ingestion?.run_requested_at && <div className="mt-1 font-semibold text-amber-700">Permintaan manual sedang menunggu poller.</div>}
                </div>
                <form action={requestIngestionNowAction}>
                  <button className={buttonClass}><Activity className="h-4 w-4" /> Cek sekarang</button>
                </form>
              </div>
            </div>
          </div>

          <div className={cardClass}>
            <div className="mb-5 flex items-start gap-3">
              <div className="rounded-xl bg-violet-50 p-2.5 text-violet-700"><ListVideo className="h-5 w-5" /></div>
              <div>
                <h2 className="font-semibold text-slate-950">Urutan dan filter feed Android</h2>
                <p className="mt-1 text-sm text-slate-500">Pengaturan ini mengubah default katalog publik. Panel editorial tetap dapat meninjau semua status dan mengurutkan daftar sendiri.</p>
              </div>
            </div>
            <form action={saveFeedSettingsAction} className="space-y-5">
              <div>
                <label className={labelClass} htmlFor="feed-sort">Urutkan video</label>
                <select id="feed-sort" name="sort_order" className={inputClass} defaultValue={feed?.sort_order ?? "newest"}>
                  <option value="newest">Terbaru dirilis</option><option value="oldest">Terlama dirilis</option>
                  <option value="duration_desc">Durasi terpanjang</option><option value="duration_asc">Durasi terpendek</option>
                </select>
              </div>
              <fieldset>
                <legend className={labelClass}>Jenis video yang ditampilkan</legend>
                <div className="flex flex-wrap gap-4">
                  {VALID_CONTENT_TYPES.map((type) => (
                    <label key={type} className="flex items-center gap-2 text-sm text-slate-700">
                      <input type="checkbox" name="content_types" value={type} defaultChecked={feed?.content_types?.includes(type) ?? true} className="accent-emerald-700" />
                      {type === "kajian" ? "Kajian" : "Short"}
                    </label>
                  ))}
                </div>
              </fieldset>
              <div className="grid gap-3 sm:grid-cols-2">
                <div>
                  <label className={labelClass} htmlFor="feed-min-duration">Durasi minimum (menit)</label>
                  <input id="feed-min-duration" name="min_duration_minutes" type="number" min="0" step="1" className={inputClass} defaultValue={feed?.min_duration_seconds == null ? "" : Math.floor(feed.min_duration_seconds / 60)} placeholder="Tanpa batas" />
                </div>
                <div>
                  <label className={labelClass} htmlFor="feed-max-duration">Durasi maksimum (menit)</label>
                  <input id="feed-max-duration" name="max_duration_minutes" type="number" min="0" step="1" className={inputClass} defaultValue={feed?.max_duration_seconds == null ? "" : Math.floor(feed.max_duration_seconds / 60)} placeholder="Tanpa batas" />
                </div>
              </div>
              <p className="text-xs leading-5 text-slate-500">Biarkan durasi kosong untuk tanpa batas. Filter tidak mengubah status review atau menghapus video dari katalog admin.</p>
              <button className={buttonClass}><Film className="h-4 w-4" /> Simpan feed</button>
            </form>
          </div>
        </section>

        <section className={cardClass}>
          <div className="mb-4 flex items-center gap-3">
            <div className="rounded-xl bg-slate-100 p-2.5 text-slate-700"><Radio className="h-5 w-5" /></div>
            <div><h2 className="font-semibold text-slate-950">Riwayat ingestion</h2><p className="mt-1 text-sm text-slate-500">Run yang dijalankan atau gagal diproses.</p></div>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[680px] text-left text-sm">
              <thead><tr className="border-b border-slate-200 text-xs text-slate-500"><th className="py-3 pr-4">Mulai</th><th className="py-3 pr-4">Pemicu</th><th className="py-3 pr-4">Status</th><th className="py-3 pr-4">Video</th><th className="py-3 pr-4">Ditulis</th><th className="py-3 pr-4">Dihapus</th><th className="py-3">Pesan</th></tr></thead>
              <tbody>
                {runs.length === 0 && <tr><td colSpan={7} className="py-5 text-center text-slate-500">Belum ada riwayat run.</td></tr>}
                {runs.map((run) => (
                  <tr key={run.id} className="border-b border-slate-100 align-top">
                    <td className="py-3 pr-4 text-xs text-slate-600">{formatDateTime(run.started_at)}</td>
                    <td className="py-3 pr-4">{run.trigger_type === "manual" ? "Manual" : "Jadwal"}</td>
                    <td className="py-3 pr-4"><span className={`rounded-full px-2 py-1 text-xs font-semibold ${run.status === "success" ? "bg-emerald-50 text-emerald-700" : run.status === "failed" ? "bg-rose-50 text-rose-700" : "bg-amber-50 text-amber-700"}`}>{run.status}</span></td>
                    <td className="py-3 pr-4">{run.videos_seen}</td><td className="py-3 pr-4">{run.candidates_written}</td><td className="py-3 pr-4">{run.removed_count}</td>
                    <td className="max-w-[260px] py-3 text-xs text-rose-700">{run.error_message || `Selesai ${run.finished_at ? formatDateTime(run.finished_at) : "sedang berjalan"}`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </main>
    </div>
  );
}
