FROM node:24.19.0-bookworm-slim AS build
WORKDIR /workspace
COPY frontend/admin/package*.json frontend/admin/
COPY frontend/client/package*.json frontend/client/
COPY frontend/client/tooling frontend/client/tooling
RUN --mount=type=cache,target=/root/.npm cd frontend/admin && npm ci --no-audit --no-fund --prefer-offline --maxsockets=5 --fetch-retries=2 --fetch-timeout=120000
RUN --mount=type=cache,target=/root/.npm cd frontend/client && npm ci --no-audit --no-fund --prefer-offline --maxsockets=5 --fetch-retries=2 --fetch-timeout=120000 --include=optional
COPY frontend/contracts frontend/contracts
COPY frontend/admin frontend/admin
COPY frontend/client frontend/client
RUN cd frontend/admin && npm run build
RUN cd frontend/client && npm run test:toolchain && npm run typecheck && npm run build:h5 && npm run build:mp-weixin
FROM nginx:1.30.6-alpine3.24@sha256:970364228b1e4f932abc22d321932816782cca8a20b85a77552a7cfe9acfb3b5
RUN for attempt in 1 2 3; do \
      apk upgrade --no-cache && exit 0; \
      [ "$attempt" = 3 ] && exit 1; \
      sleep 2; \
    done
ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.revision=$SOURCE_REVISION
COPY --from=build /workspace/frontend/admin/dist /usr/share/nginx/html/admin
COPY --from=build /workspace/frontend/client/dist/build/h5 /usr/share/nginx/html/app
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY deploy/dish.svg /usr/share/nginx/html/demo-images/dish.svg
