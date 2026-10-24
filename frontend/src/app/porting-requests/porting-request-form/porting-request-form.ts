import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { describeApiError } from '../../core/api-error';
import { operatorName } from '../../core/operator';
import { OperatorContext } from '../../core/operator-context';
import { MOBILE_NUMBER_PATTERN, PortingRequest } from '../porting-request';
import { PortingRequestApi } from '../porting-request-api';

/** Submits a porting request for one phone number, as the acting operator. */
@Component({
  selector: 'app-porting-request-form',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './porting-request-form.html',
  styleUrl: './porting-request-form.css',
})
export class PortingRequestForm {
  private readonly api = inject(PortingRequestApi);
  protected readonly operator = inject(OperatorContext).current;
  protected readonly operatorName = operatorName;

  protected readonly form = inject(NonNullableFormBuilder).group({
    phoneNumber: ['', [Validators.required, Validators.pattern(MOBILE_NUMBER_PATTERN)]],
  });

  protected readonly submitting = signal(false);
  protected readonly created = signal<PortingRequest | null>(null);
  protected readonly errorMessage = signal<string | null>(null);

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.created.set(null);
    this.errorMessage.set(null);

    this.api.submit(this.form.getRawValue().phoneNumber).subscribe({
      next: (request) => {
        this.created.set(request);
        this.form.reset();
        this.submitting.set(false);
      },
      error: (error: unknown) => {
        this.errorMessage.set(describeApiError(error));
        this.submitting.set(false);
      },
    });
  }
}
