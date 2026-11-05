"use strict";

const net = require("node:net");
const { initializeApp } = require("firebase-admin/app");
const { getAuth } = require("firebase-admin/auth");
const { getFirestore } = require("firebase-admin/firestore");

const PROJECT_ID = "washlink-main";
const CUSTOMER = {
  uid: "democustomer0001",
  email: "customer@washlink-demo.test",
  password: "WashLinkDemo2026!",
  name: "Demo Customer",
  phone: "+256700000001",
};
const PROVIDER = {
  uid: "demoprovider0001",
  email: "provider@washlink-demo.test",
  password: "WashLinkDemo2026!",
  name: "Demo Laundry",
  phone: "+256700000002",
};

function assertEmulatorIsRunning(port) {
  return new Promise((resolve, reject) => {
    const socket = net.createConnection({ host: "127.0.0.1", port });
    socket.setTimeout(1500);
    socket.once("connect", () => {
      socket.destroy();
      resolve();
    });
    socket.once("timeout", () => {
      socket.destroy();
      reject(new Error(`Firebase emulator on port ${port} did not respond.`));
    });
    socket.once("error", () => {
      reject(new Error(`Firebase emulator on port ${port} is not running.`));
    });
  });
}

async function ensureDemoUser(user, role) {
  const auth = getAuth();
  const firestore = getFirestore();
  let existing = null;
  try {
    existing = await auth.getUser(user.uid);
  } catch (error) {
    if (error.code !== "auth/user-not-found") throw error;
    try {
      existing = await auth.getUserByEmail(user.email);
      if (existing.uid !== user.uid) {
        await auth.deleteUser(existing.uid);
        await firestore.collection("users").doc(existing.uid).delete();
        await firestore.collection("providers").doc(existing.uid).delete();
        existing = null;
      }
    } catch (emailError) {
      if (emailError.code !== "auth/user-not-found") throw emailError;
    }
  }

  if (existing) {
    await auth.updateUser(user.uid, {
      email: user.email,
      password: user.password,
      displayName: user.name,
      emailVerified: true,
      disabled: false,
    });
  } else {
    await auth.createUser({
      uid: user.uid,
      email: user.email,
      password: user.password,
      displayName: user.name,
      emailVerified: true,
    });
  }

  await firestore.collection("users").doc(user.uid).set({
    uid: user.uid,
    name: user.name,
    email: user.email,
    phone: user.phone,
    role,
    isSuspended: false,
  }, { merge: true });
}

async function seedDemo() {
  process.env.FIREBASE_AUTH_EMULATOR_HOST = "127.0.0.1:9099";
  process.env.FIRESTORE_EMULATOR_HOST = "127.0.0.1:8080";
  await Promise.all([assertEmulatorIsRunning(9099), assertEmulatorIsRunning(8080)]);

  initializeApp({ projectId: PROJECT_ID });
  await ensureDemoUser(CUSTOMER, "customer");
  await ensureDemoUser(PROVIDER, "provider");
  await getFirestore().collection("providers").doc(PROVIDER.uid).set({
    uid: PROVIDER.uid,
    businessName: PROVIDER.name,
    ownerName: PROVIDER.name,
    email: PROVIDER.email,
    phone: PROVIDER.phone,
    address: "Kampala, Uganda",
    latitude: 0.3476,
    longitude: 32.5825,
    isApproved: true,
    isOpen: true,
    supportsPickupDelivery: true,
    supportsDropoff: true,
    services: [
      { name: "Wash & Fold", pricePerKg: 4500, isAvailable: true },
      { name: "Dry Cleaning", pricePerKg: 8000, isAvailable: true },
    ],
  }, { merge: true });

  console.log("Seeded Firebase Emulator demo accounts:");
  console.log(`Customer: ${CUSTOMER.email} / ${CUSTOMER.password}`);
  console.log(`Provider: ${PROVIDER.email} / ${PROVIDER.password}`);
}

seedDemo().catch((error) => {
  console.error(`Could not seed emulator demo data: ${error.message}`);
  process.exitCode = 1;
});
