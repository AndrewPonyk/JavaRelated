import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';

import { SettingsPage } from './SettingsPage';
import { db } from '../../../mocks/db/store';
import { server } from '../../../mocks/server';

describe('SettingsPage', () => {
  it('loads and displays the current profile', async () => {
    render(<SettingsPage />);

    expect(await screen.findByLabelText(/display name/i)).toHaveValue('Demo Customer');
    expect(screen.getByLabelText(/marketing e-mails/i)).not.toBeChecked();
    expect(screen.getByLabelText(/product updates/i)).toBeChecked();
  });

  it('PATCHes only the changed fields and shows the saved confirmation', async () => {
    let patchBody: Record<string, unknown> | null = null;
    server.use(
      http.patch('*/v1/settings/profile', async ({ request }) => {
        patchBody = (await request.json()) as Record<string, unknown>;
        db.profile = { ...db.profile, ...patchBody };
        return HttpResponse.json(db.profile);
      }),
    );

    render(<SettingsPage />);
    const nameInput = await screen.findByLabelText(/display name/i);

    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, 'Renamed Customer');
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i);
    expect(patchBody).toEqual({ displayName: 'Renamed Customer' }); // untouched fields omitted
    expect(db.profile.displayName).toBe('Renamed Customer');
  });

  it('does not call the API when nothing changed', async () => {
    let patched = false;
    server.use(
      http.patch('*/v1/settings/profile', () => {
        patched = true;
        return HttpResponse.json(db.profile);
      }),
    );

    render(<SettingsPage />);
    await screen.findByLabelText(/display name/i);

    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    expect(await screen.findByRole('status')).toHaveTextContent(/saved/i);
    expect(patched).toBe(false);
  });

  it('shows an error state when saving fails', async () => {
    server.use(
      http.patch('*/v1/settings/profile', () =>
        HttpResponse.json({ code: 'INTERNAL', message: 'boom' }, { status: 500 }),
      ),
    );

    render(<SettingsPage />);
    const nameInput = await screen.findByLabelText(/display name/i);

    await userEvent.clear(nameInput);
    await userEvent.type(nameInput, 'Will Fail');
    await userEvent.click(screen.getByRole('button', { name: /save changes/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(/saving failed/i);
  });

  it('shows a recoverable error state when loading fails', async () => {
    server.use(
      http.get(
        '*/v1/settings/profile',
        () => HttpResponse.json({ code: 'INTERNAL', message: 'boom' }, { status: 500 }),
        { once: true },
      ),
    );

    render(<SettingsPage />);

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(/could not load your settings/i);

    await userEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByLabelText(/display name/i)).toHaveValue('Demo Customer');
  });
});
