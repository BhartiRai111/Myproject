import { ApiErrorResponse } from '../types/user';

export interface ParsedApiError {
  /** A single, ready-to-display sentence explaining exactly what went wrong. */
  message: string;
  /** Per-field reasons (backend DTO field name -> reason), for inline highlighting. */
  fieldErrors: Record<string, string>;
}

function humanizeField(field: string): string {
  const spaced = field.replace(/([A-Z])/g, ' $1');
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

/**
 * Turns an axios error from an API call into one clear sentence plus a
 * field-error map, so the UI can always say exactly what went wrong
 * (bad value, which field, or the server being unreachable) instead of a
 * generic "failed" message.
 */
export function parseApiError(err: any, fallbackMessage: string): ParsedApiError {
  if (!err?.response) {
    // The request never got an HTTP response at all: network down, CORS
    // blocked, or the backend isn't running -- not a validation problem.
    return {
      message: 'Could not reach the server. Please check your internet connection and that the backend is running, then try again.',
      fieldErrors: {},
    };
  }

  const apiError: ApiErrorResponse | undefined = err.response.data;
  const fieldErrors = apiError?.fieldErrors || {};
  const entries = Object.entries(fieldErrors);

  if (entries.length > 0) {
    const details = entries.map(([field, reason]) => `${humanizeField(field)}: ${reason}`).join(' | ');
    return { message: details, fieldErrors };
  }

  return { message: apiError?.message || fallbackMessage, fieldErrors: {} };
}
