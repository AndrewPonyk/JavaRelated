# syntax=docker/dockerfile:1
#
# Multi-stage build: full toolchain in `build`, slim runtime in the final image.
#
# Capturing live traffic from a container requires host networking + raw-socket
# capabilities (the kernel won't hand them over otherwise):
#
#   docker run --rm -it --net=host \
#       --cap-add=NET_RAW --cap-add=NET_ADMIN \
#       npa -i eth0 -f "tcp port 443"
#
# Offline analysis needs no special privileges:
#
#   docker run --rm -v "$PWD:/data" npa -r /data/capture.pcap --headless

# ---- build stage ----------------------------------------------------------
FROM debian:bookworm-slim AS build

RUN apt-get update && apt-get install -y --no-install-recommends \
        build-essential \
        libpcap-dev \
        libncursesw5-dev \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
COPY . .
RUN make STRICT=1 OPT=-O2 && make test && make fixtures

# ---- runtime stage --------------------------------------------------------
FROM debian:bookworm-slim AS runtime

# Only the shared libraries the binary actually needs at runtime.
RUN apt-get update && apt-get install -y --no-install-recommends \
        libpcap0.8 \
        libncursesw6 \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /src/build/bin/npa /usr/local/bin/npa
COPY --from=build /src/config/patterns.rules /etc/npa/patterns.rules
COPY --from=build /src/tests/fixtures/sample.pcap /opt/npa/sample.pcap

# Run as non-root; grant only raw-capture capabilities on the binary.
# (Effective only when the container is given NET_RAW/NET_ADMIN at runtime.)
RUN setcap cap_net_raw,cap_net_admin+eip /usr/local/bin/npa || true \
    && useradd --system --no-create-home --shell /usr/sbin/nologin npa
USER npa

ENTRYPOINT ["npa"]
CMD ["--help"]
