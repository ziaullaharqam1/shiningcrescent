export default function StatusPill({ value }) {
  const v = String(value || "").toLowerCase();
  let cls = "pill-muted";
  if (/(pending|hold|credit|draft|quarantine|arriv)/.test(v) && !/arrived/.test(v)) cls = "bg-steel-100 text-steel-900";
  if (/(publish|available|paid|deliver|pass|confirm|ship|approv|arrived)/.test(v)) cls = "pill-ok";
  if (/(fefo|expir|near)/.test(v)) cls = "pill-warn";
  if (/(reject|cancel|expired|fail)/.test(v)) cls = "pill-danger";
  return (
    <span className={`pill ${cls}`}>
      {String(value || "").replaceAll("_", " ")}
    </span>
  );
}
