"use strict";

const MINIMUM_KG = 5;
const PICKUP_FEE = 5000;
const DELIVERY_FEE = 5000;
const SERVICE_FEE_RATE = 0.03;
const MAX_BOOKING_AMOUNT = 5000000;

function quoteBooking(booking, provider) {
  if (!booking || !provider || !Array.isArray(provider.services)) {
    throw new Error("Provider or service is unavailable.");
  }

  const providerId = booking.providerId;
  if (typeof providerId !== "string" || providerId.length === 0) {
    throw new Error("Booking provider is invalid.");
  }
  if (typeof booking.serviceName !== "string" || booking.serviceName.trim().length === 0) {
    throw new Error("Booking service is invalid.");
  }

  const service = provider.services.find((item) =>
    item && typeof item.name === "string" && item.name === booking.serviceName
  );
  if (!service || typeof service.pricePerKg !== "number"
      || !Number.isFinite(service.pricePerKg) || service.pricePerKg <= 0) {
    throw new Error("Selected service is not currently offered by this provider.");
  }

  const itemCount = booking.itemCount;
  if (!Number.isInteger(itemCount) || itemCount < 1 || itemCount > 50) {
    throw new Error("Booking item count is invalid.");
  }
  if (booking.serviceType !== "pickup_delivery" && booking.serviceType !== "dropoff") {
    throw new Error("Booking service type is invalid.");
  }

  const kilograms = Math.max(MINIMUM_KG, Math.round(itemCount * 0.45));
  const laundry = Math.round(kilograms * service.pricePerKg);
  const pickup = booking.serviceType === "pickup_delivery" ? PICKUP_FEE : 0;
  const delivery = booking.serviceType === "pickup_delivery" ? DELIVERY_FEE : 0;
  const serviceFee = Math.round((laundry + pickup + delivery) * SERVICE_FEE_RATE);
  const total = laundry + pickup + delivery + serviceFee;
  if (!Number.isSafeInteger(total) || total <= 0 || total > MAX_BOOKING_AMOUNT) {
    throw new Error("Booking amount is outside the supported range.");
  }

  const expectedSubtotal = laundry + pickup + delivery;
  const expected = {
    subtotal: expectedSubtotal,
    pickupFee: pickup,
    deliveryFee: delivery,
    serviceFee,
    tax: serviceFee,
    total,
  };
  for (const [field, value] of Object.entries(expected)) {
    if (typeof booking[field] !== "number" || !Number.isFinite(booking[field])
        || Math.abs(booking[field] - value) > 0.001) {
      throw new Error(`Booking ${field} does not match the provider's current price.`);
    }
  }
  return { ...expected, kilograms, laundry };
}

function isVerifiedFlutterwavePayment(transaction, expectedReference, expectedAmount) {
  return transaction
    && transaction.status === "successful"
    && transaction.tx_ref === expectedReference
    && transaction.currency === "UGX"
    && typeof transaction.amount === "number"
    && Math.abs(transaction.amount - expectedAmount) < 0.001;
}

function isValidStatusTransition(currentStatus, nextStatus, paymentProvider, paymentStatus) {
  const transitions = {
    BOOKED: ["ACCEPTED", "REJECTED"],
    ACCEPTED: ["PICKED_UP"],
    PICKED_UP: ["WASHING"],
    WASHING: ["DRYING"],
    DRYING: ["READY"],
    READY: ["OUT_FOR_DELIVERY"],
    OUT_FOR_DELIVERY: ["DELIVERED"],
  };
  if (!transitions[currentStatus]?.includes(nextStatus)) return false;
  return nextStatus !== "ACCEPTED"
    || paymentProvider === "cash"
    || (paymentProvider === "flutterwave" && paymentStatus === "PAID");
}

function canCancelBooking(booking) {
  return booking
    && booking.status === "BOOKED"
    && booking.paymentStatus !== "PAID"
    && (typeof booking.paymentReference !== "string"
      || booking.paymentReference.length === 0);
}

module.exports = {
  MAX_BOOKING_AMOUNT,
  quoteBooking,
  isVerifiedFlutterwavePayment,
  isValidStatusTransition,
  canCancelBooking,
};
