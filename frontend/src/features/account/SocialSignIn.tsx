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

/**
 * The providers' marks, as their sign-in branding guidelines ask: Google's four-color "G" and Facebook's blue "f".
 * Brand marks keep their own colors, like a photo, so they are not store tokens. Decorative: the text names the provider.
 */
function ProviderMark({ provider }: { provider: SignInProvider }) {
  if (provider === 'GOOGLE') {
    return (
      <svg className="social-mark" viewBox="0 0 18 18" aria-hidden="true">
        <path
          fill="#EA4335"
          d="M9 3.48c1.69 0 2.83.73 3.48 1.34l2.54-2.48C13.46.89 11.43 0 9 0 5.48 0 2.44 2.02.96 4.96l2.91 2.26C4.6 5.05 6.62 3.48 9 3.48z"
        />
        <path
          fill="#4285F4"
          d="M17.64 9.2c0-.74-.06-1.28-.19-1.84H9v3.34h4.96c-.1.83-.64 2.08-1.84 2.92l2.84 2.2c1.7-1.57 2.68-3.88 2.68-6.62z"
        />
        <path
          fill="#FBBC05"
          d="M3.88 10.78A5.54 5.54 0 0 1 3.58 9c0-.62.11-1.22.29-1.78L.96 4.96A9 9 0 0 0 0 9c0 1.45.35 2.82.96 4.04l2.92-2.26z"
        />
        <path
          fill="#34A853"
          d="M9 18c2.43 0 4.47-.8 5.96-2.18l-2.84-2.2c-.76.53-1.78.9-3.12.9-2.38 0-4.4-1.57-5.12-3.74L.97 13.04C2.45 15.98 5.48 18 9 18z"
        />
      </svg>
    );
  }
  return (
    <svg className="social-mark" viewBox="0 0 24 24" aria-hidden="true">
      <path
        fill="#1877F2"
        d="M24 12.07C24 5.4 18.63 0 12 0S0 5.4 0 12.07C0 18.1 4.39 23.1 10.13 24v-8.44H7.08v-3.49h3.05V9.41c0-3.02 1.79-4.7 4.53-4.7 1.31 0 2.68.24 2.68.24v2.97h-1.51c-1.49 0-1.95.93-1.95 1.89v2.26h3.33l-.53 3.49h-2.8V24C19.61 23.1 24 18.1 24 12.07z"
      />
    </svg>
  );
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
          <ProviderMark provider={provider} />
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
