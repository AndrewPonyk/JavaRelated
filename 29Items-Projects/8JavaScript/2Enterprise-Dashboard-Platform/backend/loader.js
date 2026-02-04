import { resolve as resolveTs } from 'ts-node/esm';
import * as tsConfigPaths from 'tsconfig-paths';
import { pathToFileURL } from 'url';

const { absoluteBaseUrl, paths } = tsConfigPaths.loadConfig();
const matchPath = tsConfigPaths.createMatchPath(absoluteBaseUrl, paths);

export function resolve(specifier, context, nextResolve) {
  const mappedSpecifier = matchPath(specifier);
  if (mappedSpecifier) {
    return nextResolve(pathToFileURL(mappedSpecifier).href, context);
  }
  return nextResolve(specifier, context);
}

export { load, transformSource } from 'ts-node/esm';
