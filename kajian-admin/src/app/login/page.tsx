"use client";

import { useActionState } from "react";
import { loginAction } from "@/app/actions/auth";
import { ShieldCheck, Film, Lock, Mail, AlertCircle, Info } from "lucide-react";

export default function LoginPage() {
  const [state, formAction, isPending] = useActionState(loginAction, null);

  return (
    <div className="min-h-screen bg-slate-900 flex flex-col justify-center py-12 sm:px-6 lg:px-8 relative overflow-hidden">
      {/* Decorative gradient blur background */}
      <div className="absolute -top-40 -left-40 h-96 w-96 rounded-full bg-emerald-600/20 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-40 -right-40 h-96 w-96 rounded-full bg-teal-600/20 blur-3xl pointer-events-none" />

      <div className="sm:mx-auto sm:w-full sm:max-w-md relative z-10 px-4">
        {/* Brand Header */}
        <div className="text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-emerald-600 text-white shadow-xl ring-1 ring-white/20">
            <Film className="h-7 w-7" />
          </div>
          <h1 className="mt-4 text-2xl font-bold tracking-tight text-white">
            Daily Sunnah
          </h1>
          <p className="mt-1 text-xs text-slate-400">
            Panel Kurasi & Editorial Katalog Video Kajian
          </p>
        </div>

        {/* Login Form Card */}
        <div className="mt-8 bg-slate-800/90 backdrop-blur-md py-8 px-6 shadow-2xl rounded-2xl border border-slate-700/80 sm:px-10">
          <div className="mb-6 pb-4 border-b border-slate-700/60">
            <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-emerald-400">
              <ShieldCheck className="h-4 w-4" />
              <span>Autentikasi Admin Terpercaya</span>
            </div>
            <p className="mt-1 text-xs text-slate-400 leading-relaxed">
              Masuk dengan akun yang memiliki klaim <code>app_metadata.role = "kajian_admin"</code>.
            </p>
          </div>

          {state?.error && (
            <div className="mb-6 flex items-start gap-3 rounded-xl bg-rose-950/60 border border-rose-800 p-3.5 text-xs text-rose-300">
              <AlertCircle className="h-4 w-4 text-rose-400 shrink-0 mt-0.5" />
              <span>{state.error}</span>
            </div>
          )}

          <form action={formAction} className="space-y-5">
            <div>
              <label
                htmlFor="email"
                className="block text-xs font-medium text-slate-300 mb-1.5"
              >
                Alamat Email Admin
              </label>
              <div className="relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                  <Mail className="h-4 w-4" />
                </div>
                <input
                  id="email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  required
                  placeholder="admin@dailysunnah.com"
                  className="block w-full rounded-xl border border-slate-700 bg-slate-900/80 pl-10 pr-3.5 py-2.5 text-sm text-white placeholder:text-slate-500 focus:border-emerald-500 focus:outline-hidden focus:ring-2 focus:ring-emerald-500/20"
                />
              </div>
            </div>

            <div>
              <label
                htmlFor="password"
                className="block text-xs font-medium text-slate-300 mb-1.5"
              >
                Kata Sandi
              </label>
              <div className="relative">
                <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3.5 text-slate-400">
                  <Lock className="h-4 w-4" />
                </div>
                <input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  placeholder="••••••••"
                  className="block w-full rounded-xl border border-slate-700 bg-slate-900/80 pl-10 pr-3.5 py-2.5 text-sm text-white placeholder:text-slate-500 focus:border-emerald-500 focus:outline-hidden focus:ring-2 focus:ring-emerald-500/20"
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={isPending}
              className="w-full flex justify-center items-center gap-2 rounded-xl bg-emerald-600 px-4 py-3 text-sm font-semibold text-white shadow-lg hover:bg-emerald-500 focus:outline-hidden focus:ring-2 focus:ring-emerald-500/30 transition-all disabled:opacity-50"
            >
              {isPending ? (
                <>
                  <div className="h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                  <span>Memverifikasi Sesi...</span>
                </>
              ) : (
                <span>Masuk ke Panel Editorial</span>
              )}
            </button>
          </form>

          {/* Security & Setup Advisory */}
          <div className="mt-6 pt-5 border-t border-slate-700/60 text-[11px] text-slate-400 leading-relaxed flex items-start gap-2">
            <Info className="h-4 w-4 text-emerald-400 shrink-0 mt-0.5" />
            <span>
              <strong>Pendaftaran publik ditiadakan.</strong> Role admin hanya dapat diberikan langsung oleh pemilik project di Supabase Dashboard melalui claim <code>app_metadata.role</code>.
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
