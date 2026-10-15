import { Smartphone, Info, Sparkles } from "lucide-react";

export function AndroidNoticeBanner() {
  return (
    <div className="relative overflow-hidden rounded-2xl border border-emerald-200/80 bg-gradient-to-r from-emerald-50 via-teal-50 to-slate-50 p-4 sm:p-5 shadow-xs mb-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex items-start gap-3.5">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-xs">
            <Smartphone className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h2 className="text-sm font-semibold text-slate-900">
                Sinkronisasi Aplikasi Android Daily Sunnah
              </h2>
              <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2 py-0.5 text-[11px] font-medium text-emerald-800">
                <Sparkles className="h-3 w-3 text-emerald-600" />
                Live Sync
              </span>
            </div>
            <p className="mt-1 text-xs text-slate-600 leading-relaxed max-w-3xl">
              Video <strong className="text-emerald-900 font-semibold">hanya akan muncul di aplikasi Android Daily Sunnah</strong> jika status review telah diset menjadi{" "}
              <span className="inline-flex items-center rounded-sm bg-emerald-600 px-1.5 py-0.2 text-[11px] font-medium text-white">
                Disetujui (Approved)
              </span>
              . Video berstatus <em>Pending</em> atau <em>Rejected</em> tidak akan dapat diakses oleh publik pengguna aplikasi.
            </p>
          </div>
        </div>

        <div className="w-full sm:w-auto shrink-0 flex items-center gap-2 rounded-xl bg-white/80 backdrop-blur px-3 py-2 border border-emerald-100 text-[11px] text-slate-600">
          <Info className="h-4 w-4 text-emerald-600 shrink-0" />
          <span>
            Jenis konten (<strong className="text-slate-800">kajian</strong> /{" "}
            <strong className="text-slate-800">short</strong>) adalah keputusan editorial murni, bukan semata ditentukan oleh durasi.
          </span>
        </div>
      </div>
    </div>
  );
}
