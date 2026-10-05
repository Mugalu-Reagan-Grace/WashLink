"use strict";

const { randomBytes, timingSafeEqual } = require("node:crypto");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const { defineSecret } = require("firebase-functions/params");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { logger } = require("firebase-functions");
const { onRequest } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const {
  quoteBooking,
  hasActiveRole,
  isVerifiedFlutterwavePayment,
  isValidStatusTransition,
  canCancelBooking,
  canReviewBooking,
  canAssignRider,
  validReview,
  updatedRatingAverage,
  isEligibleForProviderPayout,
  isEligibleForProviderRejectionRefund,
  canClaimProviderPayout,
} = require("./domain");

initializeApp();
const db = getFirestore();
const FLUTTERWAVE_SECRET_KEY = defineSecret("FLUTTERWAVE_SECRET_KEY");
const FLUTTERWAVE_WEBHOOK_HASH = defineSecret("FLUTTERWAVE_WEBHOOK_HASH");
const REGION = "us-central1";
const AUTOMATED_PAYOUTS_ENABLED = false;

function requireBookingId(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9]{10,40}$/.test(value)) {
    throw new HttpsError("invalid-argument", "A valid booking ID is required.");
  }
  return value;
}

function referenceBookingId(reference) {
  const match = typeof reference === "string"
    && reference.match(/^washlink_([A-Za-z0-9]{10,40})_[a-f0-9]{24}$/);
  return match ? match[1] : null;
}

function paymentMethodInfo(method) {
  if (method === "cash") {
    return { label: "Cash", option: null, cashOnDelivery: true };
  }
  if (method === "card") {
    return { label: "Flutterwave card", option: "card", cashOnDelivery: false };
  }
  if (method === "airtel") {
    return { label: "Airtel Money", option: "mobilemoneyuganda", cashOnDelivery: false };
  }
  if (method === "mtn") {
    return { label: "MTN Mobile Money", option: "mobilemoneyuganda", cashOnDelivery: false };
  }
  throw new HttpsError("invalid-argument", "Choose card, Uganda mobile money, or cash on delivery.");
}

function validWebhookHash(received, expected) {
  if (typeof received !== "string" || typeof expected !== "string") return false;
  const receivedBytes = Buffer.from(received);
  const expectedBytes = Buffer.from(expected);
  return receivedBytes.length === expectedBytes.length
    && timingSafeEqual(receivedBytes, expectedBytes);
}

async function flutterwaveRequest(path, body) {
  const response = await fetch(`https://api.flutterwave.com/v3/${path}`, {
    method: body ? "POST" : "GET",
    headers: {
      Authorization: `Bearer ${FLUTTERWAVE_SECRET_KEY.value()}`,
      "Content-Type": "application/json",
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.status !== "success") {
    logger.error("Flutterwave API request failed", {
      path,
      httpStatus: response.status,
      providerMessage: payload.message || "No response message",
    });
    throw new HttpsError("unavailable", "The payment provider could not process the request.");
  }
  return payload.data;
}

function validPayoutProfile(profile) {
  if (!profile || typeof profile !== "object"
      || typeof profile.beneficiaryName !== "string"
      || profile.beneficiaryName.trim().length < 2
      || typeof profile.accountNumber !== "string"
      || profile.accountNumber.length < 5
      || profile.accountNumber.length > 32) {
    return false;
  }
  if (profile.destinationType === "bank") {
    return typeof profile.accountBank === "string"
      && profile.accountBank.length > 0
      && (profile.destinationBranchCode == null
        || typeof profile.destinationBranchCode === "string");
  }
  return profile.destinationType === "airtel" || profile.destinationType === "mtn";
}

function payoutTransferStatus(status) {
  if (status === "SUCCESSFUL") return "SUCCESSFUL";
  if (status === "FAILED") return "FAILED";
  return "PROCESSING";
}

function payoutAttemptRef(providerId, bookingId) {
  return db.collection("users").doc(providerId)
    .collection("payoutAttempts").doc(bookingId);
}

async function transferByReference(reference) {
  const result = await flutterwaveRequest(
    `transfers?reference=${encodeURIComponent(reference)}`
  );
  if (Array.isArray(result)) {
    return result.find((transfer) => transfer && transfer.reference === reference) || null;
  }
  return result && result.reference === reference ? result : null;
}

async function processProviderPayout(providerId, bookingId) {
  const attemptRef = payoutAttemptRef(providerId, bookingId);
  const claim = randomBytes(12).toString("hex");
  let attempt;
  const claimed = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(attemptRef);
    if (!snapshot.exists) return false;
    attempt = snapshot.data();
    if (!canClaimProviderPayout(attempt, Date.now())) return false;
    transaction.update(attemptRef, {
      state: "PROCESSING",
      claim,
      claimedAt: Date.now(),
      updatedAt: Date.now(),
    });
    return true;
  });
  if (!claimed) return;

  try {
    let transfer = attempt.transferId
      ? await flutterwaveRequest(`transfers/${attempt.transferId}`)
      : await transferByReference(attempt.reference);
    if (!transfer) {
      const details = {
        amount: attempt.amount,
        currency: "UGX",
        account_bank: attempt.accountBank,
        account_number: attempt.accountNumber,
        beneficiary_name: attempt.beneficiaryName,
        reference: attempt.reference,
        narration: `WashLink booking ${bookingId}`,
      };
      if (attempt.destinationBranchCode) {
        details.destination_branch_code = attempt.destinationBranchCode;
      }
      transfer = await flutterwaveRequest("transfers", details);
    }
    if (transfer.reference !== attempt.reference
        || Number(transfer.amount) !== attempt.amount
        || transfer.currency !== "UGX") {
      throw new Error("Flutterwave returned a transfer that does not match the payout.");
    }
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(attemptRef);
      if (snapshot.exists && snapshot.get("claim") === claim) {
        transaction.update(attemptRef, {
          state: payoutTransferStatus(transfer.status),
          transferId: String(transfer.id),
          providerStatus: transfer.status,
          claim: FieldValue.delete(),
          updatedAt: Date.now(),
        });
      }
    });
  } catch (error) {
    let existingTransfer = null;
    try {
      existingTransfer = await transferByReference(attempt.reference);
    } catch (lookupError) {
      logger.warn("Could not reconcile a provider payout", {
        bookingId,
        error: lookupError.message,
      });
    }
    if (existingTransfer && existingTransfer.reference === attempt.reference) {
      await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(attemptRef);
        if (snapshot.exists && snapshot.get("claim") === claim) {
          transaction.update(attemptRef, {
            state: payoutTransferStatus(existingTransfer.status),
            transferId: String(existingTransfer.id),
            providerStatus: existingTransfer.status,
            claim: FieldValue.delete(),
            updatedAt: Date.now(),
          });
        }
      });
      return;
    }
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(attemptRef);
      if (snapshot.exists && snapshot.get("claim") === claim) {
        transaction.update(attemptRef, {
          state: "UNKNOWN",
          claim: FieldValue.delete(),
          lastError: "Transfer status could not be confirmed.",
          updatedAt: Date.now(),
        });
      }
    });
    logger.error("Provider payout needs reconciliation", { bookingId, error: error.message });
  }
}

async function processProviderRefund(bookingId) {
  const refundRef = db.collection("refundAttempts").doc(bookingId);
  const claim = randomBytes(12).toString("hex");
  let refund;
  const claimed = await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(refundRef);
    if (!snapshot.exists || snapshot.get("state") !== "REQUESTED") return false;
    refund = snapshot.data();
    transaction.update(refundRef, {
      state: "PROCESSING",
      claim,
      claimedAt: Date.now(),
      updatedAt: Date.now(),
    });
    return true;
  });
  if (!claimed) return;
  try {
    const response = await flutterwaveRequest(
      `transactions/${refund.transactionId}/refund`,
      {
        amount: refund.amount,
        comments: `WashLink provider rejection: booking ${bookingId}`,
      }
    );
    const finalRefundStatuses = [
      "completed-bank-transfer",
      "completed-momo",
      "completed-mpgs",
      "completed-offline",
      "completed-preauth",
    ];
    const refunded = finalRefundStatuses.includes(response.status);
    await db.runTransaction(async (transaction) => {
      const [attemptSnapshot, bookingSnapshot] = await Promise.all([
        transaction.get(refundRef),
        transaction.get(db.collection("bookings").doc(bookingId)),
      ]);
      if (!attemptSnapshot.exists || attemptSnapshot.get("claim") !== claim
          || !bookingSnapshot.exists) return;
      transaction.update(refundRef, {
        state: refunded ? "REFUNDED" : "INITIATED",
        refundId: response.tx_id == null ? null : String(response.tx_id),
        providerStatus: response.status || "unknown",
        claim: FieldValue.delete(),
        updatedAt: Date.now(),
      });
      transaction.update(db.collection("bookings").doc(bookingId), {
        paymentStatus: refunded ? "REFUNDED" : "REFUND_PENDING",
        updatedAt: Date.now(),
      });
    });
  } catch (error) {
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(refundRef);
      if (snapshot.exists && snapshot.get("claim") === claim) {
        transaction.update(refundRef, {
          state: "UNKNOWN",
          claim: FieldValue.delete(),
          lastAttemptAt: Date.now(),
          lastError: "Refund status could not be confirmed.",
          updatedAt: Date.now(),
        });
      }
    });
    logger.error("Provider rejection refund needs reconciliation", {
      bookingId,
      error: error.message,
    });
  }
}

async function reconcileProviderTransfer(data) {
  const reference = data && data.reference;
  const match = typeof reference === "string"
    && reference.match(/^washlink_payout_([A-Za-z0-9]{10,40})$/);
  const transferId = data && String(data.id || "");
  if (!match || !/^\d{1,30}$/.test(transferId)) return false;

  const bookingId = match[1];
  const booking = await db.collection("bookings").doc(bookingId).get();
  if (!booking.exists || typeof booking.get("providerId") !== "string") return false;
  const providerId = booking.get("providerId");
  const attemptRef = payoutAttemptRef(providerId, bookingId);
  const attempt = await attemptRef.get();
  if (!attempt.exists || attempt.get("reference") !== reference) return false;
  const transfer = await flutterwaveRequest(`transfers/${transferId}`);
  if (transfer.reference !== reference
      || Number(transfer.amount) !== attempt.get("amount")
      || transfer.currency !== "UGX") {
    return false;
  }
  await attemptRef.update({
    state: payoutTransferStatus(transfer.status),
    transferId,
    providerStatus: transfer.status,
    claim: FieldValue.delete(),
    updatedAt: Date.now(),
  });
  return true;
}

async function sendPushNotification(uid, title, body, bookingId, audience) {
  const userRef = db.collection("users").doc(uid);
  let user;
  try {
    user = await userRef.get();
  } catch (error) {
    logger.warn("Could not load push recipient", { uid, bookingId, error: error.message });
    return;
  }
  const token = user.exists ? user.get("pushToken") : null;
  if (typeof token !== "string" || token.trim().length === 0) return;
  try {
    await getMessaging().send({
      token,
      data: { title, body, bookingId, audience },
      android: { priority: "high" },
    });
  } catch (error) {
    logger.warn("Could not send WashLink push notification", {
      uid,
      bookingId,
      error: error.message,
    });
    if (error.code === "messaging/registration-token-not-registered"
        || error.code === "messaging/invalid-registration-token") {
      try {
        await userRef.update({ pushToken: FieldValue.delete() });
      } catch (cleanupError) {
        logger.warn("Could not remove stale push token", {
          uid,
          error: cleanupError.message,
        });
      }
    }
  }
}

async function verifiedTransaction(bookingId, transactionId) {
  if (typeof transactionId !== "string" || !/^\d{1,30}$/.test(transactionId)) {
    throw new HttpsError("invalid-argument", "A valid Flutterwave transaction ID is required.");
  }
  const bookingRef = db.collection("bookings").doc(bookingId);
  const bookingSnapshot = await bookingRef.get();
  if (!bookingSnapshot.exists) {
    throw new HttpsError("not-found", "Booking not found.");
  }
  const booking = bookingSnapshot.data();
  if (typeof booking.paymentReference !== "string"
      || !booking.paymentReference.startsWith(`washlink_${bookingId}_`)) {
    throw new HttpsError("failed-precondition", "This booking has no active online payment.");
  }

  const transaction = await flutterwaveRequest(`transactions/${transactionId}/verify`);
  if (String(transaction.id) !== transactionId || transaction.tx_ref !== booking.paymentReference) {
    throw new HttpsError("failed-precondition", "The transaction does not match this booking.");
  }

  if (isVerifiedFlutterwavePayment(transaction, booking.paymentReference, booking.paymentAmount)) {
    await db.runTransaction(async (firestoreTransaction) => {
      const currentSnapshot = await firestoreTransaction.get(bookingRef);
      if (!currentSnapshot.exists) {
        throw new HttpsError("not-found", "Booking not found.");
      }
      const current = currentSnapshot.data();
      if (current.status !== "BOOKED") {
        throw new HttpsError("failed-precondition", "Cancelled bookings cannot be paid.");
      }
      if (current.paymentReference !== booking.paymentReference
          || Number(current.total) !== Number(booking.paymentAmount)) {
        throw new HttpsError("failed-precondition", "The booking payment details have changed.");
      }
      if (current.paymentStatus !== "PAID") {
        firestoreTransaction.update(bookingRef, {
          paymentStatus: "PAID",
          paymentTransactionId: transactionId,
          paymentVerifiedAt: FieldValue.serverTimestamp(),
          updatedAt: Date.now(),
        });
      }
    });
    return "PAID";
  }

  if (transaction.status === "failed") {
    await db.runTransaction(async (firestoreTransaction) => {
      const currentSnapshot = await firestoreTransaction.get(bookingRef);
      if (currentSnapshot.exists
          && currentSnapshot.get("paymentReference") === booking.paymentReference
          && currentSnapshot.get("paymentStatus") !== "PAID") {
        firestoreTransaction.update(bookingRef, {
          paymentStatus: "FAILED",
          paymentTransactionId: transactionId,
          updatedAt: Date.now(),
        });
      }
    });
    return "FAILED";
  }
  return "PENDING";
}

function inputText(value, field, maxLength) {
  if (typeof value !== "string" || value.trim().length === 0
      || value.length > maxLength) {
    throw new HttpsError("invalid-argument", `${field} is invalid.`);
  }
  return value.trim();
}

exports.createBooking = onCall({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in before creating a booking.");
  }
  const data = request.data || {};
  const providerId = inputText(data.providerId, "Provider", 40);
  if (!/^[A-Za-z0-9]{10,40}$/.test(providerId)) {
    throw new HttpsError("invalid-argument", "The booking provider is invalid.");
  }
  const serviceType = data.serviceType;
  const bookingInput = {
    providerId,
    serviceType,
    serviceName: inputText(data.serviceName, "Service", 120),
    itemCount: data.itemCount,
    subtotal: data.subtotal,
    pickupFee: data.pickupFee,
    deliveryFee: data.deliveryFee,
    serviceFee: data.serviceFee,
    tax: data.tax,
    total: data.total,
  };
  const address = inputText(data.address, "Address", 500);
  const scheduledDateTime = inputText(data.scheduledDateTime, "Schedule", 200);
  const specialInstructions = typeof data.specialInstructions === "string"
    ? data.specialInstructions.slice(0, 500) : "";
  const method = paymentMethodInfo(data.paymentMethod);

  const [customer, providerUser, providerSnapshot] = await Promise.all([
    db.collection("users").doc(request.auth.uid).get(),
    db.collection("users").doc(providerId).get(),
    db.collection("providers").doc(providerId).get(),
  ]);
  if (!customer.exists || !hasActiveRole(customer.data(), "customer")
      || !providerUser.exists || !hasActiveRole(providerUser.data(), "provider")
      || !providerSnapshot.exists) {
    throw new HttpsError("failed-precondition", "The booking customer or provider is not valid.");
  }
  if (providerSnapshot.get("isApproved") === false) {
    throw new HttpsError("failed-precondition", "This provider is awaiting administrator approval.");
  }
  let quote;
  try {
    quote = quoteBooking(bookingInput, providerSnapshot.data());
  } catch (error) {
    throw new HttpsError("failed-precondition", error.message);
  }

  const provider = providerSnapshot.data();
  const authUser = await getAuth().getUser(request.auth.uid);
  const bookingId = db.collection("bookings").doc().id;
  const timestamp = Date.now();
  const bookingRef = db.collection("bookings").doc(bookingId);
  const booking = {
    id: bookingId,
    customerId: request.auth.uid,
    customerName: authUser.displayName || customer.get("name") || "Customer",
    customerPhone: customer.get("phone") || "",
    providerId,
    providerName: provider.businessName || "Laundry provider",
    serviceType,
    serviceName: bookingInput.serviceName,
    status: "BOOKED",
    address: serviceType === "dropoff"
      ? (provider.address || address) : address,
    scheduledDateTime,
    itemCount: bookingInput.itemCount,
    estimatedWeight: `${quote.kilograms} kg`,
    specialInstructions,
    subtotal: quote.subtotal,
    tax: quote.tax,
    total: quote.total,
    paymentMethod: method.label,
    paymentStatus: "PENDING",
    pickupFee: quote.pickupFee,
    deliveryFee: quote.deliveryFee,
    serviceFee: quote.serviceFee,
    createdAt: timestamp,
    updatedAt: timestamp,
  };
  const notificationRef = db.collection("users").doc(request.auth.uid)
    .collection("notifications").doc();
  const providerNotificationRef = db.collection("users").doc(providerId)
    .collection("notifications").doc();
  const notification = {
    bookingId,
    customerId: request.auth.uid,
    title: "Booked",
    body: `Order #${bookingId} with ${booking.providerName} is now Booked.`,
    createdAt: timestamp,
  };
  const providerNotification = {
    bookingId,
    providerId,
    customerId: request.auth.uid,
    title: "New booking request",
    body: `${booking.customerName} booked ${booking.serviceName}.`,
    createdAt: timestamp,
  };
  const batch = db.batch();
  batch.set(bookingRef, booking);
  batch.set(notificationRef, notification);
  batch.set(providerNotificationRef, providerNotification);
  await batch.commit();
  await sendPushNotification(providerId, "New booking request",
    `A customer booked ${booking.serviceName}.`, bookingId, "provider");
  return { bookingId };
});

exports.getUgandaPayoutBanks = onCall(
  { region: REGION, secrets: [FLUTTERWAVE_SECRET_KEY] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in to configure payouts.");
    }
    const user = await db.collection("users").doc(request.auth.uid).get();
    if (!user.exists || !hasActiveRole(user.data(), "provider")) {
      throw new HttpsError("permission-denied", "Only providers can configure payouts.");
    }
    const banks = await flutterwaveRequest("banks/UG");
    if (!Array.isArray(banks)) {
      throw new HttpsError("unavailable", "Uganda bank options are unavailable.");
    }
    return banks.filter((bank) => bank && typeof bank.code === "string")
      .map((bank) => ({
        id: String(bank.id),
        code: bank.code,
        name: bank.name,
        hasBranches: bank.has_branches === true,
      }));
  }
);

exports.getUgandaPayoutBranches = onCall(
  { region: REGION, secrets: [FLUTTERWAVE_SECRET_KEY] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in to configure payouts.");
    }
    const user = await db.collection("users").doc(request.auth.uid).get();
    if (!user.exists || !hasActiveRole(user.data(), "provider")) {
      throw new HttpsError("permission-denied", "Only providers can configure payouts.");
    }
    const bankId = inputText(request.data && request.data.bankId, "Bank", 30);
    if (!/^\d{1,20}$/.test(bankId)) {
      throw new HttpsError("invalid-argument", "The selected bank is invalid.");
    }
    const branches = await flutterwaveRequest(`banks/${bankId}/branches`);
    if (!Array.isArray(branches)) {
      throw new HttpsError("unavailable", "Bank branch options are unavailable.");
    }
    return branches.filter((branch) => branch && typeof branch.code === "string")
      .map((branch) => ({ code: branch.code, name: branch.name }));
  }
);

exports.saveProviderPayoutProfile = onCall(
  { region: REGION, secrets: [FLUTTERWAVE_SECRET_KEY] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in to configure payouts.");
    }
    const uid = request.auth.uid;
    const user = await db.collection("users").doc(uid).get();
    if (!user.exists || !hasActiveRole(user.data(), "provider")) {
      throw new HttpsError("permission-denied", "Only providers can configure payouts.");
    }
    const data = request.data || {};
    const destinationType = data.destinationType;
    const beneficiaryName = inputText(data.beneficiaryName, "Beneficiary name", 100);
    const accountNumber = inputText(data.accountNumber, "Account number", 32);
    let accountBank;
    let destinationBranchCode;
    if (destinationType === "bank") {
      accountBank = inputText(data.accountBank, "Bank", 30);
      destinationBranchCode = data.destinationBranchCode == null
        ? null : inputText(data.destinationBranchCode, "Bank branch", 30);
      const banks = await flutterwaveRequest("banks/UG");
      if (!Array.isArray(banks)) {
        throw new HttpsError("unavailable", "Uganda bank options are unavailable.");
      }
      const bank = banks.find((item) => item && item.code === accountBank);
      if (!bank) {
        throw new HttpsError("invalid-argument", "Choose a valid Uganda bank.");
      }
      if (bank.has_branches === true) {
        if (!destinationBranchCode) {
          throw new HttpsError("invalid-argument", "Choose a bank branch.");
        }
        const branches = await flutterwaveRequest(`banks/${bank.id}/branches`);
        if (!Array.isArray(branches)
            || !branches.some((item) => item && item.code === destinationBranchCode)) {
          throw new HttpsError("invalid-argument", "Choose a valid bank branch.");
        }
      } else if (destinationBranchCode) {
        throw new HttpsError("invalid-argument", "This bank does not use branch codes.");
      }
    } else if (destinationType === "airtel" || destinationType === "mtn") {
      if (!/^2567\d{8}$/.test(accountNumber)) {
        throw new HttpsError(
          "invalid-argument",
          "Enter the mobile money number in Uganda format, for example 2567XXXXXXXX."
        );
      }
      accountBank = destinationType === "airtel" ? "AIRTEL" : "MTN";
    } else {
      throw new HttpsError("invalid-argument", "Choose a bank, Airtel Money, or MTN Mobile Money.");
    }
    const profile = {
      destinationType,
      beneficiaryName,
      accountNumber,
      accountBank,
      destinationBranchCode: destinationBranchCode || null,
      updatedAt: FieldValue.serverTimestamp(),
    };
    if (!validPayoutProfile(profile)) {
      throw new HttpsError("invalid-argument", "The payout details are invalid.");
    }
    const pending = await db.collection("users").doc(uid)
      .collection("payoutAttempts").where("state", "in", ["REQUESTED", "PROCESSING", "UNKNOWN"])
      .limit(1).get();
    if (!pending.empty) {
      throw new HttpsError(
        "failed-precondition",
        "A payout is being processed. Try changing payout details after it is resolved."
      );
    }
    await db.collection("users").doc(uid).collection("payoutProfile").doc("default").set(profile);
    return { saved: true };
  }
);

exports.updateBookingStatus = onCall({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in as a provider to update a booking.");
  }
  const bookingId = requireBookingId(request.data && request.data.bookingId);
  const nextStatus = request.data && request.data.status;
  const allowedStatuses = [
    "ACCEPTED", "PICKED_UP", "WASHING", "DRYING",
    "READY", "OUT_FOR_DELIVERY", "DELIVERED", "REJECTED",
  ];
  if (!allowedStatuses.includes(nextStatus)) {
    throw new HttpsError("invalid-argument", "The requested booking status is invalid.");
  }
  const userRef = db.collection("users").doc(request.auth.uid);
  const bookingRef = db.collection("bookings").doc(bookingId);
  const notificationId = db.collection("users").doc().id;
  const timestamp = Date.now();
  const payoutProfileRef = db.collection("users").doc(request.auth.uid)
    .collection("payoutProfile").doc("default");
  const providerProfileRef = db.collection("providers").doc(request.auth.uid);
  const payoutRef = payoutAttemptRef(request.auth.uid, bookingId);
  const refundRef = db.collection("refundAttempts").doc(bookingId);
  let pushDetails;
  let payoutRequired = false;
  let refundRequired = false;

  await db.runTransaction(async (transaction) => {
    const [userSnapshot, bookingSnapshot, providerProfileSnapshot,
      payoutProfileSnapshot, payoutSnapshot, refundSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(bookingRef),
      transaction.get(providerProfileRef),
      transaction.get(payoutProfileRef),
      transaction.get(payoutRef),
      transaction.get(refundRef),
    ]);
    if (!userSnapshot.exists || !hasActiveRole(userSnapshot.data(), "provider")
        || !providerProfileSnapshot.exists) {
      throw new HttpsError("permission-denied", "Only providers can update booking status.");
    }
    if (!bookingSnapshot.exists) {
      throw new HttpsError("not-found", "Booking not found.");
    }
    if (providerProfileSnapshot.get("isApproved") === false) {
      throw new HttpsError("permission-denied", "This provider is awaiting administrator approval.");
    }
    const booking = bookingSnapshot.data();
    if (booking.providerId !== request.auth.uid) {
      throw new HttpsError("permission-denied", "You can only update your own bookings.");
    }
    if (!isValidStatusTransition(
      booking.status, nextStatus, booking.paymentProvider, booking.paymentStatus
    )) {
      throw new HttpsError(
        "failed-precondition",
        "This booking cannot move to the requested status."
      );
    }
    const payoutBooking = { ...booking, status: nextStatus };
    payoutRequired = AUTOMATED_PAYOUTS_ENABLED
      && isEligibleForProviderPayout(payoutBooking);
    if (payoutRequired) {
      if (!payoutProfileSnapshot.exists || !validPayoutProfile(payoutProfileSnapshot.data())) {
        throw new HttpsError(
          "failed-precondition",
          "Add valid payout details before marking this paid online order delivered."
        );
      }
      if (payoutSnapshot.exists) {
        throw new HttpsError("failed-precondition", "A payout attempt already exists for this booking.");
      }
    }
    refundRequired = AUTOMATED_PAYOUTS_ENABLED
      && isEligibleForProviderRejectionRefund(booking, nextStatus);
    if (refundRequired && refundSnapshot.exists) {
      throw new HttpsError("failed-precondition", "A refund attempt already exists for this booking.");
    }
    const customerId = booking.customerId;
    if (typeof customerId !== "string" || customerId.length === 0) {
      throw new HttpsError("failed-precondition", "Booking customer is missing.");
    }
    const customerNotificationRef = db.collection("users").doc(customerId)
      .collection("notifications").doc(notificationId);
    transaction.update(bookingRef, {
      status: nextStatus,
      ...(refundRequired ? { paymentStatus: "REFUND_PENDING" } : {}),
      updatedAt: timestamp,
    });
    if (payoutRequired) {
      const profile = payoutProfileSnapshot.data();
      transaction.set(payoutRef, {
        bookingId,
        providerId: request.auth.uid,
        amount: booking.subtotal,
        currency: "UGX",
        accountBank: profile.accountBank,
        accountNumber: profile.accountNumber,
        beneficiaryName: profile.beneficiaryName,
        destinationBranchCode: profile.destinationBranchCode || null,
        reference: `washlink_payout_${bookingId}`,
        state: "REQUESTED",
        createdAt: timestamp,
        updatedAt: timestamp,
      });
    }
    if (refundRequired) {
      transaction.set(refundRef, {
        bookingId,
        providerId: request.auth.uid,
        customerId,
        transactionId: booking.paymentTransactionId,
        amount: booking.paymentAmount,
        currency: "UGX",
        state: "REQUESTED",
        createdAt: timestamp,
        updatedAt: timestamp,
      });
    }
    transaction.set(customerNotificationRef, {
      bookingId,
      customerId,
      title: nextStatus.replace(/_/g, " ").toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase()),
      body: `Order #${bookingId} with ${booking.providerName || "your laundry provider"} is now ${
        nextStatus.replace(/_/g, " ").toLowerCase()
      }.`,
      createdAt: timestamp,
    });
    pushDetails = {
      customerId,
      providerName: booking.providerName || "your laundry provider",
    };
  });
  await sendPushNotification(
    pushDetails.customerId,
    nextStatus.replace(/_/g, " ").toLowerCase()
      .replace(/\b\w/g, (character) => character.toUpperCase()),
    `Order #${bookingId} with ${pushDetails.providerName} is now ${
      nextStatus.replace(/_/g, " ").toLowerCase()
    }.`,
    bookingId,
    "customer"
  );
  return {
    status: nextStatus,
    payoutInitiated: payoutRequired,
    refundInitiated: refundRequired,
  };
});

exports.submitBookingReview = onCall({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in to submit a review.");
  }
  const bookingId = requireBookingId(request.data && request.data.bookingId);
  const rating = request.data && request.data.rating;
  const reviewText = request.data && request.data.reviewText;
  if (!validReview(rating, reviewText)) {
    throw new HttpsError("invalid-argument", "Choose a rating from 1 to 5 and enter a review of at most 1,000 characters.");
  }
  const bookingRef = db.collection("bookings").doc(bookingId);
  const userRef = db.collection("users").doc(request.auth.uid);
  await db.runTransaction(async (transaction) => {
    const bookingSnapshot = await transaction.get(bookingRef);
    if (!bookingSnapshot.exists) {
      throw new HttpsError("not-found", "Booking not found.");
    }
    const booking = bookingSnapshot.data();
    if (!canReviewBooking(booking, request.auth.uid)) {
      throw new HttpsError("failed-precondition", "Only the customer can review a delivered booking.");
    }
    const providerRef = db.collection("providers").doc(booking.providerId);
    const reviewRef = providerRef.collection("reviews").doc(bookingId);
    const [userSnapshot, providerSnapshot, reviewSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(providerRef),
      transaction.get(reviewRef),
    ]);
    if (!userSnapshot.exists || !hasActiveRole(userSnapshot.data(), "customer")
        || !providerSnapshot.exists) {
      throw new HttpsError("permission-denied", "The customer or provider account is unavailable.");
    }
    if (reviewSnapshot.exists) {
      throw new HttpsError("already-exists", "You have already reviewed this booking.");
    }
    const storedCount = providerSnapshot.get("reviewCount");
    const storedRating = providerSnapshot.get("rating");
    const reviewCount = Number.isInteger(storedCount) && storedCount >= 0 ? storedCount : 0;
    const currentRating = typeof storedRating === "number" && Number.isFinite(storedRating)
      ? storedRating : 0;
    const average = updatedRatingAverage(currentRating, reviewCount, rating);
    transaction.create(reviewRef, {
      bookingId,
      customerId: request.auth.uid,
      customerName: userSnapshot.get("name") || "WashLink customer",
      rating,
      reviewText: reviewText.trim(),
      createdAt: Date.now(),
    });
    transaction.update(bookingRef, { reviewSubmitted: true });
    transaction.update(providerRef, {
      rating: average,
      reviewCount: reviewCount + 1,
    });
  });
  return { success: true };
});

exports.assignBookingRider = onCall({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in as a provider to assign a rider.");
  }
  const bookingId = requireBookingId(request.data && request.data.bookingId);
  const riderId = inputText(request.data && request.data.riderId, "Rider", 40);
  if (!/^[A-Za-z0-9]{10,40}$/.test(riderId)) {
    throw new HttpsError("invalid-argument", "The selected rider is invalid.");
  }
  const uid = request.auth.uid;
  const userRef = db.collection("users").doc(uid);
  const providerRef = db.collection("providers").doc(uid);
  const riderRef = providerRef.collection("riders").doc(riderId);
  const bookingRef = db.collection("bookings").doc(bookingId);
  await db.runTransaction(async (transaction) => {
    const [userSnapshot, providerSnapshot, riderSnapshot, bookingSnapshot] =
      await Promise.all([
        transaction.get(userRef),
        transaction.get(providerRef),
        transaction.get(riderRef),
        transaction.get(bookingRef),
      ]);
    if (!userSnapshot.exists || !hasActiveRole(userSnapshot.data(), "provider")
        || !providerSnapshot.exists) {
      throw new HttpsError("permission-denied", "Only an active provider can assign riders.");
    }
    if (providerSnapshot.get("isApproved") === false) {
      throw new HttpsError("permission-denied", "This provider is awaiting administrator approval.");
    }
    if (!bookingSnapshot.exists) {
      throw new HttpsError("not-found", "Booking not found.");
    }
    const booking = bookingSnapshot.data();
    const rider = riderSnapshot.exists ? riderSnapshot.data() : null;
    if (!canAssignRider(booking, uid, rider)) {
      throw new HttpsError("failed-precondition",
        "Choose an active rider for one of your in-progress bookings.");
    }
    transaction.update(bookingRef, {
      assignedRiderId: riderId,
      assignedRiderName: rider.name.trim(),
      assignedRiderPhone: rider.phone.trim(),
      updatedAt: Date.now(),
    });
  });
  return { success: true };
});

exports.initiateProviderPayout = onDocumentCreated(
  {
    document: "users/{providerId}/payoutAttempts/{bookingId}",
    region: REGION,
    secrets: [FLUTTERWAVE_SECRET_KEY],
  },
  async (event) => {
    const { providerId, bookingId } = event.params;
    await processProviderPayout(providerId, bookingId);
  }
);

exports.initiateProviderRejectionRefund = onDocumentCreated(
  {
    document: "refundAttempts/{bookingId}",
    region: REGION,
    secrets: [FLUTTERWAVE_SECRET_KEY],
  },
  async (event) => {
    await processProviderRefund(event.params.bookingId);
  }
);

exports.processPendingProviderPayouts = onSchedule(
  { schedule: "every 15 minutes", region: REGION, secrets: [FLUTTERWAVE_SECRET_KEY] },
  async () => {
    const attempts = await db.collectionGroup("payoutAttempts")
      .where("state", "in", ["REQUESTED", "PROCESSING", "UNKNOWN"])
      .limit(100)
      .get();
    for (const attempt of attempts.docs) {
      const data = attempt.data();
      if (typeof data.providerId === "string" && typeof data.bookingId === "string") {
        await processProviderPayout(data.providerId, data.bookingId);
      }
    }
  }
);

exports.flutterwaveTransferWebhook = onRequest(
  { region: REGION, secrets: [FLUTTERWAVE_SECRET_KEY, FLUTTERWAVE_WEBHOOK_HASH] },
  async (request, response) => {
    if (request.method !== "POST"
        || !validWebhookHash(request.get("verif-hash"), FLUTTERWAVE_WEBHOOK_HASH.value())) {
      response.sendStatus(401);
      return;
    }
    try {
      const data = request.body && request.body.data;
      response.sendStatus(await reconcileProviderTransfer(data) ? 200 : 400);
    } catch (error) {
      logger.error("Flutterwave transfer webhook could not be reconciled", {
        error: error.message,
      });
      response.sendStatus(500);
    }
  }
);

exports.cancelBooking = onCall({ region: REGION }, async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in to cancel a booking.");
  }
  const bookingId = requireBookingId(request.data && request.data.bookingId);
  const userRef = db.collection("users").doc(request.auth.uid);
  const bookingRef = db.collection("bookings").doc(bookingId);
  const notificationId = db.collection("users").doc().id;
  const timestamp = Date.now();
  let providerId;

  await db.runTransaction(async (transaction) => {
    const [userSnapshot, bookingSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(bookingRef),
    ]);
    if (!userSnapshot.exists || !hasActiveRole(userSnapshot.data(), "customer")) {
      throw new HttpsError("permission-denied", "Only customers can cancel their bookings.");
    }
    if (!bookingSnapshot.exists) {
      throw new HttpsError("not-found", "Booking not found.");
    }
    const booking = bookingSnapshot.data();
    if (booking.customerId !== request.auth.uid) {
      throw new HttpsError("permission-denied", "You can only cancel your own bookings.");
    }
    if (!canCancelBooking(booking)) {
      throw new HttpsError(
        "failed-precondition",
        "This booking can no longer be cancelled in the app. Contact support if payment was taken."
      );
    }
    if (typeof booking.providerId !== "string" || booking.providerId.length === 0) {
      throw new HttpsError("failed-precondition", "Booking provider is missing.");
    }
    providerId = booking.providerId;
    transaction.update(bookingRef, {
      status: "CANCELLED",
      updatedAt: timestamp,
    });
    transaction.set(userRef.collection("notifications").doc(notificationId), {
      bookingId,
      customerId: request.auth.uid,
      title: "Booking Cancelled",
      body: `Order #${bookingId} has been cancelled.`,
      createdAt: timestamp,
    });
    transaction.set(db.collection("users").doc(providerId)
      .collection("notifications").doc(notificationId), {
      bookingId,
      providerId,
      customerId: request.auth.uid,
      title: "Booking Cancelled",
      body: `Order #${bookingId} was cancelled by the customer.`,
      createdAt: timestamp,
    });
  });
  await sendPushNotification(providerId, "Booking cancelled",
    `Order #${bookingId} was cancelled by the customer.`, bookingId, "provider");
  return { status: "CANCELLED" };
});

exports.initializeFlutterwaveCheckout = onCall(
  { region: REGION, secrets: [FLUTTERWAVE_SECRET_KEY] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in to start a payment.");
    }
    const bookingId = requireBookingId(request.data && request.data.bookingId);
    const method = paymentMethodInfo(request.data && request.data.paymentMethod);
    const bookingRef = db.collection("bookings").doc(bookingId);
    const generatedReference = `washlink_${bookingId}_${randomBytes(12).toString("hex")}`;

    let paymentReference;
    let amount;
    let customerName = "WashLink customer";
    let customerEmail;
    try {
      const user = await getAuth().getUser(request.auth.uid);
      customerEmail = user.email;
      customerName = user.displayName || customerName;
    } catch (error) {
      logger.warn("Could not retrieve authenticated customer profile", error);
    }
    if (!method.cashOnDelivery && !customerEmail) {
      throw new HttpsError("failed-precondition", "Add an email to your WashLink account before paying.");
    }

    await db.runTransaction(async (transaction) => {
      const bookingSnapshot = await transaction.get(bookingRef);
      if (!bookingSnapshot.exists) {
        throw new HttpsError("not-found", "Booking not found.");
      }
      const booking = bookingSnapshot.data();
      if (booking.customerId !== request.auth.uid) {
        throw new HttpsError("permission-denied", "You can only pay for your own booking.");
      }
      if (booking.status !== "BOOKED") {
        throw new HttpsError("failed-precondition", "Only booked orders can be paid.");
      }
      if (booking.paymentStatus === "PAID") {
        throw new HttpsError("failed-precondition", "This booking is already paid.");
      }

      const providerId = booking.providerId;
      if (typeof providerId !== "string" || !/^[A-Za-z0-9]{10,40}$/.test(providerId)) {
        throw new HttpsError("failed-precondition", "The booking provider is invalid.");
      }
      const [customerRole, providerRole, providerSnapshot] = await Promise.all([
        transaction.get(db.collection("users").doc(request.auth.uid)),
        transaction.get(db.collection("users").doc(providerId)),
        transaction.get(db.collection("providers").doc(providerId)),
      ]);
      if (!customerRole.exists || !hasActiveRole(customerRole.data(), "customer")
          || !providerRole.exists || !hasActiveRole(providerRole.data(), "provider")
          || !providerSnapshot.exists) {
        throw new HttpsError("failed-precondition", "The booking customer or provider is not valid.");
      }
      if (providerSnapshot.get("isApproved") === false) {
        throw new HttpsError("failed-precondition", "This provider is awaiting administrator approval.");
      }

      let quote;
      try {
        quote = quoteBooking(booking, providerSnapshot.data());
      } catch (error) {
        throw new HttpsError("failed-precondition", error.message);
      }
      amount = quote.total;
      if (method.cashOnDelivery && typeof booking.paymentReference === "string"
          && booking.paymentReference.length > 0) {
        throw new HttpsError("failed-precondition",
          "This booking already has an online payment attempt. Continue that payment instead.");
      }
      const existingReference = booking.paymentStatus === "PENDING"
        && typeof booking.paymentReference === "string"
        && booking.paymentReference.startsWith(`washlink_${bookingId}_`)
        ? booking.paymentReference : generatedReference;
      paymentReference = method.cashOnDelivery ? null : existingReference;
      const updates = {
        paymentStatus: "PENDING",
        paymentMethod: method.label,
        paymentProvider: method.cashOnDelivery ? "cash" : "flutterwave",
        paymentAmount: amount,
        paymentCurrency: "UGX",
        updatedAt: Date.now(),
      };
      if (method.cashOnDelivery) {
        updates.paymentReference = FieldValue.delete();
      } else {
        updates.paymentReference = paymentReference;
      }
      transaction.update(bookingRef, updates);
    });

    if (method.cashOnDelivery) {
      return { cashOnDelivery: true };
    }

    const projectId = process.env.GCLOUD_PROJECT;
    const redirectUrl = `https://${REGION}-${projectId}.cloudfunctions.net/flutterwaveReturn`;
    const checkout = await flutterwaveRequest("payments", {
      tx_ref: paymentReference,
      amount,
      currency: "UGX",
      redirect_url: redirectUrl,
      payment_options: method.option,
      customer: { email: customerEmail, name: customerName },
      meta: { booking_id: bookingId, customer_id: request.auth.uid },
      customizations: {
        title: "WashLink laundry booking",
        description: `Payment for booking ${bookingId}`,
      },
    });
    if (typeof checkout.link !== "string" || !checkout.link.startsWith("https://")) {
      throw new HttpsError("unavailable", "The payment provider returned an invalid checkout link.");
    }
    return { checkoutUrl: checkout.link };
  }
);

exports.verifyFlutterwavePayment = onCall(
  { region: REGION, secrets: [FLUTTERWAVE_SECRET_KEY] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Sign in to verify a payment.");
    }
    const user = await db.collection("users").doc(request.auth.uid).get();
    if (!user.exists || !hasActiveRole(user.data(), "customer")) {
      throw new HttpsError("permission-denied", "This customer account is not active.");
    }
    const bookingId = requireBookingId(request.data && request.data.bookingId);
    const booking = await db.collection("bookings").doc(bookingId).get();
    if (!booking.exists || booking.get("customerId") !== request.auth.uid) {
      throw new HttpsError("permission-denied", "You can only verify your own booking.");
    }
    const paymentStatus = await verifiedTransaction(bookingId, request.data.transactionId);
    return { paymentStatus };
  }
);

exports.flutterwaveReturn = onRequest({ region: REGION }, (request, response) => {
  const reference = request.query.tx_ref;
  const bookingId = referenceBookingId(reference);
  if (!bookingId) {
    response.status(400).send("Invalid WashLink payment reference.");
    return;
  }
  const query = new URLSearchParams({
    bookingId,
    status: typeof request.query.status === "string" ? request.query.status : "unknown",
  });
  const transactionId = request.query.transaction_id;
  if (typeof transactionId === "string" && /^\d{1,30}$/.test(transactionId)) {
    query.set("transaction_id", transactionId);
  }
  const appUrl = `washlink://payment-return?${query.toString()}`;
  const escapedUrl = appUrl.replace(/&/g, "&amp;").replace(/"/g, "&quot;");
  response.status(200).type("html").send(
    `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=${escapedUrl}">`
    + `<title>Return to WashLink</title></head><body><p>Returning to WashLink…</p>`
    + `<a href="${escapedUrl}">Tap here if the app does not open</a>`
    + `<script>window.location.replace(${JSON.stringify(appUrl)});</script></body></html>`
  );
});

exports.flutterwaveWebhook = onRequest(
  { region: REGION, secrets: [FLUTTERWAVE_SECRET_KEY, FLUTTERWAVE_WEBHOOK_HASH] },
  async (request, response) => {
    if (request.method !== "POST"
        || !validWebhookHash(request.get("verif-hash"), FLUTTERWAVE_WEBHOOK_HASH.value())) {
      response.sendStatus(401);
      return;
    }
    const data = request.body && request.body.data;
    if (data && typeof data.reference === "string"
        && data.reference.startsWith("washlink_payout_")) {
      try {
        response.sendStatus(await reconcileProviderTransfer(data) ? 200 : 400);
      } catch (error) {
        logger.error("Flutterwave transfer webhook could not be reconciled", {
          error: error.message,
        });
        response.sendStatus(500);
      }
      return;
    }
    const bookingId = referenceBookingId(data && data.tx_ref);
    const transactionId = data && String(data.id || "");
    if (!bookingId || !/^\d{1,30}$/.test(transactionId)) {
      response.sendStatus(400);
      return;
    }
    try {
      await verifiedTransaction(bookingId, transactionId);
      response.sendStatus(200);
    } catch (error) {
      logger.error("Flutterwave webhook verification failed", error);
      response.sendStatus(error instanceof HttpsError && error.code === "not-found" ? 404 : 400);
    }
  }
);
