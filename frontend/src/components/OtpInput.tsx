import {
  useState,
  useRef,
  useEffect,
  useCallback,
  type ClipboardEvent,
  type KeyboardEvent
} from 'react';

/**
 * Extract up to `maxLength` numeric characters from a string.
 * Non-numeric characters are silently discarded.
 */
export function extractNumericChars(str: string, maxLength = 6): string {
  const nums = str.replace(/[^0-9]/g, '');
  return nums.slice(0, maxLength);
}

/**
 * Format seconds as "M:SS" countdown display.
 * @param {number} seconds — integer in [0, Infinity)
 * @returns {string} formatted as "M:SS"
 */
export function formatTime(seconds: number): string {
  const clamped = Math.max(0, Math.floor(seconds));
  const m = Math.floor(clamped / 60);
  const s = clamped % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

/** Below this many seconds left, the expiry starts showing. Above it the
 *  number is noise: nobody needs telling a code is good for four more
 *  minutes, and it competed with the resend cooldown for attention. */
const EXPIRY_WARN_SECONDS = 60;

/**
 * OtpInput — A reusable OTP code entry component.
 *
 * Renders `length` individual single-digit input fields with:
 * - Auto-advance on digit entry
 * - Paste handling (extracts numeric chars, distributes across fields)
 * - Backspace navigation (empty field → clear previous, move focus back)
 * - Countdown timer in "M:SS" format
 * - Auto-submit when all digits filled
 * - Full keyboard accessibility (Tab/Shift+Tab, aria-labels, group role)
 *
 * Props:
 * @param {number} [length=6] — Number of digit fields
 * @param {number} [expiresInSeconds=300] — Countdown starting value
 * @param {(code: string) => void} onComplete — Called when all digits filled
 * @param {() => void} [onExpired] — Called when countdown reaches zero
 * @param {boolean} [disabled=false] — Locks all input fields
 *
 * Styling lives in auth.css as `.auth-otp-box`. This component had a second
 * 'underline' variant drawn with inline token variables; nothing used it,
 * and it was the last thing in the app reading that token layer.
 */
export default function OtpInput({
  length = 6,
  expiresInSeconds = 300,
  expiresAt,
  onComplete,
  onExpired,
  disabled = false,
}: {
  length?: number;
  expiresInSeconds?: number;
  /**
   * Absolute deadline, in ms since epoch. Prefer this over
   * `expiresInSeconds` wherever the component can be remounted — a rejected
   * code or a "wrong address?" bounce changes the `key`, and a relative
   * countdown restarts at five minutes for a code that is already half
   * expired. The caller owns the deadline; only a resend should move it.
   */
  expiresAt?: number;
  onComplete?: (code: string) => void;
  onExpired?: () => void;
  disabled?: boolean;
}) {
  // Anchored once. Given an `expiresAt` the caller owns it; otherwise the
  // deadline is fixed at mount so the countdown is still wall-clock accurate
  // rather than a decrementing counter that drifts when the tab sleeps.
  const deadline = useRef(expiresAt ?? Date.now() + expiresInSeconds * 1000);
  useEffect(() => {
    if (expiresAt !== undefined) deadline.current = expiresAt;
  }, [expiresAt]);
  const remainingNow = useCallback(
    () => Math.max(0, Math.round((deadline.current - Date.now()) / 1000)),
    []
  );

  const [digits, setDigits] = useState<string[]>(Array(length).fill(''));
  const [secondsLeft, setSecondsLeft] = useState(remainingNow);
  const [expired, setExpired] = useState(false);
  const inputsRef = useRef<Array<HTMLInputElement | null>>([]);

  // The first empty box takes focus on mount. Without this the screen renders
  // six boxes and leaves focus on the body, so entering a code — including
  // re-entering one after a rejection, which remounts this component — began
  // with aiming at a 44px target.
  //
  // Mount-only ([]), not [disabled]: a transient failure (a 5xx, or a request
  // that never reached the server) deliberately leaves the six digits in
  // place — ForgotPasswordPage's own comment explains why — but toggling
  // `disabled` true (submitting) then false (the failure) without a remount
  // used to re-run this effect and yank focus back to the first box, right
  // next to the six preserved digits, inviting a stray keystroke to overwrite
  // the one the user got right. A real remount (a rejected code changes
  // `key`) still gets a fresh effect run regardless of the dependency array.
  useEffect(() => {
    if (disabled) return;
    inputsRef.current[0]?.focus();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount-only, see comment above
  }, []);

  // Countdown timer
  useEffect(() => {
    if (disabled || expired) return;
    if (secondsLeft <= 0) return;

    const id = setInterval(() => {
      // Recomputed from the deadline rather than decremented, so a backgrounded
      // tab (where timers are throttled) does not drift slow.
      setSecondsLeft(() => remainingNow());
    }, 1000);

    return () => clearInterval(id);
  }, [disabled, expired, secondsLeft, remainingNow]);

  // Handle timer reaching zero
  useEffect(() => {
    if (secondsLeft === 0 && !expired) {
      setExpired(true);
      onExpired?.();
    }
  }, [secondsLeft, expired, onExpired]);

  // Trigger onComplete when all digits are filled
  const checkComplete = useCallback(
    (newDigits: string[]) => {
      if (newDigits.every((d) => d !== '')) {
        onComplete?.(newDigits.join(''));
      }
    },
    [onComplete]
  );

  const handleChange = (index: number, value: string) => {
    // Accept only single numeric character
    const char = value.slice(-1);
    if (!/^[0-9]$/.test(char)) return;

    const newDigits = [...digits];
    newDigits[index] = char;
    setDigits(newDigits);

    // Auto-advance to next field
    if (index < length - 1) {
      inputsRef.current[index + 1]?.focus();
    }

    checkComplete(newDigits);
  };

  const handleKeyDown = (index: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace') {
      if (digits[index] === '' && index > 0) {
        // Empty field: clear previous and move focus back
        e.preventDefault();
        const newDigits = [...digits];
        newDigits[index - 1] = '';
        setDigits(newDigits);
        inputsRef.current[index - 1]?.focus();
      } else {
        // Non-empty field: clear current (default browser behavior handles it,
        // but we also update state)
        const newDigits = [...digits];
        newDigits[index] = '';
        setDigits(newDigits);
      }
    }
    // Tab and Shift+Tab are handled natively by the browser
  };

  const handlePaste = (e: ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const pastedText = e.clipboardData.getData('text');
    const numericChars = extractNumericChars(pastedText, length);

    if (numericChars.length === 0) return;

    const newDigits = Array(length).fill('');
    for (let i = 0; i < numericChars.length; i++) {
      newDigits[i] = numericChars[i] ?? '';
    }
    setDigits(newDigits);

    // Focus the field after the last filled digit, or the last field
    const focusIndex = Math.min(numericChars.length, length - 1);
    inputsRef.current[focusIndex]?.focus();

    checkComplete(newDigits);
  };

  const isDisabled = disabled || expired;

  return (
    <div className="auth-otp-wrap">
      <div
        role="group"
        aria-label="Verification code"
        className="auth-otp-group"
      >
        {digits.map((digit, index) => (
          <input
            key={index}
            ref={(el) => { inputsRef.current[index] = el; }}
            type="text"
            inputMode="numeric"
            maxLength={1}
            value={digit}
            disabled={isDisabled}
            aria-label={`Digit ${index + 1} of ${length}`}
            onChange={(e) => handleChange(index, e.target.value)}
            onKeyDown={(e) => handleKeyDown(index, e)}
            onPaste={handlePaste}
            autoComplete="one-time-code"
            className="auth-otp-box"
          />
        ))}
      </div>

      {/* Two live counters ran side by side here — this one and the resend
          button's cooldown — and only one of them was ever actionable. The
          expiry is silent until it is close enough to matter, so at any
          moment the screen has at most one number ticking on it. */}
      <div
        className={`auth-otp-count${expired ? ' expired' : ''}`}
        aria-live="polite"
        aria-atomic="true"
      >
        {expired
          ? <span>Code expired</span>
          : secondsLeft <= EXPIRY_WARN_SECONDS
            ? <span>Expires in {formatTime(secondsLeft)}</span>
            : null}
      </div>
    </div>
  );
}
