import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Warehouse } from '../../core/models/master-data.models';
import { WarehouseService } from '../../core/services/warehouse.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-warehouse-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/home" class="back">← Home</a>
          <h1>Warehouses</h1>
        </div>
        <button *ngIf="canWrite && !showForm" (click)="onNew()">+ New warehouse</button>
      </header>

      <section class="card filters">
        <input type="text" placeholder="Search code, name or city…" [value]="search"
          (input)="onSearch($any($event.target).value)" />
        <select [value]="activeFilter" (change)="onActiveFilter($any($event.target).value)">
          <option value="">All statuses</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </select>
      </section>

      <p class="notice error" *ngIf="error">{{ error }}</p>
      <p class="notice success" *ngIf="message">{{ message }}</p>

      <section class="card form-card" *ngIf="showForm">
        <h2>{{ editing ? 'Edit warehouse' : 'New warehouse' }}</h2>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <div class="grid">
            <label>Code<input type="text" formControlName="code" placeholder="WH-01" /></label>
            <label>Name<input type="text" formControlName="name" placeholder="Central Depot" /></label>
            <label>City<input type="text" formControlName="city" /></label>
            <label>State<input type="text" formControlName="state" /></label>
            <label>Postal code<input type="text" formControlName="postalCode" /></label>
            <label>Contact person<input type="text" formControlName="contactPerson" /></label>
            <label>Contact email<input type="text" formControlName="contactEmail" /></label>
            <label>Contact phone<input type="text" formControlName="contactPhone" /></label>
          </div>
          <label>Address<input type="text" formControlName="address" /></label>
          <label class="check"><input type="checkbox" formControlName="active" /> Active</label>
          <div class="row">
            <button type="submit" [disabled]="form.invalid || saving">{{ saving ? 'Saving…' : 'Save' }}</button>
            <button type="button" class="ghost" (click)="onCancel()">Cancel</button>
          </div>
        </form>
      </section>

      <section class="card">
        <p *ngIf="loading" class="muted">Loading…</p>
        <p *ngIf="!loading && items.length === 0" class="muted">No warehouses found.</p>
        <table *ngIf="!loading && items.length > 0">
          <thead><tr><th>Code</th><th>Name</th><th>City</th><th>Contact</th><th>Status</th><th *ngIf="canWrite">Actions</th></tr></thead>
          <tbody>
            <tr *ngFor="let w of items">
              <td><code>{{ w.code }}</code></td>
              <td>{{ w.name }}</td>
              <td>{{ w.city ?? '—' }}</td>
              <td>{{ w.contactPerson ?? w.contactEmail ?? '—' }}</td>
              <td>{{ w.active ? 'Active' : 'Inactive' }}</td>
              <td *ngIf="canWrite">
                <button class="link" (click)="onEdit(w)">Edit</button>
                <button class="link danger" (click)="onDelete(w)">Delete</button>
              </td>
            </tr>
          </tbody>
        </table>
        <p class="muted" *ngIf="total > 0">Total: {{ total }}</p>
      </section>
    </main>
  `,
  styles: [`
    .page { min-height: 100vh; background: #0f172a; color: #e2e8f0; padding: 2rem; box-sizing: border-box; }
    header { display: flex; justify-content: space-between; align-items: flex-end; max-width: 1000px; margin: 0 auto 1.5rem; }
    .back { color: #93c5fd; text-decoration: none; font-size: .85rem; }
    h1 { margin: .25rem 0 0; } h2 { margin-top: 0; font-size: 1.1rem; }
    .card { max-width: 1000px; margin: 0 auto 1rem; background: #1e293b; border-radius: 12px; padding: 1.25rem 1.5rem; }
    .filters { display: flex; gap: .75rem; }
    .filters input, .filters select { flex: 1; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0 .9rem; }
    label { display: block; margin-bottom: .8rem; font-size: .9rem; }
    label input { width: 100%; margin-top: .3rem; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; box-sizing: border-box; }
    .check { display: flex; gap: .5rem; align-items: center; }
    .row { display: flex; gap: .75rem; }
    button { padding: .55rem 1.1rem; border-radius: 8px; border: 0; background: #2563eb; color: #fff; font-weight: 600; cursor: pointer; }
    button:disabled { opacity: .6; cursor: default; }
    button.ghost { background: #0f172a; border: 1px solid #334155; }
    button.link { background: none; border: 0; color: #93c5fd; padding: .2rem .4rem; }
    button.danger { color: #fca5a5; }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: .55rem .5rem; border-bottom: 1px solid #334155; font-size: .9rem; }
    .muted { color: #94a3b8; }
    .notice { max-width: 1000px; margin: 0 auto 1rem; padding: .6rem 1rem; border-radius: 8px; }
    .error { background: #451a1a; color: #fca5a5; }
    .success { background: #14532d; color: #bbf7d0; }
  `],
})
export class WarehouseListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(WarehouseService);
  private readonly auth = inject(AuthService);

  items: Warehouse[] = [];
  total = 0;
  loading = false;
  saving = false;
  error: string | null = null;
  message: string | null = null;
  search = '';
  activeFilter = '';
  showForm = false;
  editing: Warehouse | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  get canWrite(): boolean {
    return this.auth.role === 'ADMIN' || this.auth.role === 'INVENTORY_MANAGER';
  }

  form = this.fb.group({
    code: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(30)]],
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(150)]],
    address: [''],
    city: [''],
    state: [''],
    postalCode: [''],
    contactPerson: [''],
    contactEmail: ['', [Validators.email]],
    contactPhone: [''],
    active: [true],
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    const active = this.activeFilter === '' ? null : this.activeFilter === 'true';
    this.service.list(this.search, active).subscribe({
      next: (page) => {
        this.loading = false;
        this.items = page.content;
        this.total = page.totalElements;
      },
      error: (err) => {
        this.loading = false;
        this.error = this.describeError(err, 'Failed to load warehouses.');
      },
    });
  }

  onSearch(value: string): void {
    this.search = value;
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.load(), 350);
  }

  onActiveFilter(value: string): void {
    this.activeFilter = value;
    this.load();
  }

  onNew(): void {
    this.editing = null;
    this.form.reset({ code: '', name: '', address: '', city: '', state: '', postalCode: '', contactPerson: '', contactEmail: '', contactPhone: '', active: true });
    this.showForm = true;
    this.message = null;
  }

  onEdit(w: Warehouse): void {
    this.editing = w;
    this.form.reset({
      code: w.code, name: w.name, address: w.address ?? '', city: w.city ?? '', state: w.state ?? '',
      postalCode: w.postalCode ?? '', contactPerson: w.contactPerson ?? '', contactEmail: w.contactEmail ?? '',
      contactPhone: w.contactPhone ?? '', active: w.active,
    });
    this.showForm = true;
    this.message = null;
  }

  onCancel(): void {
    this.showForm = false;
    this.editing = null;
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    this.saving = true;
    this.error = null;
    this.message = null;
    const v = this.form.getRawValue();
    const opt = (s: string | undefined | null) => (s?.trim() ? s.trim() : null);
    const req = {
      code: v.code!, name: v.name!, address: opt(v.address), city: opt(v.city), state: opt(v.state),
      postalCode: opt(v.postalCode), contactPerson: opt(v.contactPerson), contactEmail: opt(v.contactEmail),
      contactPhone: opt(v.contactPhone), active: !!v.active,
    };
    const done = {
      next: () => {
        this.saving = false;
        this.showForm = false;
        this.editing = null;
        this.message = 'Warehouse saved.';
        this.load();
      },
      error: (err: unknown) => {
        this.saving = false;
        this.error = this.describeError(err, 'Failed to save warehouse.');
      },
    };
    if (this.editing) this.service.update(this.editing.id, req).subscribe(done);
    else this.service.create(req).subscribe(done);
  }

  onDelete(w: Warehouse): void {
    if (!confirm(`Delete warehouse "${w.code}"?`)) return;
    this.error = null;
    this.message = null;
    this.service.delete(w.id).subscribe({
      next: () => {
        this.message = 'Warehouse deleted.';
        this.load();
      },
      error: (err) => {
        this.error = this.describeError(err, 'Failed to delete warehouse.');
      },
    });
  }

  private describeError(err: unknown, fallback: string): string {
    const e = err as { status?: number; error?: { message?: string } };
    if (e?.error?.message) return e.error.message;
    if (e?.status === 409) return 'Conflict: duplicate code.';
    if (e?.status === 403) return 'Forbidden: insufficient role.';
    if (e?.status === 404) return 'Record not found.';
    return fallback;
  }
}
