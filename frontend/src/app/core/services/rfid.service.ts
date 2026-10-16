import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page } from '../models/master-data.models';
import {
  RfidReadEvent,
  RfidReadRequest,
  RfidReadStatus,
  RfidReaderInfo,
  RfidTag,
  RfidTagRequest,
  RfidTagUpdateRequest,
} from '../models/rfid.models';
import { environment } from '../../../environments/environment';

export interface RfidReadFilter {
  epcCode?: string | null;
  status?: RfidReadStatus | null;
  simulated?: boolean | null;
}

/** Day 5 RFID tag mapping + reader/simulator integration API client. */
@Injectable({ providedIn: 'root' })
export class RfidService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/rfid`;

  listTags(
    search = '',
    active: boolean | null = null,
    productId: number | null = null,
    page = 0,
    size = 20
  ): Observable<Page<RfidTag>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,desc');
    if (search.trim()) params = params.set('search', search.trim());
    if (active !== null) params = params.set('active', active);
    if (productId !== null) params = params.set('productId', productId);
    return this.http.get<Page<RfidTag>>(`${this.base}/tags`, { params });
  }

  getTag(epcCode: string): Observable<RfidTag> {
    return this.http.get<RfidTag>(`${this.base}/tags/${encodeURIComponent(epcCode.trim())}`);
  }

  registerTag(request: RfidTagRequest): Observable<RfidTag> {
    return this.http.post<RfidTag>(`${this.base}/tags`, request);
  }

  updateTag(epcCode: string, request: RfidTagUpdateRequest): Observable<RfidTag> {
    return this.http.put<RfidTag>(
      `${this.base}/tags/${encodeURIComponent(epcCode.trim())}`,
      request
    );
  }

  readerInfo(): Observable<RfidReaderInfo> {
    return this.http.get<RfidReaderInfo>(`${this.base}/reader`);
  }

  processRead(request: RfidReadRequest): Observable<RfidReadEvent> {
    return this.http.post<RfidReadEvent>(`${this.base}/reads`, request);
  }

  listReads(filter: RfidReadFilter = {}, page = 0, size = 20): Observable<Page<RfidReadEvent>> {
    let params = new HttpParams()
      .set('page', page)
      .set('size', size)
      .set('sort', 'receivedAt,desc');
    if (filter.epcCode?.trim()) params = params.set('epcCode', filter.epcCode.trim());
    if (filter.status) params = params.set('status', filter.status);
    if (filter.simulated !== null && filter.simulated !== undefined) {
      params = params.set('simulated', filter.simulated);
    }
    return this.http.get<Page<RfidReadEvent>>(`${this.base}/reads`, { params });
  }
}
