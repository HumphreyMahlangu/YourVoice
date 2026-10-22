interface PasswordVisibilityButtonProps {
  visible: boolean
  onToggle: () => void
  inputId: string
  label: string
}

function PasswordVisibilityButton({
  visible,
  onToggle,
  inputId,
  label,
}: PasswordVisibilityButtonProps) {
  return (
    <button
      className="password-visibility"
      type="button"
      aria-label={`${visible ? 'Hide' : 'Show'} ${label}`}
      aria-controls={inputId}
      aria-pressed={visible}
      onClick={onToggle}
    >
      <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M2.25 12s3.5-6 9.75-6 9.75 6 9.75 6-3.5 6-9.75 6-9.75-6-9.75-6Z" />
        <circle cx="12" cy="12" r="2.75" />
        {visible && <path d="M3 21 21 3" />}
      </svg>
    </button>
  )
}

export default PasswordVisibilityButton
