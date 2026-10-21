import { Timestamp } from "firebase-admin/firestore";

/** MaxTime_PARTNER.docx §4 — every partner/admin action must be authorized server-side against
 *  one of these, never inferred from which UI button was visible. */
export type PartnerRole = "OWNER" | "MANAGER" | "INSTRUCTOR";
export type AdminRole = "OPS" | "FINANCE" | "SUPER_ADMIN";

export type PartnerType = "VENUE" | "INSTRUCTOR" | "BOTH";
export type PartnerStatus = "PENDING_VERIFICATION" | "APPROVED" | "REJECTED" | "SUSPENDED";

/** §5.3 */
export type PartnerLeadStatus =
  | "NEW"
  | "CONTACTED"
  | "QUALIFIED"
  | "NEGOTIATING"
  | "PENDING_VERIFICATION"
  | "APPROVED"
  | "REJECTED"
  | "ON_HOLD";

export interface PartnerLead {
  id: string;
  name: string;
  phone: string;
  email?: string;
  businessName: string;
  city: string;
  category: string;
  websiteOrSocial?: string;
  preferredContactMethod?: string;
  notes?: string;
  status: PartnerLeadStatus;
  source: string;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface Partner {
  id: string;
  type: PartnerType;
  displayName: string;
  legalName: string | null;
  ownerUserId: string;
  status: PartnerStatus;
  verificationNotes?: string;
  commercial: CommissionRule | null;
  /** Null until the partner actually finishes onboarding and hits "Submit for review" — status
   *  alone flips to PENDING_VERIFICATION at step 0 (business name/type), well before the venue/
   *  service steps, so it can't be used to tell a real submission apart from an abandoned draft. */
  submittedAt: Timestamp | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface PartnerUser {
  id: string;
  partnerId: string;
  /** Null until the invited phone number actually signs in and claimStaffInvite links it —
   *  §22 invites by phone/email, before the person necessarily has a MaxTime account yet. */
  userId: string | null;
  invitedPhone: string | null;
  role: PartnerRole;
  status: "ACTIVE" | "INVITED" | "DEACTIVATED";
  invitedAt: Timestamp;
  activatedAt: Timestamp | null;
}

export interface Venue {
  id: string;
  partnerId: string;
  name: string;
  address: string;
  lat: number;
  lng: number;
  contactNumber: string;
  category: string;
  description: string;
  images: string[];
  operatingHours: string;
  website?: string;
  socialLink?: string;
  amenities?: string[];
  published: boolean;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export type ResourceStatus = "ACTIVE" | "INACTIVE";

export interface Resource {
  id: string;
  venueId: string;
  partnerId: string;
  name: string; // "Court 1", "Studio A" — never expose this id to consumers, always the name
  type: string;
  capacity: number;
  status: ResourceStatus;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** Customer-facing category + generic copy — shared across partners, mirrors the consumer app's
 *  own ACTIVITY_DB. A specific bookable configuration is a Service, not this. */
export interface Activity {
  id: string;
  category: string;
  name: string;
  description: string;
  media: string[];
  status: "DRAFT" | "PUBLISHED";
}

export type ServiceFormat = "COURT_BOOKING" | "CLASS" | "GROUP_SESSION" | "TRAINER_SESSION";
export type PricingUnit = "PER_SLOT" | "PER_PERSON" | "PER_SESSION";

export interface Service {
  id: string;
  partnerId: string;
  activityId: string;
  venueId: string | null;
  resourceId: string | null;
  instructorPartnerId: string | null;
  name: string;
  format: ServiceFormat;
  durationMinutes: number;
  capacity: number;
  price: number;
  pricingUnit: PricingUnit;
  skillLevel?: string;
  ageRule?: string;
  description: string;
  equipment?: string;
  cancellationPolicyId: string;
  published: boolean;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface CancellationPolicy {
  id: string;
  partnerId: string;
  label: string;
  cutoffHoursBeforeStart: number;
  refundPercentBeforeCutoff: number;
  refundPercentAfterCutoff: number;
}

/** §11 — the rule a partner authors ("Mon/Wed/Fri 6-10pm, Court 1, 60-min slots"). Never bookable
 *  directly; [generateInventoryFromRule] expands it into concrete InventorySlot documents. */
export interface AvailabilityRule {
  id: string;
  partnerId: string;
  serviceId: string;
  venueId: string;
  activityCategory: string;
  resourceId: string | null;
  daysOfWeek: number[]; // 0=Sun..6=Sat
  startTime: string; // "18:00"
  endTime: string; // "22:00"
  slotDurationMinutes: number;
  capacity: number;
  price: number;
  bookingLeadMinutes: number; // e.g. closes 30 min before start
  advanceBookingDays: number; // e.g. bookable up to 30 days ahead
  active: boolean;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export type InventorySlotStatus = "AVAILABLE" | "BLOCKED" | "CANCELLED" | "COMPLETED";

/** §11/§39 — the actual bookable unit. `remaining` is the only field [createBookingHold] ever
 *  mutates, and only inside a transaction — this is the single source of truth for "can this be
 *  booked right now," never derived from summing Booking documents client-side. */
export interface InventorySlot {
  id: string;
  ruleId: string | null;
  partnerId: string;
  venueId: string;
  resourceId: string | null;
  serviceId: string;
  activityCategory: string;
  date: string; // "2026-09-12"
  startTime: string; // "18:00"
  endTime: string; // "19:00"
  capacity: number;
  remaining: number;
  price: number;
  status: InventorySlotStatus;
  blockedReason?: string;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** §15 — allowed transitions enforced in code, not just documented. */
export type BookingStatus =
  | "PENDING_PAYMENT"
  | "CONFIRMED"
  | "PAYMENT_FAILED"
  | "EXPIRED"
  | "CHECKED_IN"
  | "COMPLETED"
  | "CANCELLED_BY_USER"
  | "CANCELLED_BY_PARTNER"
  | "NO_SHOW"
  | "DISPUTED"
  | "RESOLVED"
  | "REFUNDED"
  | "CLOSED";

export const BOOKING_TRANSITIONS: Record<BookingStatus, BookingStatus[]> = {
  PENDING_PAYMENT: ["CONFIRMED", "PAYMENT_FAILED", "EXPIRED"],
  CONFIRMED: ["CHECKED_IN", "CANCELLED_BY_USER", "CANCELLED_BY_PARTNER", "NO_SHOW", "DISPUTED"],
  CHECKED_IN: ["COMPLETED", "DISPUTED"],
  COMPLETED: ["DISPUTED"],
  CANCELLED_BY_USER: ["REFUNDED", "CLOSED"],
  CANCELLED_BY_PARTNER: ["REFUNDED", "CLOSED"],
  NO_SHOW: ["CLOSED", "DISPUTED"],
  DISPUTED: ["RESOLVED", "CLOSED"],
  RESOLVED: ["CLOSED"],
  PAYMENT_FAILED: [],
  EXPIRED: [],
  REFUNDED: ["CLOSED"],
  CLOSED: [],
};

export interface Booking {
  id: string;
  slotId: string;
  serviceId: string;
  /** Denormalized from InventorySlot.activityCategory at createBookingHold time — same pattern
   *  as serviceId/venueId below. Exists so attendance/payment analytics (attendance.ts,
   *  payments.ts, booking.ts) can report category breakdowns without an extra Firestore read. */
  activityCategory: string;
  partnerId: string;
  venueId: string;
  customerUserId: string;
  participantCount: number;
  /** How many units of the slot's `remaining` this booking actually consumed at creation — the
   *  headcount (participantCount) for a shared slot (CLASS/GROUP_SESSION/TRAINER_SESSION), but
   *  always 1 for an exclusive COURT_BOOKING slot regardless of headcount (whoever books it gets
   *  the whole court). Every place that releases inventory back (expiry, payment failure) must
   *  release exactly this many units, not participantCount, or a court booking with 5 players
   *  would wrongly free up 5 units on a slot that only ever held 1. */
  slotUnitsHeld: number;
  status: BookingStatus;
  amount: number;
  currency: string;
  idempotencyKey: string;
  holdExpiresAt: Timestamp | null;
  /** Set by createRazorpayOrder — lets paymentWebhook map a Razorpay webhook's `order_id` back to
   *  this booking without depending on Razorpay's notes-propagation behavior. */
  providerOrderId?: string;
  cancellationReason?: string;
  cancelledBy?: "CUSTOMER" | "PARTNER" | "OPS";
  /** Set once this booking's value has been included in a Payout — keeps payout runs from
   *  double-counting a completed booking across periods. */
  payoutId?: string | null;
  /** Set by createBookingHold when a ReferralReward was applied at checkout — see referral.ts.
   *  Read back by markAttendance/paymentWebhook/expireStaleHolds to know which reward (if any)
   *  needs reverting if this booking never completes. */
  appliedReferralRewardId?: string | null;
  /** The ₹ amount actually subtracted from the pre-discount price by appliedReferralRewardId —
   *  kept for the audit trail; never used to re-derive anything. */
  referralDiscountAmount?: number | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

export interface BookingParticipant {
  id: string;
  bookingId: string;
  userId: string | null;
  displayName: string;
  status: "ACTIVE" | "REMOVED";
}

export type PaymentStatus = "INITIATED" | "PENDING" | "SUCCEEDED" | "FAILED" | "REFUNDED";

export interface Payment {
  id: string;
  bookingId: string;
  customerUserId: string;
  grossAmount: number;
  currency: string;
  provider: string;
  providerTransactionId: string; // idempotency key for the webhook
  status: PaymentStatus;
  failureReason?: string;
  createdAt: Timestamp;
  confirmedAt: Timestamp | null;
}

export interface Refund {
  id: string;
  bookingId: string;
  paymentId: string;
  amount: number;
  reason: string;
  status: "INITIATED" | "PROCESSED" | "FAILED";
  createdAt: Timestamp;
  processedAt: Timestamp | null;
}

export type AttendanceStatus = "CHECKED_IN" | "NO_SHOW" | "COMPLETED";

export interface Attendance {
  id: string;
  bookingId: string;
  participantId: string | null;
  status: AttendanceStatus;
  markedBy: string;
  createdAt: Timestamp;
}

export type CommissionModel = "PERCENTAGE" | "FIXED_FEE" | "NET_PAYOUT" | "CUSTOM";

export interface CommissionRule {
  id: string;
  partnerId: string;
  model: CommissionModel;
  value: number; // percentage points, fixed fee amount, or net payout amount depending on model
  effectiveFrom: Timestamp;
  effectiveTo: Timestamp | null;
}

export type PayoutStatus = "SCHEDULED" | "ON_HOLD" | "PAID";

export interface Payout {
  id: string;
  partnerId: string;
  periodStart: Timestamp;
  periodEnd: Timestamp;
  grossAmount: number;
  commissionAmount: number;
  adjustments: number;
  netAmount: number;
  status: PayoutStatus;
  holdReason?: string;
  bookingIds: string[];
  expectedPayoutDate: Timestamp;
  createdAt: Timestamp;
  paidAt: Timestamp | null;
}

export interface NotificationRecord {
  id: string;
  recipientUserId: string;
  event: string;
  channel: "PUSH" | "SMS" | "EMAIL";
  payload: Record<string, unknown>;
  status: "PENDING" | "SENT" | "FAILED";
  createdAt: Timestamp;
  sentAt: Timestamp | null;
}

export type SupportTicketCategory =
  | "BOOKING"
  | "PAYMENT"
  | "REFUND"
  | "INVENTORY"
  | "PARTNER"
  | "CANCELLATION"
  | "SAFETY";

export interface SupportTicket {
  id: string;
  reporterUserId: string;
  partnerId?: string;
  bookingId?: string;
  category: SupportTicketCategory;
  priority: "LOW" | "MEDIUM" | "HIGH" | "URGENT";
  status: "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";
  assigneeUserId?: string;
  messages: { authorUserId: string; text: string; at: Timestamp }[];
  resolution?: string;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** §40/§52 — every manual override, refund, payout hold, or permission change must produce one
 *  of these. Never mutate booking/payment/payout state without also writing this. */
export interface AuditLog {
  id: string;
  actorUserId: string;
  actorRole: string;
  entityType: string;
  entityId: string;
  action: string;
  reason?: string;
  before?: unknown;
  after?: unknown;
  createdAt: Timestamp;
}

// --- Referral V1 (MaxTime_Claude_Product_Design_Context.docx §6-11) ----------------------------
// Kept entirely separate from Activity Invite (joining a specific group/session) and from
// MaxCoins — different purpose, different backend logic, even though they might share a link
// format later. See functions/src/referral.ts for the functions that operate on these.

export type ReferralCodeStatus = "ACTIVE" | "DISABLED";

/** Doc ID in Firestore IS the code itself — deterministic (first 3 letters of the owner's first
 *  name, uppercase, + last 2 digits of their phone number), not randomly generated, so it's
 *  personal/memorable rather than an opaque string. Collisions get a numeric suffix appended
 *  (see referral.ts's deriveReferralCodeBase/generateMyReferralCode) rather than retried with a
 *  different value, since the base is derived from identity, not chosen freely. Uniqueness itself
 *  still comes for free via a transaction collision check, the same trick idempotency_keys uses
 *  for its own doc IDs. `ReferralConfig.codeLength` is unused now (kept on the type only for
 *  backward compatibility with already-seeded config documents) — code length varies by name/
 *  suffix rather than being fixed. */
export interface ReferralCode {
  id: string;
  ownerUserId: string;
  status: ReferralCodeStatus;
  createdAt: Timestamp;
}

/** Qualification (referrer's reward unlocked) only ever happens via markAttendance transitioning
 *  the referee's booking to COMPLETED — never on signup or payment alone. INVALIDATED is reserved
 *  for post-hoc fraud/dispute discovery after QUALIFIED; nothing in V1 writes it yet. */
export type ReferralStatus = "PENDING" | "QUALIFIED" | "EXPIRED" | "INVALIDATED";

export const REFERRAL_TRANSITIONS: Record<ReferralStatus, ReferralStatus[]> = {
  PENDING: ["QUALIFIED", "EXPIRED", "INVALIDATED"],
  QUALIFIED: ["INVALIDATED"],
  EXPIRED: [],
  INVALIDATED: [],
};

/** Provider-agnostic "how we learned about this pairing" tag. V1 only ever writes
 *  MANUAL_CODE_ENTRY — the other three exist purely so a future attribution source (Android's
 *  Play Install Referrer, then Branch/AppsFlyer) can be added without a schema change. Nothing in
 *  qualification or reward-application logic ever branches on this value — it's an audit tag. */
export type AttributionSource = "MANUAL_CODE_ENTRY" | "PLAY_INSTALL_REFERRER" | "BRANCH" | "APPSFLYER";

/** Doc ID IS the referred user's uid, not an autoId — the single most load-bearing schema
 *  decision in this feature. It makes "at most one direct referrer per user" and "first valid
 *  attribution wins" structural: a transaction's tx.set on an already-existing doc fails cleanly,
 *  instead of relying on an application-level check a race could slip past (same reasoning as
 *  idempotency_keys and the inventory_slots.remaining decrement). */
export interface Referral {
  id: string; // == referredUserId
  referrerUserId: string;
  referredUserId: string;
  code: string;
  status: ReferralStatus;
  attributionSource: AttributionSource;
  /** Raw provider payload (e.g. Play Install Referrer's referrer string) — audit-only, never
   *  read by any qualification/validation logic. */
  attributionMetadata?: Record<string, unknown>;
  /** Denormalized pointers to this referral's two ReferralReward docs — avoids a query inside
   *  markAttendance's transaction (Firestore transactions can't run arbitrary queries between
   *  reads cheaply the way a direct doc().get() can). */
  refereeRewardId: string;
  referrerRewardId: string;
  qualifyingBookingId: string | null;
  createdAt: Timestamp;
  qualifiedAt: Timestamp | null;
  updatedAt: Timestamp;
}

export type RewardRole = "REFERRER" | "REFEREE";

/** RESERVED (referee only) is the "entered a valid code, discount usable at next checkout" state.
 *  PENDING (referrer only) is the pre-qualification placeholder — not usable until the referee's
 *  booking completes. APPLIED means "attached to a specific in-flight booking, not yet final" —
 *  it reverts back to whichever state it came from (RESERVED for a referee reward, ISSUED for a
 *  referrer reward) if that booking never completes, or advances to REDEEMED once it does. VOID
 *  is reserved for fraud/dispute reversal, distinct from EXPIRED (simply timed out unused). */
export type RewardStatus = "PENDING" | "RESERVED" | "APPLIED" | "ISSUED" | "REDEEMED" | "EXPIRED" | "VOID";

export const REWARD_TRANSITIONS: Record<RewardStatus, RewardStatus[]> = {
  PENDING: ["ISSUED", "EXPIRED", "VOID"],
  RESERVED: ["APPLIED", "EXPIRED", "VOID"],
  APPLIED: ["REDEEMED", "RESERVED", "ISSUED", "VOID"],
  ISSUED: ["APPLIED", "EXPIRED", "VOID"],
  REDEEMED: ["VOID"],
  EXPIRED: [],
  VOID: [],
};

export interface ReferralReward {
  id: string;
  referralId: string; // == Referral.id
  beneficiaryUserId: string;
  role: RewardRole;
  status: RewardStatus;
  /** Snapshotted from the active ReferralConfig at the moment this doc is created — NEVER
   *  re-read live at redemption/qualification time, so an admin editing the program later can't
   *  retroactively change the value of an already-outstanding reward. This is what makes
   *  "admin-configurable, not hardcoded" auditable: configurable going forward, frozen once
   *  granted. */
  discountPercent: number;
  discountCapAmount: number;
  currency: string;
  /** Set while status is APPLIED; cleared back to null on revert. */
  bookingId: string | null;
  /** The actual computed ₹ amount from the one time this was applied — audit trail only. */
  appliedDiscountAmount: number | null;
  createdAt: Timestamp;
  appliedAt: Timestamp | null;
  redeemedAt: Timestamp | null;
  issuedAt: Timestamp | null; // REFERRER only, set at qualification
  updatedAt: Timestamp;
}

/** Admin-controlled program parameters, effective-dated exactly like CommissionRule — an admin's
 *  edit today must never retroactively change a reward whose percent/cap was already snapshotted
 *  under a previous config. Firestore-rules-writable directly by isAdmin(), no Cloud Function
 *  needed for basic CRUD (same pattern as commission_rules). */
export interface ReferralConfig {
  id: string;
  referrerDiscountPercent: number;
  referrerDiscountCapAmount: number;
  refereeDiscountPercent: number;
  refereeDiscountCapAmount: number;
  currency: string;
  /** A PENDING referral past this many days without qualifying auto-EXPIREs. */
  attributionWindowDays: number;
  /** A RESERVED/ISSUED reward past this many days unused auto-EXPIREs. */
  rewardValidityDays: number;
  /** How "new" (by Firebase Auth account-creation time) a user must be to redeem a code at all —
   *  see functions/src/referral.ts's redeemReferralCode for why this proxy exists. */
  redemptionGraceHours: number;
  codeLength: number;
  /** Kill switch: flipping every config's `active` to false immediately stops new
   *  redemptions/issuances without touching already-existing referrals/rewards, whose values are
   *  already frozen. */
  active: boolean;
  effectiveFrom: Timestamp;
  effectiveTo: Timestamp | null;
  createdAt: Timestamp;
  updatedAt: Timestamp;
}

/** Internal rate-limit bookkeeping only — doc ID is the uid being limited, never read/written by
 *  clients, mirrors idempotency_keys' "internal only" role. */
export interface ReferralRedemptionAttempts {
  failedCount: number;
  windowStartedAt: Timestamp;
}

/** Distinct from AuditLog (admin-only forensic trail) — this is the participant-facing timeline
 *  for a specific referral ("your friend joined!", "your reward is ready!"). referrerUserId/
 *  referredUserId are denormalized directly onto every event specifically so firestore.rules can
 *  authorize a participant's read with a same-document field check, no cross-document get() —
 *  matching how partner_users' rule checks resource.data.partnerId directly. */
export type ReferralEventType =
  | "CODE_GENERATED"
  | "ATTRIBUTED"
  | "REDEMPTION_REJECTED"
  | "REWARD_APPLIED"
  | "REWARD_REVERTED"
  | "REFERRAL_QUALIFIED"
  | "REWARD_REDEEMED"
  | "REWARD_ISSUED"
  | "REFERRAL_EXPIRED"
  | "REWARD_EXPIRED"
  | "REFERRAL_INVALIDATED"
  | "REWARD_VOIDED";

export interface ReferralEvent {
  id: string;
  referralId: string;
  eventType: ReferralEventType;
  actorUserId: string;
  referrerUserId: string;
  referredUserId: string;
  metadata: Record<string, unknown>;
  createdAt: Timestamp;
}
