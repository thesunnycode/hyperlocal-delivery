/**
 * The step indicator for the multi-step auth flows.
 *
 * It used to be `STEP 1 OF 3` in 10px mono — technically present, visually
 * absent, and it was the only thing telling someone how long the flow would
 * take. Segments make the length legible before the label is read: you can see
 * at a glance that there are three of these and you are on the first.
 *
 * `label` exists for the case that is not a numbered step at all — the invite
 * path sets its own copy, because "Almost there" is honest there and
 * "Step 1 of 1" would be silly.
 */
export default function AuthSteps({
  current,
  total,
  label
}: {
  current?: number;
  total?: number;
  label?: string;
}) {
  const numbered = typeof current === 'number' && typeof total === 'number';

  return (
    <div className="auth-steps">
      {numbered && (
        <div className="auth-steps-bar" role="progressbar"
          aria-valuenow={current} aria-valuemin={1} aria-valuemax={total}
          aria-label={`Step ${current} of ${total}`}>
          {Array.from({ length: total }, (_, i) => (
            <i key={i} className={i < current ? 'on' : undefined} />
          ))}
        </div>
      )}
      <span>{label ?? (numbered ? `Step ${current} of ${total}` : '')}</span>
    </div>
  );
}
