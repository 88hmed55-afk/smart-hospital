# Administration, Staff and Identity Mapping — v0.13

## Scope

This increment adds the first operational administration workspace for the Smart Hospital academic system:

- Staff directory scoped to the authenticated administrator's branch memberships.
- Create and update staff profiles, including employment status.
- Hospital role catalogue.
- Map a pre-created GlassFish File Realm principal to one staff profile, an exact set of hospital roles, and one or more authorized branches.
- Enable or disable a hospital account.
- Audit staff and account administration events.

No database migration is required. The implementation uses the existing V001–V010 schema (64 tables).

## REST contract

All endpoints are under `/api/v1/admin` and require `HOSPITAL_ADMIN`:

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/staff?branchId=...&q=...&status=...&limit=...` | Search branch staff |
| POST | `/staff` | Create staff profile |
| PUT | `/staff/{staffId}` | Update staff fields/status |
| GET | `/roles` | List role catalogue |
| GET | `/accounts?branchId=...&limit=...` | List account summaries visible in a branch |
| POST | `/accounts` | Map an existing GlassFish user to staff/roles/branches |
| PATCH | `/accounts/{accountId}/status` | Enable/disable a hospital account |

The service limits output to branch scope. The account list returns only branch memberships that the current administrator is also authorized to see. A global account-status change is rejected unless the administrator has access to every branch assigned to that account.

## GlassFish File Realm separation

Authentication credentials remain outside the application database and outside this React client. The admin workspace does **not** create or change GlassFish passwords. Provisioning occurs in two steps:

1. Create the GlassFish principal interactively in the GlassFish `bin` directory, using exactly the role groups selected in the administration form. Example (replace the groups and username with the intended values):

   ```powershell
   .\asadmin.bat create-file-user --groups HOSPITAL_MANAGER,ACCOUNTANT finance.user
   ```

   GlassFish prompts for the password. Never place the password in command arguments, source code, API requests, database seed files or screenshots.

2. In Smart Hospital, create the hospital account mapping for that username, select the same role codes and select only branches within the administrator's assigned branch scope.

The role-code set in `hospital.user_roles` must match the caller's GlassFish application role groups exactly. `CurrentUserService` rejects both missing groups and extra application groups. Every role code in the database role catalogue must remain mapped in `glassfish-web.xml`.

This is deliberately strict: an extra `HOSPITAL_ADMIN` GlassFish group must not silently grant application privileges when the hospital database account does not assign that role.

## Employee status and account lifecycle

- A staff member in `ON_LEAVE`, `SUSPENDED` or `TERMINATED` status has their linked hospital account disabled.
- `CurrentUserService` rejects an account whose linked staff profile is not `ACTIVE` even if a File Realm principal still authenticates.
- Changing the staff member back to `ACTIVE` does not automatically re-enable the hospital account. An administrator must explicitly re-enable it after reviewing the account and GlassFish group mapping.
- Account status is global. The service prevents an administrator from changing it when the account has branch memberships outside the administrator's branch scope. An inactive staff member cannot have their account re-enabled until their staff status is active. Disabling a `HOSPITAL_ADMIN` account locks the catalogue role row before counting other active administrators, serializing concurrent attempts so the final active administrator cannot be removed by two simultaneous requests.
- Staff history is retained; there is no physical deletion operation.

## Password and account flags

The database's `password_hash` field contains a non-secret marker (`GLASSFISH_FILE_REALM_ONLY`) for this authentication mode. It is not a password hash and must never be used as a credential. `must_change_password` is currently an administrative metadata flag; the academic File Realm integration does not enforce an in-application password-change workflow. Password reset and rotation remain a GlassFish administration action for this increment.

## UI

The React page at `/administration` contains:

- Staff directory, search, employment status filter and staff create/edit forms.
- Account list and enable/disable controls.
- Role catalogue.
- Account mapping form with branch/role selections and an explicit File Realm setup checklist.

The UI never stores credentials and does not expose the non-secret database marker.

## Security notes and limitations

- The administration REST resource and EJB service both enforce `HOSPITAL_ADMIN`.
- Branch access is revalidated server-side; frontend filtering is convenience only.
- Role changes after initial account creation are not supported by this UI increment. When changing an account's role set operationally, GlassFish groups and `hospital.user_roles` must be changed together under a reviewed administrative procedure; the strict exact-match check is expected to reject mismatches.
- The GlassFish File Realm + HTTP Basic arrangement is only for isolated localhost academic development. It is not an approved production identity design. Production deployment requires HTTPS, a reviewed identity provider, password lifecycle, session controls and security assessment.
