export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: Record<string, string>;
  /** Quoted to support for errors on our side, so the matching log entry can be found. */
  readonly requestId: string | null;

  constructor(status: number, code: string, message: string, fields: Record<string, string> = {}, requestId: string | null = null) {
    super(message);
    this.status = status;
    this.code = code;
    this.fields = fields;
    this.requestId = requestId;
  }
}

/** The reference to quote for an unexpected error, if there is one. */
export function errorReference(error: unknown): string | null {
  if (!(error instanceof ApiError) || !error.requestId || (error.status < 500 && error.status !== 0)) return null;
  return error.requestId.slice(0, 8);
}

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((part) => part.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

export async function ensureCsrfCookie(): Promise<void> {
  if (!readCookie('XSRF-TOKEN')) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' });
  }
}

async function send<T>(method: Method, path: string, body?: unknown, retried = false): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  const isForm = body instanceof FormData;
  if (body !== undefined && !isForm) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET') {
    await ensureCsrfCookie();
    headers['X-XSRF-TOKEN'] = decodeURIComponent(readCookie('XSRF-TOKEN') ?? '');
  }
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      credentials: 'same-origin',
      body: body === undefined ? undefined : isForm ? body : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(
      0,
      'offline',
      navigator.onLine
        ? "We couldn't reach the portal. Please try again in a moment."
        : "You're offline. Check your internet connection and try again.",
    );
  }
  if (response.status === 204) {
    return undefined as T;
  }
  const isJson = response.headers.get('content-type')?.includes('json');
  const data = isJson ? await response.json() : null;
  if (!response.ok) {
    if (response.status === 403 && data?.code === 'csrf' && !retried) {
      await fetch('/api/auth/csrf', { credentials: 'same-origin' });
      return send<T>(method, path, body, true);
    }
    throw new ApiError(
      response.status,
      data?.code ?? 'error',
      data?.detail ??
        (response.status >= 500
          ? "The portal isn't responding right now. Please try again in a minute."
          : 'Something went wrong. Please try again.'),
      data?.fields ?? {},
      data?.requestId ?? response.headers.get('X-Request-Id'),
    );
  }
  return data as T;
}

export const api = {
  get: <T>(path: string) => send<T>('GET', path),
  post: <T>(path: string, body?: unknown) => send<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => send<T>('PUT', path, body),
  delete: <T>(path: string) => send<T>('DELETE', path),
};

/** Builds a query string from the values that are set. */
export function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '' && value !== false) {
      search.set(key, String(value));
    }
  });
  const text = search.toString();
  return text ? `?${text}` : '';
}
