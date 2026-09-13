# Hyperlocal Delivery — frontend

The React single-page app for Hyperlocal Delivery: the owner console, the rider
(agent) app and the public customer tracking page. It talks to the Spring Boot
API in the repository root.

## Stack

- React 19 and TypeScript
- Vite 7 (dev server and build)
- TanStack Router (file-based routes in `src/routes/`)
- Tailwind CSS 4 plus a hand-written design system in `src/styles.css`
- Vitest with happy-dom for tests

## Development

The Maven build downloads its own Node (v20.19.0) into `frontend/node/`, so no
global Node install is needed. From this folder, with that Node on your `PATH`:

```sh
npm install
npm run dev      # http://localhost:5173, proxies /api to http://localhost:8080
npm run build    # writes ../src/main/resources/static
npm test
npm run lint
```

Start the backend first; with it stopped, the dev proxy answers `503
BACKEND_UNREACHABLE`. Set `VITE_API_PROXY_TARGET` to proxy somewhere other than
`http://localhost:8080`.

The build guides and architecture notes are in `docs/` at the repository root.
