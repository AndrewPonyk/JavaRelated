interface WindowEntry {
  count: number;
  resetAt: number;
}

export class FixedWindowRateLimiter {
  private readonly entries = new Map<string, WindowEntry>();
  private requestCount = 0;

  constructor(
    private readonly limit: number,
    private readonly windowMilliseconds = 60_000,
    private readonly now: () => number = Date.now,
  ) {}

  allow(key: string): boolean {
    const currentTime = this.now();
    this.requestCount += 1;
    if (this.requestCount % 1_000 === 0) {
      for (const [entryKey, entry] of this.entries) {
        if (entry.resetAt <= currentTime) this.entries.delete(entryKey);
      }
    }
    const previous = this.entries.get(key);
    if (!previous || previous.resetAt <= currentTime) {
      this.entries.set(key, { count: 1, resetAt: currentTime + this.windowMilliseconds });
      return true;
    }
    if (previous.count >= this.limit) return false;
    previous.count += 1;
    return true;
  }
}
