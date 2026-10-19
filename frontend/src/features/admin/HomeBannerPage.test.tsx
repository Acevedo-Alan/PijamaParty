import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { es } from '../../i18n/es.ts';
import { connectionLost, problem, signedIn, stubApi } from '../../test/adminApi.ts';
import { answer } from '../../test/catalogApi.ts';
import { renderRoute } from '../../test/renderRoute.tsx';
import { uploadTransport, type UploadReply } from './uploadTransport.ts';

const messages = es.admin.homeBanner;
const BANNER = '/api/admin/home/banner';

const EMPTY = { title: null, body: null, linkText: null, linkPath: null, image: null, published: false };
const TEXTS = {
  title: 'La noche se llena de personajes',
  body: 'Una pijamada para jugar.',
  linkText: 'Descubrí la colección',
  linkPath: '/categoria/kigurumis',
};
const IMAGE = {
  placeholder: 'data:image/webp;base64,AAAA',
  files: [
    { width: 400, url: '/uploads/b-400.webp' },
    { width: 800, url: '/uploads/b-800.webp' },
    { width: 1200, url: '/uploads/b-1200.webp' },
  ],
};
const COMPLETE = { ...TEXTS, image: IMAGE, published: false, updatedAt: '2026-10-19T12:00:00Z' };

/** Opens "Portada" with the stored banner and the given answers for the other calls. */
async function openPage(stored: unknown, routes: Record<string, unknown> = {}) {
  const api = stubApi({
    'GET /api/admin/auth/me': signedIn,
    [`GET ${BANNER}`]: answer(200, stored),
    ...routes,
  });
  renderRoute('/admin/portada');
  await screen.findByRole('heading', { level: 1, name: messages.pageTitle });
  await screen.findByLabelText(messages.title);
  return api;
}

function jpg() {
  return new File(['jpg'], 'personajes.jpg', { type: 'image/jpeg' });
}

const preview = () => within(screen.getByRole('region', { name: messages.previewTitle }));

describe('character banner in the back office (UC-32)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('given the back office menu, when it shows, then "Portada" opens the banner page', async () => {
    await openPage(EMPTY);

    expect(screen.getByRole('link', { name: es.admin.home })).toHaveAttribute('href', '/admin/portada');
    expect(screen.getByText(messages.hiddenState)).toBeInTheDocument();
    expect(screen.getByText(messages.noPhoto)).toBeInTheDocument();
  });

  it('given a new banner, when the texts are typed and saved, then the preview follows them and all four are sent', async () => {
    const api = await openPage(EMPTY, { [`PUT ${BANNER}`]: answer(200, { ...EMPTY, ...TEXTS }) });

    await userEvent.type(screen.getByLabelText(messages.title), TEXTS.title);
    await userEvent.type(screen.getByLabelText(messages.body), TEXTS.body);
    await userEvent.type(screen.getByLabelText(messages.linkText), TEXTS.linkText);
    await userEvent.type(screen.getByLabelText(messages.linkPath), TEXTS.linkPath);
    expect(preview().getByRole('heading', { level: 2, name: TEXTS.title })).toBeInTheDocument();
    expect(preview().queryByRole('link')).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: es.admin.save }));

    expect(await screen.findByRole('status')).toHaveTextContent(es.admin.saved);
    expect(api.callsTo(`PUT ${BANNER}`)[0]?.options.body).toEqual(TEXTS);
  });

  it('given a target outside the store, when saved, then the server refuses it and the field is marked', async () => {
    await openPage(EMPTY, {
      [`PUT ${BANNER}`]: problem(422, { problem: 'INVALID_BANNER', fields: ['linkPath'] }),
    });

    await userEvent.type(screen.getByLabelText(messages.title), 'Personajes');
    await userEvent.type(screen.getByLabelText(messages.linkText), 'Ver');
    await userEvent.type(screen.getByLabelText(messages.linkPath), 'https://other.site');
    await userEvent.click(screen.getByRole('button', { name: es.admin.save }));

    expect(await screen.findByRole('alert')).toHaveTextContent(messages.invalid);
    const target = screen.getByLabelText(messages.linkPath);
    expect(target).toHaveAttribute('aria-invalid', 'true');
    expect(target).toHaveAccessibleDescription(`${messages.linkPathHint} ${messages.invalidFields.linkPath}`);
    expect(screen.getByLabelText(messages.title)).toHaveAttribute('aria-invalid', 'false');
  });

  it('given a banner without a photo, when published, then it says the photo is missing', async () => {
    await openPage({ ...EMPTY, ...TEXTS }, {
      [`POST ${BANNER}/publish`]: problem(409, { problem: 'INCOMPLETE_BANNER', missing: ['photo'] }),
    });

    await userEvent.click(screen.getByRole('button', { name: messages.publish }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Para publicar falta la foto.');
    expect(screen.getByText(messages.hiddenState)).toBeInTheDocument();
  });

  it('given a complete banner, when published and then hidden, then its state follows', async () => {
    await openPage(COMPLETE, {
      [`POST ${BANNER}/publish`]: answer(200, { ...COMPLETE, published: true }),
      [`POST ${BANNER}/hide`]: answer(200, COMPLETE),
    });

    await userEvent.click(screen.getByRole('button', { name: messages.publish }));
    expect(await screen.findByText(messages.published)).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent(messages.publishedNotice);

    await userEvent.click(screen.getByRole('button', { name: messages.hide }));
    expect(await screen.findByText(messages.hiddenState)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: messages.publish })).toBeInTheDocument();
  });

  it('given an upload cut by a lost connection, when retried, then the same key and bytes are sent and the preview shows the photo', async () => {
    await openPage({ ...EMPTY, ...TEXTS });
    const created: UploadReply = { status: 201, body: COMPLETE };
    const replies: (UploadReply | Error)[] = [connectionLost(), created];
    const send = vi.spyOn(uploadTransport, 'send').mockImplementation(() => {
      const next = replies.shift() ?? created;
      return next instanceof Error ? Promise.reject(next) : Promise.resolve(next);
    });

    await userEvent.upload(screen.getByLabelText(messages.addPhoto), jpg());
    expect(await screen.findByRole('alert')).toHaveTextContent(es.admin.photos.connectionLost);
    await userEvent.click(screen.getByRole('button', { name: es.admin.photos.retry }));

    expect(await screen.findByRole('status')).toHaveTextContent(es.admin.photos.uploaded);
    const [first, retry] = send.mock.calls.map(([request]) => request);
    expect(first?.url).toBe('/api/admin/home/banner/photo');
    expect(first?.idempotencyKey).toMatch(/^[0-9a-f-]{36}$/);
    expect(retry?.idempotencyKey).toBe(first?.idempotencyKey);
    expect(retry?.file).toBe(first?.file);
    expect(preview().getByRole('presentation')).toHaveAttribute('src', '/uploads/b-400.webp');
    expect(screen.getByLabelText(messages.replacePhoto)).toBeInTheDocument();
  });

  it('given a file that is not a photo, when uploaded, then the reason shows and nothing changes', async () => {
    await openPage(COMPLETE);
    vi.spyOn(uploadTransport, 'send').mockResolvedValue({
      status: 422,
      body: { status: 422, problem: 'UNSUPPORTED_FORMAT' },
    });

    await userEvent.upload(screen.getByLabelText(messages.replacePhoto), jpg());

    expect(await screen.findByRole('alert')).toHaveTextContent(es.admin.photos.problems.UNSUPPORTED_FORMAT);
    expect(preview().getByRole('presentation')).toHaveAttribute('src', '/uploads/b-400.webp');
  });
});
