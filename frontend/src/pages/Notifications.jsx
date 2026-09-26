import { useEffect, useState } from "react";
import api from "../api/client";

export default function Notifications({ consoleView }) {
  const [data, setData] = useState({ items: [], unread: 0 });
  async function load() {
    const { data: d } = await api.get("/console/notifications");
    setData(d);
  }
  useEffect(() => { load(); }, []);
  async function read(id) {
    await api.post(`/console/notifications/${id}/read`);
    load();
  }
  return (
    <div className={consoleView ? "" : "max-w-3xl mx-auto px-4 py-10"}>
      <h1 className="page-title">Notifications</h1>
      <p className="page-hint">{data.unread} unread</p>
      <div className="mt-4 space-y-2">
        {(data.items || []).map((n) => (
          <button key={n.id} onClick={() => read(n.id)} className={`card w-full text-left p-4 hover:border-primary/20 transition-colors ${n.readFlag ? "opacity-60" : ""}`}>
            <p className="font-semibold text-primary">{n.title}</p>
            <p className="text-sm text-ink-muted">{n.body}</p>
            <p className="text-xs mt-1 text-ink-faint">{n.createdAt} · {n.refType} {n.refId}</p>
          </button>
        ))}
      </div>
    </div>
  );
}
