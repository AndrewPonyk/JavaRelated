import { describe, expect, it } from 'vitest';

import {
  chooseNext,
  defaultProgress,
  MASTERY_GATE,
  masteryToDifficulty,
  nextMastery,
  updateMastery,
} from './difficulty';
import { TOPICS, type TopicProgress } from './types';

describe('nextMastery', () => {
  it('closes 15% of the gap on a correct answer', () => {
    expect(nextMastery(0, true)).toBeCloseTo(0.15, 9);
    expect(nextMastery(0.5, true)).toBeCloseTo(0.575, 9);
  });

  it('costs 30% of current mastery on a wrong answer', () => {
    expect(nextMastery(0.5, false)).toBeCloseTo(0.35, 9);
    expect(nextMastery(0, false)).toBe(0);
  });

  it('never leaves [0, 1]', () => {
    let m = 0;
    for (let i = 0; i < 200; i++) m = nextMastery(m, true);
    expect(m).toBeLessThanOrEqual(1);
  });
});

describe('masteryToDifficulty', () => {
  it('maps mastery bands to difficulties 1–5', () => {
    expect(masteryToDifficulty(0)).toBe(1);
    expect(masteryToDifficulty(0.19)).toBe(1);
    expect(masteryToDifficulty(0.2)).toBe(2);
    expect(masteryToDifficulty(0.5)).toBe(3);
    expect(masteryToDifficulty(0.79)).toBe(4);
    expect(masteryToDifficulty(0.8)).toBe(5);
    expect(masteryToDifficulty(1)).toBe(5);
  });
});

describe('updateMastery', () => {
  it('bumps the attempt counter alongside mastery', () => {
    const before: TopicProgress = { topic: 'vectors', mastery: 0.4, attempts: 3 };
    const after = updateMastery(before, true);
    expect(after.attempts).toBe(4);
    expect(after.mastery).toBeGreaterThan(before.mastery);
  });
});

describe('chooseNext', () => {
  it('starts at the first topic for a fresh learner', () => {
    const { topic, difficulty } = chooseNext(defaultProgress());
    expect(topic).toBe('vectors');
    expect(difficulty).toBe(1);
  });

  it('advances past gated topics in curriculum order', () => {
    const progress = defaultProgress().map((p) =>
      p.topic === 'vectors' ? { ...p, mastery: MASTERY_GATE } : p,
    );
    expect(chooseNext(progress).topic).toBe('linear-combinations');
  });

  it('reviews the weakest topic once everything is gated', () => {
    const progress = TOPICS.map((topic, i) => ({
      topic,
      mastery: i === 3 ? 0.82 : 0.95,
      attempts: 10,
    }));
    expect(chooseNext(progress).topic).toBe(TOPICS[3]);
  });

  it('tolerates missing topics in the input list', () => {
    expect(chooseNext([]).topic).toBe('vectors');
  });
});
