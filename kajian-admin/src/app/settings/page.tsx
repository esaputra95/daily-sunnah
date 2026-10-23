import { redirect } from "next/navigation";
import { Activity, Archive, Clock3, Film, ListVideo, Pause, Play, Plus, Radio, RotateCcw, Settings2, Youtube } from "lucide-react";
import { Navbar } from "@/components/Navbar";
import {
  deleteSourceAction,
  manageSourceBackfillAction,
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
  const backfillReady = sources.every((source) => source.backfill_status !== undefined);
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

          <form action={saveSourceAction} className="rounded-xl border border-dashed border-slate-300 bg-slate-50/70 p-4 space-y-3">
            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
              <div>
                <label className={labelClass} htmlFor="new-source-kind">Tipe sumber</label>
                <select id="new-source-kind" name="source_kind" className={inputClass} defaultValue="channel">
                  <option value="channel">Channel</option>
                  <option value="playlist">Playlist</option>
                </select>
              </div>
              <div className="sm:col-span-2 lg:col-span-2">
                <label className={labelClass} htmlFor="new-source-id">URL atau ID YouTube</label>
                <input id="new-source-id" name="source_identifier" className={inputClass} placeholder="https://youtube.com/@channel atau playlist URL" required />
              </div>
              <div>
                <label className={labelClass} htmlFor="new-source-name">Nama tampilan</label>
                <input id="new-source-name" name="display_name" className={inputClass} placeholder="Nama channel / playlist" />
              </div>
            </div>

            <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5 items-end">
              <div>
                <label className={labelClass} htmlFor="new-source-category">Kategori default</label>
                <select id="new-source-category" name="category" className={inputClass} defaultValue="Tazkiyatun Nufus">
                  {VALID_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}
                </select>
              </div>
              <div>
                <label className={labelClass} htmlFor="new-source-type">Jenis default</label>
                <select id="new-source-type" name="content_type" className={inputClass} defaultValue="kajian">
                  {VALID_CONTENT_TYPES.map((type) => <option key={type} value={type}>{type === "kajian" ? "Kajian" : "Short"}</option>)}
                </select>
              </div>
              <div>
                <label className={labelClass} htmlFor="new-source-status">Status awal</label>
                <select id="new-source-status" name="initial_review_status" className={inputClass} defaultValue="pending">
                  <option value="pending">Pending</option>
                  <option value="approved">Auto Approve</option>
                </select>
              </div>
              <div>
                <label className={labelClass} htmlFor="new-source-max">Maks video diambil</label>
                <input id="new-source-max" name="max_videos" type="number" min="1" max="200" className={inputClass} defaultValue="10" placeholder="10" required />
              </div>
              <div>
                <input type="hidden" name="is_active" value="on" />
                <button className={`${buttonClass} w-full h-[42px]`} title="Tambah sumber">
                  <Plus className="h-4 w-4" />
                  <span>Tambah Sumber</span>
                </button>
              </div>
            </div>
          </form>

          <div className="mt-4 space-y-3">
            {sources.length === 0 && <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">Belum ada sumber terdaftar.</p>}
            {sources.length > 0 && sources.every((source) => source.backfill_status === undefined) && (
              <div className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
                Fitur arsip video lama memerlukan migration <code>202610230001_kajian_source_backfill.sql</code> di Supabase.
              </div>
            )}
            {sources.map((source) => (
              <div key={source.id} className="rounded-xl border border-slate-200 p-4 bg-white shadow-2xs space-y-3">
                <form action={saveSourceAction} className="space-y-3">
                  <input type="hidden" name="id" value={source.id} />
                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                    <div>
                      <label className={labelClass}>Tipe sumber</label>
                      <select name="source_kind" className={inputClass} defaultValue={source.source_kind}>
                        <option value="channel">Channel</option>
                        <option value="playlist">Playlist</option>
                      </select>
                    </div>
                    <div className="sm:col-span-2 lg:col-span-2">
                      <label className={labelClass}>URL / ID sumber</label>
                      <input name="source_identifier" className={inputClass} defaultValue={source.source_identifier} required />
                    </div>
                    <div>
                      <label className={labelClass}>Nama tampilan</label>
                      <input name="display_name" className={inputClass} defaultValue={source.display_name} required />
                    </div>
                  </div>

                  <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6 items-end">
                    <div>
                      <label className={labelClass}>Kategori default</label>
                      <select name="category" className={inputClass} defaultValue={source.category}>
                        {VALID_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className={labelClass}>Jenis default</label>
                      <select name="content_type" className={inputClass} defaultValue={source.content_type}>
                        {VALID_CONTENT_TYPES.map((type) => <option key={type} value={type}>{type === "kajian" ? "Kajian" : "Short"}</option>)}
                      </select>
                    </div>
                    <div>
                      <label className={labelClass}>Status awal</label>
                      <select name="initial_review_status" className={inputClass} defaultValue={source.initial_review_status ?? "pending"}>
                        <option value="pending">Pending</option>
                        <option value="approved">Auto Approve</option>
                      </select>
                    </div>
                    <div>
                      <label className={labelClass}>Maks video</label>
                      <input name="max_videos" type="number" min="1" max="200" className={inputClass} defaultValue={source.max_videos ?? 10} required />
                    </div>
                    <div className="flex items-center h-[42px] px-1">
                      <label className="flex items-center gap-2 text-xs font-semibold text-slate-700 cursor-pointer">
                        <input type="checkbox" name="is_active" defaultChecked={source.is_active} className="h-4 w-4 rounded accent-emerald-700" />
                        <span>Aktif</span>
                      </label>
                    </div>
                    <div>
                      <button className={`${buttonClass} w-full h-[42px] px-3`}>Simpan</button>
                    </div>
                  </div>
                </form>
                <div className="flex flex-wrap justify-between items-center gap-3 pt-2 border-t border-slate-100 text-xs text-slate-500">
                  <span>Batas maksimal: <strong>{source.max_videos ?? 10} video</strong> terbaru per sinkronisasi rutin</span>
                  <form action={deleteSourceAction}>
                    <input type="hidden" name="id" value={source.id} />
                    <button className="font-medium text-rose-600 hover:text-rose-800 transition">Hapus sumber</button>
                  </form>
                </div>
                <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl bg-slate-50 px-3.5 py-3">
                  <div className="flex items-start gap-2.5">
                    <Archive className="mt-0.5 h-4 w-4 shrink-0 text-emerald-700" />
                    <div className="text-xs text-slate-600">
                      <div className="font-semibold text-slate-800">
                        Arsip lama: {({ idle: "Belum dimulai", queued: "Dalam antrean", running: "Berjalan", paused: "Dijeda", completed: "Selesai", failed: "Gagal" } as const)[source.backfill_status ?? "idle"]}
                        {source.backfill_status === "completed" && source.backfill_completed_at ? ` · ${formatDateTime(source.backfill_completed_at)}` : ""}
                      </div>
                      <div className="mt-0.5">{source.backfill_pages_processed ?? 0} batch · {source.backfill_videos_processed ?? 0} video diproses</div>
                      {source.backfill_error && <div className="mt-1 max-w-xl text-rose-700">{source.backfill_error}</div>}
                    </div>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    {(source.backfill_status === "queued" || source.backfill_status === "running") ? (
                      <form action={manageSourceBackfillAction}>
                        <input type="hidden" name="id" value={source.id} />
                        <input type="hidden" name="intent" value="pause" />
                        <button disabled={!backfillReady} className="inline-flex items-center gap-1.5 rounded-lg border border-slate-300 bg-white px-3 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"><Pause className="h-3.5 w-3.5" /> Jeda</button>
                      </form>
                    ) : source.backfill_status === "paused" || source.backfill_status === "failed" ? (
                      <form action={manageSourceBackfillAction}>
                        <input type="hidden" name="id" value={source.id} />
                        <input type="hidden" name="intent" value="resume" />
                        <button disabled={!backfillReady || !source.is_active} className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-200 bg-white px-3 py-2 text-xs font-semibold text-emerald-800 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-50"><Play className="h-3.5 w-3.5" /> Lanjutkan</button>
                      </form>
                    ) : null}
                    {source.backfill_status !== "queued" && source.backfill_status !== "running" && (
                      <form action={manageSourceBackfillAction}>
                        <input type="hidden" name="id" value={source.id} />
                        <input type="hidden" name="intent" value="start" />
                        <button disabled={!backfillReady || !source.is_active} className="inline-flex items-center gap-1.5 rounded-lg border border-emerald-200 bg-white px-3 py-2 text-xs font-semibold text-emerald-800 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-50"><RotateCcw className="h-3.5 w-3.5" /> {source.backfill_status === "completed" ? "Ulangi arsip" : "Ambil arsip lama"}</button>
                      </form>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
          <p className="mt-4 text-xs leading-5 text-slate-500">Aturan dasar kurasi: Video berdurasi &ge; 5 menit otomatis masuk sebagai <strong>Kajian</strong>, sedangkan &lt; 5 menit masuk sebagai <strong>Short</strong>. Editor tetap memiliki kendali penuh untuk mengubah jenis per-video di halaman katalog.</p>
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

              {/* Pengaturan Status Review Video Baru */}
              <div className="rounded-xl border border-emerald-200/80 bg-emerald-50/60 p-3.5 space-y-1.5">
                <label className={labelClass} htmlFor="default-review-status">
                  Status Review Video Baru dari YouTube
                </label>
                <select
                  id="default-review-status"
                  name="default_review_status"
                  className={inputClass}
                  defaultValue={ingestion?.default_review_status ?? "pending"}
                >
                  <option value="pending">
                    Butuh Review Editor (Pending) — Aman, tinjau & approve manual
                  </option>
                  <option value="approved">
                    Otomatis Disetujui (Auto Approve) — Langsung tampil di Android
                  </option>
                </select>
                <p className="text-[11px] text-slate-500 leading-relaxed">
                  Jika dipilih <strong>Auto Approve</strong>, seluruh video baru hasil penarikan YouTube otomatis disetujui dan langsung tayang di aplikasi Android Daily Sunnah.
                </p>
              </div>

              <p className="text-xs text-slate-500">Zona waktu tetap Asia/Jakarta. Jadwal harian saat ini: {ingestion?.daily_time?.slice(0, 5) ?? "00:00"} WIB.</p>
              <button className={buttonClass}><Settings2 className="h-4 w-4" /> Simpan jadwal & status</button>
            </form>
            <div className="mt-6 border-t border-slate-100 pt-5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="text-xs leading-5 text-slate-600">
                  <div>Run terakhir: <span className="font-semibold text-slate-800">{formatDateTime(ingestion?.last_run_at)}</span></div>
                  <div>Perkiraan berikutnya: <span className="font-semibold text-slate-800">{formatDateTime(ingestion?.next_run_at)}</span></div>
                  <div>Status video baru: <span className={`font-semibold ${ingestion?.default_review_status === "approved" ? "text-emerald-700" : "text-amber-700"}`}>{ingestion?.default_review_status === "approved" ? "Auto Approve" : "Pending Review"}</span></div>
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
                    <td className="py-3 pr-4">{run.trigger_type === "manual" ? "Manual" : run.trigger_type === "backfill" ? "Arsip lama" : "Jadwal"}</td>
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
