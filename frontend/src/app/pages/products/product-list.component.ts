import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Category, Product } from '../../core/models/master-data.models';
import { ProductService } from '../../core/services/product.service';
import { CategoryService } from '../../core/services/category.service';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/home" class="back">← Home</a>
          <h1>Products</h1>
        </div>
        <button *ngIf="canWrite && !showForm" (click)="onNew()">+ New product</button>
      </header>

      <section class="card filters">
        <input type="text" placeholder="Search SKU or name…" [value]="search"
          (input)="onSearch($any($event.target).value)" />
        <select [value]="categoryFilter" (change)="onCategoryFilter($any($event.target).value)">
          <option value="">All categories</option>
          <option *ngFor="let c of categories" [value]="c.id">{{ c.name }}</option>
        </select>
        <select [value]="activeFilter" (change)="onActiveFilter($any($event.target).value)">
          <option value="">All statuses</option>
          <option value="true">Active</option>
          <option value="false">Inactive</option>
        </select>
      </section>

      <p class="notice error" *ngIf="error">{{ error }}</p>
      <p class="notice success" *ngIf="message">{{ message }}</p>

      <section class="card form-card" *ngIf="showForm">
        <h2>{{ editing ? 'Edit product' : 'New product' }}</h2>
        <form [formGroup]="form" (ngSubmit)="onSubmit()">
          <div class="grid">
            <label>SKU<input type="text" formControlName="sku" placeholder="PHN-001" /></label>
            <label>Barcode<input type="text" formControlName="barcode" placeholder="Optional, e.g. 8901234567890" /></label>
            <label>Name<input type="text" formControlName="name" placeholder="Phone" /></label>
            <label>Category
              <select formControlName="categoryId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let c of categories" [ngValue]="c.id">{{ c.name }} ({{ c.code }})</option>
              </select>
            </label>
            <label>Brand<input type="text" formControlName="brand" placeholder="Optional" /></label>
            <label>Unit<input type="text" formControlName="unit" placeholder="PCS" /></label>
            <label>Cost price<input type="number" step="0.01" min="0" formControlName="costPrice" /></label>
            <label>Selling price<input type="number" step="0.01" min="0" formControlName="sellingPrice" /></label>
            <label>Minimum stock level<input type="number" step="1" min="0" formControlName="minimumStockLevel" /></label>
            <label class="check"><input type="checkbox" formControlName="active" /> Active</label>
          </div>
          <label>Description<input type="text" formControlName="description" placeholder="Optional" /></label>
          <div class="row">
            <button type="submit" [disabled]="form.invalid || saving">{{ saving ? 'Saving…' : 'Save' }}</button>
            <button type="button" class="ghost" (click)="onCancel()">Cancel</button>
          </div>
        </form>
      </section>

      <section class="card">
        <p *ngIf="loading" class="muted">Loading…</p>
        <p *ngIf="!loading && items.length === 0" class="muted">No products found.</p>
        <table *ngIf="!loading && items.length > 0">
          <thead><tr><th>SKU</th><th>Barcode</th><th>Name</th><th>Category</th><th>Cost</th><th>Price</th><th>Min</th><th>Status</th><th *ngIf="canWrite">Actions</th></tr></thead>
          <tbody>
            <tr *ngFor="let p of items">
              <td><code>{{ p.sku }}</code></td>
              <td><code>{{ p.barcode ?? '—' }}</code></td>
              <td>{{ p.name }}</td>
              <td>{{ p.categoryName ?? '—' }}</td>
              <td>{{ p.costPrice }}</td>
              <td>{{ p.sellingPrice }}</td>
              <td>{{ p.minimumStockLevel ?? 0 }}</td>
              <td>{{ p.active ? 'Active' : 'Inactive' }}</td>
              <td *ngIf="canWrite">
                <button class="link" (click)="onEdit(p)">Edit</button>
                <button class="link danger" (click)="onDelete(p)">Delete</button>
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
    label input, label select { width: 100%; margin-top: .3rem; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; box-sizing: border-box; }
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
export class ProductListComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly service = inject(ProductService);
  private readonly categoriesApi = inject(CategoryService);
  private readonly auth = inject(AuthService);

  items: Product[] = [];
  categories: Category[] = [];
  total = 0;
  loading = false;
  saving = false;
  error: string | null = null;
  message: string | null = null;
  search = '';
  activeFilter = '';
  categoryFilter = '';
  showForm = false;
  editing: Product | null = null;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;

  get canWrite(): boolean {
    return this.auth.role === 'ADMIN' || this.auth.role === 'INVENTORY_MANAGER';
  }

  form = this.fb.group({
    sku: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(50)]],
    barcode: ['', [Validators.pattern(/^[A-Za-z0-9.\-+/]{4,60}$/)]],
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(150)]],
    description: [''],
    categoryId: [null as number | null, [Validators.required]],
    brand: [''],
    unit: ['PCS', [Validators.required, Validators.maxLength(20)]],
    costPrice: [0, [Validators.required, Validators.min(0)]],
    sellingPrice: [0, [Validators.required, Validators.min(0)]],
    minimumStockLevel: [0, [Validators.min(0)]],
    active: [true],
  });

  ngOnInit(): void {
    this.categoriesApi.list('', null, 0, 200).subscribe({
      next: (page) => {
        this.categories = page.content;
      },
      error: () => {
        this.error = 'Failed to load categories for the product form.';
      },
    });
    this.load();
  }

  load(): void {
    this.loading = true;
    this.error = null;
    const active = this.activeFilter === '' ? null : this.activeFilter === 'true';
    const categoryId = this.categoryFilter === '' ? null : Number(this.categoryFilter);
    this.service.list(this.search, active, categoryId).subscribe({
      next: (page) => {
        this.loading = false;
        this.items = page.content;
        this.total = page.totalElements;
      },
      error: (err) => {
        this.loading = false;
        this.error = this.describeError(err, 'Failed to load products.');
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

  onCategoryFilter(value: string): void {
    this.categoryFilter = value;
    this.load();
  }

  onNew(): void {
    this.editing = null;
    this.form.reset({ sku: '', barcode: '', name: '', description: '', categoryId: null, brand: '', unit: 'PCS', costPrice: 0, sellingPrice: 0, minimumStockLevel: 0, active: true });
    this.showForm = true;
    this.message = null;
  }

  onEdit(p: Product): void {
    this.editing = p;
    this.form.reset({
      sku: p.sku, barcode: p.barcode ?? '', name: p.name, description: p.description ?? '', categoryId: p.categoryId,
      brand: p.brand ?? '', unit: p.unit, costPrice: p.costPrice, sellingPrice: p.sellingPrice,
      minimumStockLevel: p.minimumStockLevel ?? 0, active: p.active,
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
    const barcode = v.barcode?.trim() ? v.barcode.trim().toUpperCase() : null;
    const req = {
      sku: v.sku!, name: v.name!, description: v.description || null, barcode, categoryId: v.categoryId!,
      brand: v.brand || null, unit: v.unit!, costPrice: Number(v.costPrice), sellingPrice: Number(v.sellingPrice),
      minimumStockLevel: v.minimumStockLevel === null || v.minimumStockLevel === undefined ? null : Number(v.minimumStockLevel),
      active: !!v.active,
    };
    const done = {
      next: () => {
        this.saving = false;
        this.showForm = false;
        this.editing = null;
        this.message = 'Product saved.';
        this.load();
      },
      error: (err: unknown) => {
        this.saving = false;
        this.error = this.describeError(err, 'Failed to save product.');
      },
    };
    if (this.editing) this.service.update(this.editing.id, req).subscribe(done);
    else this.service.create(req).subscribe(done);
  }

  onDelete(p: Product): void {
    if (!confirm(`Delete product "${p.sku}"?`)) return;
    this.error = null;
    this.message = null;
    this.service.delete(p.id).subscribe({
      next: () => {
        this.message = 'Product deleted.';
        this.load();
      },
      error: (err) => {
        this.error = this.describeError(err, 'Failed to delete product.');
      },
    });
  }

  private describeError(err: unknown, fallback: string): string {
    const e = err as { status?: number; error?: { message?: string } };
    if (e?.error?.message) return e.error.message;
    if (e?.status === 409) return 'Conflict: duplicate SKU.';
    if (e?.status === 403) return 'Forbidden: insufficient role.';
    if (e?.status === 404) return 'Record not found (check category).';
    return fallback;
  }
}
