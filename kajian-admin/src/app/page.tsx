import { redirect } from "next/navigation";
import { verifyAdminSession } from "@/lib/supabase/server";

export default async function HomePage() {
  const adminCheck = await verifyAdminSession();

  if (adminCheck.isAdmin) {
    redirect("/catalog");
  } else {
    redirect("/login");
  }
}
