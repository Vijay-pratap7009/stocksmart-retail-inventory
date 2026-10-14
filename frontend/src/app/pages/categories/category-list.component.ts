import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Category } from '../../core/models/master-data.models';
import { CategoryService } from '../../core/services/category.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-category-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/home" class="back">← Home</a>
          <h1>Categories</h1>
        </div>
        <button *ngIf="canWrite && !showForm" (click)="onNew()">+ New category</button>
      </header>

      <section class="card filters">
        <input type="text" placeholder="Search name or code…" [value]="search"
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
        <h2>{{ editing ? 'Edit category' : 'New category' }}</h2>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <label>Code
            <input type="text" formControlName="code" placeholder="ELEC" />
          </label>
          <label>Name
            <input type="text" formControlName="name" placeholder="Electronics" />
          </label>
          <label>Description
            <input type="text" formControlName="description" placeholder="Optional" />
          </label>
          <label class="check"><input type="checkbox" formControlName="active" /> Active</label>
          <div class="row">
            <button type="submit" [disabled]="form.invalid || saving">
              {{ saving ? 'Saving…' : 'Save' }}
            </button>
            <button type="button" class="ghost" (click)="onCancel()">Cancel</button>
          </div>
        </form>
      </section>

      <section class="card">
        <p *ngIf="loading" class="muted">Loading…</p>
        <p *ngIf="!loading && items.length === 0" class="muted">No categories found.</p>
        <table *ngIf="!loading && items.length > 0">
          <thead><tr><th>Code</th><th>Name</th><th>Status</th><th *ngIf="canWrite">Actions</th></tr></thead>
          <tbody>
            <tr *ngFor="let c of items">
              <td><code>{{ c.code }}</code></td>
              <td>{{ c.name }}</td>
              <td>{{ c.active ? 'Active' : 'Inactive' }}</td>
              <td *ngIf="canWrite">
                <button class="link" (click)="onEdit(c)">Edit</button>
                <button class="link danger" (click)="onDelete(c)">Delete</button>
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
    header { display: flex; justify-content: space-between; align-items: flex-end; max-width: 900px; margin: 0 auto 1.5rem; }
    .back { color: #93c5fd; text-decoration: none; font-size: .85rem; }
    h1 { margin: .25rem 0 0; } h2 { margin-top: 0; font-size: 1.1rem; }
    .card { max-width: 900px; margin: 0 auto 1rem; background: #1e293b; border-radius: 12px; padding: 1.25rem 1.5rem; }
    .filters { display: flex; gap: .75rem; }
    .filters input, .filters select { flex: 1; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; }
    label { display: block; margin-bottom: .8rem; font-size: .9rem; }
    label input[type=text] { width: 100%; margin-top: .3rem; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; box-sizing: border-box; }
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
    .notice { max-width: 900px; margin: 0 auto 1rem; padding: .6rem 1rem; border-radius: 8px; }
    .error { background: #451a1a; color: #fca5a5; }
    .success { background: #14532d; color: #bbf7d0; }
  `],
})
export class CategoryListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(CategoryService);
  private readonly auth = inject(AuthService);

  items: Category[] = [];
  total = 0;
  loading = false;
  saving = false;
  error: string | null = null;
  message: string | null = null;
  search = '';
  activeFilter = '';
  showForm = false;
  editing: Category | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  get canWrite(): boolean {
    return this.auth.role === 'ADMIN' || this.auth.role === 'INVENTORY_MANAGER';
  }

  form = this.fb.group({
    code: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(30)]],
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    description: [''],
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
        this.error = this.describeError(err, 'Failed to load categories.');
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
    this.form.reset({ code: '', name: '', description: '', active: true });
    this.showForm = true;
    this.message = null;
  }

  onEdit(c: Category): void {
    this.editing = c;
    this.form.reset({ code: c.code, name: c.name, description: c.description ?? '', active: c.active });
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
    const req = { code: v.code!, name: v.name!, description: v.description || null, active: !!v.active };
    const done = {
      next: () => {
        this.saving = false;
        this.showForm = false;
        this.editing = null;
        this.message = 'Category saved.';
        this.load();
      },
      error: (err: unknown) => {
        this.saving = false;
        this.error = this.describeError(err, 'Failed to save category.');
      },
    };
    if (this.editing) this.service.update(this.editing.id, req).subscribe(done);
    else this.service.create(req).subscribe(done);
  }

  onDelete(c: Category): void {
    if (!confirm(`Delete category "${c.code}"?`)) return;
    this.error = null;
    this.message = null;
    this.service.delete(c.id).subscribe({
      next: () => {
        this.message = 'Category deleted.';
        this.load();
      },
      error: (err) => {
        this.error = this.describeError(err, 'Failed to delete category.');
      },
    });
  }

  private describeError(err: unknown, fallback: string): string {
    const e = err as { status?: number; error?: { message?: string } };
    if (e?.error?.message) return e.error.message;
    if (e?.status === 409) return 'Conflict: duplicate code or referenced record.';
    if (e?.status === 403) return 'Forbidden: insufficient role.';
    if (e?.status === 404) return 'Record not found.';
    return fallback;
  }
}
