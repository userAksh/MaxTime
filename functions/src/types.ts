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
  partnerId: string;
  venueId: string;
  customerUserId: string;
  participantCount: number;
  status: BookingStatus;
  amount: number;
  currency: string;
  idempotencyKey: string;
  holdExpiresAt: Timestamp | null;
  cancellationReason?: string;
  cancelledBy?: "CUSTOMER" | "PARTNER" | "OPS";
  /** Set once this booking's value has been included in a Payout — keeps payout runs from
   *  double-counting a completed booking across periods. */
  payoutId?: string | null;
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
