import { Injectable, signal } from '@angular/core';

import { findOperator, Operator, OperatorCode, OPERATORS } from './operator';

/**
 * The operator the user is acting as (mocked authentication). Remembered across page reloads.
 */
@Injectable({ providedIn: 'root' })
export class OperatorContext {
  static readonly STORAGE_KEY = 'mnp.operator';

  private readonly selected = signal<Operator>(OperatorContext.restore());
  readonly current = this.selected.asReadonly();

  select(code: OperatorCode): void {
    const operator = findOperator(code);
    if (!operator) {
      throw new Error(`Unknown operator: ${code}`);
    }
    this.selected.set(operator);
    localStorage.setItem(OperatorContext.STORAGE_KEY, operator.code);
  }

  private static restore(): Operator {
    return findOperator(localStorage.getItem(OperatorContext.STORAGE_KEY) ?? '') ?? OPERATORS[0];
  }
}
