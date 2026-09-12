import { DomainError } from '../domain-errors';
import { createSeededService } from '../../../tests/fixtures/service-fixture';

describe('TokenService', () => {
  it('creates, versions, approves, and audits a token', async () => {
    const { service, tokenSetId } = await createSeededService();

    const created = await service.createToken({
      tokenSetId,
      name: 'color.brand.accent',
      category: 'color',
      value: '#FF00AA',
      description: 'Accent color',
      createdBy: 'test'
    });
    const approved = await service.approveToken(created.id, 'reviewer');
    const versions = await service.listVersions(created.id);
    const audit = await service.listAuditEvents({ tokenId: created.id });

    expect(approved.status).toBe('approved');
    expect(versions).toHaveLength(2);
    expect(audit.map((event) => event.eventType)).toContain('token.created');
  });

  it('rejects invalid token names and duplicate token names', async () => {
    const { service, tokenSetId } = await createSeededService();

    await expect(
      service.createToken({
        tokenSetId,
        name: 'Invalid Name',
        category: 'color',
        value: '#000'
      })
    ).rejects.toBeInstanceOf(DomainError);

    await service.createToken({
      tokenSetId,
      name: 'color.brand.primary',
      category: 'color',
      value: '#000'
    });

    await expect(
      service.createToken({
        tokenSetId,
        name: 'color.brand.primary',
        category: 'color',
        value: '#111'
      })
    ).rejects.toMatchObject({ code: 'TOKEN_EXISTS' });
  });

  it('diffs token sets and generates changelog entries', async () => {
    const { service, tokenSetId } = await createSeededService();
    const compareSet = await service.createTokenSet({ name: 'next', source: 'test' });

    const original = await service.createToken({
      tokenSetId,
      name: 'spacing.component',
      category: 'spacing',
      value: '1rem'
    });
    await service.createToken({
      tokenSetId: compareSet.id,
      name: 'spacing.component',
      category: 'spacing',
      value: '1.25rem'
    });
    await service.createToken({
      tokenSetId: compareSet.id,
      name: 'color.brand.primary',
      category: 'color',
      value: '#265CFF'
    });

    await service.updateToken(original.id, { value: '1.125rem', changeNote: 'Spacing adjustment' });
    const diff = await service.diffTokenSets(tokenSetId, compareSet.id);
    const changelog = await service.listChangelog(tokenSetId);

    expect(diff.find((entry) => entry.name === 'spacing.component')?.changeType).toBe('changed');
    expect(diff.find((entry) => entry.name === 'color.brand.primary')?.changeType).toBe('added');
    expect(changelog.some((entry) => entry.changeNote === 'Spacing adjustment')).toBe(true);
  });

  it('supports deleting tokens and token sets', async () => {
    const { service, tokenSetId } = await createSeededService();
    const token = await service.createToken({
      tokenSetId,
      name: 'color.brand.remove',
      category: 'color',
      value: '#123456'
    });

    await service.deleteToken(token.id, 'test');
    await expect(service.getToken(token.id)).rejects.toMatchObject({ code: 'TOKEN_NOT_FOUND' });

    await service.deleteTokenSet(tokenSetId, 'test');
    await expect(service.getTokenSet(tokenSetId)).rejects.toMatchObject({
      code: 'TOKEN_SET_NOT_FOUND'
    });
  });

  it('handles lifecycle edge cases and status summaries', async () => {
    const { service, tokenSetId } = await createSeededService();
    const token = await service.createToken({
      tokenSetId,
      name: 'color.status.review',
      category: 'color',
      value: '#C1121F'
    });

    await service.rejectToken(token.id, 'reviewer', 'Contrast failed');
    await expect(service.getStatusSummary()).resolves.toMatchObject({ status: 'danger' });

    await service.deprecateToken(token.id, 'reviewer', 'Removed from system');
    await expect(service.rejectToken(token.id, 'reviewer')).rejects.toMatchObject({
      code: 'TOKEN_DEPRECATED'
    });
  });

  it('validates token set updates and duplicate token set names', async () => {
    const { service, tokenSetId } = await createSeededService();
    const next = await service.createTokenSet({ name: 'next', source: 'test' });

    await expect(service.createTokenSet({ name: 'Invalid Name' })).rejects.toMatchObject({
      code: 'INVALID_TOKEN_SET_NAME'
    });
    await expect(service.updateTokenSet(next.id, { name: 'core' })).rejects.toMatchObject({
      code: 'TOKEN_SET_EXISTS'
    });
    await expect(service.updateTokenSet(tokenSetId, { description: null })).resolves.toMatchObject({
      description: null
    });
  });
});
