import { MemoryTokenRepository } from '../../src/db/token.repository';
import { TokenService } from '../../src/services/token.service';

export async function createSeededService(): Promise<{
  repository: MemoryTokenRepository;
  service: TokenService;
  tokenSetId: string;
}> {
  const repository = new MemoryTokenRepository();
  const service = new TokenService(repository);
  const tokenSet = await service.createTokenSet({
    name: 'core',
    description: 'Core tokens',
    source: 'test'
  });

  return { repository, service, tokenSetId: tokenSet.id };
}
