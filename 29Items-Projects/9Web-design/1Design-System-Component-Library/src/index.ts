import './styles/main.scss';
import { defineTokenGovernanceApp } from './app/TokenGovernanceApp';
import { definePrimitives } from './components/primitives';
import { defineStatusCard } from './components/StatusCard';

definePrimitives();
defineStatusCard();
defineTokenGovernanceApp();

export { StatusCardElement, defineStatusCard } from './components/StatusCard';
export { definePrimitives } from './components/primitives';
export { TokenGovernanceAppElement, defineTokenGovernanceApp } from './app/TokenGovernanceApp';
export type { DesignToken, DesignTokenGroup, TokenCategory } from './tokens/token-types';
