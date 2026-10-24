import { TestBed } from '@angular/core/testing';

import { OperatorContext } from './operator-context';

describe('OperatorContext', () => {
  beforeEach(() => localStorage.clear());

  it('starts as Vodafone when nothing was chosen before', () => {
    expect(TestBed.inject(OperatorContext).current().code).toBe('vodafone');
  });

  it('switches the acting operator and remembers it', () => {
    TestBed.inject(OperatorContext).select('orange');

    expect(TestBed.inject(OperatorContext).current().name).toBe('Orange');
    expect(localStorage.getItem(OperatorContext.STORAGE_KEY)).toBe('orange');
  });

  it('restores the operator chosen in an earlier visit', () => {
    localStorage.setItem(OperatorContext.STORAGE_KEY, 'etisalat');

    expect(TestBed.inject(OperatorContext).current().code).toBe('etisalat');
  });

  it('ignores an unknown stored value', () => {
    localStorage.setItem(OperatorContext.STORAGE_KEY, 'we');

    expect(TestBed.inject(OperatorContext).current().code).toBe('vodafone');
  });
});
