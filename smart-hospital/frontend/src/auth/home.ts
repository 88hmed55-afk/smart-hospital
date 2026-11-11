/** The first page a signed-in user should see for their primary role. */
export function homeForRoles(roles: string[]): string {
  if (roles.some((role) => ['HOSPITAL_ADMIN', 'HOSPITAL_MANAGER', 'ACCOUNTANT'].includes(role))) return '/dashboard';
  if (roles.includes('RECEPTIONIST')) return '/patients';
  if (roles.includes('DOCTOR')) return '/appointments';
  if (roles.includes('NURSE')) return '/admissions';
  if (roles.includes('LAB_SUPERVISOR') || roles.includes('LAB_TECHNICIAN')) return '/laboratory';
  if (roles.includes('PHARMACIST')) return '/pharmacy';
  if (roles.includes('INVENTORY_OFFICER')) return '/inventory';
  return '/login';
}
