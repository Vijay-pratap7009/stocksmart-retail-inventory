export const environment = {
  production: true,
  // Same-origin by design in production: the Nginx container serves the app and
  // reverse-proxies `/api/*` to BACKEND_URL (default `backend:8080` inside the
  // compose network). `BACKEND_URL` is configurable at container start — it is
  // never exposed to the browser.
  apiUrl: '',
};
