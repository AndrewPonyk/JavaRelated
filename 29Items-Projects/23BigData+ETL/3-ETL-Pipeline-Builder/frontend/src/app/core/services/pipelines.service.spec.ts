import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { PipelinesService } from './pipelines.service';

describe('PipelinesService', () => {
  let service: PipelinesService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PipelinesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists pipelines', () => {
    let count = -1;
    service.list().subscribe((items) => (count = items.length));

    const req = httpMock.expectOne('/api/v1/pipelines');
    expect(req.request.method).toBe('GET');
    req.flush([]);

    expect(count).toBe(0);
  });

  it('creates a pipeline with the form payload', () => {
    const payload = {
      name: 'orders-daily',
      schedule: '0 2 * * *',
      source: 's3://lake/raw/orders/',
      target: 'analytics.marts.fct_business_metrics_daily',
    };
    service.create(payload).subscribe();

    const req = httpMock.expectOne('/api/v1/pipelines');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ ...payload, id: 'p1', status: 'draft' });
  });

  it('patches status on update', () => {
    service.update('p1', { status: 'paused' }).subscribe();

    const req = httpMock.expectOne('/api/v1/pipelines/p1');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'paused' });
    req.flush({});
  });

  it('deletes by id', () => {
    service.delete('p1').subscribe();

    const req = httpMock.expectOne('/api/v1/pipelines/p1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
