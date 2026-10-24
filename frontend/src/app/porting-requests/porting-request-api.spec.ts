import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { PortingRequestApi } from './porting-request-api';

describe('PortingRequestApi', () => {
  let api: PortingRequestApi;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PortingRequestApi);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('submits one phone number', () => {
    api.submit('01012345678').subscribe();

    const request = backend.expectOne('/api/porting-requests');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ phoneNumber: '01012345678' });
  });

  it('asks for a page of requests', () => {
    api.list(2, 10).subscribe();

    const request = backend.expectOne('/api/porting-requests?page=2&size=10');
    expect(request.request.method).toBe('GET');
  });

  it('accepts and rejects by id', () => {
    api.accept('abc').subscribe();
    api.reject('def').subscribe();

    expect(backend.expectOne('/api/porting-requests/abc/accept').request.method).toBe('POST');
    expect(backend.expectOne('/api/porting-requests/def/reject').request.method).toBe('POST');
  });
});
