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
Set `VITE_WASHLINK_API_BASE_URL` to the hosted API's HTTPS origin in the
environment used to build the web client. Set the API's `PUBLIC_WEB_ORIGIN` to
the exact deployed web origin so its CORS policy permits requests.

## Build and deploy

```powershell
npm run build --prefix web
firebase deploy --only hosting --project washlink-main
```

Provider order-status and rider-assignment actions use the standalone WashLink
API, not Firebase Cloud Functions. Admin account suspension and provider
approval continue to use Firestore and are restricted by the deployed rules.
