# Daily Sunnah — Panel Admin Kurasi Katalog Video Kajian & Pengaturan Ingestion

Panel admin berbasis web untuk kurasi editorial katalog video kajian Daily Sunnah. Dibangun menggunakan **Next.js 15 (App Router)**, **TypeScript**, **Tailwind CSS**, dan **@supabase/ssr**.

Aplikasi ini berjalan sebagai aplikasi web mandiri di folder `panel/kajian-admin/` dan terintegrasi dengan basis data Supabase (`public.kajian_videos`, `public.kajian_sources`, `public.kajian_ingestion_settings`, `public.kajian_ingestion_runs`, dan `public.kajian_feed_settings`).

---

## 📌 Fitur Utama

### 1. Autentikasi & Keamanan (Supabase Auth & RLS)
- Login berbasis Supabase Auth dengan claim terpercaya: `app_metadata.role = "kajian_admin"` (juga mendukung `user_metadata.role = "kajian_admin"`).
- Tidak ada form pendaftaran publik (pendaftaran ditutup).
- Seluruh query dan modifikasi dilindungi **Row Level Security (RLS)** PostgreSQL.
- Tidak pernah menggunakan `service_role` atau `SUPABASE_SECRET_KEY` di client browser.

### 2. Katalog Video Editorial (`/catalog`)
- Tab status: **Pending**, **Approved**, **Rejected**, dan **Semua** dengan penghitung jumlah real-time.
- Pencarian judul dan channel.
- Filter berdasarkan Kategori: `Akidah`, `Fiqih`, `Muamalah`, `Sirah`, `Tazkiyatun Nufus`.
- Filter berdasarkan Jenis Konten: `kajian` atau `short`.
- Pengurutan: Terbaru/Terlama rilis YouTube, Terbaru masuk sistem, Durasi terpanjang/terpendek.
- Pemutar preview YouTube responsif (`youtube-nocookie.com`) untuk format horizontal dan vertikal.
- Pengubahan kategori dan jenis konten oleh editor, dilanjutkan aksi **Approve** atau **Reject** dengan modal konfirmasi dan pengisian timestamp `reviewed_at`.
- Status video baru selalu berstatus `pending` sampai disetujui admin.

### 3. Pengaturan Operasional Ingestion & Feed (`/settings`)
- **Kelola Sumber YouTube (`kajian_sources`)**:
  - Tambah, edit, dan hapus sumber video bertipe **Playlist** (ID playlist YouTube) atau **Channel** (ID channel `UC...` atau handle `@handle` seperti `@MuhammadNuzulDzikri`).
  - Atur kategori dan jenis default per sumber, serta toggle status aktif (`is_active`).
- **Jadwal Sinkronisasi (`kajian_ingestion_settings`)**:
  - Pilihan mode: **Harian** (`daily`) pada jam tertentu (WIB / Asia/Jakarta) atau **Interval** (`interval`) dari 15 menit hingga 30 hari.
  - Tampilan informasi run terakhir (`last_run_at`) dan perkiraan run berikutnya (`next_run_at`).
  - Tombol **"Cek sekarang"** untuk memicu ingestion segera secara aman dan idempoten via antrean poller.
- **Urutan & Filter Feed Publik (`kajian_feed_settings`)**:
  - Pengaturan urutan feed default aplikasi Android (terbaru, terlama, durasi).
  - Pembatasan jenis konten yang ditampilkan (kajian, short).
  - Pembatasan durasi minimum dan maksimum (opsional).
- **Riwayat Ingestion (`kajian_ingestion_runs`)**:
  - Pemantauan 8 run terakhir dengan status (`running`, `success`, `failed`), jumlah video terproses, kandidat tertulis, video dihapus/tidak tersedia, dan rincian pesan error jika gagal.

---

## 📁 Struktur Direktori

```text
panel/kajian-admin/
├── .env.example
├── .env.local                    # Lokal only (masuk .gitignore)
├── .gitignore
├── next.config.ts
├── package.json
├── postcss.config.mjs
├── tailwind.config.ts
├── tsconfig.json
├── README.md
├── supabase/
│   └── migrations/
│       ├── 20261015000000_kajian_admin_rls.sql           # RLS & grant update editorial video
│       └── 202610160001_kajian_ingestion_settings.sql   # Tabel sources, settings, runs, feed
└── src/
    ├── middleware.ts             # Session refresh & proteksi route /catalog dan /settings
    ├── app/
    │   ├── layout.tsx
    │   ├── page.tsx              # Root redirect ke /catalog atau /login
    │   ├── globals.css
    │   ├── login/page.tsx        # Login admin
    │   ├── catalog/              # Dashboard kurasi katalog
    │   ├── settings/             # Pengaturan sumber, jadwal, feed, dan riwayat
    │   └── actions/              # Server actions (auth, editorial, settings)
    ├── components/               # Navbar, FilterBar, VideoCard, PreviewModal, dll.
    └── lib/                      # Supabase client/server helper, types, utils
```

---

## 🚀 Panduan Menjalankan Panel Admin Lokal

### 1. Masuk ke Folder Panel
```bash
cd panel/kajian-admin
```

### 2. Instal Dependensi
```bash
npm install
```

### 3. Konfigurasi Environment Variables Lokal
Buat file `.env.local` (file ini otomatis diabaikan oleh Git via `.gitignore`):

```env
NEXT_PUBLIC_SUPABASE_URL=https://your-project-id.supabase.co
NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY=your-supabase-publishable-key
```

> **Keamanan**: Jangan pernah menaruh secret key (`SUPABASE_SECRET_KEY`, `service_role`, atau `YOUTUBE_DATA_API_KEY`) ke dalam file `.env.local` atau source code panel web. Panel hanya memerlukan URL dan Publishable Key.

### 4. Jalankan Server Development
```bash
npm run dev
```
Buka browser di **http://localhost:3000**.

---

## 🗄️ Menjalankan Migration SQL di Supabase

Jalankan file migration berikut secara berurutan di **Supabase Dashboard** $\rightarrow$ **SQL Editor**:

1. **`202610140001_kajian_videos.sql`** (sudah ada di repo utama):
   Membuat tabel dasar `public.kajian_videos` dan trigger update `updated_at`.
2. **`20261015000000_kajian_admin_rls.sql`**:
   Mengaktifkan hak SELECT untuk publik (hanya `approved`), hak baca penuh untuk admin, serta hak `UPDATE` editorial untuk role `kajian_admin`.
3. **`202610160001_kajian_ingestion_settings.sql`**:
   Membuat tabel `kajian_sources`, `kajian_ingestion_settings`, `kajian_ingestion_runs`, dan `kajian_feed_settings`. Memasukkan seed awal (playlist Tazkiyatun Nufus serta channel `@MuhammadNuzulDzikri`).

---

## 🔐 Pembuatan User Admin & Pemberian Role

1. Di **Supabase Dashboard**, buka **Authentication** $\rightarrow$ **Users** $\rightarrow$ **Add User**.
2. Masukkan email dan password, centang **Auto Confirm User**.
3. Di **SQL Editor**, sematkan claim role admin ke akun tersebut:
   ```sql
   UPDATE auth.users
   SET
     raw_app_meta_data = raw_app_meta_data || '{"role": "kajian_admin"}'::jsonb,
     raw_user_meta_data = raw_user_meta_data || '{"role": "kajian_admin"}'::jsonb
   WHERE email = 'EMAIL_ADMIN_ANDA';
   ```
4. Jika sebelumnya sudah login, lakukan **Logout** lalu **Login kembali** di browser agar Supabase menerbitkan token JWT baru yang memuat claim role tersebut.

---

## ⚙️ Worker Ingestion & GitHub Actions

Worker ingestion berjalan via GitHub Actions di repo utama (`.github/workflows/ingest-kajian.yml`) dan script `tools/ingest_kajian_catalog.py`:

- **Jadwal Polling**: Berjalan setiap 15 menit (`*/15 * * * *`). Eksekusi dapat mengalami penundaan antrean tergantung beban server GitHub Actions.
- **Logika Cerdas**: Worker membaca konfigurasi `kajian_ingestion_settings` dari Supabase. Jika belum tiba waktu jadwal dan tidak ada permintaan manual ("Cek sekarang"), worker selesai lebih awal tanpa memakan kuota YouTube Data API.
- **Pencegahan Konkurensi**: Worker memeriksa `kajian_ingestion_runs` aktif agar dua proses tidak berjalan bersamaan.
- **Resolusi Channel**: Mendukung URL atau handle channel (seperti `@MuhammadNuzulDzikri`) dengan otomatis menyelesaikan ID channel dan *uploads playlist* melalui YouTube Data API.
- **Pencatatan Run**: Setiap eksekusi otomatis dicatat di `kajian_ingestion_runs` beserta status sukses/gagal, jumlah video terproses, dan error.

### Setup GitHub Actions Secrets (Di Repository Settings GitHub)
Atur ketiga secrets ini di **Settings** $\rightarrow$ **Secrets and variables** $\rightarrow$ **Actions**:
- `YOUTUBE_DATA_API_KEY`: API Key YouTube Data API v3 dari Google Cloud Console.
- `SUPABASE_URL`: URL project Supabase Anda (`https://your-project.supabase.co`).
- `SUPABASE_SECRET_KEY`: Service role secret key Supabase (hanya untuk worker CI backend, tidak boleh diberikan ke client).
