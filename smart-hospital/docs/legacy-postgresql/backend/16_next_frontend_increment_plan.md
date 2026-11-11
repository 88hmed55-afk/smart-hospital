# Next Increment Plan — React UI Foundation

The next implementation increment starts the React + TypeScript frontend after the v0.9 backend API contract is frozen for this milestone.

## Proposed frontend foundation
- Vite + React + TypeScript.
- Arabic RTL-first shell with English-ready localization structure.
- React Router for module navigation.
- TanStack Query for server-state fetching and cache invalidation.
- React Hook Form + Zod for forms and client-side input feedback; backend validation remains authoritative.
- Accessible component primitives and responsive layout.
- Separate route/layout groups for reception, doctor, nurse, laboratory, pharmacy, accountant and management.
- API client with typed DTOs, request correlation IDs and safe error display.
- Authentication flow for the local GlassFish development realm; avoid persisting plaintext credentials or exposing secrets to logs.

## Initial UI routes
- `/login`
- `/app/dashboard`
- `/app/patients`
- `/app/appointments`
- `/app/clinical/encounters`
- `/app/laboratory`
- `/app/pharmacy`
- `/app/admissions`
- `/app/billing`
- `/app/analytics`
- `/app/administration`

The frontend does not connect to PostgreSQL and does not duplicate backend business rules. It displays permissions returned by the server for usability only; the backend remains the authorization boundary.
