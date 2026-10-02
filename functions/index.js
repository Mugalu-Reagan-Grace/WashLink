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
const {
  quoteBooking,
  isVerifiedFlutterwavePayment,
  isValidStatusTransition,
  canCancelBooking,
} = require("./domain");

initializeApp();
const db = getFirestore();
const FLUTTERWAVE_SECRET_KEY = defineSecret("FLUTTERWAVE_SECRET_KEY");
const FLUTTERWAVE_WEBHOOK_HASH = defineSecret("FLUTTERWAVE_WEBHOOK_HASH");
const REGION = "us-central1";

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
  if (!customer.exists || customer.get("role") !== "customer"
      || !providerUser.exists || providerUser.get("role") !== "provider"
      || !providerSnapshot.exists) {
    throw new HttpsError("failed-precondition", "The booking customer or provider is not valid.");
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
  const notification = {
    bookingId,
    customerId: request.auth.uid,
    title: "Booked",
    body: `Order #${bookingId} with ${booking.providerName} is now Booked.`,
    createdAt: timestamp,
  };
  const batch = db.batch();
  batch.set(bookingRef, booking);
  batch.set(notificationRef, notification);
  await batch.commit();
  await sendPushNotification(providerId, "New booking request",
    `A customer booked ${booking.serviceName}.`, bookingId, "provider");
  return { bookingId };
});

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
  let pushDetails;

  await db.runTransaction(async (transaction) => {
    const [userSnapshot, bookingSnapshot] = await Promise.all([
      transaction.get(userRef),
      transaction.get(bookingRef),
    ]);
    if (!userSnapshot.exists || userSnapshot.get("role") !== "provider") {
      throw new HttpsError("permission-denied", "Only providers can update booking status.");
    }
    if (!bookingSnapshot.exists) {
      throw new HttpsError("not-found", "Booking not found.");
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
    const customerId = booking.customerId;
    if (typeof customerId !== "string" || customerId.length === 0) {
      throw new HttpsError("failed-precondition", "Booking customer is missing.");
    }
    const customerNotificationRef = db.collection("users").doc(customerId)
      .collection("notifications").doc(notificationId);
    transaction.update(bookingRef, {
      status: nextStatus,
      updatedAt: timestamp,
    });
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
  return { status: nextStatus };
});

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
    if (!userSnapshot.exists || userSnapshot.get("role") !== "customer") {
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
      if (!customerRole.exists || customerRole.get("role") !== "customer"
          || !providerRole.exists || providerRole.get("role") !== "provider"
          || !providerSnapshot.exists) {
        throw new HttpsError("failed-precondition", "The booking customer or provider is not valid.");
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
