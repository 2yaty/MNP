import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { OperatorContext } from './operator-context';
import { organizationInterceptor } from './organization.interceptor';

describe('organizationInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([organizationInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('US-UI-1-AC1 sends the acting operator as the organization header on API calls', () => {
    TestBed.inject(OperatorContext).select('orange');

    http.get('/api/porting-requests').subscribe();

    expect(backend.expectOne('/api/porting-requests').request.headers.get('organization')).toBe(
      'orange',
    );
  });

  it('follows the operator when it changes', () => {
    const context = TestBed.inject(OperatorContext);
    context.select('orange');
    http.get('/api/a').subscribe();
    context.select('etisalat');
    http.get('/api/b').subscribe();

    expect(backend.expectOne('/api/a').request.headers.get('organization')).toBe('orange');
    expect(backend.expectOne('/api/b').request.headers.get('organization')).toBe('etisalat');
  });

  it('leaves non-API requests alone', () => {
    http.get('/assets/config.json').subscribe();

    expect(backend.expectOne('/assets/config.json').request.headers.has('organization')).toBe(
      false,
    );
  });
});
