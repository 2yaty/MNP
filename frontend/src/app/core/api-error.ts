import { HttpErrorResponse } from '@angular/common/http';

/** The backend's RFC 9457 error body; validation errors add a field → message map. */
interface ProblemDetail {
  readonly status: number;
  readonly detail?: string;
  readonly errors?: Readonly<Record<string, string>>;
}

/** A message for the user, taken from the backend's problem details when there are any. */
export function describeApiError(error: unknown): string {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return 'Cannot reach the server. Check that the backend is running.';
    }
    const problem = error.error as Partial<ProblemDetail> | null;
    const fieldErrors = Object.entries(problem?.errors ?? {}).map(
      ([field, message]) => `${field} ${message}`,
    );
    if (fieldErrors.length > 0) {
      return fieldErrors.join('; ');
    }
    if (problem?.detail) {
      return problem.detail;
    }
  }
  return 'Something went wrong. Please try again.';
}
