# MNP frontend

Angular 21 app for submitting porting requests and viewing them. See the [main README](../README.md)
for running the whole stack with Docker.

```bash
npm ci
npm start                    # http://localhost:4200, /api proxied to the backend on localhost:8080
npx ng test --watch=false    # unit tests (Vitest + jsdom)
npx ng build                 # production build in dist/mnp-frontend
```

The **Acting as** picker in the header chooses the operator; it's sent as the `organization` header
on every API call (mocked authentication) and remembered across reloads.

| Path | Contents |
|------|----------|
| `src/app/core/` | Operators, the acting-operator context, the `organization` interceptor, error messages |
| `src/app/porting-requests/` | API client, the request form, and the request list with accept/reject |
| `nginx.conf` | Serves the built app in Docker and forwards `/api` to the backend container |
