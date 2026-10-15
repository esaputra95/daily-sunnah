import { createServerClient, type CookieOptions } from "@supabase/ssr";
import { cookies } from "next/headers";

export async function createClient() {
  const cookieStore = await cookies();

  const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const supabaseKey = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;

  if (!supabaseUrl || !supabaseKey) {
    throw new Error(
      "Missing NEXT_PUBLIC_SUPABASE_URL or NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY in environment variables."
    );
  }

  return createServerClient(supabaseUrl, supabaseKey, {
    cookies: {
      getAll() {
        return cookieStore.getAll();
      },
      setAll(cookiesToSet: { name: string; value: string; options: CookieOptions }[]) {
        try {
          cookiesToSet.forEach(({ name, value, options }) =>
            cookieStore.set(name, value, options)
          );
        } catch {
          // The `setAll` method was called from a Server Component.
          // Middleware handles token refreshment.
        }
      },
    },
  });
}

/**
 * Memverifikasi apakah session user yang sedang aktif memiliki claim app_metadata.role = "kajian_admin".
 * Fungsi ini digunakan di setiap Server Action dan route proteksi untuk memastikan otorisasi.
 */
export async function verifyAdminSession() {
  const supabase = await createClient();
  const {
    data: { user },
    error,
  } = await supabase.auth.getUser();

  if (error || !user) {
    return {
      isAdmin: false,
      user: null,
      error: "Sesi tidak valid atau belum login. Silakan login terlebih dahulu.",
    };
  }

  const role = user.app_metadata?.role;
  if (role !== "kajian_admin") {
    return {
      isAdmin: false,
      user,
      error:
        "Akses ditolak. Akun Anda tidak memiliki claim 'app_metadata.role = kajian_admin'. Hubungi pemilik project Supabase.",
    };
  }

  return {
    isAdmin: true,
    user,
    error: null,
  };
}
