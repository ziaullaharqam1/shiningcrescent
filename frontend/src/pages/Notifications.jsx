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
      <h1 className="font-display text-3xl md:text-4xl text-grove-800">Notifications</h1>
      <p className="text-sm text-grove-600">{data.unread} unread</p>
      <div className="mt-4 space-y-2">
        {(data.items || []).map((n) => (
          <button key={n.id} onClick={() => read(n.id)} className={`card w-full text-left p-4 ${n.readFlag ? "opacity-60" : ""}`}>
            <p className="font-semibold">{n.title}</p>
            <p className="text-sm text-grove-700">{n.body}</p>
            <p className="text-xs mt-1">{n.createdAt} · {n.refType} {n.refId}</p>
          </button>
        ))}
      </div>
    </div>
  );
}
