import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { Page, PortingRequest } from './porting-request';

/** Calls the backend's porting request endpoints; the interceptor adds the `organization` header. */
@Injectable({ providedIn: 'root' })
export class PortingRequestApi {
  static readonly BASE_URL = '/api/porting-requests';

  private readonly http = inject(HttpClient);

  /** The acting operator is the recipient; the donor is worked out by the backend. */
  submit(phoneNumber: string): Observable<PortingRequest> {
    return this.http.post<PortingRequest>(PortingRequestApi.BASE_URL, { phoneNumber });
  }

  list(page: number, size: number): Observable<Page<PortingRequest>> {
    return this.http.get<Page<PortingRequest>>(PortingRequestApi.BASE_URL, {
      params: { page, size },
    });
  }

  accept(id: string): Observable<PortingRequest> {
    return this.http.post<PortingRequest>(`${PortingRequestApi.BASE_URL}/${id}/accept`, null);
  }

  reject(id: string): Observable<PortingRequest> {
    return this.http.post<PortingRequest>(`${PortingRequestApi.BASE_URL}/${id}/reject`, null);
  }
}
