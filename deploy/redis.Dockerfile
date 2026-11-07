FROM redis:7.4.12-alpine@sha256:ec1c2d3603c3d7d65b153bedda0248b641aa3bd7684503406b27b9e870ac0796 AS upstream

FROM alpine:3.24.4@sha256:0f49f331d9212aff11b932863be4eb01cdf962969aa86dab7d1ee21ac6f5f52b
ARG RUNTIME_REFRESH=initial
# Alpine 3.21 has no fixed zlib package yet. Keep the official Redis binaries on a patched runtime.
RUN test -n "$RUNTIME_REFRESH" && apk add --no-cache tzdata setpriv libssl3 libcrypto3 'zlib>=1.4-r0' \
    && addgroup -S -g 1000 redis && adduser -S -G redis -u 999 redis \
    && mkdir /data && chown redis:redis /data
COPY --from=upstream /usr/local/bin/ /usr/local/bin/
RUN redis-server --version && redis-cli --version
ADD --checksum=sha256:abffee3703161c0a6a0cd7f635b2c1ebb3b81bd199e9d18ea89652d678ae4287 \
    https://raw.githubusercontent.com/redis/redis/7.4.12/LICENSE.txt /usr/local/share/licenses/redis/LICENSE.txt
ADD --checksum=sha256:a225a3c89b998b276a8ec942f34dc2f6552a931422a1db0fe122c72a618bf65a \
    https://raw.githubusercontent.com/redis/docker-library-redis/786872f8c248c1f0990f93244cbcb3949329016d/LICENSE /usr/local/share/licenses/redis/docker-LICENSE
ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.revision=$SOURCE_REVISION
ENV REDIS_VERSION=7.4.12
VOLUME /data
WORKDIR /data
EXPOSE 6379
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["redis-server"]
