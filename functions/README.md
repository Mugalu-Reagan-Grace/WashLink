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
checkout has begun, cancellation remains blocked. If a provider rejects a
Flutterwave-paid booking while it is still `BOOKED`, the backend requests a
full refund and changes the payment state to `REFUND_PENDING`. A successful
refund API response only means Flutterwave accepted the request; this version
does not claim that the refund has reached the customer or automatically
reconcile its final status. Uncertain requests must be checked in Flutterwave
before anyone retries them.

Providers can save one private Uganda payout destination: a bank account
(including a bank branch where required), Airtel Money, or MTN Mobile Money.
The destination is stored under the provider's owner-only `users/{uid}` data,
not in the publicly readable provider profile. After a Flutterwave-paid booking
is delivered, the backend transfers its `subtotal` in UGX; the `serviceFee` is
retained. A scheduled worker and authenticated transfer webhook reconcile the
transfer using its stable booking reference. The existing `flutterwaveWebhook`
endpoint handles both payment and transfer events.

Cash-on-delivery bookings are collected directly by the provider and do not
create an automatic transfer or fee withholding. Providers receive the full
cash collection under this policy. Flutterwave payouts require approved KYC,
Transfers enabled, server IP allowlisting, and sufficient UGX wallet balance.
Never save provider bank/mobile-money details in `providers/{uid}`.

## One-time project and provider setup

Use Node.js 20 and the Firebase CLI. Cloud Functions deployment requires a
Firebase project on the Blaze billing plan, Cloud Functions and Secret Manager
APIs enabled, and permission to create/use Secret Manager secrets. The Firebase
project ID configured in this checkout is `washlink-main`.

1. In the Flutterwave dashboard, enable UGX card and Uganda mobile-money
   payment methods and UGX transfers. Set the payment webhook URL to:
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
rules have been deployed and a successful test-mode payment plus signed payment
and transfer webhook events have been confirmed. Refund final-status
reconciliation still requires manual verification in the Flutterwave dashboard.

Local checks that do not require credentials:

```powershell
Push-Location functions
node --test
node --check index.js
Pop-Location
```
