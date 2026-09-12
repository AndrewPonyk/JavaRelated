#!/usr/bin/env bash
# ============================================================================
#  scripts/tune_host.sh — bare-metal latency tuning for a trading host.
#  Run as root before starting the engine. Idempotent. These settings remove
#  OS-induced jitter from the isolated trading cores. MUST match TRADING_CORES
#  in the engine config and the `isolcpus=` kernel boot parameter.
#
#  Verify isolation at boot: add to GRUB cmdline ->
#     isolcpus=2-5 nohz_full=2-5 rcu_nocbs=2-5 intel_pstate=disable
# ============================================================================
set -euo pipefail

TRADING_CORES="${TRADING_CORES:-2-5}"

echo ">> Setting CPU governor to performance"
for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
    echo performance > "$c" 2>/dev/null || true
done

echo ">> Disabling deep C-states (latency from wakeups)"
# Example: cpupower idle-set -D 0   (or per-core via /dev/cpu_dma_latency held open)

echo ">> Reserving huge pages"
echo 2048 > /proc/sys/vm/nr_hugepages || true   # 2048 * 2MiB = 4GiB

echo ">> Moving IRQs off trading cores ${TRADING_CORES}"
# Example: for each /proc/irq/*/smp_affinity_list, exclude TRADING_CORES.
#       Pin NIC RX/TX queue IRQs to a housekeeping core.

echo ">> NIC tuning (replace eth0 with the trading interface)"
# ethtool -G eth0 rx 4096 tx 4096           # larger rings
# ethtool -C eth0 adaptive-rx off rx-usecs 0  # disable interrupt coalescing
# ethtool -K eth0 gro off lro off             # off for latency-sensitive paths

echo ">> Disabling transparent huge pages (use explicit hugepages instead)"
echo never > /sys/kernel/mm/transparent_hugepage/enabled || true

echo ">> Writeback / swappiness"
sysctl -w vm.swappiness=1 >/dev/null || true

echo ">> Done. Start the engine pinned to cores ${TRADING_CORES}."
# Reminder: the engine itself calls mlockall(MCL_CURRENT|MCL_FUTURE) at startup.
