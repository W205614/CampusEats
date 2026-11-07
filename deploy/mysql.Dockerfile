FROM scratch AS patch
ADD --checksum=sha256:7300400c68f72c78650a0d76cf233deb8860a32771c5b0bb8bc4ee509ac6bb22 \
    https://yum.oracle.com/repo/OracleLinux/OL9/baseos/latest/x86_64/getPackage/curl-7.76.1-40.el9_8.9.x86_64.rpm \
    /curl.rpm
ADD --checksum=sha256:a522b6ab35fe1118a580537a375ab4f9d41f94b00411497f1d6cdb5e35353dfd \
    https://yum.oracle.com/repo/OracleLinux/OL9/baseos/latest/x86_64/getPackage/libcurl-7.76.1-40.el9_8.9.x86_64.rpm \
    /libcurl.rpm
ADD --checksum=sha256:b2b6b4e6c92fc6d15ff2bf28d1142fa54cf5dd43c69e2d2f2513c5b301061904 \
    https://yum.oracle.com/repo/OracleLinux/OL9/baseos/latest/x86_64/getPackage/glibc-2.34-284.0.1.el9_8.4.x86_64.rpm \
    /glibc.rpm
ADD --checksum=sha256:b98ad2cda8c4e6d84d0f033372fe3a4010662173a65b923ecd6f5f011b723e12 \
    https://yum.oracle.com/repo/OracleLinux/OL9/baseos/latest/x86_64/getPackage/glibc-common-2.34-284.0.1.el9_8.4.x86_64.rpm \
    /glibc-common.rpm
ADD --checksum=sha256:9febe6bc5f4ec4fc5bd42853c9ab2c97bf0e954e85eba75de15c4621f97c56e9 \
    https://yum.oracle.com/repo/OracleLinux/OL9/baseos/latest/x86_64/getPackage/glibc-minimal-langpack-2.34-284.0.1.el9_8.4.x86_64.rpm \
    /glibc-minimal-langpack.rpm

FROM mysql:8.4.13@sha256:a0225d1ea6631c8bc2385dc4c2faba17bbf1834ecf369e1659a9a382bd8d5dd7
ARG SOURCE_REVISION=unknown
LABEL org.opencontainers.image.revision=$SOURCE_REVISION
# Oracle ELSA-2026-92907 and ELSA-2026-95618 patch packages in the pinned base image.
RUN --mount=from=patch,target=/patch rpm --checksig /patch/*.rpm \
    && rpm -Uvh /patch/*.rpm
