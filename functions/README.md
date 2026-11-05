# Pesapal checkout

Bookings are created by the authenticated `createBooking` callable, which checks
the customer/provider accounts and recalculates the amount from the provider's
current listed service. Firestore rules prohibit client-created bookings and
allow providers to accept only cash bookings registered by the backend or
online bookings verified as paid.

The Android app starts online checkout through the authenticated
`initializePesapalCheckout` callable. Pesapal supplies its hosted checkout URL;
customers choose among the payment options available to their account there.
Only `verifyPesapalPayment` or the `pesapalIpn` endpoint can mark a new Pesapal
booking paid. Both paths query Pesapal's transaction-status API and validate
the booking reference, amount, and UGX currency; a browser redirect is never
proof of payment. Cash on delivery remains a separate option and never contacts
Pesapal.

`PESAPAL_ENVIRONMENT` selects `sandbox` (the default) or `live`. Credentials are
server-only Firebase secrets. Register the `pesapalIpn` URL in the matching
Pesapal environment and configure its returned notification ID as a secret.
The callback URL only returns the customer to the app; the IPN and authenticated
verification callable independently reconcile payment status.

The `verifyFlutterwavePayment`, `flutterwaveReturn`, and
`flutterwaveWebhook` endpoints remain for historical Flutterwave bookings.
New bookings no longer create Flutterwave checkouts. Existing Flutterwave
refund and transfer records are also retained for legacy reconciliation;
automated payouts are disabled, and Pesapal payouts/refunds are not implemented.
Keep the existing `FLUTTERWAVE_SECRET_KEY` and
`FLUTTERWAVE_WEBHOOK_HASH` secrets until those legacy transactions no longer
need reconciliation.

## Local presentation without Blaze

The Firebase Emulator Suite runs Authentication, Firestore, Storage, and
Functions locally. It does not deploy anything or require a Blaze billing plan.
Use it for a local presentation; it is not a public hosting solution.

1. Install Java and Node.js 20, then from the repository root start the
   emulators:

   ```powershell
   firebase.cmd emulators:start --project washlink-main
   ```

   Keep this terminal running. The emulator UI is at
   `http://127.0.0.1:4000`.

2. In a second terminal, seed the local demo accounts and approved laundry
   provider:

   ```powershell
   Push-Location functions
   npm.cmd run seed:demo
   Pop-Location
   ```

   The seed script refuses to run unless the local Auth and Firestore emulator
   ports are listening. Demo accounts (for the emulator only):

   - Customer: `customer@washlink-demo.test` / `WashLinkDemo2026!`
   - Provider: `provider@washlink-demo.test` / `WashLinkDemo2026!`

3. For Android Emulator, install the debug app with emulator routing enabled:

   ```powershell
   .\gradlew.bat :app:installDebug -PuseFirebaseEmulators=true
   ```

   Open WashLink on the Android Emulator and sign in with the demo customer. The
   default host `10.0.2.2` routes from Android Emulator to the computer running
   Firebase.

   For a physical phone, connect the phone and computer to the same Wi-Fi.
   Find the computer's IPv4 address with `ipconfig` (the Wi-Fi adapter address),
   then build the demo APK using that address, for example:

   ```powershell
   .\gradlew.bat :app:assembleDebug -PuseFirebaseEmulators=true -PfirebaseEmulatorHost=192.168.1.20
   ```

   Transfer `app\build\outputs\apk\debug\app-debug.apk` to the phone and install
   it. Keep the emulator terminal running and allow the computer's private
   network connection through Windows Firewall if prompted. The phone cannot
   reach the local demo over mobile data; both devices must stay on the same
   Wi-Fi network.

4. To present the web client, copy `web\.env.example` to `web\.env.local`, fill
   in the Firebase web app's existing configuration values, set
   `VITE_USE_FIREBASE_EMULATORS=true`, and leave the emulator host as
   `127.0.0.1` when using the same computer. Then start the web app:

   ```powershell
   Push-Location web
   npm.cmd run dev
   Pop-Location
   ```

Select **Cash** to create a booking without Pesapal. The customer and provider
can sign in separately to see the booking flow. Emulator data is local and is
not copied to production; run the seed command again after a reset. Emulator
routing is opt-in for the Android debug build and web app, and should not be
enabled for a release build. Online checkout and provider payouts are not part
of this local demo; use only the cash flow while running it.

Bookings and provider status changes are also handled by authenticated
callables. Status changes atomically persist the customer in-app event and send
an FCM push when the recipient has registered a device token. Customers receive
status updates; providers receive new booking requests. The first app launch
after sign-in requests Android notification permission where required.

Customers can cancel only a still-booked order before a payment attempt starts.
The cancellation callable checks ownership and commits the cancelled status and
customer notification together, then notifies the provider. Once an online
checkout has begun, cancellation remains blocked. If a provider rejects a
historical Flutterwave-paid booking while it is still `BOOKED`, the backend
requests a full refund and changes the payment state to `REFUND_PENDING`. A
successful refund API response only means Flutterwave
accepted the request; this version does not claim that the refund has reached
the customer or automatically reconcile its final status. Pesapal refunds need
manual handling; do not represent them as automated.

Providers can save one private Uganda payout destination: a bank account
(including a bank branch where required), Airtel Money, or MTN Mobile Money.
The destination is stored under the provider's owner-only `users/{uid}` data,
not in the publicly readable provider profile. Historical Flutterwave payout
support is retained in the backend, but `AUTOMATED_PAYOUTS_ENABLED` is
currently `false`. Pesapal funds are not automatically transferred to providers.

Cash-on-delivery bookings are collected directly by the provider and do not
create an automatic transfer or fee withholding. Providers receive the full
cash collection under this policy. Historical Flutterwave payouts require
approved KYC, Transfers enabled, server IP allowlisting, and sufficient UGX
wallet balance.

Never save provider bank/mobile-money details in `providers/{uid}`.

## Customer reviews and administrator accounts

Customers can submit one 1-5 star rating per delivered booking. The
`submitBookingReview` callable verifies booking ownership and delivery, writes
the review under `providers/{providerId}/reviews/{bookingId}`, and updates the
provider's aggregate rating in the same Firestore transaction. The app displays
recent reviews on provider details.

To bootstrap an administrator, first create a dedicated Firebase Authentication
account. Then, using the Firebase Console as a trusted project owner, create
`users/{authUid}` with the matching `uid`, `role: "admin"`, and
`isSuspended: false`. Never grant an app user the admin role from the client.
Admins can review all accounts, suspend or restore non-admin accounts, and
approve or unapprove provider listings. Newly registered providers stay hidden
from discovery until approved; older provider profiles without an approval
field remain visible until an administrator explicitly unapproves them.

## One-time project and provider setup

Use Node.js 20 and the Firebase CLI. Cloud Functions deployment requires a
Firebase project on the Blaze billing plan, Cloud Functions and Secret Manager
APIs enabled, and permission to create/use Secret Manager secrets. The Firebase
project ID configured in this checkout is `washlink-main`.

1. From the repository root, authenticate and select the project:

   ```powershell
   firebase login
   firebase use washlink-main
   ```

2. Store the Pesapal consumer key and secret in Firebase Secret Manager. The
   Firebase CLI prompts for each value; enter them directly there, never in
   source files, Android configuration, command arguments, or chat:

   ```powershell
   firebase functions:secrets:set PESAPAL_CONSUMER_KEY
   firebase functions:secrets:set PESAPAL_CONSUMER_SECRET
   ```

3. Since the IPN ID is returned only after registering the deployed endpoint,
   set a temporary value for the required secret before the initial deployment:

   ```powershell
   firebase functions:secrets:set PESAPAL_IPN_ID
   ```

   Enter `pending` at the prompt. Choose `sandbox` or `live` for the
   `PESAPAL_ENVIRONMENT` parameter during deployment; use sandbox credentials
   with sandbox mode only. Install Functions dependencies and deploy once so
   the IPN endpoint exists:

   ```powershell
   Push-Location functions
   npm.cmd install
   Pop-Location
   firebase deploy --only functions
   ```

4. After deploying Functions, register
   `https://us-central1-washlink-main.cloudfunctions.net/pesapalIpn` with the
   Pesapal API 3.0 URL-registration endpoint for the selected environment:
   authenticate with `POST /api/Auth/RequestToken`, then submit
   `POST /api/URLSetup/RegisterIPN` with JSON
   `{"url":"https://us-central1-washlink-main.cloudfunctions.net/pesapalIpn","ipn_notification_type":"GET"}`.
   Replace the temporary value with the returned notification ID, then redeploy
   Functions so the callable uses the registered endpoint:

   ```powershell
   firebase functions:secrets:set PESAPAL_IPN_ID
   firebase deploy --only functions
   ```

   The `pesapalReturn` callback URL is included in each order request
   automatically; it must not be used as a payment notification endpoint.

5. Deploy Firestore rules when ready:

   ```powershell
   firebase deploy --only firestore:rules
   ```

The app uses the existing Firebase Android configuration and the `us-central1`
callables. The Pesapal return endpoint redirects into the registered
`washlink://payment-return` app URI. Do not ship a release until a sandbox
payment, callback, IPN, and provider-side status verification have all been
confirmed. Historical Flutterwave refund reconciliation still requires manual
verification in the Flutterwave dashboard.

Local checks that do not require credentials:

```powershell
Push-Location functions
node --test
node --check index.js
Pop-Location
```
