import { redirect } from "next/navigation";
import { createClient, verifyAdminSession } from "@/lib/supabase/server";
import { Navbar } from "@/components/Navbar";
import { AndroidNoticeBanner } from "@/components/AndroidNoticeBanner";
import { StatusTabs } from "@/components/StatusTabs";
import { FilterBar } from "@/components/FilterBar";
import { VideoList } from "@/components/VideoList";
import { Pagination } from "@/components/Pagination";
import { AddVideoButton } from "@/components/AddVideoButton";
import { KajianVideo, StatusCounts } from "@/lib/types";

interface CatalogPageProps {
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>;
}

export default async function CatalogPage({ searchParams }: CatalogPageProps) {
  // 1. Verifikasi role admin
  const adminCheck = await verifyAdminSession();
  if (!adminCheck.isAdmin) {
    redirect("/login?error=unauthorized");
  }

  const user = adminCheck.user;
  const userEmail = user?.email;
  const userRole = user?.app_metadata?.role as string | undefined;

  // 2. Baca parameter pencarian dan filter (Next.js 15 async searchParams)
  const resolvedParams = await searchParams;

  const currentStatusParam = typeof resolvedParams.status === "string" ? resolvedParams.status : "pending";
  const searchQuery = typeof resolvedParams.q === "string" ? resolvedParams.q.trim() : "";
  const categoryFilter = typeof resolvedParams.category === "string" ? resolvedParams.category : "";
  const contentTypeFilter = typeof resolvedParams.content_type === "string" ? resolvedParams.content_type : "";
  const minDurationMinutes = typeof resolvedParams.min_minutes === "string" ? Number(resolvedParams.min_minutes) : NaN;
  const maxDurationMinutes = typeof resolvedParams.max_minutes === "string" ? Number(resolvedParams.max_minutes) : NaN;
  const sortOption = typeof resolvedParams.sort === "string" ? resolvedParams.sort : "newest";
  const pageParam = typeof resolvedParams.page === "string" ? resolvedParams.page : "1";

  const pageSize = 12;
  const currentPage = Math.max(1, parseInt(pageParam, 10) || 1);
  const from = (currentPage - 1) * pageSize;
  const to = from + pageSize - 1;

  const supabase = await createClient();

  // 3. Ambil jumlah untuk masing-masing status tab
  const [pendingRes, approvedRes, rejectedRes, allRes] = await Promise.all([
    supabase.from("kajian_videos").select("id", { count: "exact", head: true }).eq("review_status", "pending"),
    supabase.from("kajian_videos").select("id", { count: "exact", head: true }).eq("review_status", "approved"),
    supabase.from("kajian_videos").select("id", { count: "exact", head: true }).eq("review_status", "rejected"),
    supabase.from("kajian_videos").select("id", { count: "exact", head: true }),
  ]);

  const counts: StatusCounts = {
    pending: pendingRes.count || 0,
    approved: approvedRes.count || 0,
    rejected: rejectedRes.count || 0,
    all: allRes.count || 0,
  };

  // 4. Query data video sesuai tab dan filter yang dipilih
  let query = supabase.from("kajian_videos").select("*", { count: "exact" });

  // Filter status (jika bukan 'all')
  if (currentStatusParam && currentStatusParam !== "all") {
    query = query.eq("review_status", currentStatusParam);
  }

  // Filter kategori
  if (categoryFilter) {
    query = query.eq("category", categoryFilter);
  }

  // Filter content_type
  if (contentTypeFilter) {
    query = query.eq("content_type", contentTypeFilter);
  }

  if (Number.isInteger(minDurationMinutes) && minDurationMinutes >= 0) {
    query = query.gte("duration_seconds", minDurationMinutes * 60);
  }
  if (Number.isInteger(maxDurationMinutes) && maxDurationMinutes >= 0) {
    query = query.lte("duration_seconds", maxDurationMinutes * 60 + 59);
  }

  // Filter kata kunci pencarian (judul atau channel_name)
  if (searchQuery) {
    query = query.or(
      `title.ilike.%${searchQuery}%,channel_name.ilike.%${searchQuery}%`
    );
  }

  // Pengurutan
  if (sortOption === "oldest") {
    query = query.order("published_at", { ascending: true, nullsFirst: false });
  } else if (sortOption === "created_desc") {
    query = query.order("created_at", { ascending: false });
  } else if (sortOption === "duration_desc") {
    query = query.order("duration_seconds", { ascending: false, nullsFirst: false });
  } else if (sortOption === "duration_asc") {
    query = query.order("duration_seconds", { ascending: true, nullsFirst: false });
  } else {
    // Default newest (terbaru berdasarkan tanggal rilis YouTube)
    query = query.order("published_at", { ascending: false, nullsFirst: false });
  }

  // Batasi rentang halaman
  query = query.range(from, to);

  const { data: videos, count, error } = await query;

  if (error) {
    console.error("Error fetching kajian videos:", error);
  }

  const videoList = (videos as KajianVideo[]) || [];
  const totalCount = count || 0;
  const totalPages = Math.ceil(totalCount / pageSize);

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col">
      <Navbar userEmail={userEmail} role={userRole} />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Banner Sinkronisasi Android */}
        <AndroidNoticeBanner />

        {/* Header Katalog & Tombol Tambah Video */}
        <div className="mb-6 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-slate-950">
              Katalog Video Kajian
            </h1>
            <p className="mt-1 text-xs text-slate-500">
              Kelola, kurasi, dan tinjau video sebelum disinkronkan ke aplikasi Android Daily Sunnah
            </p>
          </div>
          <AddVideoButton />
        </div>

        {/* Tab Status Review */}
        <div className="mb-6">
          <StatusTabs currentStatus={currentStatusParam} counts={counts} />
        </div>

        {/* Error Alert jika query Supabase gagal atau ditolak RLS */}
        {error && (
          <div className="mb-6 rounded-2xl border border-rose-200 bg-rose-50 p-4 text-xs text-rose-800">
            <div className="font-semibold text-rose-900 mb-1">
              Gagal memuat data dari Supabase (Error: {error.message})
            </div>
            <p className="text-rose-700">
              Kode: {error.code}. Jika error berkaitan dengan RLS/permission, pastikan migration SQL telah dijalankan di Supabase SQL Editor dan akun Anda memiliki claim role <code>kajian_admin</code>.
            </p>
          </div>
        )}

        {/* Filter Bar (Search, Kategori, Jenis, Pengurutan) */}
        <FilterBar />

        {/* Daftar Video Grid & Toasts */}
        <VideoList videos={videoList} />

        {/* Pagination */}
        <Pagination
          currentPage={currentPage}
          totalPages={totalPages}
          totalCount={totalCount}
          pageSize={pageSize}
        />
      </main>
    </div>
  );
}
