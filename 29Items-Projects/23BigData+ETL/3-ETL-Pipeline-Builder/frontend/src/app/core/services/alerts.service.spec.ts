import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AlertsService } from './alerts.service';

describe('AlertsService', () => {
  let service: AlertsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AlertsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches recent alerts with a limit', () => {
    service.recent(25).subscribe();

    const req = httpMock.expectOne('/api/v1/alerts?limit=25');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('acknowledges an alert', () => {
    let acknowledged = false;
    service.acknowledge('a1').subscribe((r) => (acknowledged = r.acknowledged));

    const req = httpMock.expectOne('/api/v1/alerts/a1/ack');
    expect(req.request.method).toBe('POST');
    req.flush({ alert_id: 'a1', acknowledged: true });

    expect(acknowledged).toBeTrue();
  });
});
