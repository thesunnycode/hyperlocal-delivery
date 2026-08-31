/**
 * The wire shapes of every backend response and request this frontend
 * touches, derived one-to-one from the Java records under
 * `src/main/java/com/hyperlocal/delivery/dto/`.
 *
 * The Java DTOs are the source of truth, not `API_CONTRACT.md` — that doc is
 * known-stale. When a DTO changes, change the type here in the same commit and
 * `tsc` will point at every component that needs updating. That is the whole
 * reason this file exists: before it, a renamed backend field surfaced as
 * `undefined` on screen with no test failure and no error.
 *
 * Conventions:
 * - Java `Long`/`long`/`Integer`/`double` -> `number`.
 * - Every timestamp is already a `String` on the Java side (each `from(...)`
 *   factory calls `.toString()` on the temporal), so it is `string` here, not
 *   `Date` — ISO-8601, parsed at the render site by `utils/format`.
 * - `| null` appears wherever a Java factory can pass `null` (an unassigned
 *   agent, an undelivered shipment). Do not "simplify" these away; the null
 *   checks they force are the point.
 * - Java `List.of()` fallbacks mean a collection is never null, only empty.
 */

// ─── Wire enums ──────────────────────────────────────────────────────────
// Three backend enums carry @JsonValue, so the JSON value is NOT the Java
// constant name. Typing these from the constant names instead of the wire
// values would break every comparison silently.

/**
 * `model/ShipmentStatus` — `@JsonValue` emits the lowercase `wireValue`.
 * Matches `utils/statusMachine`'s keys exactly. There is no `created` state:
 * shipments are auto-assigned straight into `assigned`.
 */
export type ShipmentStatus =
  | 'assigned'
  | 'picked_up'
  | 'in_transit'
  | 'out_for_delivery'
  | 'delivered'
  | 'failed'
  | 'returned';

/**
 * `model/UserRole` — the Java constants are `BUSINESS_OWNER` and
 * `DELIVERY_AGENT`, but `@JsonValue` emits the short code.
 */
export type UserRole = 'OWNER' | 'AGENT';

/**
 * `model/FailureReason` — `@JsonValue` emits the human label, which is what
 * `FAILURE_REASONS` in `utils/statusMachine` offers in the attempt sheet.
 */
export type FailureReason =
  | 'Customer absent'
  | 'Address not found'
  | 'Refused'
  | 'Damaged'
  | 'Other';

/**
 * `model/OtpPurpose` — no `@JsonValue`, so the wire values are the constant
 * names themselves.
 */
export type OtpPurpose = 'REGISTRATION' | 'PASSWORD_RESET';

// ─── Response envelopes ──────────────────────────────────────────────────
// Every controller returns one of these. `apiClient.apiFetch` unwraps them:
// a payload carrying both `status` and `data` is reduced to its `data`.

/** `dto/common/ApiSuccess` */
export type ApiSuccessEnvelope<T> = { status: 'success'; data: T };

/** `dto/common/Pagination` */
export type Pagination = {
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
};

/**
 * `dto/common/ApiSuccessPage`.
 *
 * NOTE: `apiFetch`'s unwrap keys off `status` + `data`, so callers of a paged
 * endpoint receive only the row array — `pagination` is discarded before any
 * caller sees it. That is why `listShipments` and `getRegister` below return
 * plain arrays despite the backend paging them. Changing it is a behaviour
 * change and belongs in its own task.
 */
export type ApiSuccessPageEnvelope<T> = {
  status: 'success';
  data: T[];
  pagination: Pagination;
};

/** `dto/common/ApiError` — the body `apiClient` reads `.message` off. */
export type ApiErrorBody = {
  status: 'error';
  code: string;
  message: string;
  field: string | null;
  timestamp: string;
  path: string;
  reference: string;
};

/**
 * What a body-less endpoint resolves to. `apiFetch` returns `null` on 204
 * (logout, reset-password) but an empty string on a 202 with no body
 * (forgot-password), because only the 204 path short-circuits before the
 * body is read. Callers ignore the value in both cases.
 */
export type EmptyResponse = null | string;

// ─── Shipments ───────────────────────────────────────────────────────────

/**
 * `dto/shipment/ShipmentEventDto`.
 *
 * Carries the same value under several names — `toStatus`/`status` are
 * identical, `notes`/`meta` are identical, and `createdAt`/`stamp`/`time` are
 * all the same ISO string. The aliases exist on the wire because the backend
 * was shaped to match what these components already read. Every one is typed
 * here deliberately; dropping one would break whichever surface reads it.
 */
export type ShipmentEvent = {
  /** null on the first event — there is no status to come from. */
  fromStatus: ShipmentStatus | null;
  toStatus: ShipmentStatus;
  /** Alias of `toStatus`. This is the one the timeline reads. */
  status: ShipmentStatus;
  changedBy: string | null;
  notes: string | null;
  /** Alias of `notes`. */
  meta: string | null;
  label: string | null;
  createdAt: string | null;
  /** Alias of `createdAt`. This is the one the timeline reads. */
  stamp: string | null;
  /** Alias of `createdAt`. */
  time: string | null;
};

/**
 * `dto/shipment/DeliveryAttemptDto`. Also alias-heavy:
 * `attemptNumber`/`no`, `failureReason`/`reason`, `attemptedAt`/`stamp`.
 */
export type DeliveryAttempt = {
  id: number;
  shipmentId: number | null;
  agentId: number | null;
  agentName: string | null;
  attemptNumber: number;
  /** Alias of `attemptNumber`. */
  no: number;
  failureReason: FailureReason | null;
  /** Alias of `failureReason`. */
  reason: FailureReason | null;
  note: string | null;
  attemptedAt: string | null;
  /** Alias of `attemptedAt`. */
  stamp: string | null;
};

/** `dto/shipment/ShipmentResponseDto` — the full single-shipment shape. */
export type Shipment = {
  id: number;
  /** The public tracking token — a UUID. Powers `/track/:token`. */
  token: string;
  status: ShipmentStatus;
  customerName: string;
  customerPhone: string;
  address: string;
  scheduledAt: string | null;
  deliveredAt: string | null;
  agentId: number | null;
  agentName: string | null;
  events: ShipmentEvent[];
  attempts: DeliveryAttempt[];
  createdAt: string | null;
  updatedAt: string | null;
};

/**
 * `dto/shipment/ShipmentSummaryDto` — the list shape. Deliberately thinner
 * than `Shipment`: no phone, no events, no attempts. A list row that needs
 * one of those has to fetch the detail.
 */
export type ShipmentSummary = {
  id: number;
  token: string;
  status: ShipmentStatus;
  customerName: string;
  address: string;
  agentId: number | null;
  agentName: string | null;
  scheduledAt: string | null;
  createdAt: string | null;
};

/**
 * `dto/tracking/PublicTrackingResponse` — the public, unauthenticated shape,
 * shaped server-side to the privacy boundary. Note what is absent and must
 * stay absent: agent identity, agent phone, internal ids, failure reasons.
 */
export type PublicTrackingEvent = {
  status: ShipmentStatus;
  label: string | null;
  stamp: string | null;
};

export type PublicTracking = {
  trackingToken: string;
  status: ShipmentStatus;
  customerName: string;
  address: string;
  scheduledAt: string | null;
  deliveredAt: string | null;
  businessName: string | null;
  businessPhone: string | null;
  events: PublicTrackingEvent[];
};

// ─── Auth ────────────────────────────────────────────────────────────────

/** `dto/auth/UserResponse` — also the `GET/PATCH /auth/me` payload. */
export type User = {
  id: number;
  email: string;
  /** The person's name. Maps from the entity's `fullName`. */
  name: string;
  phone: string | null;
  role: UserRole;
  businessId: number;
  businessName: string;
  businessPhone: string | null;
  isActive: boolean;
  createdAt: string | null;
};

/**
 * `dto/auth/AuthResponse`. `token` is an alias of `accessToken` — the session
 * layer reads `token`.
 */
export type AuthResult = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  /** Alias of `accessToken`. */
  token: string;
  role: UserRole;
  user: User;
};

/** `dto/auth/OtpSentResponse` — from register and resend-otp. */
export type OtpSent = { message: string; email: string };

/**
 * `dto/auth/OtpErrorResponse` — the body of a rejected OTP verification, read
 * by both OTP surfaces to decide whether to let the user retry.
 *
 * Every field is optional because the surfaces read it off a thrown error
 * whose body shape depends on which guard rejected the code.
 */
export type OtpErrorBody = {
  error?: string | null;
  reason?: string | null;
  attemptsRemaining?: number | null;
};

/** `dto/auth/ResetTokenResponse` — from verify-reset-otp. */
export type ResetToken = { resetToken: string };

// ─── Agents ──────────────────────────────────────────────────────────────

/**
 * `dto/agent/AgentResponse` — returned by create, get-one, update,
 * deactivate and reactivate.
 */
export type Agent = {
  id: number;
  email: string;
  name: string;
  phone: string | null;
  active: boolean;
  openCount: number;
  deliveredCount: number;
  failedCount: number;
  joinedAt: string | null;
  updatedAt: string | null;
};

/**
 * `dto/agent/AgentSummaryDto` — what `GET /agents` returns, and NOT the same
 * shape as `Agent`. It has no `deliveredCount`/`failedCount`/`updatedAt`, and
 * its join timestamp is called `createdAt` rather than `joinedAt`. Reading
 * `joinedAt` off a list row yields `undefined`.
 */
/** `dto/invite/InviteResponse` — the one moment the raw token is visible. */
export type AgentInvite = {
  inviteUrl: string;
  expiresAt: string;
  /** False when SMTP is unconfigured: the link was only logged, so the owner
   *  has to deliver it. The UI must not claim an email was sent. */
  emailed: boolean;
};

/** `dto/invite/InvitePreviewResponse` — what an unauthenticated setup screen may know. */
export type InvitePreview = {
  fullName: string;
  email: string;
  businessName: string | null;
  expiresAt: string;
};

export type AgentSummary = {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  active: boolean;
  openCount: number;
  createdAt: string | null;
};

// ─── Reporting (owner-only, read-only) ───────────────────────────────────

/** `dto/reports/DayPointDto` */
export type DayPoint = {
  date: string;
  total: number;
  delivered: number;
  failed: number;
  progress: number;
};

/** `dto/reports/AgentBarDto` */
export type AgentBar = { name: string; delivered: number; pct: number };

/** `dto/reports/ReasonCountDto` — `label` is a `FailureReason` label. */
export type ReasonCount = { label: string; count: number; pct: number };

/** `dto/reports/OverviewReportDto` */
export type OverviewReport = {
  total: number;
  delivered: number;
  deliveredPct: number;
  failed: number;
  failedPct: number;
  returned: number;
  returnedPct: number;
  inProgress: number;
  inProgressPct: number;
  onTimeRate: number;
  avgDeliveryHours: number;
  firstAttemptRate: number;
  days: DayPoint[];
  agentBars: AgentBar[];
  reasons: ReasonCount[];
};

/** `dto/reports/TrendReportDto` — `rows` and `days` are the same list. */
export type TrendReport = { rows: DayPoint[]; days: DayPoint[] };

/** `dto/reports/AgentPerformanceRowDto` */
export type AgentPerformanceRow = {
  id: number;
  name: string;
  active: boolean;
  assigned: number;
  delivered: number;
  failed: number;
  returned: number;
  open: number;
  avgHours: number;
  perDay: number;
};

/** `dto/reports/RegisterRowDto` */
export type RegisterRow = {
  token: string;
  status: ShipmentStatus;
  customerName: string;
  address: string;
  agentName: string | null;
  scheduledAt: string | null;
  deliveredAt: string | null;
};

/**
 * What the admin register sends as its `status` query param.
 *
 * Unlike every other status filter in this API, this one takes a list:
 * ReportController#register declares the param as a String and runs it
 * through parseStatuses, which splits on commas and rejects any unknown
 * value with a 400. That is why the register's select can offer
 * 'delivered,failed' as one option while /shipments only ever takes one
 * status.
 */
export type RegisterStatusParam = ShipmentStatus | 'delivered,failed';

/** `GET /api/reports/:kind/export` accepts exactly these two. */
export type ExportKind = 'agent-performance' | 'register';

// ─── Request bodies ──────────────────────────────────────────────────────
// These describe what `src/api/*` actually SENDS, which in several places is
// not the Java field name: the records carry `@JsonAlias` so that Jackson
// accepts the shorter name this frontend has always used. Where they differ,
// the Java name is noted.

/** `dto/auth/LoginRequest` */
export type LoginBody = { email: string; password: string };

/** `dto/auth/RegisterRequest` */
export type RegisterBody = {
  businessName: string;
  ownerName: string;
  email: string;
  phone: string;
  /** Minimum 8 characters, enforced server-side. */
  password: string;
};

/** `dto/auth/VerifyOtpRequest` — `code` must be exactly 6 digits. */
export type VerifyOtpBody = { email: string; code: string };

/** `dto/auth/ResendOtpRequest` */
export type ResendOtpBody = { email: string; purpose: OtpPurpose };

/** `dto/auth/ForgotPasswordRequest` */
export type ForgotPasswordBody = { email: string };

/**
 * `dto/auth/ResetPasswordRequest`. The frontend sends `resetToken` and
 * `newPassword`; the record also accepts `token`/`password` via `@JsonAlias`.
 */
export type ResetPasswordBody = { resetToken: string; newPassword: string };

/**
 * `dto/auth/UpdateAccountRequest` — every field optional, it is a PATCH.
 * Not currently called: `OwnerAccountPage` is read-only by design.
 */
export type UpdateAccountBody = {
  fullName?: string;
  phone?: string;
  businessName?: string;
  businessPhone?: string;
};

/**
 * `dto/shipment/CreateShipmentRequest`. `address` and `scheduledAt` are
 * `@JsonAlias`es for the Java `deliveryAddress` and `scheduledDeliveryAt`.
 * `scheduledAt` must be in the future (`@Future`).
 */
export type CreateShipmentBody = {
  customerName: string;
  customerPhone: string;
  address: string;
  scheduledAt: string;
};

/**
 * `dto/shipment/AdvanceRequest` — the body of every forward status move
 * (pickup, start-transit, out-for-delivery, deliver, return). `note` is a
 * `@JsonAlias` for the Java `notes`.
 */
export type AdvanceBody = { note?: string | null };

/** `dto/attempt/FailAttemptRequest` — logging a failed delivery attempt. */
export type FailAttemptBody = { reason: FailureReason; notes?: string | null };

/**
 * `dto/shipment/ReassignRequest`. A null `agentId` means "auto — pick the
 * least-loaded active agent". `note` is a `@JsonAlias` for `notes`.
 */
export type ReassignBody = {
  /**
   * A null means "auto — least-loaded active agent". OwnerShipmentsPage
   * passes the radio group's value, which is a string id, and Jackson
   * binds either to the Java Long — so both are typed rather than
   * coercing one into the other and changing what goes on the wire.
   */
  agentId: number | string | null;
  note?: string | null;
};

/**
 * `dto/agent/CreateAgentRequest`. `name` is a `@JsonAlias` for the Java
 * `fullName`. Password is omitted by this frontend, which means "generate one
 * server-side".
 */
export type CreateAgentBody = { name: string; email: string; phone: string };

/**
 * `dto/agent/UpdateAgentRequest` — `name` aliases `fullName`. Email is the
 * login and is deliberately not updatable here.
 */
export type UpdateAgentBody = { name: string; phone: string };
