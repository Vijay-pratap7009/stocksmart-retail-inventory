import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AuthResponse, LoginRequest } from '../models/auth.models';
import { environment } from '../../../environments/environment';

const TOKEN_KEY = 'stocksmart_token';
const USER_KEY = 'stocksmart_user';
const ROLE_KEY = 'stocksmart_role';

/**
 * Day 1 authentication state: login → store JWT → expose session.
 * Token is kept in localStorage (reasonable Day 1 default; documented in README).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/api/v1/auth/login`, request).pipe(
      tap((res) => this.storeSession(res))
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    localStorage.removeItem(ROLE_KEY);
  }

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  get username(): string | null {
    return localStorage.getItem(USER_KEY);
  }

  get role(): string | null {
    return localStorage.getItem(ROLE_KEY);
  }

  get isAuthenticated(): boolean {
    return !!this.token;
  }

  private storeSession(res: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(USER_KEY, res.username);
    localStorage.setItem(ROLE_KEY, res.role);
  }
}
