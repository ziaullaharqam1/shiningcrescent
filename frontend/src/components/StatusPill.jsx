export default function StatusPill({ value }) {
  const v = String(value || "").toLowerCase();
  let cls = "bg-sky text-grove-800";
  if (/(pending|hold|credit|draft|quarantine|arriv)/.test(v) && !/arrived/.test(v)) cls = "bg-[#D6E6F0] text-[#1E3A4C]";
  if (/(publish|available|paid|deliver|pass|confirm|ship|approv|arrived)/.test(v)) cls = "bg-grove-100 text-grove-800";
  if (/(reject|cancel|expired)/.test(v)) cls = "bg-red-100 text-red-700";
  return (
    <span className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${cls}`}>
      {String(value || "").replaceAll("_", " ")}
    </span>
  );
}
