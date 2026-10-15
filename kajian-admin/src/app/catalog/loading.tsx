export default function CatalogLoading() {
  return (
    <div className="min-h-screen bg-slate-50">
      <div className="h-16 border-b border-slate-200 bg-white" />
      <main className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Banner Skeleton */}
        <div className="h-24 w-full rounded-2xl bg-slate-200/70 animate-pulse mb-6" />

        {/* Tabs Skeleton */}
        <div className="h-12 w-96 rounded-xl bg-slate-200/70 animate-pulse mb-6" />

        {/* FilterBar Skeleton */}
        <div className="h-16 w-full rounded-2xl bg-slate-200/70 animate-pulse mb-6" />

        {/* Video Cards Grid Skeleton */}
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {Array.from({ length: 6 }).map((_, i) => (
            <div
              key={i}
              className="rounded-2xl border border-slate-200 bg-white p-5 space-y-4"
            >
              <div className="aspect-16/9 rounded-xl bg-slate-200 animate-pulse" />
              <div className="h-4 w-3/4 rounded bg-slate-200 animate-pulse" />
              <div className="h-3 w-1/2 rounded bg-slate-200 animate-pulse" />
              <div className="h-16 w-full rounded bg-slate-100 animate-pulse" />
            </div>
          ))}
        </div>
      </main>
    </div>
  );
}
