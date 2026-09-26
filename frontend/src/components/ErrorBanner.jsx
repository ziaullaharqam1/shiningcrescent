export default function ErrorBanner({ children, className = "" }) {
  if (!children) return null;
  return (
    <p role="alert" className={`text-sm text-danger-ink bg-danger-soft border border-red-100 rounded-lg px-3 py-2 font-sans ${className}`.trim()}>
      {children}
    </p>
  );
}
