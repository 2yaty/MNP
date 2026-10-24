import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import { OperatorContext } from './core/operator-context';

describe('App', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ imports: [App], providers: [provideRouter([])] });
  });

  it('offers the three operators to act as', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const options = [...fixture.nativeElement.querySelectorAll('select option')].map(
      (option: HTMLOptionElement) => option.textContent,
    );
    expect(options).toEqual(['Vodafone', 'Orange', 'Etisalat']);
  });

  it('switches the acting operator from the picker', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('select');

    select.value = 'etisalat';
    select.dispatchEvent(new Event('change'));

    expect(TestBed.inject(OperatorContext).current().code).toBe('etisalat');
  });
});
