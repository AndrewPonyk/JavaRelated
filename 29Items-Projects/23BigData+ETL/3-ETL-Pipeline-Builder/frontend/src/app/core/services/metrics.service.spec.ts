import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { MetricsService } from './metrics.service';

describe('MetricsService', () => {
  let service: MetricsService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(MetricsService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('fetches the current metrics snapshot', () => {
    let received: unknown;
    service.getSnapshot().subscribe((tiles) => (received = tiles));

    const req = httpMock.expectOne('/api/v1/metrics/current');
    expect(req.request.method).toBe('GET');
    req.flush([
      { metric: 'orders_per_second', value: 42, window_start_ms: 0, window_ms: 500 },
    ]);

    expect(received).toEqual([
      { metric: 'orders_per_second', value: 42, window_start_ms: 0, window_ms: 500 },
    ]);
  });
});
