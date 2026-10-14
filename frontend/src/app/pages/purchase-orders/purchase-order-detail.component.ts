import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Page, Product, Supplier, Warehouse } from '../../core/models/master-data.models';
import { PurchaseOrder, PurchaseReceipt } from '../../core/models/order.models';
import { PurchaseOrderService } from '../../core/services/purchase-order.service';
import { ProductService } from '../../core/services/product.service';
import { WarehouseService } from '../../core/services/warehouse.service';
import { SupplierService } from '../../core/services/supplier.service';
import { AuthService } from '../../core/services/auth.service';

interface DraftLine {
  productId: number | null;
  orderedQuantity: number | null;
  unitCost: number | null;
}

@Component({
  selector: 'app-purchase-order-detail',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterLink],
  template: `
    <main class="page">
      <header>
        <div>
          <a routerLink="/purchase-orders" class="back">← Purchase orders</a>
          <h1>{{ isNew ? 'New purchase order' : (order?.poNumber ?? 'Order') }}</h1>
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
            <label>PO number (optional)<input type="text" formControlName="poNumber" placeholder="Auto-generated" /></label>
            <label>Supplier
              <select formControlName="supplierId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let s of suppliers" [ngValue]="s.id">{{ s.code }} — {{ s.name }}</option>
              </select>
            </label>
            <label>Warehouse
              <select formControlName="warehouseId">
                <option [ngValue]="null">Select…</option>
                <option *ngFor="let w of warehouses" [ngValue]="w.id">{{ w.code }} — {{ w.name }}</option>
              </select>
            </label>
            <label>Order date<input type="date" formControlName="orderDate" /></label>
            <label>Expected delivery<input type="date" formControlName="expectedDeliveryDate" /></label>
          </div>
          <label>Notes<input type="text" formControlName="notes" /></label>
          <h3>Lines</h3>
          <div class="line" *ngFor="let l of draftLines; let i = index">
            <select [(ngModel)]="l.productId" [ngModelOptions]="{standalone: true}">
              <option [ngValue]="null">Product…</option>
              <option *ngFor="let p of products" [ngValue]="p.id">{{ p.sku }} — {{ p.name }}</option>
            </select>
            <input type="number" step="0.0001" min="0" placeholder="Qty" [(ngModel)]="l.orderedQuantity" [ngModelOptions]="{standalone: true}" />
            <input type="number" step="0.01" min="0" placeholder="Unit cost" [(ngModel)]="l.unitCost" [ngModelOptions]="{standalone: true}" />
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
        <p><strong>Supplier:</strong> {{ order.supplierName }} ({{ order.supplierCode }}) ·
          <strong>Warehouse:</strong> {{ order.warehouseCode }} — {{ order.warehouseName }}</p>
        <p><strong>Date:</strong> {{ order.orderDate }} ·
          <strong>Expected:</strong> {{ order.expectedDeliveryDate || '—' }} ·
          <strong>Total:</strong> {{ order.orderTotal }}</p>
        <p class="muted" *ngIf="order.notes">{{ order.notes }}</p>
        <table>
          <thead><tr><th>SKU</th><th>Product</th><th>Ordered</th><th>Received</th><th>Remaining</th><th>Unit cost</th></tr></thead>
          <tbody>
            <tr *ngFor="let l of order.items">
              <td><code>{{ l.sku }}</code></td>
              <td>{{ l.productName }}</td>
              <td>{{ l.orderedQuantity }}</td>
              <td>{{ l.receivedQuantity }}</td>
              <td>{{ l.remainingQuantity }}</td>
              <td>{{ l.unitCost }}</td>
            </tr>
          </tbody>
        </table>
        <div class="row actions">
          <button *ngIf="canManage && order.status === 'DRAFT'" (click)="onEdit()">Edit draft</button>
          <button *ngIf="canManage && order.status === 'DRAFT' && order.items.length > 0" (click)="onSubmit()">Submit order</button>
          <button class="warn" *ngIf="canManage && isOpen()" (click)="onCancel()">Cancel order</button>
        </div>
      </section>

      <!-- Receipt -->
      <section class="card" *ngIf="!loading && order && !editing && canReceive && isReceivable()">
        <h3>Record goods receipt</h3>
        <div class="line" *ngFor="let l of order.items">
          <span class="grow"><code>{{ l.sku }}</code> (remaining {{ l.remainingQuantity }})</span>
          <input type="number" step="0.0001" min="0" placeholder="Qty" [(ngModel)]="receiptQty[l.id]" />
        </div>
        <label>Reference (optional, idempotency key)<input type="text" [(ngModel)]="receiptRef" /></label>
        <button (click)="onReceive()" [disabled]="saving">{{ saving ? 'Receiving…' : 'Receive goods' }}</button>
      </section>

      <!-- Receipt history -->
      <section class="card" *ngIf="!loading && order && receipts.length > 0">
        <h3>Receipt history</h3>
        <div *ngFor="let r of receipts" class="hist">
          <p><code>{{ r.receiptNumber }}</code> · {{ r.receivedAt || '' }} · by {{ r.receivedByUsername }}</p>
          <p class="muted" *ngFor="let l of r.lines">{{ l.sku }}: {{ l.quantity }}</p>
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
export class PurchaseOrderDetailComponent implements OnInit {
  private readonly fb = inject(FormBuilder);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly service = inject(PurchaseOrderService);
  private readonly productsApi = inject(ProductService);
  private readonly warehousesApi = inject(WarehouseService);
  private readonly suppliersApi = inject(SupplierService);
  private readonly auth = inject(AuthService);

  isNew = false;
  editing = false;
  loading = false;
  saving = false;
  error: string | null = null;
  message: string | null = null;
  order: PurchaseOrder | null = null;
  receipts: PurchaseReceipt[] = [];
  products: Product[] = [];
  warehouses: Warehouse[] = [];
  suppliers: Supplier[] = [];
  draftLines: DraftLine[] = [];
  receiptQty: Record<number, number | null> = {};
  receiptRef = '';
  private orderId: number | null = null;

  get canManage(): boolean {
    const r = this.auth.role;
    return r === 'ADMIN' || r === 'PROCUREMENT_USER';
  }

  get canReceive(): boolean {
    const r = this.auth.role;
    return r === 'ADMIN' || r === 'PROCUREMENT_USER' || r === 'INVENTORY_MANAGER' || r === 'WAREHOUSE_STAFF';
  }

  form = this.fb.group({
    poNumber: [''],
    supplierId: [null as number | null, [Validators.required]],
    warehouseId: [null as number | null, [Validators.required]],
    orderDate: [new Date().toISOString().slice(0, 10), [Validators.required]],
    expectedDeliveryDate: [''],
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
    this.suppliersApi.list('', null, 0, 200).subscribe({
      next: (p: Page<Supplier>) => {
        this.suppliers = p.content;
      },
      error: () => undefined,
    });
    if (this.isNew) {
      this.draftLines = [{ productId: null, orderedQuantity: null, unitCost: null }];
    } else {
      this.load();
    }
  }

  isOpen(): boolean {
    return !!this.order && this.order.status !== 'RECEIVED' && this.order.status !== 'CANCELLED';
  }

  isReceivable(): boolean {
    return !!this.order
      && (this.order.status === 'SUBMITTED' || this.order.status === 'PARTIALLY_RECEIVED');
  }

  load(): void {
    if (this.orderId == null) return;
    this.loading = true;
    this.error = null;
    this.service.get(this.orderId).subscribe({
      next: (o) => {
        this.loading = false;
        this.order = o;
        this.receiptQty = {};
        this.service.receipts(o.id).subscribe({
          next: (r) => {
            this.receipts = r;
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
    this.draftLines.push({ productId: null, orderedQuantity: null, unitCost: null });
  }

  removeLine(i: number): void {
    this.draftLines.splice(i, 1);
  }

  linesValid(): boolean {
    return this.draftLines.length > 0 && this.draftLines.every((l) =>
      l.productId != null && (l.orderedQuantity ?? 0) > 0 && (l.unitCost ?? -1) >= 0);
  }

  onSave(): void {
    if (this.form.invalid || !this.linesValid()) return;
    const v = this.form.getRawValue();
    const req = {
      poNumber: v.poNumber?.trim() || null,
      supplierId: v.supplierId!,
      warehouseId: v.warehouseId!,
      orderDate: v.orderDate!,
      expectedDeliveryDate: v.expectedDeliveryDate || null,
      notes: v.notes?.trim() || null,
      items: this.draftLines.map((l) => ({
        productId: l.productId!,
        orderedQuantity: Number(l.orderedQuantity),
        unitCost: Number(l.unitCost),
      })),
    };
    this.saving = true;
    this.error = null;
    this.message = null;
    const done = {
      next: (o: PurchaseOrder) => {
        this.saving = false;
        if (this.isNew) this.router.navigate(['/purchase-orders', o.id]);
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
    this.startEdit();
    this.editing = true;
    this.message = null;
  }

  startEdit(): void {    if (!this.order) return;
    this.form.reset({
      poNumber: this.order.poNumber,
      supplierId: this.order.supplierId,
      warehouseId: this.order.warehouseId,
      orderDate: this.order.orderDate,
      expectedDeliveryDate: this.order.expectedDeliveryDate ?? '',
      notes: this.order.notes ?? '',
    });
    this.draftLines = this.order.items.map((l) => ({
      productId: l.productId,
      orderedQuantity: l.orderedQuantity,
      unitCost: l.unitCost,
    }));
  }

  onSubmit(): void {
    if (!this.order || !confirm(`Submit order ${this.order.poNumber}?`)) return;
    this.op(this.service.submit(this.order.id), 'Order submitted.');
  }

  onCancel(): void {
    if (!this.order || !confirm(`Cancel order ${this.order.poNumber}? Received stock is kept.`)) return;
    this.op(this.service.cancel(this.order.id), 'Order cancelled.');
  }

  onReceive(): void {
    if (!this.order) return;
    const lines = Object.entries(this.receiptQty)
      .filter(([, q]) => (q ?? 0) > 0)
      .map(([itemId, q]) => ({ poItemId: Number(itemId), quantity: Number(q) }));
    if (lines.length === 0) {
      this.error = 'Enter a quantity for at least one line.';
      return;
    }
    if (!confirm(`Receive ${lines.length} line(s)?`)) return;
    this.saving = true;
    this.error = null;
    this.message = null;
    this.service.receive(this.order.id, lines, this.receiptRef || null).subscribe({
      next: (r) => {
        this.saving = false;
        this.receiptRef = '';
        this.message = `Receipt ${r.receiptNumber} recorded.`;
        this.load();
      },
      error: (err: unknown) => {
        this.saving = false;
        this.error = this.describeError(err, 'Failed to record receipt.');
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
    if (e?.status === 409) return 'Conflict: duplicate number, ineligible status, or over-receipt.';
    if (e?.status === 403) return 'Forbidden: insufficient role.';
    if (e?.status === 404) return 'Order or referenced record not found.';
    return fallback;
  }
}
