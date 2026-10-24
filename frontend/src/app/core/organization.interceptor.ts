import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { OperatorContext } from './operator-context';

export const ORGANIZATION_HEADER = 'organization';

/** Sends the acting operator on every API call, the way the backend identifies the caller. */
export const organizationInterceptor: HttpInterceptorFn = (request, next) => {
  if (!request.url.startsWith('/api/')) {
    return next(request);
  }
  const operator = inject(OperatorContext).current();
  return next(request.clone({ setHeaders: { [ORGANIZATION_HEADER]: operator.code } }));
};
