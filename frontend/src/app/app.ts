import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { OperatorCode, OPERATORS } from './core/operator';
import { OperatorContext } from './core/operator-context';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  private readonly operatorContext = inject(OperatorContext);
  protected readonly operator = this.operatorContext.current;
  protected readonly operators = OPERATORS;

  protected selectOperator(event: Event): void {
    this.operatorContext.select((event.target as HTMLSelectElement).value as OperatorCode);
  }
}
