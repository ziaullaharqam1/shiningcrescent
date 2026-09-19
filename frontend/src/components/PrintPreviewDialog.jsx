import { useEffect, useRef, useState } from "react";

export default function PrintPreviewDialog() {
  const [preview, setPreview] = useState(null);
  const frameRef = useRef(null);

  useEffect(() => {
    function onPreview(e) {
      setPreview(e.detail);
    }
    window.addEventListener("rc-print-preview", onPreview);
    return () => window.removeEventListener("rc-print-preview", onPreview);
  }, []);

  useEffect(() => {
    if (!preview) return undefined;
    function onKey(e) {
      if (e.key === "Escape") setPreview(null);
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [preview]);

  if (!preview) return null;

  function printNow() {
    const win = frameRef.current?.contentWindow;
    if (!win) return;
    win.focus();
    win.print();
  }

  return (
    <div className="fixed inset-0 z-[80] bg-grove-900/50 flex items-end sm:items-center justify-center p-0 sm:p-6" role="dialog" aria-modal="true" aria-label="Print preview">
      <div className="bg-white w-full sm:max-w-4xl sm:rounded-2xl shadow-xl flex flex-col max-h-[100dvh] sm:max-h-[92vh] overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-2 px-4 py-3 border-b border-grove-100 bg-grove-50">
          <div>
            <p className="font-semibold text-grove-800">{preview.title}</p>
            <p className="text-xs text-grove-600">Print preview — paper or Save as PDF in the print dialog</p>
          </div>
          <div className="flex gap-2">
            <button type="button" className="btn-primary text-sm" onClick={printNow}>Print / Save as PDF</button>
            <button type="button" className="btn-ghost text-sm" onClick={() => setPreview(null)}>Close</button>
          </div>
        </div>
        <iframe
          ref={frameRef}
          title={preview.title}
          srcDoc={preview.html}
          className="w-full flex-1 min-h-[70vh] bg-white border-0"
        />
      </div>
    </div>
  );
}
