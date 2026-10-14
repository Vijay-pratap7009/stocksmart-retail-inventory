import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Category, CategoryRequest, Page } from '../models/master-data.models';
import { environment } from '../../../environments/environment';

@Injectable({ providedIn: 'root' })
export class CategoryService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/api/v1/categories`;

  list(search?: string, active?: boolean | null, page = 0, size = 20): Observable<Page<Category>> {
    let params = new HttpParams().set('page', page).set('size', size).set('sort', 'id,asc');
    if (search?.trim()) params = params.set('search', search.trim());
    if (active !== undefined && active !== null) params = params.set('active', active);
    return this.http.get<Page<Category>>(this.base, { params });
  }

  get(id: number): Observable<Category> {
    return this.http.get<Category>(`${this.base}/${id}`);
  }

  create(request: CategoryRequest): Observable<Category> {
    return this.http.post<Category>(this.base, request);
  }

  update(id: number, request: CategoryRequest): Observable<Category> {
    return this.http.put<Category>(`${this.base}/${id}`, request);
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
