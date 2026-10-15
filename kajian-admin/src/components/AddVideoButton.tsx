"use client";

import { useState } from "react";
import { Plus, CheckCircle2 } from "lucide-react";
import { AddVideoModal } from "./AddVideoModal";

export function AddVideoButton() {
  const [isOpen, setIsOpen] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  const handleSuccess = (message: string) => {
    setToastMessage(message);
    setTimeout(() => {
      setToastMessage((curr) => (curr === message ? null : curr));
    }, 4500);
  };

  return (
    <>
      <button
        type="button"
        onClick={() => setIsOpen(true)}
        className="inline-flex items-center gap-2 rounded-xl bg-emerald-700 px-4 py-2.5 text-xs font-semibold text-white shadow-xs hover:bg-emerald-800 active:scale-[0.98] transition-all"
        title="Tambah dan simpan video YouTube langsung ke database untuk pengujian atau kurasi cepat"
      >
        <Plus className="h-4 w-4" />
        <span>Tambah Video Manual</span>
      </button>

      {/* Floating success toast */}
      {toastMessage && (
        <div className="fixed bottom-6 right-6 z-50 animate-in slide-in-from-bottom-5 duration-200">
          <div className="flex items-center gap-2.5 rounded-2xl bg-emerald-950/95 text-white p-4 shadow-2xl ring-1 ring-emerald-700/50 text-xs font-medium max-w-md">
            <CheckCircle2 className="h-4 w-4 text-emerald-400 shrink-0" />
            <span>{toastMessage}</span>
          </div>
        </div>
      )}

      <AddVideoModal
        isOpen={isOpen}
        onClose={() => setIsOpen(false)}
        onSuccess={handleSuccess}
      />
    </>
  );
}
