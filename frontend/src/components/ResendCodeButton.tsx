import { useState, useEffect, useCallback } from 'react';
import { RefreshCw } from 'lucide-react';

/**
 * Resend OTP code button with a 30-second cooldown.
 *
 * Starts in cooldown state on mount (assuming an OTP was just sent).
 * Shows countdown text while disabled ("Resend code (Ns)").
 * Calls onResend() when clicked after cooldown expires, then resets cooldown.
 */
export default function ResendCodeButton({
  onResend,
  cooldownSeconds = 30
}: {
  onResend: () => void;
  cooldownSeconds?: number;
}) {
  const [remaining, setRemaining] = useState(cooldownSeconds);

  useEffect(() => {
    if (remaining <= 0) return;

    const id = setInterval(() => {
      setRemaining((prev) => {
        if (prev <= 1) {
          clearInterval(id);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(id);
  }, [remaining]);

  const handleClick = useCallback(() => {
    if (remaining > 0) return;
    onResend();
    setRemaining(cooldownSeconds);
  }, [remaining, onResend, cooldownSeconds]);

  const disabled = remaining > 0;

  return (
    <button
      type="button"
      className="auth-resend-btn"
      disabled={disabled}
      onClick={handleClick}
      aria-label={disabled ? `Resend code in ${remaining} seconds` : 'Resend code'}
    >
      <RefreshCw size={14} strokeWidth={2.2} />
      <span>{disabled ? `Resend code (${remaining}s)` : 'Resend code'}</span>
    </button>
  );
}
