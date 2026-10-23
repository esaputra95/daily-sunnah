import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Kajian Admin | Daily Sunnah",
  description: "Panel kurasi dan editorial katalog video kajian Daily Sunnah",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="id">
      <body className="min-h-screen bg-slate-50 text-slate-900 antialiased font-sans">
        {children}
      </body>
    </html>
  );
}
