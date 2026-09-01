import { useMemo } from 'react';

/**
 * Password strength, shown as four segments and a word.
 *
 * Scored locally and cheaply. This is NOT a security control — the server's
 * minimum length is the control, and this cannot see the breach corpora that
 * would make a real judgement. It exists to answer the one question the hint
 * text below it cannot: "is what I just typed any good?" A field that only
 * ever says "at least 8 characters" rewards `password` and `12345678`
 * identically, and a shop owner picking a password for a console that holds
 * their customers' addresses and phone numbers deserves a nudge.
 *
 * Deliberately NOT a blocker. Nothing here prevents submission — the server
 * decides what is acceptable, and a client-side gate that disagrees with it
 * produces a form that refuses a password the API would have taken.
 *
 * Four levels, because five is a distinction nobody can act on and three
 * cannot separate "long but obvious" from "genuinely fine".
 */

/** The handful that show up in real Indian small-business signups. Not a
 *  dictionary — a dictionary belongs on the server. These are the ones worth
 *  catching before the user commits to them. */
const OBVIOUS = [
  'password', 'passw0rd', '12345678', '123456789', 'qwerty', 'qwertyui',
  'iloveyou', 'welcome', 'admin123', 'letmein', 'abc12345', 'india123'
];

export type Strength = 0 | 1 | 2 | 3;

export function scorePassword(pw: string, min: number): Strength {
  const v = pw.trim();
  if (v.length === 0 || v.length < min) return 0;

  const lower = v.toLowerCase();
  // An obvious password is weak at any length. Caught before the variety
  // score, or `Password123!` scores as strong on its character classes.
  if (OBVIOUS.some((o) => lower.includes(o))) return 1;

  // A single repeated character or a straight run passes a naive length test.
  if (/^(.)\1+$/.test(v)) return 1;
  if (/^(?:0123456789|abcdefghijklmnopqrstuvwxyz){1,}/.test(lower.slice(0, 10)) && v.length < 16) return 1;

  const classes = [/[a-z]/, /[A-Z]/, /\d/, /[^A-Za-z0-9]/].filter((re) => re.test(v)).length;

  // Length carries more weight than variety: a long passphrase of lowercase
  // words beats a short one with a symbol bolted on, and the research has
  // been consistent on that for years.
  if (v.length >= 16 || (v.length >= 12 && classes >= 3)) return 3;
  if (v.length >= 12 || classes >= 3) return 2;
  return 1;
}

const WORD: Record<Strength, string> = {
  0: 'Too short',
  1: 'Weak',
  2: 'Good',
  3: 'Strong'
};

/**
 * `min` is passed in rather than imported so the component cannot drift from
 * whichever page's minimum it is sitting under.
 */
export default function PasswordStrength({
  value, min, id
}: {
  value: string;
  min: number;
  /** Id to reference from the input's aria-describedby. */
  id?: string;
}) {
  const score = useMemo(() => scorePassword(value, min), [value, min]);

  // Nothing typed yet: the hint line below the field already states the
  // minimum, so a row of empty segments saying "Too short" would be scolding
  // someone who has not started.
  if (!value) return null;

  return (
    <div className="auth-meter" id={id}>
      <span className="auth-meter-bars" aria-hidden="true">
        {[0, 1, 2, 3].map((i) => (
          <span key={i} className={i <= score ? `on s${score}` : undefined} />
        ))}
      </span>
      {/* Polite, and only the word — announcing four segment states on every
          keystroke would make the field unusable with a screen reader. */}
      <span className={`auth-meter-w s${score}`} aria-live="polite">{WORD[score]}</span>
    </div>
  );
}
