import type { ApiProblem } from '../types/api';

const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '');

export class ApiError extends Error {
  status: number;
  code?: string;

  constructor(status: number, message: string, code?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

export async function apiRequest<T>(
  path: string,
  token: string,
  init: RequestInit = {},
): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (token) headers.set('Authorization', token);
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');

  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      ...init,
      headers,
      credentials: 'same-origin',
    });
  } catch {
    throw new ApiError(0, 'تعذر الاتصال بالخادم. تحقق من تشغيل GlassFish وإعداد عنوان API.');
  }

  if (!response.ok) {
    let problem: ApiProblem = {};
    try { problem = await response.json() as ApiProblem; } catch { /* non-JSON server response */ }
    const fallback = response.status === 401 || response.status === 403
      ? 'تعذر تسجيل الدخول أو لا تملك صلاحية تنفيذ هذه العملية.'
      : `حدث خطأ أثناء الاتصال بالخادم (${response.status}).`;
    throw new ApiError(response.status, problem.message || problem.detail || fallback, problem.code);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}
