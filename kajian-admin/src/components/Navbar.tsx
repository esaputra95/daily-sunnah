"use client";

import Link from "next/link";
import { useTransition } from "react";
import { logoutAction } from "@/app/actions/auth";
import { LogOut, ShieldCheck, Film, Settings2, ListVideo } from "lucide-react";

interface NavbarProps {
  userEmail?: string;
  role?: string;
}

export function Navbar({ userEmail, role }: NavbarProps) {
  const [isPending, startTransition] = useTransition();

  const handleLogout = () => {
    startTransition(async () => {
      await logoutAction();
    });
  };

  return (
    <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/95 backdrop-blur shadow-xs">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex h-16 items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-emerald-600 text-white shadow-xs">
              <Film className="h-5 w-5" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="text-base font-bold text-slate-900 tracking-tight">
                  Daily Sunnah
                </span>
                <span className="rounded-md bg-emerald-50 px-2 py-0.5 text-xs font-semibold text-emerald-700 ring-1 ring-emerald-600/20 ring-inset">
                  Kajian Editorial
                </span>
              </div>
              <p className="text-xs text-slate-500">
                Panel Kurasi Video Katalog Resmi
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3 sm:gap-4">
            <nav className="flex items-center gap-1 rounded-xl bg-slate-100 p-1" aria-label="Navigasi panel">
              <Link href="/catalog" className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-xs font-medium text-slate-600 transition hover:bg-white hover:text-slate-950 sm:px-3">
                <ListVideo className="h-3.5 w-3.5" /><span>Katalog</span>
              </Link>
              <Link href="/settings" className="inline-flex items-center gap-1.5 rounded-lg px-2.5 py-2 text-xs font-medium text-slate-600 transition hover:bg-white hover:text-slate-950 sm:px-3">
                <Settings2 className="h-3.5 w-3.5" /><span>Pengaturan</span>
              </Link>
            </nav>
            {userEmail && (
              <div className="hidden sm:flex flex-col items-end">
                <span className="text-xs font-medium text-slate-700">
                  {userEmail}
                </span>
                <div className="flex items-center gap-1 text-[11px] text-emerald-600 font-medium">
                  <ShieldCheck className="h-3 w-3" />
                  <span>{role || "kajian_admin"}</span>
                </div>
              </div>
            )}

            <button
              onClick={handleLogout}
              disabled={isPending}
              className="inline-flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3.5 py-1.5 text-xs font-medium text-slate-700 shadow-2xs hover:bg-slate-50 hover:text-slate-900 transition-colors disabled:opacity-50"
              title="Keluar dari sesi admin"
            >
              <LogOut className="h-3.5 w-3.5 text-slate-400" />
              <span>{isPending ? "Keluar..." : "Logout"}</span>
            </button>
          </div>
        </div>
      </div>
    </header>
  );
}
