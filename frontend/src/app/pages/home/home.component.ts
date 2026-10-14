import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <h1>StockSmart</h1>
        <button (click)="onLogout()">Sign out</button>
      </header>
      <section class="card">
        <p>Authentication successful.</p>
        <p>User: <strong>{{ username }}</strong></p>
        <p>Role: <strong>{{ role }}</strong></p>
        <p class="probe">Backend probe: <code>{{ probe() }}</code></p>
      </section>
      <section class="card">
        <h2>Master data (Day 2)</h2>
        <nav>
          <a routerLink="/categories">Categories</a>
          <a routerLink="/products">Products</a>
          <a routerLink="/warehouses">Warehouses</a>
          <a routerLink="/suppliers">Suppliers</a>
        </nav>
      </section>
    </main>
  `,
  styles: [`
    .page { min-height: 100vh; background: #0f172a; color: #e2e8f0; padding: 2rem; box-sizing: border-box; }
    header { display: flex; justify-content: space-between; align-items: center; max-width: 720px; margin: 0 auto 1.5rem; }
    button { padding: .5rem 1rem; border-radius: 8px; border: 1px solid #334155; background: #1e293b; color: #e2e8f0; cursor: pointer; }
    .card { max-width: 720px; margin: 0 auto 1rem; background: #1e293b; border-radius: 12px; padding: 2rem; }
    h2 { margin-top: 0; font-size: 1.1rem; }
    .probe { color: #94a3b8; }
    nav { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: .75rem; }
    nav a { display: block; text-align: center; padding: .8rem; border-radius: 8px; background: #0f172a; border: 1px solid #334155; color: #93c5fd; text-decoration: none; }
  `],
})
export class HomeComponent {
  private readonly auth = inject(AuthService);
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  username = this.auth.username ?? '—';
  role = this.auth.role ?? '—';
  probe = signal('checking…');

  constructor() {
    this.http.get(`${environment.apiUrl}/api/v1/test/protected`).subscribe({
      next: (res) => this.probe.set(JSON.stringify(res)),
      error: (err) => this.probe.set(`FAILED (HTTP ${err?.status ?? '?'})`),
    });
  }

  onLogout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
