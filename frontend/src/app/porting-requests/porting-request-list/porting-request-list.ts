import { DatePipe } from '@angular/common';
import { Component, effect, inject, signal, untracked } from '@angular/core';
import { Observable } from 'rxjs';

import { describeApiError } from '../../core/api-error';
import { operatorName } from '../../core/operator';
import { OperatorContext } from '../../core/operator-context';
import { Page, PortingRequest } from '../porting-request';
import { PortingRequestApi } from '../porting-request-api';

/**
 * The requests the acting operator may see, newest first. The donor of a pending request can accept or
 * reject it here.
 */
@Component({
  selector: 'app-porting-request-list',
  imports: [DatePipe],
  templateUrl: './porting-request-list.html',
  styleUrl: './porting-request-list.css',
})
export class PortingRequestList {
  static readonly PAGE_SIZE = 10;

  private readonly api = inject(PortingRequestApi);
  protected readonly operator = inject(OperatorContext).current;
  protected readonly operatorName = operatorName;

  protected readonly result = signal<Page<PortingRequest> | null>(null);
  protected readonly loading = signal(false);
  protected readonly answeringId = signal<string | null>(null);
  /** Kept apart so the reload after a refused answer doesn't wipe out the reason it was refused. */
  protected readonly loadError = signal<string | null>(null);
  protected readonly answerError = signal<string | null>(null);

  constructor() {
    // Each operator sees a different set of requests, so start again from the first page when it changes.
    effect(() => {
      this.operator();
      untracked(() => {
        this.answerError.set(null);
        this.load(0);
      });
    });
  }

  protected load(page: number): void {
    this.loading.set(true);
    this.loadError.set(null);
    this.api.list(page, PortingRequestList.PAGE_SIZE).subscribe({
      next: (result) => {
        this.result.set(result);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        this.loadError.set(describeApiError(error));
        this.loading.set(false);
      },
    });
  }

  protected canAnswer(request: PortingRequest): boolean {
    return request.status === 'PENDING' && request.donor === this.operator().code;
  }

  protected accept(request: PortingRequest): void {
    this.answer(request, this.api.accept(request.id));
  }

  protected reject(request: PortingRequest): void {
    this.answer(request, this.api.reject(request.id));
  }

  private answer(request: PortingRequest, call: Observable<PortingRequest>): void {
    this.answeringId.set(request.id);
    this.answerError.set(null);
    call.subscribe({
      next: () => {
        this.answeringId.set(null);
        this.load(this.result()?.page ?? 0);
      },
      error: (error: unknown) => {
        this.answeringId.set(null);
        this.answerError.set(describeApiError(error));
        // The request may have changed meanwhile (for example, canceled by the timeout); show its real state.
        this.load(this.result()?.page ?? 0);
      },
    });
  }
}
