export default function ErrorBanner({ children, className = "" }) {
  if (!children) return null;
  return (
    <p role="alert" className={`text-sm text-red-700 bg-red-50 border border-red-100 rounded-xl px-3 py-2 ${className}`.trim()}>
      {children}
    </p>
  );
}
