import { Routes } from '@angular/router';

import { PortingRequestForm } from './porting-requests/porting-request-form/porting-request-form';
import { PortingRequestList } from './porting-requests/porting-request-list/porting-request-list';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'requests' },
  { path: 'requests', component: PortingRequestList, title: 'Porting requests · MNP' },
  { path: 'requests/new', component: PortingRequestForm, title: 'New porting request · MNP' },
  { path: '**', redirectTo: 'requests' },
];
