import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { OperatorContext } from '../../core/operator-context';
import { PortingRequest } from '../porting-request';
import { PortingRequestForm } from './porting-request-form';

describe('PortingRequestForm', () => {
  let fixture: ComponentFixture<PortingRequestForm>;
  let backend: HttpTestingController;
  let element: HTMLElement;

  beforeEach(async () => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [PortingRequestForm],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.inject(OperatorContext).select('orange');
    backend = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(PortingRequestForm);
    element = fixture.nativeElement;
    await fixture.whenStable();
  });

  afterEach(() => backend.verify());

  it('shows who is submitting', () => {
    expect(element.textContent).toContain("You're submitting as Orange");
  });

  it('US-UI-1-AC2 flags an invalid number before sending and keeps submit disabled', async () => {
    await type('0101234567');

    expect(element.querySelector('.field-error')?.textContent).toContain('11-digit mobile number');
    expect(submitButton().disabled).toBe(true);
    backend.expectNone('/api/porting-requests');
  });

  it('US-UI-1-AC1/AC4 submits the number and confirms the pending request', async () => {
    await type('01012345678');
    expect(submitButton().disabled).toBe(false);

    submitButton().click();
    const request = backend.expectOne('/api/porting-requests');
    expect(request.request.body).toEqual({ phoneNumber: '01012345678' });
    request.flush(pendingRequest());
    await fixture.whenStable();

    expect(element.querySelector('.alert-success')?.textContent).toContain('01012345678');
    expect(element.querySelector('.alert-success')?.textContent).toContain('PENDING');
    expect(element.querySelector('.alert-success')?.textContent).toContain('waiting for Vodafone');
    expect(input().value).toBe('');
  });

  it("US-UI-1-AC3 shows the server's message when the request is refused", async () => {
    await type('01012345678');
    submitButton().click();
    backend
      .expectOne('/api/porting-requests')
      .flush(
        { status: 409, detail: 'Phone number 01012345678 already has a pending porting request' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain(
      'already has a pending porting request',
    );
    expect(submitButton().disabled).toBe(false);
  });

  function input(): HTMLInputElement {
    return element.querySelector('#phoneNumber')!;
  }

  function submitButton(): HTMLButtonElement {
    return element.querySelector('button[type="submit"]')!;
  }

  async function type(value: string): Promise<void> {
    input().value = value;
    input().dispatchEvent(new Event('input'));
    input().dispatchEvent(new Event('blur'));
    await fixture.whenStable();
  }

  function pendingRequest(): PortingRequest {
    return {
      id: '01a16f75-98d6-7022-be1d-787b044382b4',
      phoneNumber: '01012345678',
      recipient: 'orange',
      donor: 'vodafone',
      status: 'PENDING',
      createdAt: '2026-10-24T10:15:30.123Z',
      resolvedAt: null,
    };
  }
});
