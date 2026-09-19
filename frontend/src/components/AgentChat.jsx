import { useEffect, useRef, useState } from "react";
import { Bot, Mic, MicOff, Send, Volume2, VolumeX, X } from "lucide-react";
import api from "../api/client";
import { errMsg } from "../lib/errors";
import { useAuth } from "../context/AuthContext";

function recognitionCtor() {
  if (typeof window === "undefined") return null;
  return window.SpeechRecognition || window.webkitSpeechRecognition || null;
}

function pickRecorderMime() {
  const types = ["audio/webm;codecs=opus", "audio/webm", "audio/mp4", "audio/ogg"];
  if (typeof MediaRecorder === "undefined") return "";
  return types.find((t) => MediaRecorder.isTypeSupported(t)) || "";
}

export default function AgentChat() {
  const { user, can } = useAuth();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [listening, setListening] = useState(false);
  const [error, setError] = useState("");
  const [hint, setHint] = useState("");
  const [voiceOn, setVoiceOn] = useState(() => localStorage.getItem("rc_agent_speak") === "1");
  const [messages, setMessages] = useState([
    { role: "assistant", content: "I can create, edit, search or delete products, handle purchase orders, approve or quarantine, generate reports, and add to cart. Tap the mic to speak. The agent stays silent unless you turn the speaker on." },
  ]);
  const recRef = useRef(null);
  const streamRef = useRef(null);
  const recorderRef = useRef(null);
  const chunksRef = useRef([]);
  const listeningRef = useRef(false);
  const finalTextRef = useRef("");
  const busyRef = useRef(false);
  const voiceOnRef = useRef(voiceOn);
  const bottom = useRef(null);

  useEffect(() => {
    busyRef.current = busy;
  }, [busy]);

  useEffect(() => {
    voiceOnRef.current = voiceOn;
  }, [voiceOn]);

  useEffect(() => {
    bottom.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, open]);

  useEffect(() => () => stopCapture(false), []);

  if (!user || (!can("AGENT:VIEW") && !can("AGENT:CREATE") && !user.console)) return null;

  function speak(text) {
    if (!voiceOnRef.current || !text || typeof window === "undefined" || !window.speechSynthesis) return;
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.rate = 1;
    window.speechSynthesis.speak(u);
  }

  function toggleSpeaker() {
    setVoiceOn((on) => {
      const next = !on;
      localStorage.setItem("rc_agent_speak", next ? "1" : "0");
      if (!next && window.speechSynthesis) window.speechSynthesis.cancel();
      return next;
    });
  }

  async function send(text) {
    const message = (text ?? input).trim();
    if (!message || busyRef.current) return;
    setInput("");
    finalTextRef.current = "";
    setError("");
    setHint("");
    setMessages((m) => [...m, { role: "user", content: message }]);
    setBusy(true);
    busyRef.current = true;
    try {
      const history = messages.slice(-8).map((x) => ({ role: x.role, content: x.content }));
      const { data } = await api.post("/agent/chat", { message, history });
      const reply = data.message || "Done.";
      setMessages((m) => [...m, { role: "assistant", content: reply, tools: data.tools }]);
      speak(reply);
    } catch (e) {
      const msg = errMsg(e);
      setError(msg);
      setMessages((m) => [...m, { role: "assistant", content: msg }]);
    } finally {
      setBusy(false);
      busyRef.current = false;
    }
  }

  function stopCapture(keepListeningFlag) {
    listeningRef.current = !!keepListeningFlag;
    try {
      recRef.current?.stop();
    } catch {
      /* already stopped */
    }
    recRef.current = null;
    const rec = recorderRef.current;
    recorderRef.current = null;
    if (rec && rec.state !== "inactive") {
      try {
        rec.stop();
      } catch {
        /* ignore */
      }
    }
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (!keepListeningFlag) setListening(false);
  }

  async function transcribeBlob(blob) {
    if (!blob || blob.size < 800) return "";
    const mime = blob.type || "audio/webm";
    const ext = mime.includes("mp4") ? "m4a" : mime.includes("ogg") ? "ogg" : "webm";
    const fd = new FormData();
    fd.append("file", blob, `speech.${ext}`);
    try {
      const { data } = await api.post("/agent/transcribe", fd);
      return (data?.text || "").trim();
    } catch (e) {
      const msg = errMsg(e);
      if (/chrome or edge|live voice|microphone/i.test(msg)) return "";
      throw e;
    }
  }

  async function finishVoice(recorded) {
    const spoken = finalTextRef.current.trim();
    let text = spoken;
    if (!text && recorded) {
      setHint("Converting speech to text…");
      try {
        text = await transcribeBlob(recorded);
      } catch (e) {
        setError(errMsg(e));
        setHint("");
        return;
      }
    }
    setHint("");
    if (!text) {
      setError("I did not catch any words. Tap the mic, wait for Listening, speak clearly, then tap the mic again.");
      return;
    }
    setInput(text);
    await send(text);
  }

  async function toggleMic() {
    if (listeningRef.current) {
      listeningRef.current = false;
      setListening(false);
      setHint("Finishing…");
      const rec = recorderRef.current;
      if (rec && rec.state !== "inactive") {
        rec.onstop = async () => {
          const blob = new Blob(chunksRef.current, { type: rec.mimeType || "audio/webm" });
          chunksRef.current = [];
          stopCapture(false);
          await finishVoice(blob);
        };
        try {
          rec.stop();
        } catch {
          stopCapture(false);
          await finishVoice(null);
        }
        try {
          recRef.current?.stop();
        } catch {
          /* ignore */
        }
        return;
      }
      stopCapture(false);
      await finishVoice(null);
      return;
    }

    if (!window.isSecureContext) {
      setError("Voice needs https. Open the site with the lock icon, then try the mic again.");
      return;
    }
    if (!navigator.mediaDevices?.getUserMedia) {
      setError("This browser cannot use the microphone. Use Chrome or Edge.");
      return;
    }

    setError("");
    setHint("Allow the microphone, then speak…");
    finalTextRef.current = "";
    chunksRef.current = [];

    let stream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true },
      });
    } catch {
      setError("Microphone is blocked. In the address bar, allow Microphone for this site and try again.");
      setHint("");
      return;
    }
    streamRef.current = stream;

    const mime = pickRecorderMime();
    if (typeof MediaRecorder !== "undefined") {
      try {
        const recorder = mime ? new MediaRecorder(stream, { mimeType: mime }) : new MediaRecorder(stream);
        recorderRef.current = recorder;
        recorder.ondataavailable = (ev) => {
          if (ev.data && ev.data.size > 0) chunksRef.current.push(ev.data);
        };
        recorder.start(250);
      } catch {
        recorderRef.current = null;
      }
    }

    const Ctor = recognitionCtor();
    if (Ctor) {
      const rec = new Ctor();
      rec.lang = (navigator.language || "en-US").startsWith("ar") ? "ar-AE" : "en-US";
      rec.continuous = true;
      rec.interimResults = true;
      rec.maxAlternatives = 1;
      rec.onresult = (ev) => {
        let interim = "";
        let finalText = finalTextRef.current;
        for (let i = ev.resultIndex; i < ev.results.length; i += 1) {
          const piece = ev.results[i][0]?.transcript || "";
          if (ev.results[i].isFinal) finalText += `${piece} `;
          else interim += piece;
        }
        finalTextRef.current = finalText;
        const shown = `${finalText}${interim}`.trim();
        if (shown) {
          setInput(shown);
          setHint("Listening… tap the mic again when you finish.");
        }
      };
      rec.onerror = (ev) => {
        const code = ev.error;
        if (code === "no-speech" || code === "aborted") return;
        if (code === "not-allowed") {
          setError("Microphone is blocked. Allow it for this site in the browser, then tap the mic.");
        } else if (code === "network") {
          setHint("Live captions paused. Keep speaking, then tap the mic again to convert the recording.");
        }
      };
      rec.onend = () => {
        if (!listeningRef.current) return;
        try {
          rec.start();
        } catch {
          /* Chrome throws if start is too soon */
        }
      };
      recRef.current = rec;
      try {
        rec.start();
      } catch (e) {
        recRef.current = null;
        setHint("Recording… tap the mic again when you finish speaking.");
      }
    } else {
      setHint("Recording… tap the mic again when you finish speaking.");
    }

    listeningRef.current = true;
    setListening(true);
    setHint((h) => h || "Listening… speak now, then tap the mic again.");
  }

  return (
    <>
      <button
        type="button"
        className="fixed bottom-5 right-5 z-40 h-14 w-14 rounded-full bg-grove-600 text-white shadow-lg grid place-items-center hover:bg-grove-700"
        aria-label="Open agent chat"
        onClick={() => setOpen(true)}
      >
        <Bot size={22} />
      </button>
      {open && (
        <div className="fixed bottom-5 right-5 z-50 w-[min(100%-1.5rem,380px)] h-[min(72vh,540px)] card flex flex-col overflow-hidden shadow-xl">
          <div className="flex items-center justify-between px-3 py-2 border-b border-grove-100 bg-grove-50">
            <p className="font-semibold text-grove-800 text-sm">Trading agent</p>
            <div className="flex items-center gap-1">
              <button
                type="button"
                className={`p-2 rounded-full hover:bg-white ${voiceOn ? "text-grove-700" : "text-grove-400"}`}
                onClick={toggleSpeaker}
                aria-label={voiceOn ? "Mute agent voice" : "Unmute agent voice"}
                title={voiceOn ? "Speaker on — tap to mute" : "Silent — tap to let the agent speak"}
              >
                {voiceOn ? <Volume2 size={16} /> : <VolumeX size={16} />}
              </button>
              <button type="button" className="p-2 rounded-full hover:bg-white" onClick={() => { stopCapture(false); setOpen(false); }} aria-label="Close">
                <X size={16} />
              </button>
            </div>
          </div>
          <div className="flex-1 overflow-y-auto p-3 space-y-2 text-sm">
            {messages.map((m, i) => (
              <div key={i} className={`rounded-2xl px-3 py-2 ${m.role === "user" ? "bg-grove-600 text-white ml-8" : "bg-grove-50 text-grove-800 mr-6"}`}>
                {m.content}
              </div>
            ))}
            <div ref={bottom} />
          </div>
          {hint && <p className="px-3 pb-1 text-xs text-grove-700">{hint}</p>}
          {error && <p className="px-3 pb-1 text-xs text-red-700">{error}</p>}
          <form
            className="p-2 border-t border-grove-100 flex gap-1"
            onSubmit={(e) => {
              e.preventDefault();
              send();
            }}
          >
            <button
              type="button"
              className={`btn-ghost px-3 ${listening ? "bg-grove-600 text-white border-grove-600" : ""}`}
              onClick={toggleMic}
              disabled={busy}
              aria-label={listening ? "Stop and convert speech" : "Start voice"}
              title={listening ? "Tap to convert speech to text" : "Tap to speak"}
            >
              {listening ? <MicOff size={16} /> : <Mic size={16} />}
            </button>
            <input
              className="input"
              value={input}
              placeholder={listening ? "Speak now…" : busy ? "Working…" : "Ask the agent…"}
              onChange={(e) => setInput(e.target.value)}
              disabled={busy}
            />
            <button className="btn-primary px-3" disabled={busy || listening} aria-label="Send">
              <Send size={16} />
            </button>
          </form>
        </div>
      )}
    </>
  );
}
