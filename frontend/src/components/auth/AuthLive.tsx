import { useEffect, useState, type FormEvent, type ReactNode } from "react";
import { Link, useNavigate } from "@tanstack/react-router";
import { ArrowLeft, ArrowRight, Check, Eye, EyeOff, Loader2, MailCheck } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Brand } from "@/components/DeliveryApp";
import { Banner, errText } from "@/components/owner/OwnerLive";
import riderImage from "@/assets/delivery-rider.jpg";
import riderLoop from "@/assets/delivery-rider-loop.mp4.asset.json";
import { ApiError, setSession } from "@/lib/hl/apiClient";
import {
  acceptInvite,
  login,
  previewInvite,
  registerOwner,
  requestPasswordReset,
  resendOtp,
  resetPassword,
  verifyRegistrationOtp,
  verifyResetOtp,
} from "@/lib/hl/authApi";
import type { AuthResult, InvitePreview, OtpPurpose } from "@/lib/hl/types";

function Shell({
  eyebrow,
  title,
  intro,
  children,
  foot,
}: {
  eyebrow: string;
  title: string;
  intro: ReactNode;
  children: ReactNode;
  foot?: ReactNode;
}) {
  return (
    <div className="auth-layout">
      <aside className="auth-aside">
        <Brand dark />
        <div className="auth-aside-content">
          <div className="auth-artwork" aria-hidden="true">
            <video autoPlay loop muted playsInline>
              <source src="/delivery-rider-loop.webm" type="video/webm" />
              <source src={riderLoop.url} type="video/mp4" />
            </video>
          </div>
          <div className="auth-pitch">
            <span className="eyebrow">HYPERLOCAL DELIVERY</span>
            <h2>Every delivery, handled beautifully.</h2>
            <p>From the first pickup to the final doorstep, keep the whole day in view.</p>
          </div>
        </div>
        <div className="auth-aside-foot">A clearer way to move locally.</div>
      </aside>
      <main className="auth-main">
        <div className="auth-mobile-brand">
          <Brand />
        </div>
        <div className="auth-artwork auth-artwork-mobile" aria-hidden="true">
          <video autoPlay loop muted playsInline>
            <source src="/delivery-rider-loop.webm" type="video/webm" />
            <source src={riderLoop.url} type="video/mp4" />
          </video>
        </div>
        <div className="auth-form-area">
          <p className="eyebrow">{eyebrow}</p>
          <h1>{title}</h1>
          <p className="auth-intro">{intro}</p>
          {children}
          {foot}
        </div>
      </main>
    </div>
  );
}

function Password({
  label = "Password",
  value,
  onChange,
  auto = "new-password",
}: {
  label?: string;
  value: string;
  onChange: (v: string) => void;
  auto?: string;
}) {
  const [show, setShow] = useState(false);
  return (
    <label>
      {label}
      <span className="password-field">
        <input
          type={show ? "text" : "password"}
          autoComplete={auto}
          required
          minLength={8}
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
        <button
          type="button"
          className="password-toggle"
          onClick={() => setShow(!show)}
          aria-label={show ? "Hide password" : "Show password"}
        >
          {show ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </span>
    </label>
  );
}
const strong = (p: string) => p.length >= 8 && /[A-Za-z]/.test(p) && /\d/.test(p);
const pwHint = "At least 8 characters with a letter and a number.";

function otpText(e: unknown) {
  const b = (e instanceof ApiError ? e.body : null) as {
    reason?: string;
    attemptsRemaining?: number;
  } | null;
  if (b && typeof b === "object") {
    if (b.reason === "INVALID_OTP")
      return `That code isn’t right.${b.attemptsRemaining != null ? ` ${b.attemptsRemaining} ${b.attemptsRemaining === 1 ? "try" : "tries"} left.` : ""}`;
    if (b.reason === "OTP_EXPIRED" || b.reason === "EXPIRED")
      return "This code has expired. Tap “Resend code” for a new one.";
    if (b.reason === "TOO_MANY_ATTEMPTS" || b.attemptsRemaining === 0)
      return "Too many wrong tries. Tap “Resend code” for a new one.";
  }
  return errText(e);
}

function OtpStep({
  email,
  purpose,
  onVerify,
  onBack,
}: {
  email: string;
  purpose: OtpPurpose;
  onVerify: (code: string) => Promise<void>;
  onBack: () => void;
}) {
  const [code, setCode] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [cool, setCool] = useState(30);
  const [sent, setSent] = useState(false);
  useEffect(() => {
    if (cool <= 0) return;
    const t = setTimeout(() => setCool((c) => c - 1), 1000);
    return () => clearTimeout(t);
  }, [cool]);
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!/^\d{6}$/.test(code)) {
      setErr("Enter the 6-digit code.");
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      await onVerify(code);
    } catch (e2) {
      setErr(otpText(e2));
      setBusy(false);
    }
  }
  return (
    <form className="form-stack" onSubmit={submit}>
      <div className="otp-note">
        <MailCheck />{" "}
        <span>
          We sent a 6-digit code to <strong>{email}</strong>. It may take a minute — check spam too.
        </span>
      </div>
      <label>
        Verification code
        <input
          className="otp-input"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          value={code}
          onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
          placeholder="••••••"
          autoFocus
        />
      </label>
      {err && <Banner error={err} />}
      <Button variant="coral" type="submit" disabled={busy || code.length !== 6}>
        {busy && <Loader2 className="spin" />}Verify <ArrowRight />
      </Button>
      <div className="auth-row split">
        <button type="button" className="text-link" onClick={onBack}>
          <ArrowLeft size={14} /> Change email
        </button>
        <button
          type="button"
          className="text-link"
          disabled={cool > 0}
          onClick={async () => {
            try {
              await resendOtp(email, purpose);
              setSent(true);
              setCool(30);
            } catch (e2) {
              setErr(errText(e2));
            }
          }}
        >
          {cool > 0 ? `Resend in ${cool}s` : sent ? "Resend again" : "Resend code"}
        </button>
      </div>
    </form>
  );
}

function finish(navigate: ReturnType<typeof useNavigate>, r: AuthResult) {
  setSession({ token: r.token || r.accessToken, role: r.role, user: r.user });
  navigate({ to: r.role === "AGENT" ? "/agent/assignments" : "/owner/shipments" });
}

/* ---------- owner sign-up ---------- */
export function LiveSignup() {
  const navigate = useNavigate();
  const [f, setF] = useState({
    businessName: "",
    ownerName: "",
    email: "",
    phone: "",
    businessPhone: "",
    password: "",
  });
  const [step, setStep] = useState<"form" | "otp">("form");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) =>
    setF({ ...f, [k]: e.target.value });
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!strong(f.password)) {
      setErr(pwHint);
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      await registerOwner({ ...f, email: f.email.trim(), businessPhone: f.businessPhone });
      setStep("otp");
    } catch (e2) {
      setErr(errText(e2));
    } finally {
      setBusy(false);
    }
  }
  return (
    <Shell
      eyebrow={step === "form" ? "START FREE" : "STEP 2 OF 2"}
      title={step === "form" ? "Create your business" : "Check your email"}
      intro={
        step === "form"
          ? "Set up your delivery workspace in a minute. You’ll add riders next."
          : "Enter the code to finish creating your account."
      }
      foot={
        <p className="auth-switch">
          Already have an account?{" "}
          <Link to="/login" className="text-link">
            Sign in
          </Link>
        </p>
      }
    >
      {step === "form" ? (
        <form className="form-stack" onSubmit={submit}>
          <label>
            Business name
            <input
              required
              value={f.businessName}
              onChange={set("businessName")}
              placeholder="e.g. Green Leaf Grocers"
            />
          </label>
          <div className="form-grid">
            <label>
              Your name
              <input required autoComplete="name" value={f.ownerName} onChange={set("ownerName")} />
            </label>
            <label>
              Your phone
              <input
                required
                type="tel"
                autoComplete="tel"
                value={f.phone}
                onChange={set("phone")}
                placeholder="+91…"
              />
            </label>
          </div>
          <label>
            Email
            <input
              required
              type="email"
              autoComplete="email"
              value={f.email}
              onChange={set("email")}
              placeholder="you@business.com"
            />
          </label>
          <label>
            Business phone (optional)
            <input
              type="tel"
              value={f.businessPhone}
              onChange={set("businessPhone")}
              placeholder="Shown to customers on tracking"
            />
          </label>
          <Password value={f.password} onChange={(v) => setF({ ...f, password: v })} />
          <small className="field-hint">{pwHint}</small>
          {err && <Banner error={err} />}
          <Button variant="coral" type="submit" disabled={busy}>
            {busy && <Loader2 className="spin" />}Send verification code <ArrowRight />
          </Button>
        </form>
      ) : (
        <OtpStep
          email={f.email.trim()}
          purpose="REGISTRATION"
          onBack={() => setStep("form")}
          onVerify={async (code) =>
            finish(navigate, await verifyRegistrationOtp(f.email.trim(), code))
          }
        />
      )}
    </Shell>
  );
}

/* ---------- password reset ---------- */
export function LiveForgot() {
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [step, setStep] = useState<"email" | "otp" | "new" | "done">("email");
  const [token, setToken] = useState("");
  const [pw, setPw] = useState("");
  const [pw2, setPw2] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const titles = {
    email: [
      "RESET PASSWORD",
      "Forgot your password?",
      "Enter your account email and we’ll send a code.",
    ],
    otp: ["STEP 2 OF 3", "Check your email", "Enter the code we sent you."],
    new: ["STEP 3 OF 3", "Choose a new password", "Use something you haven’t used before."],
    done: ["ALL SET", "Password updated", "You can sign in with your new password now."],
  } as const;
  const [eb, t, intro] = titles[step];
  return (
    <Shell
      eyebrow={eb}
      title={t}
      intro={intro}
      foot={
        <p className="auth-switch">
          <Link to="/login" className="text-link">
            <ArrowLeft size={14} /> Back to sign in
          </Link>
        </p>
      }
    >
      {step === "email" && (
        <form
          className="form-stack"
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setErr(null);
            try {
              await requestPasswordReset(email.trim());
              setStep("otp");
            } catch (e2) {
              setErr(errText(e2));
            } finally {
              setBusy(false);
            }
          }}
        >
          <label>
            Email
            <input
              required
              type="email"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@business.com"
            />
          </label>
          {err && <Banner error={err} />}
          <Button variant="coral" type="submit" disabled={busy}>
            {busy && <Loader2 className="spin" />}Send code <ArrowRight />
          </Button>
        </form>
      )}
      {step === "otp" && (
        <OtpStep
          email={email.trim()}
          purpose="PASSWORD_RESET"
          onBack={() => setStep("email")}
          onVerify={async (code) => {
            const r = await verifyResetOtp(email.trim(), code);
            setToken(r.resetToken);
            setStep("new");
          }}
        />
      )}
      {step === "new" && (
        <form
          className="form-stack"
          onSubmit={async (e) => {
            e.preventDefault();
            if (!strong(pw)) {
              setErr(pwHint);
              return;
            }
            if (pw !== pw2) {
              setErr("Passwords don’t match.");
              return;
            }
            setBusy(true);
            setErr(null);
            try {
              await resetPassword(token, pw);
              try {
                finish(navigate, await login(email.trim(), pw));
              } catch {
                setStep("done");
              }
            } catch (e2) {
              setErr(
                e2 instanceof ApiError && e2.status === 400
                  ? "This reset has expired. Start again."
                  : errText(e2),
              );
              setBusy(false);
            }
          }}
        >
          <Password label="New password" value={pw} onChange={setPw} />
          <Password label="Confirm password" value={pw2} onChange={setPw2} />
          <small className="field-hint">{pwHint}</small>
          {err && <Banner error={err} />}
          <Button variant="coral" type="submit" disabled={busy}>
            {busy && <Loader2 className="spin" />}Update password
          </Button>
        </form>
      )}
      {step === "done" && (
        <Button variant="coral" asChild>
          <Link to="/login">
            <Check /> Go to sign in
          </Link>
        </Button>
      )}
    </Shell>
  );
}

/* ---------- rider invite setup ---------- */
export function LiveSetup() {
  const navigate = useNavigate();
  const [token, setToken] = useState<string | null>(null);
  const [invite, setInvite] = useState<InvitePreview | null>(null);
  const [state, setState] = useState<"loading" | "ok" | "dead" | "error" | "missing">("loading");
  const [pw, setPw] = useState("");
  const [pw2, setPw2] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  function check(t: string) {
    setState("loading");
    previewInvite(t)
      .then((p) => {
        setInvite(p);
        setState("ok");
      })
      .catch((e) =>
        setState(e instanceof ApiError && e.status >= 400 && e.status < 500 ? "dead" : "error"),
      );
  }
  useEffect(() => {
    const t = new URLSearchParams(window.location.search).get("token");
    setToken(t);
    if (t) check(t);
    else setState("missing");
  }, []);
  const title =
    state === "ok"
      ? `Welcome, ${invite?.fullName.split(" ")[0] ?? ""}`
      : state === "loading"
        ? "Checking your link…"
        : "This link can’t be used";
  return (
    <Shell
      eyebrow="RIDER SETUP"
      title={title}
      intro={
        state === "ok" ? (
          <>
            Choose a password to join <strong>{invite?.businessName ?? "your team"}</strong> as a
            rider.
          </>
        ) : state === "loading" ? (
          "One moment."
        ) : state === "error" ? (
          "We couldn’t reach the service. Check your connection and retry."
        ) : (
          "It may have expired or already been used. Ask your business owner to send a new setup link."
        )
      }
      foot={
        <p className="auth-switch">
          Already set up?{" "}
          <Link to="/login" className="text-link">
            Sign in
          </Link>
        </p>
      }
    >
      {state === "loading" && (
        <div className="live-loading">
          <Loader2 className="spin" />
        </div>
      )}
      {state === "error" && token && (
        <Button variant="coral" onClick={() => check(token)}>
          Try again
        </Button>
      )}
      {state === "ok" && invite && token && (
        <form
          className="form-stack"
          onSubmit={async (e) => {
            e.preventDefault();
            if (!strong(pw)) {
              setErr(pwHint);
              return;
            }
            if (pw !== pw2) {
              setErr("Passwords don’t match.");
              return;
            }
            setBusy(true);
            setErr(null);
            try {
              await acceptInvite(token, pw);
              finish(navigate, await login(invite.email, pw));
            } catch (e2) {
              setErr(errText(e2));
              setBusy(false);
            }
          }}
        >
          <label>
            Email
            <input value={invite.email} readOnly />
          </label>
          <Password value={pw} onChange={setPw} />
          <Password label="Confirm password" value={pw2} onChange={setPw2} />
          <small className="field-hint">{pwHint}</small>
          {err && <Banner error={err} />}
          <Button variant="coral" type="submit" disabled={busy}>
            {busy && <Loader2 className="spin" />}Activate account <ArrowRight />
          </Button>
        </form>
      )}
    </Shell>
  );
}
