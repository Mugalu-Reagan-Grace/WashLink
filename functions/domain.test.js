"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const {
  quoteBooking,
  isVerifiedFlutterwavePayment,
  isValidStatusTransition,
  canCancelBooking,
  isEligibleForProviderPayout,
  isEligibleForProviderRejectionRefund,
  canClaimProviderPayout,
} = require("./domain");

const provider = { services: [{ name: "Wash & Fold", pricePerKg: 5000 }] };
const booking = {
  providerId: "provider-1",
  serviceName: "Wash & Fold",
  serviceType: "pickup_delivery",
  itemCount: 15,
  subtotal: 45000,
  pickupFee: 5000,
  deliveryFee: 5000,
  serviceFee: 1350,
  tax: 1350,
  total: 46350,
};

test("prices a pickup using the provider's listed service price", () => {
  assert.equal(quoteBooking(booking, provider).total, 46350);
});

test("prices a drop-off without pickup or delivery fees", () => {
  const dropoff = {
    ...booking,
    serviceType: "dropoff",
    subtotal: 35000,
    pickupFee: 0,
    deliveryFee: 0,
    serviceFee: 1050,
    tax: 1050,
    total: 36050,
  };
  assert.equal(quoteBooking(dropoff, provider).total, 36050);
});

test("rejects stale/forged booking totals and unlisted services", () => {
  assert.throws(() => quoteBooking({ ...booking, total: 1 }, provider), /total/);
  assert.throws(() => quoteBooking({ ...booking, serviceName: "Unlisted" }, provider), /not currently offered/);
});

test("only accepts a successful, matching UGX Flutterwave transaction", () => {
  const transaction = {
    status: "successful",
    tx_ref: "washlink_booking_ref",
    currency: "UGX",
    amount: 46350,
  };
  assert.equal(isVerifiedFlutterwavePayment(transaction, "washlink_booking_ref", 46350), true);
  assert.equal(isVerifiedFlutterwavePayment({ ...transaction, amount: 46349 }, transaction.tx_ref, 46350), false);
  assert.equal(isVerifiedFlutterwavePayment({ ...transaction, currency: "USD" }, transaction.tx_ref, 46350), false);
});

test("allows only sequential provider status transitions and blocks unpaid online acceptance", () => {
  assert.equal(isValidStatusTransition("BOOKED", "ACCEPTED", "cash", "PENDING"), true);
  assert.equal(isValidStatusTransition("BOOKED", "ACCEPTED", "flutterwave", "PAID"), true);
  assert.equal(isValidStatusTransition("BOOKED", "ACCEPTED", "flutterwave", "PENDING"), false);
  assert.equal(isValidStatusTransition("BOOKED", "WASHING", "cash", "PENDING"), false);
  assert.equal(isValidStatusTransition("DELIVERED", "ACCEPTED", "cash", "PENDING"), false);
  assert.equal(isValidStatusTransition("BOOKED", "REJECTED", "flutterwave", "PENDING"), true);
});

test("only allows cancellation of booked orders before payment begins", () => {
  assert.equal(canCancelBooking({
    status: "BOOKED",
    paymentStatus: "PENDING",
  }), true);
  assert.equal(canCancelBooking({
    status: "BOOKED",
    paymentStatus: "PAID",
  }), false);
  assert.equal(canCancelBooking({
    status: "BOOKED",
    paymentStatus: "PENDING",
    paymentReference: "washlink_ref",
  }), false);
  assert.equal(canCancelBooking({
    status: "ACCEPTED",
    paymentStatus: "PENDING",
  }), false);
});

test("pays only delivered, verified online bookings and uses subtotal before service fee", () => {
  const delivered = {
    status: "DELIVERED",
    paymentProvider: "flutterwave",
    paymentStatus: "PAID",
    subtotal: 45000,
    serviceFee: 1350,
  };
  assert.equal(isEligibleForProviderPayout(delivered), true);
  assert.equal(isEligibleForProviderPayout({ ...delivered, paymentProvider: "cash" }), false);
  assert.equal(isEligibleForProviderPayout({ ...delivered, paymentStatus: "PENDING" }), false);
  assert.equal(isEligibleForProviderPayout({ ...delivered, subtotal: 45000.5 }), false);
});

test("refunds only paid online bookings rejected before work with a matching transaction amount", () => {
  const rejected = {
    status: "BOOKED",
    paymentProvider: "flutterwave",
    paymentStatus: "PAID",
    paymentTransactionId: "123456",
    paymentAmount: 46350,
    total: 46350,
  };
  assert.equal(isEligibleForProviderRejectionRefund(rejected, "REJECTED"), true);
  assert.equal(isEligibleForProviderRejectionRefund(
    { ...rejected, status: "ACCEPTED" }, "REJECTED"
  ), false);
  assert.equal(isEligibleForProviderRejectionRefund(
    { ...rejected, paymentProvider: "cash" }, "REJECTED"
  ), false);
  assert.equal(isEligibleForProviderRejectionRefund(
    { ...rejected, paymentAmount: 1 }, "REJECTED"
  ), false);
});

test("payout retries reclaim uncertain or stale attempts but never duplicate active or terminal attempts", () => {
  const now = 1_000_000;
  assert.equal(canClaimProviderPayout({ state: "REQUESTED" }, now), true);
  assert.equal(canClaimProviderPayout({ state: "UNKNOWN" }, now), true);
  assert.equal(canClaimProviderPayout({
    state: "PROCESSING",
    claimedAt: now - 6 * 60 * 1000,
  }, now), true);
  assert.equal(canClaimProviderPayout({
    state: "PROCESSING",
    claimedAt: now - 60 * 1000,
  }, now), false);
  assert.equal(canClaimProviderPayout({ state: "SUCCESSFUL" }, now), false);
  assert.equal(canClaimProviderPayout({ state: "FAILED" }, now), false);
});
