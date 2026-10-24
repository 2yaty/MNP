import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OperatorContext } from '../../core/operator-context';
import { Page, PortingRequest, PortingStatus } from '../porting-request';
import { PortingRequestList } from './porting-request-list';

describe('PortingRequestList', () => {
  let fixture: ComponentFixture<PortingRequestList>;
  let backend: HttpTestingController;
  let element: HTMLElement;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      imports: [PortingRequestList],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(OperatorContext).select('vodafone');
    backend = TestBed.inject(HttpTestingController);
  });

  afterEach(() => backend.verify());

  it('US-UI-2-AC2 shows each request with its number, operators, status and times', async () => {
    await render(page([request('1', 'ACCEPTED', { resolvedAt: '2026-10-24T10:16:00Z' })]));

    const cells = [...element.querySelectorAll('tbody tr:first-child td')].map((cell) =>
      cell.textContent?.trim(),
    );
    expect(cells.slice(0, 4)).toEqual(['01012345678', 'Orange', 'Vodafone', 'ACCEPTED']);
    expect(cells[4]).toContain('2026');
    expect(cells[5]).toContain('2026');
  });

  it('offers accept and reject only on pending requests where the acting operator is the donor', async () => {
    await render(
      page([
        request('mine-pending', 'PENDING'),
        request('mine-accepted', 'ACCEPTED'),
        request('theirs-pending', 'PENDING', { donor: 'etisalat', recipient: 'vodafone' }),
      ]),
    );

    const actionsPerRow = [...element.querySelectorAll('tbody tr')].map(
      (row) => row.querySelectorAll('.actions button').length,
    );
    expect(actionsPerRow).toEqual([2, 0, 0]);
  });

  it('accepts a request and reloads the page', async () => {
    await render(page([request('abc', 'PENDING')]));

    buttonNamed('Accept').click();
    backend.expectOne('/api/porting-requests/abc/accept').flush(request('abc', 'ACCEPTED'));
    backend.expectOne(listUrl(0)).flush(page([request('abc', 'ACCEPTED')]));
    await fixture.whenStable();

    expect(element.querySelector('.status')?.textContent).toBe('ACCEPTED');
  });

  it('rejects a request', async () => {
    await render(page([request('abc', 'PENDING')]));

    buttonNamed('Reject').click();
    backend.expectOne('/api/porting-requests/abc/reject').flush(request('abc', 'REJECTED'));
    backend.expectOne(listUrl(0)).flush(page([request('abc', 'REJECTED')]));
    await fixture.whenStable();

    expect(element.querySelector('.status')?.textContent).toBe('REJECTED');
  });

  it("shows the server's message when an answer is refused, and the request's real state", async () => {
    await render(page([request('abc', 'PENDING')]));

    buttonNamed('Accept').click();
    backend
      .expectOne('/api/porting-requests/abc/accept')
      .flush(
        { status: 409, detail: 'Porting request abc is already CANCELED' },
        { status: 409, statusText: 'Conflict' },
      );
    backend.expectOne(listUrl(0)).flush(page([request('abc', 'CANCELED')]));
    await fixture.whenStable();

    expect(element.querySelector('[role="alert"]')?.textContent).toContain('already CANCELED');
    expect(element.querySelector('.status')?.textContent).toBe('CANCELED');
  });

  it('moves between pages', async () => {
    await render(page([request('1', 'PENDING')], { page: 0, totalItems: 15, totalPages: 2 }));
    expect(buttonNamed('Previous').disabled).toBe(true);
    expect(element.querySelector('.pager')?.textContent).toContain('Page 1 of 2');

    buttonNamed('Next').click();
    backend
      .expectOne(listUrl(1))
      .flush(page([request('2', 'PENDING')], { page: 1, totalItems: 15, totalPages: 2 }));
    await fixture.whenStable();

    expect(element.querySelector('.pager')?.textContent).toContain('Page 2 of 2');
    expect(buttonNamed('Next').disabled).toBe(true);
  });

  it('US-UI-2-AC1 reloads from the first page when the acting operator changes', async () => {
    await render(page([request('1', 'PENDING')]));

    TestBed.inject(OperatorContext).select('etisalat');
    await fixture.whenStable();

    backend.expectOne(listUrl(0)).flush(page([]));
    await fixture.whenStable();
    expect(element.textContent).toContain('No porting requests to show.');
  });

  async function render(firstPage: Page<PortingRequest>): Promise<void> {
    fixture = TestBed.createComponent(PortingRequestList);
    element = fixture.nativeElement;
    await fixture.whenStable();
    backend.expectOne(listUrl(0)).flush(firstPage);
    await fixture.whenStable();
  }

  function buttonNamed(name: string): HTMLButtonElement {
    const button = [...element.querySelectorAll('button')].find(
      (candidate) => candidate.textContent?.trim() === name,
    );
    if (!button) {
      throw new Error(`No button named ${name}`);
    }
    return button;
  }

  function listUrl(pageNumber: number): string {
    return `/api/porting-requests?page=${pageNumber}&size=${PortingRequestList.PAGE_SIZE}`;
  }

  function page(
    items: PortingRequest[],
    overrides: Partial<Page<PortingRequest>> = {},
  ): Page<PortingRequest> {
    return { items, page: 0, size: 10, totalItems: items.length, totalPages: 1, ...overrides };
  }

  function request(
    id: string,
    status: PortingStatus,
    overrides: Partial<PortingRequest> = {},
  ): PortingRequest {
    return {
      id,
      phoneNumber: '01012345678',
      recipient: 'orange',
      donor: 'vodafone',
      status,
      createdAt: '2026-10-24T10:15:30.123Z',
      resolvedAt: status === 'PENDING' ? null : '2026-10-24T10:16:00Z',
      ...overrides,
    };
  }
});
