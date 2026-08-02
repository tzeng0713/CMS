import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';

export interface Dashboard {
  customers: number;
  activeContracts: number;
  officeCustomers: number;
  registrationCustomers: number;
  rentPayments: number;
  refunds: number;
  monthlyRentAmount: number;
  latestPayments: Array<Record<string, unknown>>;
  notifications: {
    expiringContracts: Array<{
      company_name: string;
      end_date_text: string;
      rental_item: string | null;
    }>;
    unpaidRent: Array<{
      customer_id: number;
      contract_id: number;
      company_name: string;
      next_period_start: string;
      payment_months: number | null;
      rent: number | null;
      suggested_amount: number | null;
    }>;
    incompleteContracts: Array<{
      customer_id: number;
      contract_id: number;
      company_name: string;
      signed_date_text: string | null;
      signer_staff_id: number | null;
      signer_staff_name: string | null;
      start_date_text: string | null;
      rent: number | null;
      lease_status: string;
    }>;
  };
}

export interface AuthUser {
  staff_id: number;
  staff_name: string;
  account: string;
  branch_id: number | null;
  branch_name: string | null;
  role_permission_id: number;
  role_name: string;
  scope: string | null;
  canCreateRent: boolean;
  canEditRent: boolean;
  canEditStaff: boolean;
  canCreateOffice: boolean;
  canEditAllBranches: boolean;
  canViewAllOffices: boolean;
  canManageBranch: boolean;
}

export interface CustomerSummary {
  customer_id: number;
  company_name: string;
  tax_id: string | null;
  status: number | null;
  rental_item: string | null;
  rental_status: number | null;
  owner_name: string | null;
  owner_birthday: string | null;
  contact_birthday: string | null;
  phone: string | null;
  referrer: string | null;
  accountant_info: string | null;
  account_info: string | null;
  is_agent: boolean | null;
  registration_type: string | null;
  contract_id: number | null;
  lease_status: string | null;
  rent: number | null;
  deposit: number | null;
  office_no: string | null;
  branch_name: string | null;
}

export interface CustomerDetail extends CustomerSummary {
  contact_person: string | null;
  forwarding_address: string | null;
  petty_cash: number | null;
  notes: string | null;
  relatedCompanies: Array<{
    relation_member_id: number;
    customer_id: number | null;
    company_name: string;
    is_resolved: boolean;
  }>;
  contracts: Array<Record<string, unknown>>;
  sameOwnerCompanies: CustomerSummary[];
  rentPayments: Array<Record<string, unknown>>;
  chargeLists: Array<Record<string, unknown>>;
  refunds: Array<Record<string, unknown>>;
}

export interface CustomerPayload {
  companyName: string;
  taxId?: string;
  status?: number;
  rentalItem?: string;
  rentalStatus?: number;
  ownerName?: string;
  ownerBirthday?: string;
  contactPerson?: string;
  contactBirthday?: string;
  phone?: string;
  forwardingAddress?: string;
  pettyCash?: number | null;
  referrer?: string;
  accountantInfo?: string;
  accountInfo?: string;
  isAgent?: boolean;
  relatedCompanyNames?: string[];
  notes?: string;
  registrationType?: string;
  updatedBy?: number;
}

export interface CustomerSearchFilters {
  companyName?: string;
  taxId?: string;
  phone?: string;
  ownerName?: string;
  branchId?: number | null;
  officeNo?: string;
  ownerBirthdayMonth?: number | null;
  contactBirthdayMonth?: number | null;
}

export interface RentPaymentPayload {
  customerId?: number;
  contractId?: number;
  paymentMonth?: number;
  paymentDateText?: string;
  feeStartDateText?: string;
  feeEndDateText?: string;
  amount?: number;
  receiptNo?: string;
  note?: string;
  updatedBy?: number;
}

export interface OfficeContact {
  office_contact_id: number;
  person_name: string | null;
  phone: string | null;
}

export interface OfficeSummary {
  office_id: number;
  office_no: string | null;
  branch_id: number;
  branch_name: string | null;
  notes: string | null;
  contacts: OfficeContact[];
}

export interface OfficeContactPayload {
  personName?: string;
  phone?: string;
}

export interface OfficePayload {
  officeNo?: string;
  branchId: number;
  notes?: string;
  contacts?: OfficeContactPayload[];
}

export interface BranchSummary {
  branch_id: number;
  branch_name: string;
}

export interface BranchPayload {
  branchName: string;
}

export interface RoleSummary {
  role_permission_id: number;
  role_name: string;
}

export interface ContractPayload {
  customerId?: number;
  officeId?: number | null;
  rentalItem?: string;
  rentalStatus?: string;
  signedDateText?: string;
  signerStaffId?: number | null;
  partnerStaffId?: number | null;
  sourceText?: string;
  paymentMonths?: number | null;
  startDateText?: string;
  endDateText?: string;
  terminationDateText?: string;
  rent?: number | null;
  deposit?: number | null;
  leaseImagePath?: string;
  leaseStatus?: string;
  updatedBy?: number;
}

export interface ContractSearchFilters {
  companyName?: string;
  startDateText?: string;
  endDateText?: string;
  leaseStatus?: string;
}

export interface ChargeListSummary {
  charge_list_id: number;
  customer_id: number;
  contract_id: number;
  company_name: string;
  office_no: string | null;
  branch_name: string | null;
  contract_rent: number | null;
  fee_start_month: string;
  fee_end_month: string;
  management_fee: number;
  electricity_fee: number;
  printing_fee: number;
  tax: number;
  advance_payment: number;
  repair_fee: number;
  total_amount: number;
  status: number;
  created_by: number | null;
  created_by_name: string | null;
  issued_at: string;
  updated_by: number | null;
  updated_at: string;
}

export interface ChargeListPayload {
  customerId?: number;
  contractId?: number;
  feeStartMonth?: string;
  feeEndMonth?: string;
  managementFee?: number;
  electricityFee?: number;
  printingFee?: number;
  tax?: number;
  advancePayment?: number;
  repairFee?: number;
  updatedBy?: number;
}

export interface ChargeListSearchFilters {
  chargeListId?: number | null;
  customerId?: number | null;
  contractId?: number | null;
  feeStartMonth?: string;
  feeEndMonth?: string;
  status?: number | null;
  createdBy?: number | null;
  issuedFrom?: string;
  issuedTo?: string;
  page?: number;
  pageSize?: number;
  sortBy?: string;
  sortDir?: string;
}

export interface PagedResult<T> {
  content: T[];
  totalElements: number;
  page: number;
  pageSize: number;
}

@Injectable({ providedIn: 'root' })
export class CmsApiService {
  private readonly baseUrl = environment.apiBaseUrl;

  constructor(private readonly http: HttpClient) {}

  login(payload: { account: string; password: string }): Observable<AuthUser> {
    return this.http.post<AuthUser>(`${this.baseUrl}/auth/login`, payload);
  }

  register(payload: { staffName: string; account: string; password: string; roleName: string }): Observable<AuthUser> {
    return this.http.post<AuthUser>(`${this.baseUrl}/auth/register`, payload);
  }

  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>(`${this.baseUrl}/dashboard`);
  }

  customers(searchOrFilters: string | CustomerSearchFilters = ''): Observable<CustomerSummary[]> {
    const params =
      typeof searchOrFilters === 'string'
        ? searchOrFilters
          ? { search: searchOrFilters }
          : {}
        : Object.fromEntries(
            Object.entries(searchOrFilters).filter(([, value]) => value !== undefined && value !== null && value !== '')
          );
    return this.http.get<CustomerSummary[]>(`${this.baseUrl}/customers`, {
      params
    });
  }

  customerLookup(search = ''): Observable<CustomerSummary[]> {
    return this.http.get<CustomerSummary[]>(`${this.baseUrl}/customers/lookup`, {
      params: search ? { search } : {}
    });
  }

  customerDetail(id: number): Observable<CustomerDetail> {
    return this.http.get<CustomerDetail>(`${this.baseUrl}/customers/${id}`);
  }

  createCustomer(payload: CustomerPayload): Observable<CustomerDetail> {
    return this.http.post<CustomerDetail>(`${this.baseUrl}/customers`, payload);
  }

  createCustomerWithContract(
    payload: {
      customer: CustomerPayload;
      contract: ContractPayload;
      firstPaymentAmount?: number | null;
      firstPaymentDateText?: string;
    },
    leaseImage?: File | null
  ): Observable<CustomerDetail> {
    const formData = new FormData();
    formData.append('payload', JSON.stringify(payload));
    if (leaseImage) {
      formData.append('leaseImage', leaseImage);
    }
    return this.http.post<CustomerDetail>(`${this.baseUrl}/customers/with-contract`, formData);
  }

  updateCustomer(id: number, payload: CustomerPayload): Observable<CustomerDetail> {
    return this.http.put<CustomerDetail>(`${this.baseUrl}/customers/${id}`, payload);
  }

  branchList(): Observable<BranchSummary[]> {
    return this.http.get<BranchSummary[]>(`${this.baseUrl}/branches`);
  }

  createBranch(payload: BranchPayload): Observable<BranchSummary> {
    return this.http.post<BranchSummary>(`${this.baseUrl}/branches`, payload);
  }

  updateBranch(id: number, payload: BranchPayload): Observable<BranchSummary> {
    return this.http.put<BranchSummary>(`${this.baseUrl}/branches/${id}`, payload);
  }

  offices(branchId?: number | null): Observable<OfficeSummary[]> {
    return this.http.get<OfficeSummary[]>(`${this.baseUrl}/offices`, {
      params: branchId ? { branchId } : {}
    });
  }

  createOffice(payload: OfficePayload): Observable<OfficeSummary> {
    return this.http.post<OfficeSummary>(`${this.baseUrl}/offices`, payload);
  }

  updateOffice(id: number, payload: OfficePayload): Observable<OfficeSummary> {
    return this.http.put<OfficeSummary>(`${this.baseUrl}/offices/${id}`, payload);
  }

  contracts(searchOrFilters: string | ContractSearchFilters = ''): Observable<Array<Record<string, unknown>>> {
    const params =
      typeof searchOrFilters === 'string'
        ? searchOrFilters
          ? { search: searchOrFilters }
          : {}
        : Object.fromEntries(
            Object.entries(searchOrFilters).filter(([, value]) => value !== undefined && value !== null && value !== '')
          );
    return this.http.get<Array<Record<string, unknown>>>(`${this.baseUrl}/contracts`, {
      params
    });
  }

  createContract(payload: ContractPayload): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/contracts`, payload);
  }

  latestContract(customerId: number): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.baseUrl}/customers/${customerId}/latest-contract`);
  }

  updateContract(id: number, payload: ContractPayload): Observable<Record<string, unknown>> {
    return this.http.put<Record<string, unknown>>(`${this.baseUrl}/contracts/${id}`, payload);
  }

  staff(branchId?: number | null): Observable<Array<Record<string, unknown>>> {
    return this.http.get<Array<Record<string, unknown>>>(`${this.baseUrl}/staff`, {
      params: branchId ? { branchId } : {}
    });
  }

  updateStaff(id: number, payload: { rolePermissionId: number }): Observable<Record<string, unknown>> {
    return this.http.put<Record<string, unknown>>(`${this.baseUrl}/staff/${id}`, payload);
  }

  rentPayments(search = ''): Observable<Array<Record<string, unknown>>> {
    return this.http.get<Array<Record<string, unknown>>>(`${this.baseUrl}/rent-payments`, {
      params: search ? { search } : {}
    });
  }

  chargeLists(filters: ChargeListSearchFilters = {}): Observable<PagedResult<ChargeListSummary>> {
    const params = Object.fromEntries(
      Object.entries(filters).filter(([, value]) => value !== undefined && value !== null && value !== '')
    );
    return this.http.get<PagedResult<ChargeListSummary>>(`${this.baseUrl}/charge-lists`, { params });
  }

  chargeListDetail(id: number): Observable<ChargeListSummary> {
    return this.http.get<ChargeListSummary>(`${this.baseUrl}/charge-lists/${id}`);
  }

  createChargeList(payload: ChargeListPayload): Observable<ChargeListSummary> {
    return this.http.post<ChargeListSummary>(`${this.baseUrl}/charge-lists`, payload);
  }

  updateChargeList(id: number, payload: ChargeListPayload): Observable<ChargeListSummary> {
    return this.http.put<ChargeListSummary>(`${this.baseUrl}/charge-lists/${id}`, payload);
  }

  refunds(): Observable<Array<Record<string, unknown>>> {
    return this.http.get<Array<Record<string, unknown>>>(`${this.baseUrl}/refunds`);
  }

  createRentPayment(payload: RentPaymentPayload): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`${this.baseUrl}/rent-payments`, payload);
  }

  updateRentPayment(id: number, payload: RentPaymentPayload): Observable<Record<string, unknown>> {
    return this.http.put<Record<string, unknown>>(`${this.baseUrl}/rent-payments/${id}`, payload);
  }

  metadata(): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`${this.baseUrl}/metadata`);
  }
}
