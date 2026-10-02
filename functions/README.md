# Flutterwave checkout

Bookings are created by the authenticated `createBooking` callable, which checks
the customer/provider accounts and recalculates the amount from the provider's
current listed service. Firestore rules prohibit client-created bookings and
allow providers to accept only cash bookings registered by the backend or
Flutterwave bookings verified as paid.

The Android app starts card, Airtel Money, and MTN Mobile Money payments through
the authenticated `initializeFlutterwaveCheckout` callable. Only
`verifyFlutterwavePayment` or a hash-authenticated webhook can mark an online
booking paid; the Android redirect is only a request to verify, never proof of
payment. Cash on delivery calls the same authenticated booking/payment checks,
but does not create a hosted Flutterwave checkout.

Bookings and provider status changes are also handled by authenticated
callables. Status changes atomically persist the customer in-app event and send
an FCM push when the recipient has registered a device token. Customers receive
status updates; providers receive new booking requests. The first app launch
after sign-in requests Android notification permission where required.

Customers can cancel only a still-booked order before a payment attempt starts.
The cancellation callable checks ownership and commits the cancelled status and
customer notification together, then notifies the provider. Once an online
checkout has begun or payment has completed, cancellation/refunds must be
handled through support; automated refunds are not implemented.

## One-time project and provider setup

Use Node.js 20 and the Firebase CLI. Cloud Functions deployment requires a
Firebase project on the Blaze billing plan, Cloud Functions and Secret Manager
APIs enabled, and permission to create/use Secret Manager secrets. The Firebase
project ID configured in this checkout is `washlink-main`.

1. In the Flutterwave dashboard, enable UGX card and Uganda mobile-money
   payment methods. Set the webhook URL to:
   `https://us-central1-washlink-main.cloudfunctions.net/flutterwaveWebhook`
2. Set a webhook secret/hash in the Flutterwave dashboard. Keep it private.
3. From the repository root, authenticate and select the project:

   ```powershell
   firebase login
   firebase use washlink-main
   ```

4. Store the Flutterwave API secret key and the matching webhook hash in
   Firebase Secret Manager. The Firebase CLI prompts for each value; enter it
   directly there, never in source files, Android configuration, command
   arguments, or chat:

   ```powershell
   firebase functions:secrets:set FLUTTERWAVE_SECRET_KEY
   firebase functions:secrets:set FLUTTERWAVE_WEBHOOK_HASH
   ```

5. Install the Functions dependencies and deploy when ready:

   ```powershell
   Push-Location functions
   npm.cmd install
   Pop-Location
   firebase deploy --only functions
   firebase deploy --only firestore:rules
   ```

The app uses the existing Firebase Android configuration and the `us-central1`
callables. The hosted return endpoint redirects into the registered
`washlink://payment-return` app URI. Configure the Flutterwave webhook URL in
the dashboard after deployment. Do not ship a release until the backend and
rules have been deployed and a successful test-mode payment plus a signed
webhook have been confirmed.

Local checks that do not require credentials:

```powershell
Push-Location functions
node --test
node --check index.js
Pop-Location
```
