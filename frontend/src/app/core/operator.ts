/** The operators seeded in the backend; `code` is also the `organization` header value. */
export type OperatorCode = 'vodafone' | 'orange' | 'etisalat';

export interface Operator {
  readonly code: OperatorCode;
  readonly name: string;
}

export const OPERATORS: readonly Operator[] = [
  { code: 'vodafone', name: 'Vodafone' },
  { code: 'orange', name: 'Orange' },
  { code: 'etisalat', name: 'Etisalat' },
];

export function findOperator(code: string): Operator | undefined {
  return OPERATORS.find((operator) => operator.code === code);
}

/** Display name for an operator code from the API, falling back to the code itself. */
export function operatorName(code: string): string {
  return findOperator(code)?.name ?? code;
}
