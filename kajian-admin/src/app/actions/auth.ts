"use server";

import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";

export interface AuthActionResult {
  error?: string;
  success?: boolean;
}

export async function loginAction(
  prevState: AuthActionResult | null,
  formData: FormData
): Promise<AuthActionResult> {
  const email = formData.get("email") as string;
  const password = formData.get("password") as string;

  if (!email || !password) {
    return { error: "Email dan kata sandi wajib diisi." };
  }

  const supabase = await createClient();

  const { data, error } = await supabase.auth.signInWithPassword({
    email,
    password,
  });

  if (error) {
    return { error: `Gagal login: ${error.message}` };
  }

  // Verifikasi claim peran app_metadata.role
  const user = data.user;
  const role = user?.app_metadata?.role;

  if (role !== "kajian_admin") {
    // Segera sign out jika akun tidak memiliki claim kajian_admin
    await supabase.auth.signOut();
    return {
      error:
        "Akses ditolak: Akun ini tidak memiliki hak admin (claim role: kajian_admin). Hubungi pengelola Supabase.",
    };
  }

  redirect("/catalog");
}

export async function logoutAction() {
  const supabase = await createClient();
  await supabase.auth.signOut();
  redirect("/login");
}
