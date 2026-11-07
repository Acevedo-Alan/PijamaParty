import { Link } from 'react-router';
import { es } from '../../i18n/es.ts';
import { analyticsIds, hasAnalytics } from '../analytics/analytics.ts';
import { COOKIES_PATH, DATA_DELETION_PATH, PRIVACY_PATH } from './FaqLink.tsx';
import { HelpPage } from './HelpPages.tsx';

/** RNF-30.5 (T-E6-06): the privacy policy, a draft until T-E6-21. */
export function PrivacyPage() {
  const messages = es.privacy;
  return (
    <HelpPage title={messages.title} description={es.seo.privacyDescription}>
      <p className="help-intro">{messages.intro}</p>
      {Object.values(messages.sections).map(({ title, text }) => (
        <section key={title}>
          <h2>{title}</h2>
          <p>{text}</p>
        </section>
      ))}
      <p>
        {messages.moreBefore} <Link to={COOKIES_PATH}>{messages.cookiesLink}</Link> {messages.moreBetween}{' '}
        <Link to={DATA_DELETION_PATH}>{messages.deletionLink}</Link>.
      </p>
    </HelpPage>
  );
}

/** RNF-25 (T-E6-06): the cookies the store needs and, when the build has analytics ids, the optional ones. */
export function CookiesPolicyPage() {
  const messages = es.cookiesPolicy;
  const analytics = hasAnalytics(analyticsIds());
  return (
    <HelpPage title={messages.title} description={es.seo.cookiesDescription}>
      <p className="help-intro">{messages.intro}</p>
      <section aria-labelledby="cookies-necessary">
        <h2 id="cookies-necessary">{messages.necessaryTitle}</h2>
        <p>{messages.necessaryIntro}</p>
        <ul className="help-list">
          {messages.necessary.map((cookie) => (
            <li key={cookie}>{cookie}</li>
          ))}
        </ul>
      </section>
      {analytics && (
        <>
          <section aria-labelledby="cookies-optional">
            <h2 id="cookies-optional">{messages.optionalTitle}</h2>
            <p>{messages.optionalIntro}</p>
            <ul className="help-list">
              {messages.optional.map((cookie) => (
                <li key={cookie}>{cookie}</li>
              ))}
            </ul>
            <p>{messages.noPersonalData}</p>
          </section>
          <section aria-labelledby="cookies-choice">
            <h2 id="cookies-choice">{messages.choiceTitle}</h2>
            <p>{messages.choiceText}</p>
          </section>
        </>
      )}
      <p>
        {messages.privacyBefore} <Link to={PRIVACY_PATH}>{messages.privacyLink}</Link>.
      </p>
    </HelpPage>
  );
}

/** UC-28 (T-E6-06, T-E4-12): the data deletion instructions that Meta requires for Facebook Login. */
export function DataDeletionPage() {
  const messages = es.dataDeletion;
  return (
    <HelpPage title={messages.title} description={es.seo.dataDeletionDescription}>
      <p className="help-intro">{messages.intro}</p>
      <section aria-labelledby="deletion-steps">
        <h2 id="deletion-steps">{messages.stepsTitle}</h2>
        <ol className="help-list">
          {messages.steps.map((step) => (
            <li key={step}>{step}</li>
          ))}
        </ol>
      </section>
      <section aria-labelledby="deletion-without-access">
        <h2 id="deletion-without-access">{messages.withoutAccessTitle}</h2>
        <p>{messages.withoutAccess}</p>
      </section>
      <section aria-labelledby="deletion-what">
        <h2 id="deletion-what">{messages.whatTitle}</h2>
        <p>{messages.what}</p>
      </section>
      <section aria-labelledby="deletion-download">
        <h2 id="deletion-download">{messages.downloadTitle}</h2>
        <p>{messages.download}</p>
      </section>
      <p>
        {messages.privacyBefore} <Link to={PRIVACY_PATH}>{messages.privacyLink}</Link>.
      </p>
    </HelpPage>
  );
}
