import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { HttpClientModule } from '@angular/common/http';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink, HttpClientModule],
  template: `
    <main class="login-page">
      <section class="card">
        <h1>StockSmart</h1>
        <p class="subtitle">Retail Inventory Optimization &amp; Supply Chain Management</p>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <label>
            Username
            <input type="text" formControlName="username" autocomplete="username" placeholder="admin" />
          </label>
          <label>
            Password
            <input type="password" formControlName="password" autocomplete="current-password" placeholder="••••••••" />
          </label>
          <p class="error" *ngIf="error">{{ error }}</p>
          <button type="submit" [disabled]="form.invalid || loading">
            {{ loading ? 'Signing in…' : 'Sign in' }}
          </button>
        </form>
        <p class="hint">Day 1 demo: <code>admin / Admin&#64;123</code> · <code>manager / Admin&#64;123</code></p>
      </section>
    </main>
  `,
  styles: [`
    .login-page { min-height: 100vh; display: grid; place-items: center; background: #0f172a; color: #e2e8f0; }
    .card { width: min(420px, 92vw); background: #1e293b; border-radius: 12px; padding: 2rem; box-shadow: 0 12px 40px rgba(0,0,0,.4); }
    h1 { margin: 0; font-size: 1.8rem; }
    .subtitle { color: #94a3b8; margin: 0.25rem 0 1.5rem; }
    label { display: block; margin-bottom: 1rem; font-size: .9rem; }
    input { width: 100%; margin-top: .35rem; padding: .6rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; box-sizing: border-box; }
    button { width: 100%; padding: .7rem; border: 0; border-radius: 8px; background: #2563eb; color: #fff; font-weight: 600; cursor: pointer; }
    button:disabled { opacity: .6; cursor: default; }
    .error { color: #fca5a5; }
    .hint { color: #94a3b8; font-size: .85rem; }
  `],
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  loading = false;
  error: string | null = null;

  form = this.fb.group({
    username: ['', [Validators.required, Validators.minLength(3)]],
    password: ['', [Validators.required, Validators.minLength(4)]],
  });

  onSubmit(): void {
    if (this.form.invalid) return;
    this.loading = true;
    this.error = null;
    const { username, password } = this.form.getRawValue();
    this.auth.login({ username: username!, password: password! }).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigate(['/home']);
      },
      error: (err) => {
        this.loading = false;
        this.error =
          err?.status === 401 ? 'Invalid username or password.' : 'Login failed. Is the backend running?';
      },
    });
  }
}
