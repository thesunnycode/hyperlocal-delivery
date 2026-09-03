import { useEffect, useState } from 'react';
import { X, Copy, Check, MessageCircle, Mail, Smartphone } from 'lucide-react';
import { useModalBehaviour } from '../lib/useModalBehaviour';
import { inviteAgent } from '../api/agentsApi';
import { formatDateTime } from '../utils/format';
import type { AgentInvite } from '../types/api';

/**
 * Getting a new agent into the app.
 *
 * The gap this fills: creating an agent mints an account with a random
 * password that is stored as a hash and sent nowhere. The agent has no way to
 * learn the account exists, so before this the roster could fill up with people
 * who could never sign in. The old copy claimed sign-in details were emailed;
 * `AgentService` sends nothing.
 *
 * Opening this sheet MINTS an invite (POST /agents/:id/invite): a single-use
 * link that expires, and that retires any earlier link for the same agent.
 * The raw token exists exactly once — here and in the email — and only its
 * hash is stored, so this sheet is the only place it can be read.
 *
 * The server emails the link when SMTP is configured and says so via
 * `emailed`. When it is not, nothing was sent and this sheet says exactly
 * that rather than implying otherwise — the owner then delivers it themselves
 * over WhatsApp or SMS, which suits the setting anyway: a small business owner
 * already has their rider on WhatsApp.
 */
export default function AgentInviteSheet({
  open,
  onClose,
  agentId,
  name,
  email,
  phone,
  businessName
}: {
  open: boolean;
  onClose: () => void;
  agentId: number | null;
  name: string;
  email: string;
  phone?: string | null;
  businessName?: string;
}) {
  const [copied, setCopied] = useState<'link' | 'message' | null>(null);
  const [issued, setIssued] = useState<AgentInvite | null>(null);
  const [failed, setFailed] = useState(false);
  const panel = useModalBehaviour(open, onClose);

  // Minted on open rather than on render, so a closed sheet never burns a token.
  useEffect(() => {
    if (!open || !agentId) return;
    setIssued(null);
    setFailed(false);
    inviteAgent(agentId).then(setIssued).catch(() => setFailed(true));
  }, [open, agentId]);

  if (!open) return null;

  const setupUrl = issued?.inviteUrl ?? '';
  const firstName = (name || '').trim().split(/\s+/)[0] || 'there';
  const business = businessName || 'your delivery business';

  const message = `Hi ${firstName}, I have added you as a delivery rider for ${business}.

Set your password here — it takes a minute:
${setupUrl}

The link works once, and it is just for you. After that, sign in with ${email} and your deliveries for the day will be waiting.`;

  const copy = (what: 'link' | 'message') => {
    navigator.clipboard?.writeText(what === 'link' ? setupUrl : message);
    setCopied(what);
    setTimeout(() => setCopied(null), 1800);
  };

  /* wa.me needs a full international number. A bare local number would open a
     chat with the wrong person or none at all, so the recipient is only
     prefilled when the owner typed a country code; otherwise WhatsApp opens on
     the contact picker with the message ready. */
  const intl = (phone ?? '').trim().startsWith('+')
    ? (phone ?? '').replace(/[^\d]/g, '')
    : '';
  const wa = `https://wa.me/${intl}?text=${encodeURIComponent(message)}`;
  const sms = `sms:${(phone ?? '').replace(/\s/g, '')}?body=${encodeURIComponent(message)}`;
  const mail = `mailto:${encodeURIComponent(email)}`
    + `?subject=${encodeURIComponent(`Your ${business} delivery account`)}`
    + `&body=${encodeURIComponent(message)}`;

  return (
    <div className="ow-scrim" role="presentation" onClick={onClose}>
      <aside ref={panel} className="ow-sheet" role="dialog" aria-modal="true"
        aria-labelledby="ow-invite-title" onClick={(e) => e.stopPropagation()}>
        <div className="ow-sheet-h">
          <b id="ow-invite-title">Get {firstName} signed in</b>
          <button type="button" className="ow-x" data-modal-close aria-label="Close" onClick={onClose}>
            <X size={16} strokeWidth={2.2} />
          </button>
        </div>

        <div className="ow-sheet-b">
          {failed ? (
            <p className="ow-inline-err ow-invite-status">
              <span>
                <b>Could not create an invite link.</b> Nothing was sent. Close this and try
                again from the menu on their row.
              </span>
            </p>
          ) : !issued ? (
            <p className="ow-note ow-invite-status">
              <span>Creating a single-use link for {name}&hellip;</span>
            </p>
          ) : (
            <p className="ow-note ow-invite-status">
              <span>
                {issued.emailed
                  ? <><b>Sent to {email}.</b> You can pass the link on yourself too &mdash; some
                    riders will find it faster than their inbox.</>
                  : <><b>Nothing was emailed.</b> This server has no mail configured, so the link
                    below is the only copy. Send it to {name} and they will set their own
                    password.</>}
                <br />
                Works once, and expires {formatDateTime(issued.expiresAt)}.
              </span>
            </p>
          )}

          {issued && (
          <div className="ow-f">
            <label>Send it with</label>
            <div className="ow-invite-row">
              <a className="ow-btn pri" href={wa} target="_blank" rel="noreferrer">
                <MessageCircle size={15} strokeWidth={2.1} /> WhatsApp
              </a>
              {phone && (
                <a className="ow-btn" href={sms}>
                  <Smartphone size={15} strokeWidth={2.1} /> Text message
                </a>
              )}
              <a className="ow-btn" href={mail}>
                <Mail size={15} strokeWidth={2.1} /> Email
              </a>
            </div>
            <p className="hint">
              Opens the app you already use, with the message written. You send it, so
              {phone ? ' it arrives from a number they recognise.' : ' it arrives from you.'}
            </p>
          </div>
          )}

          {issued && (
          <div className="ow-f">
            <label htmlFor="invite-msg">What they will get</label>
            <textarea id="invite-msg" readOnly value={message} rows={9}
              className="ow-invite-textarea" />
            <div className="ow-invite-row ow-invite-row-mt">
              <button type="button" className="ow-btn" onClick={() => copy('message')}>
                {copied === 'message' ? <Check size={13} strokeWidth={2.4} /> : <Copy size={13} strokeWidth={2.1} />}
                {copied === 'message' ? 'Copied' : 'Copy message'}
              </button>
              <button type="button" className="ow-btn" onClick={() => copy('link')}>
                {copied === 'link' ? <Check size={13} strokeWidth={2.4} /> : <Copy size={13} strokeWidth={2.1} />}
                {copied === 'link' ? 'Copied' : 'Copy link only'}
              </button>
            </div>
          </div>
          )}

          <div className="ow-sec">What happens next</div>
          <ol className="ow-steps">
            <li><b>They open the link</b><span>It greets them by name, so they know it is genuine.</span></li>
            <li><b>They choose their own password</b><span>You never see it, and you cannot set it for them.</span></li>
            <li><b>They sign in</b><span>With {email} and the password they just chose.</span></li>
          </ol>

          <p className="ow-lock ow-lock-mid">
            <span>
              The link works once. Send a new one any time from the menu on their row — doing
              that cancels this one. Until they finish they cannot sign in, and auto-assign will
              still hand them shipments, so it is worth chasing.
            </span>
          </p>
        </div>

        <div className="ow-sheet-f">
          <button type="button" className="ow-btn pri ow-sheet-submit"
            onClick={onClose}>
            Done
          </button>
        </div>
      </aside>
    </div>
  );
}
