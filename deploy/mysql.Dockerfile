FROM scratch AS patch
ADD --checksum=sha256:7300400c68f72c78650a0d76cf233deb8860a32771c5b0bb8bc4ee509ac6bb22 https://yum.oracle.com/repo/OracleLinux/OL9/baseos/latest/x86_64/getPackage/curl-7.76.1-40.el9_8.9.x86_64.rpm /curl.rpm
ADD --checksum=sha256:a522b6ab35fe1118a580537a375ab4f9d41f94b00411497f1d6cdb5e35353dfd https://yum.oracle.com/repo/OracleLinux/OL9/baseos/latest/x86_64/getPackage/libcurl-7.76.1-40.el9_8.9.x86_64.rpm /libcurl.rpm
FROM mysql:8.4.13@sha256:a0225d1ea6631c8bc2385dc4c2faba17bbf1834ecf369e1659a9a382bd8d5dd7
ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.revision=$SOURCE_REVISION
# Oracle ELSA-2026-92907 is newer than this fixed upstream image.
RUN --mount=from=patch,target=/patch rpm --checksig /patch/curl.rpm /patch/libcurl.rpm \
    && rpm -Uvh /patch/curl.rpm /patch/libcurl.rpm
