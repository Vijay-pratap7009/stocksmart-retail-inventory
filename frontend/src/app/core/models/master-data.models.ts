/** Day 2 master-data models mirroring the backend DTOs. */

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface Category {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface CategoryRequest {
  code: string;
  name: string;
  description?: string | null;
  active?: boolean | null;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  description?: string | null;
  categoryId: number;
  categoryName?: string | null;
  categoryCode?: string | null;
  brand?: string | null;
  unit: string;
  costPrice: number;
  sellingPrice: number;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProductRequest {
  sku: string;
  name: string;
  description?: string | null;
  categoryId: number;
  brand?: string | null;
  unit: string;
  costPrice: number;
  sellingPrice: number;
  active?: boolean | null;
}

export interface Warehouse {
  id: number;
  code: string;
  name: string;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  contactPerson?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface WarehouseRequest {
  code: string;
  name: string;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  contactPerson?: string | null;
  contactEmail?: string | null;
  contactPhone?: string | null;
  active?: boolean | null;
}

export interface Supplier {
  id: number;
  code: string;
  name: string;
  contactPerson?: string | null;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface SupplierRequest {
  code: string;
  name: string;
  contactPerson?: string | null;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  city?: string | null;
  state?: string | null;
  postalCode?: string | null;
  active?: boolean | null;
}
