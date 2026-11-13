"use strict";

const crypto = require("node:crypto");
const express = require("express");
const { applicationDefault, cert, initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { FieldValue, getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const {
  canAssignRider,
  canCancelBooking,
  canReviewBooking,
  hasActiveRole,
  isApprovedProvider,
  isValidStatusTransition,
  isVerifiedPesapalPayment,
  quoteBooking,
  updatedRatingAverage,
  validReview,
} = require("../functions/domain");

const app = express();
app.disable("x-powered-by");
app.use(express.json({ limit: "32kb" }));
app.use(express.urlencoded({ extended: false, limit: "32kb" }));
const webOrigins = (process.env.PUBLIC_WEB_ORIGIN || "")
  .split(",").map((origin) => origin.trim()).filter(Boolean);
app.use((request, response, next) => {
  const origin = request.get("Origin");
  if (origin && !webOrigins.includes(origin)) {
    return next(new ApiError(403, "This web app is not allowed to use the WashLink API."));
  }
  if (origin) {
    response.set("Access-Control-Allow-Origin", origin);
    response.set("Vary", "Origin");
    response.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
    response.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  }
  if (request.method === "OPTIONS") return response.sendStatus(204);
  return next();
});

const rawServiceAccount = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
const firebaseApp = initializeApp(rawServiceAccount
  ? { credential: cert(JSON.parse(rawServiceAccount)) }
  : { credential: applicationDefault() });
const db = getFirestore(firebaseApp);
const auth = getAuth(firebaseApp);

class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

function requireText(value, field, maxLength) {
  if (typeof value !== "string" || value.trim().length === 0 || value.length > maxLength) {
    throw new ApiError(400, `${field} is invalid.`);
  }
  return value.trim();
}

function requireBookingId(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9]{10,40}$/.test(value)) {
    throw new ApiError(400, "A valid booking ID is required.");
  }
  return value;
}

function paymentMethodInfo(method) {
  if (method === "cash") return { label: "Cash", cashOnDelivery: true };
  if (["card", "airtel", "mtn"].includes(method)) {
    return { label: "Pesapal online payment", cashOnDelivery: false };
  }
  throw new ApiError(400, "Choose card, Uganda mobile money, or cash on delivery.");
}

function pesapalBaseUrl() {
  if (process.env.PESAPAL_ENVIRONMENT === "live") return "https://pay.pesapal.com/v3";
  if (process.env.PESAPAL_ENVIRONMENT === "sandbox") {
    return "https://cybqa.pesapal.com/pesapalv3";
  }
  throw new ApiError(503, "Pesapal environment must be set to sandbox or live.");
}

async function pesapalToken() {
  if (!process.env.PESAPAL_CONSUMER_KEY || !process.env.PESAPAL_CONSUMER_SECRET) {
    throw new ApiError(503, "Pesapal is not configured yet.");
  }
  const response = await fetch(`${pesapalBaseUrl()}/api/Auth/RequestToken`, {
    method: "POST",
    headers: { Accept: "application/json", "Content-Type": "application/json" },
    body: JSON.stringify({
      consumer_key: process.env.PESAPAL_CONSUMER_KEY,
      consumer_secret: process.env.PESAPAL_CONSUMER_SECRET,
    }),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || typeof payload.token !== "string" || !payload.token) {
    console.error("Pesapal authentication failed", { httpStatus: response.status });
    throw new ApiError(502, "Pesapal authentication failed.");
  }
  return payload.token;
}

async function pesapalRequest(path, token, body) {
  const response = await fetch(`${pesapalBaseUrl()}/${path}`, {
    method: body ? "POST" : "GET",
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || (payload.status != null && String(payload.status) !== "200")
      || payload.error) {
    console.error("Pesapal API request failed", {
      path,
      httpStatus: response.status,
      providerMessage: payload.message || payload.error?.message || "No response message",
    });
    throw new ApiError(502, "Pesapal could not process the payment request.");
  }
  return payload;
}

async function requireCustomer(request, response, next) {
  const header = request.get("authorization") || "";
  const match = header.match(/^Bearer (.+)$/i);
  if (!match) return next(new ApiError(401, "Sign in before using the payment service."));

  let decoded;
  try {
    decoded = await auth.verifyIdToken(match[1]);
  } catch (_error) {
    return next(new ApiError(401, "Your sign-in has expired. Sign in again."));
  }

  try {
    const userSnapshot = await db.collection("users").doc(decoded.uid).get();
    if (!userSnapshot.exists || !hasActiveRole(userSnapshot.data(), "customer")) {
      throw new ApiError(403, "This customer account is not active.");
    }
    request.user = { uid: decoded.uid, auth: decoded };
    request.customerProfile = userSnapshot.data();
    return next();
  } catch (error) {
    return next(error);
  }
}

async function requireUserRole(request, role) {
  const header = request.get("authorization") || "";
  const match = header.match(/^Bearer (.+)$/i);
  if (!match) throw new ApiError(401, "Sign in before using the WashLink service.");
  let decoded;
  try {
    decoded = await auth.verifyIdToken(match[1]);
  } catch (_error) {
    throw new ApiError(401, "Your sign-in has expired. Sign in again.");
  }
  const user = await db.collection("users").doc(decoded.uid).get();
  if (!user.exists || !hasActiveRole(user.data(), role)) {
    throw new ApiError(403, `Only active ${role}s can use this service.`);
  }
  return decoded.uid;
}

async function reconcilePesapalPayment(reference, trackingId, providerTransaction) {
  if (typeof reference !== "string" || !/^wl_[a-f0-9]{32}$/.test(reference)
      || typeof trackingId !== "string" || !/^[A-Za-z0-9-]{10,100}$/.test(trackingId)
      || !providerTransaction || providerTransaction.merchant_reference !== reference) {
    throw new ApiError(400, "Pesapal payment identifiers are invalid.");
  }
  const matches = await db.collection("bookings")
    .where("paymentReference", "==", reference).limit(1).get();
  if (matches.empty) throw new ApiError(404, "No booking matches this Pesapal payment.");
  const bookingRef = matches.docs[0].ref;
  const booking = matches.docs[0].data();
  if (booking.paymentProvider !== "pesapal"
      || (booking.paymentTrackingId && booking.paymentTrackingId !== trackingId)) {
    throw new ApiError(409, "The Pesapal transaction does not match the booking.");
  }

  const statusCode = Number(providerTransaction.status_code);
  const isPaid = isVerifiedPesapalPayment(providerTransaction, reference, booking.paymentAmount);
  if (statusCode === 1 && !isPaid) {
    throw new ApiError(409, "Pesapal payment amount or currency does not match.");
  }
  const nextStatus = isPaid ? "PAID"
    : statusCode === 2 || statusCode === 0 ? "FAILED"
      : statusCode === 3 ? "REVERSED" : "PENDING";

  await db.runTransaction(async (transaction) => {
    const currentSnapshot = await transaction.get(bookingRef);
    if (!currentSnapshot.exists) throw new ApiError(404, "Booking not found.");
    const current = currentSnapshot.data();
    if (current.paymentReference !== reference || current.paymentProvider !== "pesapal"
        || (current.paymentTrackingId && current.paymentTrackingId !== trackingId)) {
      throw new ApiError(409, "The Pesapal transaction no longer matches the booking.");
    }
    if (isPaid && current.status !== "BOOKED" && current.paymentStatus !== "PAID") {
      throw new ApiError(409, "Cancelled bookings cannot be paid.");
    }
    const updates = {
      paymentTrackingId: trackingId,
      paymentInitializationState: (current.paymentCheckoutUrl
        || ["PAID", "FAILED", "REVERSED"].includes(nextStatus)) ? "READY" : "UNKNOWN",
      updatedAt: Date.now(),
    };
    if (current.paymentStatus !== "PAID" || nextStatus === "REVERSED") {
      updates.paymentStatus = nextStatus;
    }
    if (isPaid) {
      updates.paymentVerifiedAt = FieldValue.serverTimestamp();
      if (providerTransaction.confirmation_code) {
        updates.paymentConfirmationCode = String(providerTransaction.confirmation_code);
      }
    }
    transaction.update(bookingRef, updates);
  });
  return nextStatus;
}

async function verifyPesapalPayment(reference, trackingId) {
  const token = await pesapalToken();
  const result = await pesapalRequest(
    `api/Transactions/GetTransactionStatus?orderTrackingId=${encodeURIComponent(trackingId)}`,
    token
  );
  return reconcilePesapalPayment(reference, trackingId, result);
}

async function sendBookingPush(uid, title, body, bookingId, audience) {
  const user = await db.collection("users").doc(uid).get();
  const token = user.exists ? user.get("pushToken") : null;
  if (typeof token !== "string" || !token.trim()) return;
  try {
    await getMessaging().send({
      token,
      data: { title, body, bookingId, audience },
      android: { priority: "high" },
    });
  } catch (error) {
    console.warn("Could not send booking push notification", {
      uid,
      bookingId,
      error: error.message,
    });
  }
}

app.post("/v1/bookings/status", async (request, response, next) => {
  try {
    const uid = await requireUserRole(request, "provider");
    const bookingId = requireBookingId(request.body && request.body.bookingId);
    const nextStatus = request.body && request.body.status;
    const allowedStatuses = [
      "ACCEPTED", "PICKED_UP", "WASHING", "DRYING",
      "READY", "OUT_FOR_DELIVERY", "DELIVERED", "REJECTED",
    ];
    if (!allowedStatuses.includes(nextStatus)) {
      throw new ApiError(400, "The requested booking status is invalid.");
    }
    const bookingRef = db.collection("bookings").doc(bookingId);
    const userRef = db.collection("users").doc(uid);
    const providerRef = db.collection("providers").doc(uid);
    let pushDetails;
    await db.runTransaction(async (transaction) => {
      const [user, provider, bookingSnapshot] = await Promise.all([
        transaction.get(userRef),
        transaction.get(providerRef),
        transaction.get(bookingRef),
      ]);
      if (!user.exists || !hasActiveRole(user.data(), "provider")
          || !provider.exists || !isApprovedProvider(provider.data())) {
        throw new ApiError(403, "Only an active, approved provider can update booking status.");
      }
      if (!bookingSnapshot.exists) throw new ApiError(404, "Booking not found.");
      const booking = bookingSnapshot.data();
      if (booking.providerId !== uid) throw new ApiError(403, "You can only update your own bookings.");
      if (!isValidStatusTransition(
        booking.status, nextStatus, booking.paymentProvider, booking.paymentStatus)) {
        throw new ApiError(409, "This booking cannot move to the requested status.");
      }
      if (typeof booking.customerId !== "string" || !booking.customerId) {
        throw new ApiError(409, "Booking customer is missing.");
      }
      const customerNotification = db.collection("users").doc(booking.customerId)
        .collection("notifications").doc();
      const title = nextStatus.replace(/_/g, " ").toLowerCase()
        .replace(/\b\w/g, (character) => character.toUpperCase());
      const body = `Order #${bookingId} with ${booking.providerName || "your laundry provider"} is now ${
        nextStatus.replace(/_/g, " ").toLowerCase()
      }.`;
      transaction.update(bookingRef, { status: nextStatus, updatedAt: Date.now() });
      transaction.set(customerNotification, {
        bookingId,
        customerId: booking.customerId,
        title,
        body,
        createdAt: Date.now(),
      });
      pushDetails = { customerId: booking.customerId, title, body };
    });
    await sendBookingPush(pushDetails.customerId, pushDetails.title,
      pushDetails.body, bookingId, "customer");
    response.json({ status: nextStatus });
  } catch (error) {
    next(error);
  }
});

app.post("/v1/bookings/cancel", requireCustomer, async (request, response, next) => {
  try {
    const bookingId = requireBookingId(request.body && request.body.bookingId);
    const uid = request.user.uid;
    const bookingRef = db.collection("bookings").doc(bookingId);
    const userRef = db.collection("users").doc(uid);
    let providerId;
    await db.runTransaction(async (transaction) => {
      const [user, snapshot] = await Promise.all([
        transaction.get(userRef),
        transaction.get(bookingRef),
      ]);
      if (!user.exists || !hasActiveRole(user.data(), "customer")) {
        throw new ApiError(403, "Only customers can cancel their bookings.");
      }
      if (!snapshot.exists) throw new ApiError(404, "Booking not found.");
      const booking = snapshot.data();
      if (booking.customerId !== uid) throw new ApiError(403, "You can only cancel your own bookings.");
      if (!canCancelBooking(booking)) {
        throw new ApiError(409,
          "This booking can no longer be cancelled in the app. Contact support if payment was taken.");
      }
      if (typeof booking.providerId !== "string" || !booking.providerId) {
        throw new ApiError(409, "Booking provider is missing.");
      }
      providerId = booking.providerId;
      const now = Date.now();
      transaction.update(bookingRef, { status: "CANCELLED", updatedAt: now });
      transaction.set(userRef.collection("notifications").doc(), {
        bookingId, customerId: uid, title: "Booking Cancelled",
        body: `Order #${bookingId} has been cancelled.`, createdAt: now,
      });
      transaction.set(db.collection("users").doc(providerId)
        .collection("notifications").doc(), {
        bookingId, providerId, customerId: uid, title: "Booking Cancelled",
        body: `Order #${bookingId} was cancelled by the customer.`, createdAt: now,
      });
    });
    await sendBookingPush(providerId, "Booking cancelled",
      `Order #${bookingId} was cancelled by the customer.`, bookingId, "provider");
    response.json({ status: "CANCELLED" });
  } catch (error) {
    next(error);
  }
});

app.post("/v1/bookings/review", requireCustomer, async (request, response, next) => {
  try {
    const bookingId = requireBookingId(request.body && request.body.bookingId);
    const { rating, reviewText } = request.body || {};
    if (!validReview(rating, reviewText)) {
      throw new ApiError(400, "Choose a rating from 1 to 5 and enter a review of at most 1,000 characters.");
    }
    const uid = request.user.uid;
    const bookingRef = db.collection("bookings").doc(bookingId);
    await db.runTransaction(async (transaction) => {
      const bookingSnapshot = await transaction.get(bookingRef);
      if (!bookingSnapshot.exists) throw new ApiError(404, "Booking not found.");
      const booking = bookingSnapshot.data();
      if (!canReviewBooking(booking, uid)) {
        throw new ApiError(409, "Only the customer can review a delivered booking.");
      }
      const userRef = db.collection("users").doc(uid);
      const providerRef = db.collection("providers").doc(booking.providerId);
      const reviewRef = providerRef.collection("reviews").doc(bookingId);
      const [user, provider, review] = await Promise.all([
        transaction.get(userRef),
        transaction.get(providerRef),
        transaction.get(reviewRef),
      ]);
      if (!user.exists || !hasActiveRole(user.data(), "customer") || !provider.exists) {
        throw new ApiError(403, "The customer or provider account is unavailable.");
      }
      if (review.exists) throw new ApiError(409, "You have already reviewed this booking.");
      const count = Number.isInteger(provider.get("reviewCount")) && provider.get("reviewCount") >= 0
        ? provider.get("reviewCount") : 0;
      const currentRating = typeof provider.get("rating") === "number"
        && Number.isFinite(provider.get("rating")) ? provider.get("rating") : 0;
      transaction.create(reviewRef, {
        bookingId,
        customerId: uid,
        customerName: user.get("name") || "WashLink customer",
        rating,
        reviewText: reviewText.trim(),
        createdAt: Date.now(),
      });
      transaction.update(bookingRef, { reviewSubmitted: true });
      transaction.update(providerRef, {
        rating: updatedRatingAverage(currentRating, count, rating),
        reviewCount: count + 1,
      });
    });
    response.json({ success: true });
  } catch (error) {
    next(error);
  }
});

app.post("/v1/bookings/assign-rider", async (request, response, next) => {
  try {
    const uid = await requireUserRole(request, "provider");
    const bookingId = requireBookingId(request.body && request.body.bookingId);
    const riderId = requireText(request.body && request.body.riderId, "Rider", 40);
    if (!/^[A-Za-z0-9]{10,40}$/.test(riderId)) {
      throw new ApiError(400, "The selected rider is invalid.");
    }
    const providerRef = db.collection("providers").doc(uid);
    const bookingRef = db.collection("bookings").doc(bookingId);
    await db.runTransaction(async (transaction) => {
      const [user, provider, riderSnapshot, bookingSnapshot] = await Promise.all([
        transaction.get(db.collection("users").doc(uid)),
        transaction.get(providerRef),
        transaction.get(providerRef.collection("riders").doc(riderId)),
        transaction.get(bookingRef),
      ]);
      if (!user.exists || !hasActiveRole(user.data(), "provider")
          || !provider.exists || !isApprovedProvider(provider.data())) {
        throw new ApiError(403, "Only an active, approved provider can assign riders.");
      }
      if (!bookingSnapshot.exists) throw new ApiError(404, "Booking not found.");
      const rider = riderSnapshot.exists ? riderSnapshot.data() : null;
      if (!canAssignRider(bookingSnapshot.data(), uid, rider)) {
        throw new ApiError(409, "Choose an active rider for one of your in-progress bookings.");
      }
      transaction.update(bookingRef, {
        assignedRiderId: riderId,
        assignedRiderName: rider.name.trim(),
        assignedRiderPhone: rider.phone.trim(),
        updatedAt: Date.now(),
      });
    });
    response.json({ success: true });
  } catch (error) {
    next(error);
  }
});

app.get("/health", (_request, response) => response.json({ status: "ok" }));

app.post("/v1/bookings", requireCustomer, async (request, response, next) => {
  try {
    const data = request.body || {};
    const providerId = requireText(data.providerId, "Provider", 40);
    if (!/^[A-Za-z0-9]{10,40}$/.test(providerId)) {
      throw new ApiError(400, "The booking provider is invalid.");
    }
    const serviceNames = data.serviceNames === undefined
      ? [requireText(data.serviceName, "Service", 120)] : data.serviceNames;
    if (!Array.isArray(serviceNames) || serviceNames.length < 1 || serviceNames.length > 20) {
      throw new ApiError(400, "Select between 1 and 20 services.");
    }
    const normalizedNames = serviceNames.map((name) => requireText(name, "Service", 120));
    const bookingInput = {
      providerId,
      serviceType: data.serviceType,
      serviceNames: normalizedNames,
      serviceName: normalizedNames.join(", "),
      itemCount: data.itemCount,
      weightKg: data.weightKg,
      subtotal: data.subtotal,
      pickupFee: data.pickupFee,
      deliveryFee: data.deliveryFee,
      serviceFee: data.serviceFee,
      tax: data.tax,
      total: data.total,
    };
    const address = requireText(data.address, "Address", 500);
    const scheduledDateTime = requireText(data.scheduledDateTime, "Schedule", 200);
    const specialInstructions = typeof data.specialInstructions === "string"
      ? data.specialInstructions.slice(0, 500) : "";
    const method = paymentMethodInfo(data.paymentMethod);

    const [providerUser, providerSnapshot, authCustomer] = await Promise.all([
      db.collection("users").doc(providerId).get(),
      db.collection("providers").doc(providerId).get(),
      auth.getUser(request.user.uid),
    ]);
    if (!providerUser.exists || !hasActiveRole(providerUser.data(), "provider")
        || !providerSnapshot.exists || !isApprovedProvider(providerSnapshot.data())) {
      throw new ApiError(409, "This provider is not available for bookings.");
    }
    let quote;
    try {
      quote = quoteBooking(bookingInput, providerSnapshot.data());
    } catch (error) {
      throw new ApiError(409, error.message);
    }
    const provider = providerSnapshot.data();
    const bookingRef = db.collection("bookings").doc();
    const bookingId = bookingRef.id;
    const now = Date.now();
    const booking = {
      id: bookingId,
      customerId: request.user.uid,
      customerName: authCustomer.displayName || request.customerProfile.name || "Customer",
      customerPhone: request.customerProfile.phone || "",
      providerId,
      providerName: provider.businessName || "Laundry provider",
      serviceType: bookingInput.serviceType,
      serviceName: bookingInput.serviceName,
      serviceNames: normalizedNames,
      status: "BOOKED",
      address: bookingInput.serviceType === "dropoff" ? (provider.address || address) : address,
      scheduledDateTime,
      itemCount: bookingInput.itemCount,
      weightKg: quote.kilograms,
      estimatedWeight: `${quote.kilograms} kg`,
      specialInstructions,
      subtotal: quote.subtotal,
      tax: quote.tax,
      total: quote.total,
      paymentMethod: method.label,
      paymentStatus: "PENDING",
      paymentProvider: method.cashOnDelivery ? "cash" : "pesapal",
      ...(method.cashOnDelivery ? { paymentAmount: quote.total, paymentCurrency: "UGX" } : {}),
      pickupFee: quote.pickupFee,
      deliveryFee: quote.deliveryFee,
      serviceFee: quote.serviceFee,
      createdAt: now,
      updatedAt: now,
    };
    const customerNotification = db.collection("users").doc(request.user.uid)
      .collection("notifications").doc();
    const providerNotification = db.collection("users").doc(providerId)
      .collection("notifications").doc();
    const batch = db.batch();
    batch.set(bookingRef, booking);
    batch.set(customerNotification, {
      bookingId,
      customerId: request.user.uid,
      title: "Booked",
      body: `Order #${bookingId} with ${booking.providerName} is now Booked.`,
      createdAt: now,
    });
    batch.set(providerNotification, {
      bookingId,
      providerId,
      customerId: request.user.uid,
      title: "New booking request",
      body: `${booking.customerName} booked ${booking.serviceName}.`,
      createdAt: now,
    });
    await batch.commit();
    await sendBookingPush(providerId, "New booking request",
      `A customer booked ${booking.serviceName}.`, bookingId, "provider");
    response.status(201).json({ bookingId });
  } catch (error) {
    next(error);
  }
});

app.post("/v1/payments/pesapal/initialize", requireCustomer, async (request, response, next) => {
  let bookingRef;
  let paymentReference;
  try {
    if (!process.env.PESAPAL_IPN_ID || process.env.PESAPAL_IPN_ID === "pending") {
      throw new ApiError(503, "Pesapal IPN is not configured yet.");
    }
    const bookingId = requireBookingId(request.body && request.body.bookingId);
    const method = paymentMethodInfo(request.body && request.body.paymentMethod);
    if (method.cashOnDelivery) throw new ApiError(400, "Cash bookings do not need online checkout.");
    const customer = await auth.getUser(request.user.uid);
    if (!customer.email) throw new ApiError(409, "Add a valid email to your account before paying.");

    bookingRef = db.collection("bookings").doc(bookingId);
    const generatedReference = `wl_${crypto.randomBytes(16).toString("hex")}`;
    let amount = 0;
    let phone = "";
    let checkoutUrl = null;
    let booking;
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(bookingRef);
      if (!snapshot.exists) throw new ApiError(404, "Booking not found.");
      booking = snapshot.data();
      if (booking.customerId !== request.user.uid) throw new ApiError(403, "You can only pay for your own booking.");
      if (booking.status !== "BOOKED") throw new ApiError(409, "Only booked orders can be paid.");
      if (booking.paymentStatus === "PAID") throw new ApiError(409, "This booking is already paid.");
      if (booking.paymentStatus === "REVERSED") {
        throw new ApiError(409, "This payment was reversed. Contact support.");
      }
      if (booking.paymentProvider !== "pesapal") {
        throw new ApiError(409, "This booking is not set up for Pesapal.");
      }
      const providerId = booking.providerId;
      const [customerRole, providerRole, providerSnapshot] = await Promise.all([
        transaction.get(db.collection("users").doc(request.user.uid)),
        transaction.get(db.collection("users").doc(providerId)),
        transaction.get(db.collection("providers").doc(providerId)),
      ]);
      if (!customerRole.exists || !hasActiveRole(customerRole.data(), "customer")
          || !providerRole.exists || !hasActiveRole(providerRole.data(), "provider")
          || !providerSnapshot.exists || !isApprovedProvider(providerSnapshot.data())) {
        throw new ApiError(409, "The booking customer or provider is not valid.");
      }
      let quote;
      try {
        quote = quoteBooking(booking, providerSnapshot.data());
      } catch (error) {
        throw new ApiError(409, error.message);
      }
      amount = quote.total;
      phone = typeof customerRole.get("phone") === "string" ? customerRole.get("phone") : "";
      if (booking.paymentStatus !== "FAILED" && booking.paymentInitializationState === "READY"
          && typeof booking.paymentCheckoutUrl === "string"
          && booking.paymentCheckoutUrl.startsWith("https://")) {
        checkoutUrl = booking.paymentCheckoutUrl;
        paymentReference = booking.paymentReference;
        return;
      }
      if (["PROCESSING", "UNKNOWN"].includes(booking.paymentInitializationState)) {
        throw new ApiError(409, "This payment is being reconciled. Check order status before retrying.");
      }
      paymentReference = generatedReference;
      transaction.update(bookingRef, {
        paymentStatus: "PENDING",
        paymentMethod: method.label,
        paymentProvider: "pesapal",
        paymentAmount: amount,
        paymentCurrency: "UGX",
        paymentReference,
        paymentInitializationState: "PROCESSING",
        paymentCheckoutUrl: FieldValue.delete(),
        paymentTrackingId: FieldValue.delete(),
        updatedAt: Date.now(),
      });
    });
    if (checkoutUrl) return response.json({ checkoutUrl });

    const apiUrl = process.env.PUBLIC_API_URL || process.env.RENDER_EXTERNAL_URL;
    if (!apiUrl) throw new ApiError(503, "The public API URL is not configured.");
    const names = (customer.displayName || "WashLink customer").trim().split(/\s+/);
    const token = await pesapalToken();
    const order = await pesapalRequest("api/Transactions/SubmitOrderRequest", token, {
      id: paymentReference,
      currency: "UGX",
      amount,
      description: `WashLink booking ${bookingId}`.slice(0, 100),
      callback_url: `${apiUrl.replace(/\/$/, "")}/v1/payments/pesapal/return`,
      notification_id: process.env.PESAPAL_IPN_ID,
      billing_address: {
        email_address: customer.email,
        phone_number: phone || undefined,
        country_code: "UG",
        first_name: names[0] || "WashLink",
        last_name: names.slice(1).join(" ") || "Customer",
      },
    });
    if (typeof order.order_tracking_id !== "string" || !order.order_tracking_id
        || typeof order.redirect_url !== "string" || !order.redirect_url.startsWith("https://")
        || order.merchant_reference !== paymentReference) {
      throw new ApiError(502, "Pesapal returned an invalid checkout response.");
    }
    await db.runTransaction(async (transaction) => {
      const snapshot = await transaction.get(bookingRef);
      if (!snapshot.exists || snapshot.get("paymentReference") !== paymentReference
          || snapshot.get("status") !== "BOOKED") {
        throw new ApiError(409, "The booking changed while checkout was starting.");
      }
      transaction.update(bookingRef, {
        paymentTrackingId: order.order_tracking_id,
        paymentCheckoutUrl: order.redirect_url,
        paymentInitializationState: "READY",
        updatedAt: Date.now(),
      });
    });
    response.json({ checkoutUrl: order.redirect_url });
  } catch (error) {
    if (bookingRef && paymentReference) {
      await db.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(bookingRef);
        if (snapshot.exists && snapshot.get("paymentReference") === paymentReference
            && snapshot.get("paymentInitializationState") === "PROCESSING") {
          transaction.update(bookingRef, { paymentInitializationState: "UNKNOWN", updatedAt: Date.now() });
        }
      }).catch((reconcileError) => console.error("Could not reconcile checkout state", reconcileError));
    }
    next(error);
  }
});

app.post("/v1/payments/pesapal/verify", requireCustomer, async (request, response, next) => {
  try {
    const bookingId = requireBookingId(request.body && request.body.bookingId);
    const trackingId = request.body && request.body.orderTrackingId;
    if (typeof trackingId !== "string" || !/^[A-Za-z0-9-]{10,100}$/.test(trackingId)) {
      throw new ApiError(400, "A valid Pesapal order tracking ID is required.");
    }
    const bookingSnapshot = await db.collection("bookings").doc(bookingId).get();
    if (!bookingSnapshot.exists || bookingSnapshot.get("customerId") !== request.user.uid) {
      throw new ApiError(403, "You can only verify your own booking.");
    }
    if (bookingSnapshot.get("paymentProvider") !== "pesapal"
        || typeof bookingSnapshot.get("paymentReference") !== "string") {
      throw new ApiError(409, "This booking has no active Pesapal payment.");
    }
    if (bookingSnapshot.get("paymentTrackingId")
        && bookingSnapshot.get("paymentTrackingId") !== trackingId) {
      throw new ApiError(409, "This tracking ID does not match the booking.");
    }
    const paymentStatus = await verifyPesapalPayment(
      bookingSnapshot.get("paymentReference"), trackingId);
    response.json({ paymentStatus });
  } catch (error) {
    next(error);
  }
});

function callbackParameter(request, name) {
  const value = request.method === "GET" ? request.query[name] : request.body?.[name];
  return typeof value === "string" ? value : "";
}

app.all("/v1/payments/pesapal/return", async (request, response) => {
  const trackingId = callbackParameter(request, "OrderTrackingId");
  const reference = callbackParameter(request, "OrderMerchantReference");
  if (!/^[A-Za-z0-9-]{10,100}$/.test(trackingId) || !/^wl_[a-f0-9]{32}$/.test(reference)) {
    return response.status(400).send("Invalid WashLink payment reference.");
  }
  try {
    const matches = await db.collection("bookings")
      .where("paymentReference", "==", reference).limit(1).get();
    if (matches.empty || matches.docs[0].get("paymentProvider") !== "pesapal"
        || (matches.docs[0].get("paymentTrackingId")
          && matches.docs[0].get("paymentTrackingId") !== trackingId)) {
      return response.status(404).send("WashLink booking not found.");
    }
    const appUrl = `washlink://payment-return?${new URLSearchParams({
      bookingId: matches.docs[0].id,
      orderTrackingId: trackingId,
    })}`;
    const escapedUrl = appUrl.replace(/&/g, "&amp;").replace(/"/g, "&quot;");
    return response.type("html").send(
      `<!doctype html><html><head><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=${escapedUrl}">`
      + `<title>Return to WashLink</title></head><body><p>Returning to WashLink…</p>`
      + `<a href="${escapedUrl}">Tap here if the app does not open</a>`
      + `<script>window.location.replace(${JSON.stringify(appUrl)});</script></body></html>`
    );
  } catch (error) {
    console.error("Pesapal callback failed", error);
    return response.status(500).send("Could not return to WashLink. Check your order status in the app.");
  }
});

app.all("/v1/payments/pesapal/ipn", async (request, response) => {
  const notificationType = callbackParameter(request, "OrderNotificationType");
  const trackingId = callbackParameter(request, "OrderTrackingId");
  const reference = callbackParameter(request, "OrderMerchantReference");
  if (notificationType !== "IPNCHANGE" || !/^[A-Za-z0-9-]{10,100}$/.test(trackingId)
      || !/^wl_[a-f0-9]{32}$/.test(reference)) {
    return response.status(400).json({ status: 500, message: "Invalid IPN notification." });
  }
  try {
    await verifyPesapalPayment(reference, trackingId);
    return response.json({
      orderNotificationType: notificationType,
      orderTrackingId: trackingId,
      orderMerchantReference: reference,
      status: 200,
    });
  } catch (error) {
    console.error("Pesapal IPN reconciliation failed", error);
    return response.status(500).json({
      orderNotificationType: notificationType,
      orderTrackingId: trackingId,
      orderMerchantReference: reference,
      status: 500,
    });
  }
});

app.use((error, _request, response, _next) => {
  if (error instanceof ApiError) {
    return response.status(error.status).json({ message: error.message });
  }
  console.error("WashLink API request failed", error);
  return response.status(500).json({ message: "The server could not complete the request." });
});

const port = Number(process.env.PORT) || 3000;
if (require.main === module) {
  app.listen(port, "0.0.0.0", () => console.log(`WashLink API listening on ${port}`));
}

module.exports = app;
