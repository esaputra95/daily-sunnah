import { type ClassValue, clsx } from "clsx";
import { twMerge } from "tailwind-merge";

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function formatDuration(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || isNaN(seconds)) {
    return "-";
  }

  const hrs = Math.floor(seconds / 3600);
  const mins = Math.floor((seconds % 3600) / 60);
  const secs = seconds % 60;

  if (hrs > 0) {
    return `${hrs}:${mins.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
  }
  return `${mins}:${secs.toString().padStart(2, "0")}`;
}

export function formatDate(dateString: string | null | undefined): string {
  if (!dateString) return "-";
  try {
    const date = new Date(dateString);
    return new Intl.DateTimeFormat("id-ID", {
      dateStyle: "medium",
      timeStyle: "short",
    }).format(date);
  } catch {
    return dateString;
  }
}

const YOUTUBE_ID_REGEX = /^[A-Za-z0-9_-]{11}$/;

export function extractYouTubeVideoId(input: string): string | null {
  const trimmed = input.trim();
  if (YOUTUBE_ID_REGEX.test(trimmed)) {
    return trimmed;
  }

  try {
    const url = new URL(trimmed.startsWith("http") ? trimmed : `https://${trimmed}`);
    const host = url.hostname.toLowerCase();

    // youtu.be/<id>
    if (host === "youtu.be") {
      const id = url.pathname.slice(1).split("/")[0].split("?")[0];
      return YOUTUBE_ID_REGEX.test(id) ? id : null;
    }

    // youtube.com / m.youtube.com
    if (host.includes("youtube.com")) {
      const v = url.searchParams.get("v");
      if (v && YOUTUBE_ID_REGEX.test(v)) return v;

      if (url.pathname.includes("/shorts/")) {
        const parts = url.pathname.split("/shorts/")[1]?.split("/")[0]?.split("?")[0];
        if (parts && YOUTUBE_ID_REGEX.test(parts)) return parts;
      }

      if (url.pathname.includes("/embed/")) {
        const parts = url.pathname.split("/embed/")[1]?.split("/")[0]?.split("?")[0];
        if (parts && YOUTUBE_ID_REGEX.test(parts)) return parts;
      }
    }
  } catch {
    // Abaikan jika bukan URL valid
  }

  const match = trimmed.match(/(?:youtu\.be\/|youtube\.com\/(?:embed\/|v\/|watch\?v=|shorts\/))([A-Za-z0-9_-]{11})/);
  if (match && match[1]) {
    return match[1];
  }

  return null;
}

