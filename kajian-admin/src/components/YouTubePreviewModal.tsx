"use client";

import { useEffect } from "react";
import { X, ExternalLink, Play } from "lucide-react";

interface YouTubePreviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  videoId: string;
  title: string;
  channelName?: string | null;
  contentType?: "kajian" | "short";
}

export function YouTubePreviewModal({
  isOpen,
  onClose,
  videoId,
  title,
  channelName,
  contentType = "kajian",
}: YouTubePreviewModalProps) {
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    if (isOpen) {
      document.body.style.overflow = "hidden";
      window.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      document.body.style.overflow = "auto";
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen, onClose]);

  if (!isOpen) return null;

  const isShort = contentType === "short";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/80 backdrop-blur-xs animate-in fade-in duration-200">
      <div
        className="fixed inset-0"
        onClick={onClose}
        aria-hidden="true"
      />

      <div
        className={`relative z-10 w-full overflow-hidden rounded-2xl bg-slate-900 text-white shadow-2xl ring-1 ring-white/10 ${
          isShort ? "max-w-sm" : "max-w-4xl"
        }`}
      >
        {/* Header */}
        <div className="flex items-center justify-between border-b border-slate-800 px-4 py-3 bg-slate-950/80">
          <div className="flex-1 pr-4 min-w-0">
            <h3 className="text-sm font-semibold text-slate-100 truncate">
              {title}
            </h3>
            {channelName && (
              <p className="text-xs text-slate-400 truncate">
                Channel: {channelName}
              </p>
            )}
          </div>
          <div className="flex items-center gap-2">
            <a
              href={`https://www.youtube.com/watch?v=${videoId}`}
              target="_blank"
              rel="noopener noreferrer"
              className="inline-flex items-center gap-1 text-xs text-emerald-400 hover:text-emerald-300 font-medium px-2 py-1 rounded-md bg-emerald-950/50 hover:bg-emerald-900/50 transition-colors"
            >
              <span>Buka YouTube</span>
              <ExternalLink className="h-3 w-3" />
            </a>
            <button
              onClick={onClose}
              className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-800 hover:text-white transition-colors"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        {/* Video Player */}
        <div
          className={`relative bg-black flex items-center justify-center ${
            isShort ? "aspect-9/16 max-h-[75vh]" : "aspect-16/9"
          }`}
        >
          <iframe
            src={`https://www.youtube-nocookie.com/embed/${videoId}?autoplay=1&rel=0&modestbranding=1`}
            title={title}
            className="w-full h-full border-0"
            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
            allowFullScreen
          />
        </div>

        {/* Footer */}
        <div className="px-4 py-2.5 bg-slate-950/90 flex items-center justify-between text-xs text-slate-400">
          <span className="flex items-center gap-1.5">
            <Play className="h-3 w-3 text-emerald-400" />
            YouTube ID: <code className="text-slate-300 font-mono">{videoId}</code>
          </span>
          <button
            onClick={onClose}
            className="px-3 py-1 rounded-md bg-slate-800 hover:bg-slate-700 text-slate-200 transition-colors"
          >
            Tutup
          </button>
        </div>
      </div>
    </div>
  );
}
