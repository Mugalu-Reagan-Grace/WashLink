# WashLink Operations

Responsive browser workspace for existing provider and administrator accounts.
Customers continue to use the Android app.

## Run locally

The Firebase Web app is registered in `washlink-main`. The local Firebase config
is in the ignored `web/.env.local`; copy `web/.env.example` and fill it from
Firebase Console if setting up another environment.

```powershell
npm install --prefix web
npm run dev --prefix web
```

The signed-in account must have a verified email and an active `provider` or
`admin` role in `users/{uid}`. Firestore rules remain responsible for data
access; the browser UI is not an authorization boundary.

## Build and deploy

```powershell
npm run build --prefix web
firebase deploy --only hosting --project washlink-main
```

The provider order-status and rider-assignment actions call the existing
`updateBookingStatus` and `assignBookingRider` Functions. Those Functions must
be deployed before these actions can work; Firebase currently requires the
Blaze plan for that deployment. Admin account suspension and provider approval
are restricted by the deployed Firestore rules.
