import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';
import { expect, test as setup } from '@playwright/test';
import { ADMIN_EMAIL, ADMIN_PASSWORD } from '../stubs/settings.ts';
import { ADMIN_STORAGE_STATE, ADMIN_TOTP_SECRET } from '../support/admin.ts';
import { resetClock } from '../support/backdoors.ts';
import { totpCode } from '../support/totp.ts';

/**
 * UC-04 (T-E1-16): signs the first administrator in once and saves the session for the tests. On a fresh backend it
 * enrolls the second factor, playing the authenticator app; on a reused one it signs in with the saved secret.
 */
setup('the administrator signs in to the back office', async ({ page }) => {
  // A run stopped halfway may have left the clock moved forward; the codes and sessions need the real time.
  await resetClock();

  await page.goto('/admin/ingresar');
  await page.getByLabel('Email').fill(ADMIN_EMAIL);
  await page.getByLabel('Contraseña').fill(ADMIN_PASSWORD);
  await page.getByRole('button', { name: 'Ingresar' }).click();

  const enrollment = page.getByRole('heading', { name: 'Activá la verificación en dos pasos' });
  const secondFactor = page.getByRole('heading', { name: 'Verificación en dos pasos' });
  await expect(enrollment.or(secondFactor)).toBeVisible();

  if (await enrollment.isVisible()) {
    const secret = (await page.locator('.enrollment-secret').innerText()).replaceAll(' ', '');
    mkdirSync(dirname(ADMIN_TOTP_SECRET), { recursive: true });
    writeFileSync(ADMIN_TOTP_SECRET, secret);
    await page.getByLabel('Código que muestra la app').fill(totpCode(secret, new Date()));
    await page.getByRole('button', { name: 'Activar' }).click();
    await page.getByRole('checkbox', { name: 'Guardé los códigos en un lugar seguro' }).check();
    await page.getByRole('button', { name: 'Entrar al backoffice' }).click();
  } else {
    if (!existsSync(ADMIN_TOTP_SECRET)) {
      throw new Error(`The backend has an enrolled administrator but ${ADMIN_TOTP_SECRET} is missing: restart it`);
    }
    const secret = readFileSync(ADMIN_TOTP_SECRET, 'utf8').trim();
    await page.getByLabel('Código de verificación').fill(totpCode(secret, new Date()));
    await page.getByRole('button', { name: 'Verificar' }).click();
  }

  await expect(page.getByRole('navigation', { name: 'Secciones del backoffice' })).toBeVisible();
  await page.context().storageState({ path: ADMIN_STORAGE_STATE });
});
