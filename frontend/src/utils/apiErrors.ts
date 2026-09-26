import { ApiError } from '../lib/apiClient';
import type { ApiErrorBody } from '../types/api';

/**
 * Human labels for the field names the backend's Bean Validation errors name
 * (`ApiError.field`, from `GlobalExceptionHandler.handleMethodArgumentNotValid`
 * — always the *first* violated field, never the full set).
 *
 * Kept here rather than duplicated per form, because a rename on either side
 * (a DTO field, or a form's own wording) only has one place to go wrong.
 */
const FIELD_LABELS: Record<string, string> = {
  businessName: 'Business name',
  ownerName: 'Your name',
  fullName: 'Your name',
  name: 'Name',
  email: 'Email',
  phone: 'Phone',
  businessPhone: 'Business phone',
  customerPhone: 'Phone',
  password: 'Password',
  newPassword: 'Password',
  customerName: 'Customer name',
  address: 'Delivery address',
  deliveryAddress: 'Delivery address',
  scheduledAt: 'Delivery window',
  scheduledDeliveryAt: 'Delivery window',
  code: 'Code'
};

function labelFor(field: string | null): string {
  if (!field) return 'This field';
  return (
    FIELD_LABELS[field] ??
    field.replace(/([A-Z])/g, ' $1').replace(/^./, (c) => c.toUpperCase())
  );
}

/**
 * Turn a raw Jakarta Bean Validation message (what `@NotBlank`, `@Pattern`,
 * `@Size` etc. produce by default — "must not be blank",
 * `must match "[+\d\s\-]{7,20}"`, "size must be between 8 and
 * 2147483647") into copy a shop owner can act on, without ever showing them
 * a regex, a Java type name or an int ceiling.
 *
 * This is deliberately a client-side translation rather than a backend
 * rewrite: `ApiError.message` is a documented, stable field other endpoints
 * already return prose through (business-rule rejections are already
 * hand-written and pass through unchanged below), so the mapping only
 * intercepts the mechanical, framework-generated shapes.
 */
export function friendlyValidationMessage(field: string | null, raw: string): string {
  const label = labelFor(field);

  if (/must not be blank|must not be empty|must not be null/i.test(raw)) {
    return `Enter ${label.toLowerCase()}.`;
  }

  if (/must match/i.test(raw)) {
    if (field && /phone/i.test(field)) {
      return `${label} doesn't look like a phone number. Use digits, spaces, "+" and "-" only.`;
    }
    if (field === 'code') return 'Enter all 6 digits.';
    return `${label} isn't in a valid format.`;
  }

  const size = raw.match(/size must be between (\d+) and (\d+)/i);
  if (size) {
    const min = Number(size[1]);
    const max = Number(size[2]);
    if (min > 1) return `${label} must be at least ${min} characters.`;
    if (max < 1000) return `${label} must be ${max} characters or fewer.`;
    return `${label} is too long.`;
  }

  if (/must be a future date/i.test(raw)) {
    return 'Pick a delivery time later than now.';
  }

  if (/must be a well-formed email address/i.test(raw)) {
    return 'Enter a valid email address.';
  }

  // Anything else is either already hand-written prose (a business-rule
  // rejection) or short enough to be safe — but never pass through something
  // that looks like a regex, a stack frame or a type name.
  const looksMechanical = /[{}\\[\]]|Exception|@|\.java/.test(raw);
  if (!looksMechanical && raw.length < 140) return raw;
  return `Check ${label.toLowerCase()} and try again.`;
}

export type FieldError = { field: string | null; message: string };

/**
 * From a caught error, produce a friendly `{ field, message }` for inline
 * display — or `null` when this isn't a per-field validation rejection (a
 * network failure, a 401, a 409 duplicate, a 5xx) so the caller falls back
 * to its own banner/toast handling.
 */
export function fieldErrorFrom(err: unknown): FieldError | null {
  if (!(err instanceof ApiError)) return null;
  if (err.status < 400 || err.status >= 500) return null;
  const body = err.body;
  if (!body || typeof body !== 'object') return null;
  const b = body as ApiErrorBody;
  if (b.code !== 'VALIDATION_ERROR' || !b.message) return null;
  return { field: b.field ?? null, message: friendlyValidationMessage(b.field ?? null, b.message) };
}
