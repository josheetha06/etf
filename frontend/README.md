# ETT HR portal: web app

React 19, TypeScript, Vite and Mantine. See the main [README](../README.md) for the whole product.

```bash
npm install
npm run dev      # http://localhost:5173, forwards /api to http://localhost:8080
npm run lint
npm run build    # type-checks, then writes the production files to dist/
```

| Folder | Contents |
| --- | --- |
| `src/pages/` | One file per screen. `job/` and `interviews/` hold the parts of the larger screens |
| `src/components/` | App layout, page header, badges, loading and empty states, match evidence |
| `src/api/` | `client.ts` (fetch with session cookie and CSRF header), `types.ts` (API shapes), `hooks.ts` (TanStack Query hooks) |
| `src/lib/` | Time-zone formatting, display labels, notifications |
| `src/theme.ts` | Colours, fonts and component defaults |
| `nginx/` | Production web server settings and security headers, used by the Dockerfile |

Times are always shown with their zone. Interview times are entered in the candidate's zone and shown
in both the candidate's and the HR user's zones.
