import { useMutation } from '@tanstack/react-query';

import { checkInteractions } from '../api/client';
import type { DrugInput, InteractionCheckResponse } from '../types';

export interface CheckArgs {
  drugs: DrugInput[];
  includeMl: boolean;
}

/** Mutation hook that submits a drug list and returns interaction results. */
export function useInteractionCheck() {
  return useMutation<InteractionCheckResponse, Error, CheckArgs>({
    mutationFn: ({ drugs, includeMl }) => checkInteractions(drugs, includeMl),
  });
}
