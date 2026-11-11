# Smart Hospital Frontend — Jakarta EE 10 / MySQL Edition

React + TypeScript Arabic RTL frontend for the Smart Hospital academic system. The backend API runs on GlassFish 7 / Jakarta EE 10; the database is MySQL 8.0.38.

## Requirements
- Node.js 26.4.0 (the version installed on the development PC)
- npm 11 or a compatible npm release
- Smart Hospital EAR deployed on GlassFish as `smart-hospital` at `http://localhost:8080` (context root `/`)

## Run locally on Windows

```powershell
cd frontend
Copy-Item .env.example .env
npm install
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api/*` to `http://localhost:8080/api/*` (the deployed EAR uses the root context). Update `vite.config.ts` if the context root changes.

## Authentication and access
The academic backend currently uses the GlassFish `file` realm with HTTP Basic authentication. The frontend retains the authorization value in React memory only; it is not written to localStorage, sessionStorage, cookies or application logs. A browser refresh requires signing in again. A local GlassFish user must have the exact role group, plus a matching active row in `hospital.user_accounts` and active branch membership. MySQL demo seeds create application-account rows only; they do **not** create GlassFish passwords. Use `glassfish/create-demo-users.ps1` to provision the synthetic development users in the File Realm.

The identity endpoint is `GET /api/v1/security/me`; it returns the authenticated account's own user ID, roles, permitted branch IDs and default branch. Backend authorization remains authoritative; hiding a menu item is not a security control.

## Implemented workspaces
- Arabic RTL application shell, responsive navigation and sign-in flow.
- Branch selector populated from the user's allowed branches.
- Operational dashboard, aggregate indicators and explainable decision cards.
- Patient directory, search, detail, registration and update.
- Appointment listing, booking and check-in.
- Clinical encounter workspace, vital signs, notes and diagnoses.
- Laboratory orders and result workflow.
- Pharmacy prescriptions, stock and FEFO dispense workflow.
- Admission and bed management.
- Billing invoices and patient payments.
- Supplier, purchase-order and goods-receipt workspace.
- General-ledger workspace: chart of accounts, trial balance, journal listing, manual journal drafts, posting and reversal drafts.
- Administration workspace: staff, account status, role catalogue and branch-scoped account mapping.
- Governance workspace: operational threshold settings, audit-log browser and notification inbox.

## Development data and safety
This academic frontend uses synthetic demo data only and is not a clinically approved product. Do not enter real patient information. Clinical decisions and result interpretation remain the responsibility of qualified clinicians.

## Commands
- `npm run dev` — development server.
- `npm run typecheck` — TypeScript checks.
- `npm run build` — typecheck and production bundle.
- `npm run preview` — preview the production bundle.

The production bundle (`npm run build`) has been built successfully on the target Windows machine and is packaged into the WAR at the application root. GlassFish File Realm authentication is intended for isolated localhost development, not production deployment.
