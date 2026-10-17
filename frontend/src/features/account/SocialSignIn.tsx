import { useQuery } from '@tanstack/react-query';
import { es } from '../../i18n/es.ts';
import { fetchSignInProviders, signInWithProviderUrl, type SignInProvider } from './accountApi.ts';
import './account.css';

const messages = es.account.social;

/** The query key of the enabled providers; they change only with a deployment. */
export const SIGN_IN_PROVIDERS_KEY = ['account', 'sign-in-providers'] as const;

/** The providers' brands, as people write them. */
export const PROVIDER_NAMES: Record<SignInProvider, string> = { GOOGLE: 'Google', FACEBOOK: 'Facebook' };

/** The enabled providers; empty while they load or when none is enabled. */
export function useSignInProviders(): SignInProvider[] {
  const { data } = useQuery({
    queryKey: SIGN_IN_PROVIDERS_KEY,
    queryFn: ({ signal }) => fetchSignInProviders(signal),
    staleTime: Infinity,
  });
  return data ?? [];
}

interface SocialSignInProps {
  /** Only these providers, when given (the link confirmation page offers the one just linked). */
  only?: SignInProvider[];
  /** Whether the email form follows, so the buttons end with "o con tu email". */
  beforeForm?: boolean;
}

/**
 * UC-27 (T-E4-14): "Continuar con Google" and "Continuar con Facebook", only for the enabled providers. Each one is a
 * whole-page navigation to the provider, which comes back to the storefront signed in. Never in the checkout.
 */
export function SocialSignIn({ only, beforeForm = true }: SocialSignInProps) {
  const providers = useSignInProviders().filter((provider) => only === undefined || only.includes(provider));
  if (providers.length === 0) {
    return null;
  }
  return (
    <div className="social-sign-in">
      {providers.map((provider) => (
        <a
          key={provider}
          className={`social-button social-${provider.toLowerCase()}`}
          href={signInWithProviderUrl(provider)}
        >
          {messages.continueWith(PROVIDER_NAMES[provider])}
        </a>
      ))}
      {beforeForm && <p className="social-or">{messages.or}</p>}
    </div>
  );
}

/** Where the provider's return left the browser: the `social` value of the sign-in page's query. */
export function socialMessage(social: string | null): { text: string; problem: boolean } | null {
  switch (social) {
    case 'confirm-link':
      return { text: messages.confirmLink, problem: false };
    case 'cancelled':
      return { text: messages.cancelled, problem: false };
    case 'already-linked':
      return { text: messages.alreadyLinked, problem: true };
    case 'failed':
      return { text: messages.failed, problem: true };
    default:
      return null;
  }
}
