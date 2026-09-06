import { onCall, HttpsError } from "firebase-functions/v2/https";
import { FieldValue } from "firebase-admin/firestore";
import { db } from "./admin";
import { writeAuditLog } from "./audit";
import { AttendanceStatus, Booking, BOOKING_TRANSITIONS, BookingStatus } from "./types";

const ATTENDANCE_TO_BOOKING_STATUS: Record<AttendanceStatus, BookingStatus> = {
  CHECKED_IN: "CHECKED_IN",
  NO_SHOW: "NO_SHOW",
  COMPLETED: "COMPLETED",
};

/**
 * §20 — "Partner opens Today's Bookings -> Check In / Mark Attended." One callable for all three
 * transitions since they share the same authorization + audit-logging shape; BOOKING_TRANSITIONS
 * is the single source of truth for which one is actually legal from the booking's current state
 * (e.g. you can't mark COMPLETED a booking that was never CHECKED_IN).
 */
export const markAttendance = onCall(async (request) => {
  const partnerId = request.auth?.token?.partnerId as string | undefined;
  if (!partnerId) throw new HttpsError("permission-denied", "Not a partner user.");

  const bookingId = request.data?.bookingId as string;
  const status = request.data?.status as AttendanceStatus;
  if (!bookingId || !(status in ATTENDANCE_TO_BOOKING_STATUS)) {
    throw new HttpsError("invalid-argument", "bookingId and a valid status are required.");
  }
  const nextBookingStatus = ATTENDANCE_TO_BOOKING_STATUS[status];

  await db.runTransaction(async (tx) => {
    const bookingRef = db.collection("bookings").doc(bookingId);
    const bookingSnap = await tx.get(bookingRef);
    if (!bookingSnap.exists) throw new HttpsError("not-found", "Booking not found.");
    const booking = bookingSnap.data() as Booking;

    if (booking.partnerId !== partnerId) {
      throw new HttpsError("permission-denied", "This booking belongs to a different partner.");
    }
    if (!BOOKING_TRANSITIONS[booking.status].includes(nextBookingStatus)) {
      throw new HttpsError(
        "failed-precondition",
        `Cannot mark ${status} from booking status ${booking.status}.`
      );
    }

    tx.update(bookingRef, { status: nextBookingStatus, updatedAt: FieldValue.serverTimestamp() });

    const attendanceRef = db.collection("attendance").doc();
    tx.set(attendanceRef, {
      bookingId,
      participantId: null,
      status,
      markedBy: request.auth!.uid,
      createdAt: FieldValue.serverTimestamp(),
    });

    await writeAuditLog(
      {
        actorUserId: request.auth!.uid,
        actorRole: "PARTNER",
        entityType: "Booking",
        entityId: bookingId,
        action: `ATTENDANCE_${status}`,
        before: { status: booking.status },
        after: { status: nextBookingStatus },
      },
      tx
    );
  });

  return { ok: true };
});
