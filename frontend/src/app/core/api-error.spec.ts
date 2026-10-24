import { HttpErrorResponse } from '@angular/common/http';

import { describeApiError } from './api-error';

describe('describeApiError', () => {
  it('lists field errors from a validation problem', () => {
    const error = new HttpErrorResponse({
      status: 400,
      error: {
        status: 400,
        detail: 'Request validation failed',
        errors: { phoneNumber: 'must be an 11-digit mobile number starting with 01' },
      },
    });

    expect(describeApiError(error)).toBe(
      'phoneNumber must be an 11-digit mobile number starting with 01',
    );
  });

  it("uses the problem's detail otherwise", () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: {
        status: 409,
        detail: 'Phone number 01012345678 already has a pending porting request',
      },
    });

    expect(describeApiError(error)).toBe(
      'Phone number 01012345678 already has a pending porting request',
    );
  });

  it('explains when the server cannot be reached', () => {
    expect(describeApiError(new HttpErrorResponse({ status: 0 }))).toContain(
      'Cannot reach the server',
    );
  });

  it('falls back to a generic message', () => {
    expect(describeApiError(new Error('boom'))).toBe('Something went wrong. Please try again.');
    expect(describeApiError(new HttpErrorResponse({ status: 500, error: 'oops' }))).toBe(
      'Something went wrong. Please try again.',
    );
  });
});
