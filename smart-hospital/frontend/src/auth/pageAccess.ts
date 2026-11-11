/**
 * Single source of truth for which roles may open each module.
 *
 * The lists mirror the read endpoints' `@RequiresRoles` annotations on the API.
 * Keeping them identical means the sidebar never advertises a screen the caller
 * would only be able to open as a 403, and the layout can bounce direct URL
 * visits back to the caller's home screen.
 */
export const pageRoles: Record<string, string[]> = {
  '/dashboard': ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'ACCOUNTANT'],
  '/security': [
    'HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'RECEPTIONIST', 'DOCTOR', 'NURSE',
    'LAB_TECHNICIAN', 'LAB_SUPERVISOR', 'PHARMACIST', 'ACCOUNTANT', 'INVENTORY_OFFICER',
  ],
  '/patients': ['HOSPITAL_ADMIN', 'RECEPTIONIST', 'DOCTOR', 'NURSE'],
  '/appointments': ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'RECEPTIONIST', 'DOCTOR'],
  '/clinical': ['DOCTOR', 'NURSE'],
  '/laboratory': ['LAB_TECHNICIAN', 'LAB_SUPERVISOR'],
  '/pharmacy': ['PHARMACIST'],
  '/admissions': ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'DOCTOR', 'NURSE'],
  '/billing': ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'ACCOUNTANT'],
  '/accounting': ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'ACCOUNTANT'],
  '/inventory': ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'INVENTORY_OFFICER'],
  '/analytics': ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'ACCOUNTANT'],
  '/administration': ['HOSPITAL_ADMIN'],
  '/settings': ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER'],
  '/audit': ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER'],
};

export function canAccessPage(path: string, roles: string[] | undefined): boolean {
  const allowed = pageRoles[path];
  if (!allowed) return true;
  return (roles ?? []).some((role) => allowed.includes(role));
}
