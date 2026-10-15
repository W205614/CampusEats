FROM node:24.19.0-bookworm-slim AS build
WORKDIR /workspace
COPY frontend/admin/package*.json frontend/admin/
COPY frontend/client/package*.json frontend/client/
COPY frontend/client/tooling frontend/client/tooling
RUN --mount=type=cache,target=/root/.npm cd frontend/admin && npm ci --no-audit --no-fund --fetch-retries=1 --fetch-timeout=30000
RUN --mount=type=cache,target=/root/.npm cd frontend/client && npm ci --no-audit --no-fund --fetch-retries=1 --fetch-timeout=30000 --include=optional
COPY frontend/contracts frontend/contracts
COPY frontend/admin frontend/admin
COPY frontend/client frontend/client
RUN cd frontend/admin && npm run build
RUN cd frontend/client && npm run test:toolchain && npm run typecheck && npm run build:h5 && npm run build:mp-weixin
FROM nginx:1.27-alpine
COPY --from=build /workspace/frontend/admin/dist /usr/share/nginx/html/admin
COPY --from=build /workspace/frontend/client/dist/build/h5 /usr/share/nginx/html/app
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/dish.svg /usr/share/nginx/html/demo-images/dish.svg
