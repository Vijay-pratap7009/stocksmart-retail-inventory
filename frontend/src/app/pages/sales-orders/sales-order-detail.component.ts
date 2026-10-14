import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Page, Product, Warehouse } from '../../core/models/master-data.models';
import { SalesFulfillment, SalesOrder } from '../../core/models/order.models';
import { SalesOrderService } from '../../core/services/sales-order.service';
import { ProductService } from '../../core/services/product.service';
import { WarehouseService } from '../../core/services/warehouse.service';
import { AuthService } from '../../core/services/auth.service';

interface DraftLine {
  productId: number | null;
  orderedQuantity: number | null;
  unitPrice: number | null;
}

@Component({
  selector: 'app-sales-order-detail',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/sales-orders" class="back">← Sales orders</a>
          <h1>{{ isNew ? 'New sales order' : (order?.soNumber ?? 'Order') }}</h1>
        </div>
        <span class="badge" *ngIf="order">{{ order.status }}</span>
      </header>

      <p class="notice error" *ngIf="error">{{ error }}</p>
      <p class="notice success" *ngIf="message">{{ message }}</p>
      <p *ngIf="loading" class="notice muted-card">Loading…</p>

      <!-- Create / edit form -->
      <section class="card" *ngIf="!loading && (isNew || editing)">
        <form [formGroup]="form" (ngSubmit)="onSave()">
          <div class="grid">
            <label>SO number (optional)<input type="text" formControlName="soNumber" placeholder="Auto-generated" /></label>
            <label>Warehouse
              <select formControlName="warehouseId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.code }} — {{ w.name }}</option>
              </select>
            </label>
            <label>Customer name<input type="text" formControlName="customerName" /></label>
            <label>Customer contact<input type="text" formControlName="customerContact" /></label>
            <label>Order date<input type="date" formControlName="orderDate" /></label>
          </div>
          <label>Notes<input type="text" formControlName="notes" /></label>
          <h3>Lines</h3>
          <div class="line" *ngFor="let l of draftLines; let i = index">
            <select [(ngModel)]="l.productId" [ngModelOptions]="{standalone: true}">
              <option [ngValue]="null">Product…</option>
              <option *ngFor="let p of products" [ngValue]="p.id">{{ p.sku }} — {{ p.name }}</option>
            </select>
            <input type="number" step="0.0001" min="0" placeholder="Qty" [(ngModel)]="l.orderedQuantity" [ngModelOptions]="{standalone: true}" />
            <input type="number" step="0.01" min="0" placeholder="Unit price" [(ngModel)]="l.unitPrice" [ngModelOptions]="{standalone: true}" />
            <button type="button" class="ghost danger" (click)="removeLine(i)">Remove</button>
          </div>
          <div class="row">
            <button type="button" class="ghost" (click)="addLine()">+ Add line</button>
            <button type="submit" [disabled]="form.invalid || !linesValid() || saving">
              {{ saving ? 'Saving…' : 'Save draft' }}
            </button>
            <button type="button" class="ghost" *ngIf="!isNew" (click)="editing = false">Cancel</button>
          </div>
        </form>
      </section>

      <!-- Detail view -->
      <section class="card" *ngIf="!loading && order && !editing">
        <p><strong>Customer:</strong> {{ order.customerName }}
          <span *ngIf="order.customerContact">({{ order.customerContact }})</span> ·
          <strong>Warehouse:</strong> {{ order.warehouseCode }} — {{ order.warehouseName }}</p>
        <p><strong>Date:</strong> {{ order.orderDate }} · <strong>Total:</strong> {{ order.orderTotal }}</p>
        <p class="muted" *ngIf="order.notes">{{ order.notes }}</p>
        <table>
          <thead><tr><th>SKU</th><th>Product</th><th>Ordered</th><th>Fulfilled</th><th>Remaining</th><th>Unit price</th></tr></thead>
          <tbody>
            <tr *ngFor="let l of order.items">
              <td><code>{{ l.sku }}</code></td>
              <td>{{ l.productName }}</td>
              <td>{{ l.orderedQuantity }}</td>
              <td>{{ l.fulfilledQuantity }}</td>
              <td>{{ l.remainingQuantity }}</td>
              <td>{{ l.unitPrice }}</td>
            </tr>
          </tbody>
        </table>
        <div class="row actions">
          <button *ngIf="canManage && order.status === 'DRAFT'" (click)="onEdit()">Edit draft</button>
          <button *ngIf="canManage && order.status === 'DRAFT' && order.items.length > 0" (click)="onConfirm()">Confirm order</button>
          <button class="warn" *ngIf="canManage && isOpen()" (click)="onCancel()">Cancel order</button>
        </div>
      </section>

      <!-- Fulfillment -->
      <section class="card" *ngIf="!loading && order && !editing && canFulfill && isFulfillable()">
        <h3>Fulfill quantities</h3>
        <p class="muted">Stock is checked at fulfillment time — confirming does not reserve it.</p>
        <div class="line" *ngFor="let l of order.items">
          <span class="grow"><code>{{ l.sku }}</code> (remaining {{ l.remainingQuantity }})</span>
          <input type="number" step="0.0001" min="0" placeholder="Qty" [(ngModel)]="fulfillQty[l.id]" />
        </div>
        <label>Reference (optional, idempotency key)<input type="text" [(ngModel)]="fulfillRef" /></label>
        <button (click)="onFulfill()" [disabled]="saving">{{ saving ? 'Fulfilling…' : 'Fulfill' }}</button>
      </section>

      <!-- Fulfillment history -->
      <section class="card" *ngIf="!loading && order && fulfillments.length > 0">
        <h3>Fulfillment history</h3>
        <div *ngFor="let f of fulfillments" class="hist">
          <p><code>{{ f.fulfillmentNumber }}</code> · {{ f.fulfilledAt || '' }} · by {{ f.fulfilledByUsername }}</p>
          <p class="muted" *ngFor="let l of f.lines">{{ l.sku }}: {{ l.quantity }}</p>
        </div>
      </section>
    </main>
  `,
  styles: [`
    .page { min-height: 100vh; background: #0f172a; color: #e2e8f0; padding: 2rem; box-sizing: border-box; }
    header { display: flex; justify-content: space-between; align-items: center; max-width: 1000px; margin: 0 auto 1.5rem; }
    .back { color: #93c5fd; text-decoration: none; font-size: .85rem; }
    h1 { margin: .25rem 0 0; font-size: 1.4rem; } h3 { margin: 1.2rem 0 .6rem; font-size: 1rem; }
    .card { max-width: 1000px; margin: 0 auto 1rem; background: #1e293b; border-radius: 12px; padding: 1.25rem 1.5rem; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 0 .9rem; }
    label { display: block; margin-bottom: .8rem; font-size: .9rem; }
    label input, label select { width: 100%; margin-top: .3rem; padding: .55rem .7rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; box-sizing: border-box; }
    .line { display: flex; gap: .6rem; align-items: center; margin-bottom: .6rem; }
    .line select, .line input { flex: 1; padding: .5rem .6rem; border-radius: 8px; border: 1px solid #334155; background: #0f172a; color: #e2e8f0; }
    .grow { flex: 2; }
    .row { display: flex; gap: .75rem; margin-top: .8rem; flex-wrap: wrap; }
    .actions { margin-top: 1rem; }
    button { padding: .55rem 1.1rem; border-radius: 8px; border: 0; background: #2563eb; color: #fff; font-weight: 600; cursor: pointer; }
    button:disabled { opacity: .6; cursor: default; }
    button.ghost { background: #0f172a; border: 1px solid #334155; }
    button.danger { color: #fca5a5; }
    button.warn { background: #7f1d1d; }
    table { width: 100%; border-collapse: collapse; margin-top: .6rem; }
    th, td { text-align: left; padding: .5rem; border-bottom: 1px solid #334155; font-size: .9rem; }
    .badge { background: #0f172a; border: 1px solid #334155; border-radius: 6px; padding: .2rem .6rem; }
    .muted { color: #94a3b8; }
    .muted-card { max-width: 1000px; margin: 0 auto 1rem; color: #94a3b8; }
    .hist { border-top: 1px solid #334155; padding: .5rem 0; }
    .notice { max-width: 1000px; margin: 0 auto 1rem; padding: .6rem 1rem; border-radius: 8px; }
    .error { background: #451a1a; color: #fca5a5; }
    .success { background: #14532d; color: #bbf7d0; }
  `],
})
export class SalesOrderDetailComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(SalesOrderService);
  private readonly productsApi = inject(ProductService);
  private readonly warehousesApi = inject(WarehouseService);
  private readonly auth = inject(AuthService);

  isNew = false;
  editing = false;
  loading = false;
  saving = false;
  error: string | null = null;
  message: string | null = null;
  order: SalesOrder | null = null;
  fulfillments: SalesFulfillment[] = [];
  products: Product[] = [];
  warehouses: Warehouse[] = [];
  draftLines: DraftLine[] = [];
  fulfillQty: Record<number, number | null> = {};
  fulfillRef = '';
  private orderId: number | null = null;

  get canManage(): boolean {
    const r = this.auth.role;
    return r === 'ADMIN' || r === 'SALES_USER';
  }

  get canFulfill(): boolean {
    const r = this.auth.role;
    return r === 'ADMIN' || r === 'SALES_USER' || r === 'INVENTORY_MANAGER' || r === 'WAREHOUSE_STAFF';
  }

  form = this.fb.group({
    soNumber: [''],
    warehouseId: [null as number | null, [Validators.required]],
    customerName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(150)]],
    customerContact: [''],
    orderDate: [new Date().toISOString().slice(0, 10), [Validators.required]],
    notes: [''],
  });

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    this.isNew = id === 'new';
    this.orderId = this.isNew ? null : Number(id);
    this.productsApi.list('', null, null, 0, 200).subscribe({
      next: (p: Page<Product>) => {
        this.products = p.content;
      },
      error: () => undefined,
    });
    this.warehousesApi.list('', null, 0, 200).subscribe({
      next: (p: Page<Warehouse>) => {
        this.warehouses = p.content;
      },
      error: () => undefined,
    });
    if (this.isNew) {
      this.draftLines = [{ productId: null, orderedQuantity: null, unitPrice: null }];
    } else {
      this.load();
    }
  }

  isOpen(): boolean {
    return !!this.order && this.order.status !== 'FULFILLED' && this.order.status !== 'CANCELLED';
  }

  isFulfillable(): boolean {
    return !!this.order
      && (this.order.status === 'CONFIRMED' || this.order.status === 'PARTIALLY_FULFILLED');
  }

  load(): void {
    if (this.orderId == null) return;
    this.loading = true;
    this.error = null;
    this.service.get(this.orderId).subscribe({
      next: (o) => {
        this.loading = false;
        this.order = o;
        this.fulfillQty = {};
        this.service.fulfillments(o.id).subscribe({
          next: (f) => {
            this.fulfillments = f;
          },
          error: () => undefined,
        });
      },
      error: (err) => {
        this.loading = false;
        this.error = this.describeError(err, 'Failed to load order.');
      },
    });
  }

  addLine(): void {
    this.draftLines.push({ productId: null, orderedQuantity: null, unitPrice: null });
  }

  removeLine(i: number): void {
    this.draftLines.splice(i, 1);
  }

  linesValid(): boolean {
    return this.draftLines.length > 0 && this.draftLines.every((l) =>
      l.productId != null && (l.orderedQuantity ?? 0) > 0 && (l.unitPrice ?? -1) >= 0);
  }

  onSave(): void {
    if (this.form.invalid || !this.linesValid()) return;
    const v = this.form.getRawValue();
    const req = {
      soNumber: v.soNumber?.trim() || null,
      warehouseId: v.warehouseId!,
      customerName: v.customerName!.trim(),
      customerContact: v.customerContact?.trim() || null,
      orderDate: v.orderDate!,
      notes: v.notes?.trim() || null,
      items: this.draftLines.map((l) => ({
        productId: l.productId!,
        orderedQuantity: Number(l.orderedQuantity),
        unitPrice: Number(l.unitPrice),
      })),
    };
    this.saving = true;
    this.error = null;
    this.message = null;
    const done = {
      next: (o: SalesOrder) => {
        this.saving = false;
        if (this.isNew) this.router.navigate(['/sales-orders', o.id]);
        else {
          this.editing = false;
          this.message = 'Draft saved.';
          this.load();
        }
      },
      error: (err: unknown) => {
        this.saving = false;
        this.error = this.describeError(err, 'Failed to save order.');
      },
    };
    if (this.isNew) this.service.create(req).subscribe(done);
    else this.service.update(this.orderId!, req).subscribe(done);
  }

  onEdit(): void {
    if (!this.order) return;
    this.form.reset({
      soNumber: this.order.soNumber,
      warehouseId: this.order.warehouseId,
      customerName: this.order.customerName,
      customerContact: this.order.customerContact ?? '',
      orderDate: this.order.orderDate,
      notes: this.order.notes ?? '',
    });
    this.draftLines = this.order.items.map((l) => ({
      productId: l.productId,
      orderedQuantity: l.orderedQuantity,
      unitPrice: l.unitPrice,
    }));
    this.editing = true;
    this.message = null;
  }

  onConfirm(): void {
    if (!this.order || !confirm(`Confirm order ${this.order.soNumber}? Stock is checked at fulfillment, not now.`)) return;
    this.op(this.service.confirm(this.order.id), 'Order confirmed.');
  }

  onCancel(): void {
    if (!this.order || !confirm(`Cancel order ${this.order.soNumber}?`)) return;
    this.op(this.service.cancel(this.order.id), 'Order cancelled.');
  }

  onFulfill(): void {
    if (!this.order) return;
    const lines = Object.entries(this.fulfillQty)
      .filter(([, q]) => (q ?? 0) > 0)
      .map(([itemId, q]) => ({ soItemId: Number(itemId), quantity: Number(q) }));
    if (lines.length === 0) {
      this.error = 'Enter a quantity for at least one line.';
      return;
    }
    if (!confirm(`Fulfill ${lines.length} line(s)? Stock will be issued.`)) return;
    this.saving = true;
    this.error = null;
    this.message = null;
    this.service.fulfill(this.order.id, lines, this.fulfillRef || null).subscribe({
      next: (f) => {
        this.saving = false;
        this.fulfillRef = '';
        this.message = `Fulfillment ${f.fulfillmentNumber} recorded.`;
        this.load();
      },
      error: (err: unknown) => {
        this.saving = false;
        this.error = this.describeError(err, 'Failed to fulfill order.');
      },
    });
  }

  private op(obs: { subscribe(o: object): void }, ok: string): void {
    this.saving = true;
    this.error = null;
    this.message = null;
    obs.subscribe({
      next: () => {
        this.saving = false;
        this.message = ok;
        this.load();
      },
      error: (err: unknown) => {
        this.saving = false;
        this.error = this.describeError(err, 'Operation failed.');
      },
    } as never);
  }

  private describeError(err: unknown, fallback: string): string {
    const e = err as { status?: number; error?: { message?: string } };
    if (e?.error?.message) return e.error.message;
    if (e?.status === 409) return 'Conflict: duplicate number, ineligible status, over-fulfillment, or insufficient stock.';
    if (e?.status === 403) return 'Forbidden: insufficient role.';
    if (e?.status === 404) return 'Order or referenced record not found.';
    return fallback;
  }
}
