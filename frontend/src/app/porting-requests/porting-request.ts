import { OperatorCode } from '../core/operator';

export type PortingStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CANCELED';

/** A porting request as the API returns it; operators appear by code. */
export interface PortingRequest {
  readonly id: string;
  readonly phoneNumber: string;
  readonly recipient: OperatorCode;
  readonly donor: OperatorCode;
  readonly status: PortingStatus;
  readonly createdAt: string;
  readonly resolvedAt: string | null;
}

/** One page of results; `page` is zero-based. */
export interface Page<T> {
  readonly items: readonly T[];
  readonly page: number;
  readonly size: number;
  readonly totalItems: number;
  readonly totalPages: number;
}

/** Same rule as the backend's @MobileNumber: 11 digits starting with 01. */
export const MOBILE_NUMBER_PATTERN = /^01\d{9}$/;
