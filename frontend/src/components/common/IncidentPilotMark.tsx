export function IncidentPilotMark({ className = "" }: { className?: string }) {
  return (
    <svg viewBox="0 0 40 40" fill="none" aria-hidden="true" className={className}>
      <path d="M20 3 35 12v16L20 37 5 28V12Z" stroke="currentColor" strokeWidth="2" />
      <path
        d="m11 23 6-11 6 16 6-11"
        stroke="currentColor"
        strokeWidth="3"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      <circle cx="29" cy="17" r="2.5" fill="currentColor" />
    </svg>
  );
}
