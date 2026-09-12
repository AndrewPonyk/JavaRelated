import { reportClientError } from '../clientErrors.js';
import { validateReservationInput } from '../../shared/validation/reservation.js';

export class ReservationForm {
  constructor({ form, endpoint, onSuccess = () => undefined, onError = () => undefined }) {
    this.form = form;
    this.endpoint = endpoint;
    this.onSuccess = onSuccess;
    this.onError = onError;
    this.status = form?.querySelector('[data-reservation-status]');
  }

  init() {
    if (!this.form) return;

    this.form.addEventListener('submit', async (event) => {
      event.preventDefault();
      await this.submit();
    });
  }

  async submit() {
    const payload = Object.fromEntries(new FormData(this.form).entries());
    const result = validateReservationInput(payload);

    if (!result.ok) {
      this.renderFieldErrors(result.errors);
      this.setStatus(result.errors[0].message, 'error');
      this.onError('validation');
      return;
    }

    this.clearFieldErrors();
    this.setStatus('Sending reservation request...', 'loading');

    try {
      const response = await fetch(this.endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(result.data)
      });
      const body = await response.json();

      if (!response.ok || !body.ok) {
        throw new Error(body.error || 'Reservation request failed.');
      }

      this.form.reset();
      this.clearFieldErrors();
      this.setStatus('Request received. We will confirm availability shortly.', 'success');
      this.onSuccess();
    } catch (error) {
      reportClientError('reservation_form', error);
      this.setStatus('We could not send the request. Please call the restaurant or try again.', 'error');
      this.onError('network');
    }
  }

  setStatus(message, state) {
    if (!this.status) return;

    this.status.textContent = message;
    this.status.dataset.state = state;
  }

  renderFieldErrors(errors) {
    this.clearFieldErrors();

    errors.forEach((error) => {
      const field = this.form.elements[error.field];
      if (!field) return;

      const id = `${error.field}-error`;
      field.setAttribute('aria-invalid', 'true');
      field.setAttribute('aria-describedby', id);

      const message = document.createElement('p');
      message.id = id;
      message.className = 'text-sm text-red-200';
      message.dataset.fieldError = error.field;
      message.textContent = error.message;
      field.closest('label')?.append(message);
    });
  }

  clearFieldErrors() {
    this.form.querySelectorAll('[data-field-error]').forEach((element) => element.remove());
    Array.from(this.form.elements).forEach((field) => {
      field.removeAttribute('aria-invalid');
      field.removeAttribute('aria-describedby');
    });
  }
}
