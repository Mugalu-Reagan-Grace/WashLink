"use strict";

const { after, before, test } = require("node:test");
const assert = require("node:assert/strict");
const app = require("./server");

let server;
let baseUrl;

before(async () => {
  server = app.listen(0, "127.0.0.1");
  await new Promise((resolve) => server.once("listening", resolve));
  baseUrl = `http://127.0.0.1:${server.address().port}`;
});

after(async () => {
  if (server) await new Promise((resolve, reject) =>
    server.close((error) => error ? reject(error) : resolve()));
});

test("health endpoint reports readiness", async () => {
  const response = await fetch(`${baseUrl}/health`);
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: "ok" });
});

test("booking endpoint requires Firebase authentication", async () => {
  const response = await fetch(`${baseUrl}/v1/bookings`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: "{}",
  });
  assert.equal(response.status, 401);
  assert.match((await response.json()).message, /Sign in/);
});

test("provider status endpoint requires Firebase authentication", async () => {
  const response = await fetch(`${baseUrl}/v1/bookings/status`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ bookingId: "abcdefghij", status: "ACCEPTED" }),
  });
  assert.equal(response.status, 401);
});

test("Pesapal IPN rejects malformed callbacks", async () => {
  const response = await fetch(`${baseUrl}/v1/payments/pesapal/ipn?OrderNotificationType=IPNCHANGE`);
  assert.equal(response.status, 400);
});
