# Dockerfile — isolated lab for building and testing shellcode.
# Educational/CTF use only. Run extracted payloads HERE, never on your host.
#
# Build:  docker build -t shellcode-lab .
# Run:    docker run --rm -it \
#           --cap-add=SYS_PTRACE --security-opt seccomp=unconfined \
#           shellcode-lab
# (ptrace/seccomp flags let pwntools spawn & trace test processes; they are
#  NOT about weakening the host -- the container is the sandbox.)

FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        nasm build-essential binutils \
        socat \
        python3 python3-pip python3-venv \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /workspace

COPY requirements.txt .
RUN python3 -m pip install --no-cache-dir -r requirements.txt

COPY . .

# Build shellcode + raw bytes + the vulnerable target so the lab is ready.
RUN make all extract vuln

CMD ["/bin/bash"]
