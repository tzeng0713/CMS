import { CommonModule } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import {
  CmsApiService,
  AuthUser,
  BranchPayload,
  BranchSummary,
  ContractPayload,
  ContractSearchFilters,
  CustomerDetail,
  CustomerPayload,
  CustomerSearchFilters,
  CustomerSummary,
  Dashboard,
  OfficePayload,
  OfficeSummary,
  RoleSummary,
  RentPaymentPayload
} from './core/cms-api.service';

type ViewKey =
  | 'home'
  | 'customer-search'
  | 'customer-new'
  | 'customer-detail'
  | 'contract-search'
  | 'contract-new'
  | 'rent-search'
  | 'rent-new'
  | 'staff-overview'
  | 'charges'
  | 'refunds'
  | 'targets'
  | 'office-search'
  | 'office-new'
  | 'branch-management';

interface NavItem {
  key: ViewKey;
  label: string;
}

interface NavGroup {
  label: string;
  children: NavItem[];
}

type CustomerForm = {
  companyName: string;
  taxId: string;
  status: number;
  rentalItem: string;
  rentalStatus: number;
  ownerName: string;
  ownerBirthday: string;
  contactPerson: string;
  phone: string;
  forwardingAddress: string;
  pettyCash: number | null;
  referrer: string;
  notes: string;
  registrationType: string;
  updatedBy: number;
};

type ContractForm = {
  customerId: number | null;
  officeId: number | null;
  rentalItem: string;
  rentalStatus: string;
  signedDateText: string;
  signerStaffId: number | null;
  paymentMonths: number | null;
  startDateText: string;
  endDateText: string;
  terminationDateText: string;
  rent: number | null;
  deposit: number | null;
  leaseStatus: string;
  updatedBy: number;
};

type OfficeContactForm = {
  personName: string;
  phone: string;
};

type OfficeForm = {
  officeNo: string;
  branchId: number | null;
  notes: string;
  contacts: OfficeContactForm[];
};

const emptyOfficeForm = (): OfficeForm => ({
  officeNo: '',
  branchId: null,
  notes: '',
  contacts: []
});

const emptyCustomerForm = (): CustomerForm => ({
  companyName: '',
  taxId: '',
  status: 0,
  rentalItem: '登記',
  rentalStatus: 1,
  ownerName: '',
  ownerBirthday: '',
  contactPerson: '',
  phone: '',
  forwardingAddress: '',
  pettyCash: null,
  referrer: '',
  notes: '',
  registrationType: '登記',
  updatedBy: 1
});

const emptyContractForm = (): ContractForm => ({
  customerId: null,
  officeId: null,
  rentalItem: '登記',
  rentalStatus: '登記',
  signedDateText: '',
  signerStaffId: null,
  paymentMonths: null,
  startDateText: '',
  endDateText: '',
  terminationDateText: '',
  rent: null,
  deposit: null,
  leaseStatus: '綁約中',
  updatedBy: 1
});

@Component({
  selector: 'cms-root',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent implements OnInit {
  readonly roleOptions = ['主管', '督導秘書', '一般秘書'];
  readonly statusOptions = [
    { value: 0, label: '租賃中' },
    { value: 1, label: '解約中' },
    { value: 2, label: '合約已到期' }
  ];
  readonly rentalItemOptions = ['辦公室', '座位', '登記', '聯絡處'];
  readonly rentalStatusOptions = [
    { value: 1, label: '地址' },
    { value: 2, label: '地址+服務' },
    { value: 3, label: '僅服務' }
  ];
  readonly contractRentalStatusOptions = ['登記', '辦公室', '登記+辦公室'];
  readonly contractStatusOptions = ['綁約中', '已解約'];

  private readonly allNavGroups: NavGroup[] = [
    {
      label: '客戶管理',
      children: [
        { key: 'customer-search', label: '查詢客戶' },
        { key: 'customer-new', label: '新增客戶' }
      ]
    },
    {
      label: '租約管理',
      children: [
        { key: 'contract-search', label: '查詢租約' },
        { key: 'contract-new', label: '新增租約' }
      ]
    },
    {
      label: '租金管理',
      children: [
        { key: 'rent-search', label: '查詢租金' },
        { key: 'rent-new', label: '新增租金' }
      ]
    },
    {
      label: '辦公室管理',
      children: [
        { key: 'office-search', label: '查詢辦公室' },
        { key: 'office-new', label: '新增辦公室' }
      ]
    },
    {
      label: '分館管理',
      children: [{ key: 'branch-management', label: '分館總覽' }]
    },
    {
      label: '職員管理',
      children: [{ key: 'staff-overview', label: '職員總覽' }]
    },
    {
      label: '其他查詢',
      children: [
        { key: 'charges', label: '收費清單' },
        { key: 'refunds', label: '退款紀錄' },
        { key: 'targets', label: '業績目標' }
      ]
    }
  ];

  get navGroups(): NavGroup[] {
    const user = this.currentUser();
    if (!user) {
      return [];
    }
    return this.allNavGroups
      .map((group) => ({
        ...group,
        children: group.children.filter((item) => {
          if (item.key === 'rent-new') return user.canCreateRent;
          return true;
        })
      }))
      .filter((group) => group.children.length);
  }

  currentUser = signal<AuthUser | null>(this.loadStoredUser());
  authMode = signal<'login' | 'register'>('login');
  activeView = signal<ViewKey>('home');
  dashboard = signal<Dashboard | null>(null);
  customers = signal<CustomerSummary[]>([]);
  selectedCustomer = signal<CustomerDetail | null>(null);
  contracts = signal<Array<Record<string, unknown>>>([]);
  offices = signal<OfficeSummary[]>([]);
  branches = signal<BranchSummary[]>([]);
  roles = signal<RoleSummary[]>([]);
  staffOptions = signal<Array<Record<string, unknown>>>([]);
  staffRows = signal<Array<Record<string, unknown>>>([]);
  rentPayments = signal<Array<Record<string, unknown>>>([]);
  chargeLists = signal<Array<Record<string, unknown>>>([]);
  refunds = signal<Array<Record<string, unknown>>>([]);
  salesTargets = signal<Array<Record<string, unknown>>>([]);
  newCustomerOptions = signal<CustomerSummary[]>([]);
  rentCustomerOptions = signal<CustomerSummary[]>([]);
  contractCustomerOptions = signal<CustomerSummary[]>([]);
  selectedRentCustomer = signal<CustomerDetail | null>(null);
  officeRows = signal<OfficeSummary[]>([]);
  editingOffice = signal<OfficeSummary | null>(null);
  officeBranchFilter: number | null = null;
  branchRows = signal<BranchSummary[]>([]);
  editingBranch = signal<BranchSummary | null>(null);
  creatingBranch = signal(false);
  loading = signal(false);
  saving = signal(false);
  editingCustomer = signal(false);
  error = signal('');
  success = signal('');
  search = '';
  rentSearch = '';
  customerFilters: CustomerSearchFilters = {
    companyName: '',
    taxId: '',
    phone: '',
    ownerName: '',
    branchId: null,
    officeNo: ''
  };
  contractFilters: ContractSearchFilters = {
    companyName: '',
    startDateText: '',
    endDateText: '',
    leaseStatus: ''
  };
  staffBranchFilter: number | null = null;
  contractCustomerSearch = '';
  rentCustomerSearch = '';
  rentPaymentMonth = this.currentMonthValue();
  rentEditMonth = this.currentMonthValue();

  loginForm = {
    account: '',
    password: ''
  };

  registerForm = {
    staffName: '',
    account: '',
    password: '',
    roleName: '一般秘書'
  };

  newCustomerForm: CustomerForm = emptyCustomerForm();
  newCustomerContractForm: ContractForm = emptyContractForm();
  newCustomerLeaseImage: File | null = null;
  customerEditForm: CustomerForm = emptyCustomerForm();
  newContractForm: ContractForm = emptyContractForm();
  contractEditForm: ContractForm = emptyContractForm();
  newOfficeForm: OfficeForm = emptyOfficeForm();
  officeEditForm: OfficeForm = emptyOfficeForm();
  newBranchForm = { branchName: '' };
  branchEditForm = { branchName: '' };
  editingContract = signal<Record<string, unknown> | null>(null);
  editingRentPayment = signal<Record<string, unknown> | null>(null);
  rentEditForm: Partial<RentPaymentPayload> = {};
  rentForm: Partial<RentPaymentPayload> = {
    paymentMonth: this.monthNumberFromInput(this.rentPaymentMonth),
    updatedBy: this.currentStaffId()
  };

  selectedCustomerId = computed(() => this.selectedCustomer()?.customer_id ?? null);
  sameOwnerCompanies = computed(() => this.selectedCustomer()?.sameOwnerCompanies ?? []);

  constructor(private readonly api: CmsApiService, private readonly router: Router) {}

  ngOnInit(): void {
    this.router.events.subscribe((event) => {
      if (event instanceof NavigationEnd) {
        this.applyRoute(event.urlAfterRedirects);
      }
    });
    if (this.router.url === '/') {
      this.router.navigateByUrl('/home');
    } else {
      this.applyRoute(this.router.url);
    }
    if (this.currentUser()) {
      this.refresh();
    }
  }

  refresh(): void {
    if (!this.currentUser()) {
      return;
    }
    this.loadDashboard();
    this.loadActiveViewData();
    if (this.activeView() === 'customer-search' && !this.customers().length) {
      this.loadCustomers();
    }
  }

  setView(view: ViewKey): void {
    if (view === 'rent-new' && !this.canCreateRent()) {
      view = 'rent-search';
    }
    this.router.navigateByUrl(this.pathForView(view));
  }

  private activateView(view: ViewKey): void {
    this.activeView.set(view);
    this.error.set('');
    this.success.set('');
    this.loadActiveViewData();
  }

  loadActiveViewData(): void {
    switch (this.activeView()) {
      case 'home':
        break;
      case 'customer-search':
        this.loadCustomerSupportData();
        this.loadCustomers();
        break;
      case 'customer-new':
        this.loadNewCustomerSupportData();
        break;
      case 'customer-detail':
        this.loadCustomerSupportData();
        break;
      case 'contract-search':
        this.loadContracts();
        this.loadOffices();
        break;
      case 'contract-new':
        this.loadOffices();
        this.loadStaffSupportData();
        if (!this.newContractForm.signerStaffId) {
          this.newContractForm.signerStaffId = this.currentStaffId();
        }
        break;
      case 'rent-search':
        this.loadRentPayments();
        break;
      case 'office-search':
        this.loadOfficeRows();
        this.loadMetadata();
        break;
      case 'office-new':
        this.loadMetadata();
        if (!this.canEditAllBranches()) {
          this.newOfficeForm.branchId = this.currentUser()?.branch_id ?? null;
        }
        break;
      case 'branch-management':
        this.loadBranchRows();
        break;
      case 'staff-overview':
        this.loadStaffOverview();
        break;
      case 'charges':
        this.loadChargeLists();
        break;
      case 'refunds':
        this.loadRefunds();
        break;
      case 'targets':
        this.loadMetadata();
        break;
    }
  }

  loadDashboard(): void {
    this.api.dashboard().subscribe({
      next: (value) => this.dashboard.set(value),
      error: () => this.error.set('無法載入總覽資料，請確認 API 是否啟動。')
    });
  }

  login(): void {
    this.api.login(this.loginForm).subscribe({
      next: (user) => this.finishLogin(user),
      error: () => this.error.set('登入失敗，請確認帳號密碼。')
    });
  }

  register(): void {
    this.api.register(this.registerForm).subscribe({
      next: (user) => this.finishLogin(user),
      error: () => this.error.set('申請帳號失敗，請確認帳號是否重複或欄位未填。')
    });
  }

  logout(): void {
    sessionStorage.removeItem('cmsUser');
    this.currentUser.set(null);
    this.dashboard.set(null);
    this.selectedCustomer.set(null);
    this.customers.set([]);
    this.contracts.set([]);
    this.rentPayments.set([]);
    this.staffRows.set([]);
    this.router.navigateByUrl('/home');
    this.error.set('');
    this.success.set('');
  }

  canCreateRent(): boolean {
    return this.currentUser()?.canCreateRent === true;
  }

  canEditRent(): boolean {
    return this.currentUser()?.canEditRent === true;
  }

  canEditStaff(): boolean {
    return this.currentUser()?.canEditStaff === true;
  }

  canCreateOffice(): boolean {
    return this.currentUser()?.canCreateOffice === true;
  }

  canEditAllBranches(): boolean {
    return this.currentUser()?.canEditAllBranches === true;
  }

  canViewAllOffices(): boolean {
    return this.currentUser()?.canViewAllOffices === true;
  }

  canManageBranch(): boolean {
    return this.currentUser()?.canManageBranch === true;
  }

  loadBranchRows(): void {
    const user = this.currentUser();
    this.api.branchList().subscribe({
      next: (rows) => {
        if (!user?.canManageBranch && user?.branch_id) {
          this.branchRows.set(rows.filter((r) => r.branch_id === user.branch_id));
        } else {
          this.branchRows.set(rows);
        }
      },
      error: () => this.error.set('無法載入分館資料。')
    });
  }

  startCreateBranch(): void {
    this.editingBranch.set(null);
    this.branchEditForm = { branchName: '' };
    this.newBranchForm = { branchName: '' };
    this.creatingBranch.set(true);
  }

  cancelCreateBranch(): void {
    this.creatingBranch.set(false);
    this.newBranchForm = { branchName: '' };
  }

  createBranch(): void {
    if (!this.newBranchForm.branchName.trim()) {
      this.error.set('請輸入分館名稱。');
      return;
    }
    this.saving.set(true);
    const payload: BranchPayload = { branchName: this.newBranchForm.branchName.trim() };
    this.api.createBranch(payload).subscribe({
      next: (created) => {
        this.saving.set(false);
        this.error.set('');
        this.success.set('分館已新增。');
        this.branchRows.update((rows) => [...rows, created]);
        this.branches.update((rows) => [...rows, created]);
        this.cancelCreateBranch();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('分館新增失敗。');
      }
    });
  }

  startEditBranch(branch: BranchSummary): void {
    this.creatingBranch.set(false);
    this.newBranchForm = { branchName: '' };
    this.editingBranch.set(branch);
    this.branchEditForm = { branchName: branch.branch_name };
  }

  cancelEditBranch(): void {
    this.editingBranch.set(null);
    this.branchEditForm = { branchName: '' };
  }

  saveBranchEdit(): void {
    const branch = this.editingBranch();
    if (!branch) return;
    if (!this.branchEditForm.branchName.trim()) {
      this.error.set('請輸入分館名稱。');
      return;
    }
    this.saving.set(true);
    const payload: BranchPayload = { branchName: this.branchEditForm.branchName.trim() };
    this.api.updateBranch(branch.branch_id, payload).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.error.set('');
        this.success.set('分館已更新。');
        this.branchRows.update((rows) => rows.map((r) => (r.branch_id === updated.branch_id ? updated : r)));
        this.branches.update((rows) => rows.map((r) => (r.branch_id === updated.branch_id ? updated : r)));
        this.cancelEditBranch();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('分館更新失敗。');
      }
    });
  }

  canEditOffice(office: OfficeSummary): boolean {
    const user = this.currentUser();
    if (!user) return false;
    return user.canEditAllBranches || office.branch_id === user.branch_id;
  }

  loadOfficeRows(): void {
    const user = this.currentUser();
    const effectiveBranchId = !user?.canViewAllOffices && user?.branch_id
      ? user.branch_id
      : this.officeBranchFilter;
    this.api.offices(effectiveBranchId ?? undefined).subscribe({
      next: (rows) => this.officeRows.set(rows),
      error: () => this.error.set('無法載入辦公室資料。')
    });
  }

  filterOfficeByBranch(branchId: number | null): void {
    if (!this.canViewAllOffices()) return;
    this.officeBranchFilter = branchId;
    this.loadOfficeRows();
  }

  createOffice(): void {
    if (!this.canEditAllBranches()) {
      this.newOfficeForm.branchId = this.currentUser()?.branch_id ?? null;
    }
    if (!this.newOfficeForm.branchId) {
      this.error.set('請選擇分館。');
      return;
    }
    this.saving.set(true);
    this.api.createOffice(this.toOfficePayload(this.newOfficeForm)).subscribe({
      next: () => {
        this.saving.set(false);
        this.error.set('');
        this.success.set('辦公室已新增。');
        this.newOfficeForm = emptyOfficeForm();
        this.setView('office-search');
        this.loadOfficeRows();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('辦公室新增失敗。');
      }
    });
  }

  startEditOffice(office: OfficeSummary): void {
    this.editingOffice.set(office);
    this.officeEditForm = {
      officeNo: office.office_no ?? '',
      branchId: office.branch_id,
      notes: office.notes ?? '',
      contacts: office.contacts.map((c) => ({ personName: c.person_name ?? '', phone: c.phone ?? '' }))
    };
  }

  cancelEditOffice(): void {
    this.editingOffice.set(null);
    this.officeEditForm = emptyOfficeForm();
  }

  saveOfficeEdit(): void {
    const office = this.editingOffice();
    if (!office) return;
    if (!this.officeEditForm.branchId) {
      this.error.set('請選擇分館。');
      return;
    }
    this.saving.set(true);
    this.api.updateOffice(office.office_id, this.toOfficePayload(this.officeEditForm)).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.error.set('');
        this.success.set('辦公室已更新。');
        this.officeRows.update((rows) => rows.map((r) => (r.office_id === updated.office_id ? updated : r)));
        this.cancelEditOffice();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('辦公室更新失敗。');
      }
    });
  }

  addOfficeContact(form: OfficeForm): void {
    form.contacts = [...form.contacts, { personName: '', phone: '' }];
  }

  removeOfficeContact(form: OfficeForm, index: number): void {
    form.contacts = form.contacts.filter((_, i) => i !== index);
  }

  private toOfficePayload(form: OfficeForm): OfficePayload {
    return {
      officeNo: form.officeNo || undefined,
      branchId: form.branchId!,
      notes: form.notes || undefined,
      contacts: form.contacts
        .filter((c) => c.personName.trim() || c.phone.trim())
        .map((c) => ({ personName: c.personName || undefined, phone: c.phone || undefined }))
    };
  }

  isNavActive(key: ViewKey): boolean {
    return this.activeView() === key || (key === 'customer-search' && this.activeView() === 'customer-detail');
  }

  goHome(): void {
    this.router.navigateByUrl('/home');
  }

  loadCustomers(): void {
    this.loading.set(true);
    this.api.customers(this.customerFilters).subscribe({
      next: (value) => {
        this.customers.set(value);
        this.loading.set(false);
        if (!value.length) {
          this.selectedCustomer.set(null);
          this.editingCustomer.set(false);
        }
      },
      error: () => {
        this.loading.set(false);
        this.error.set('無法載入客戶清單。');
      }
    });
  }

  resetCustomerFilters(): void {
    this.customerFilters = {
      companyName: '',
      taxId: '',
      phone: '',
      ownerName: '',
      branchId: null,
      officeNo: ''
    };
    this.loadCustomers();
  }

  loadCustomerSupportData(): void {
    if (this.branches().length) {
      return;
    }
    this.api.metadata().subscribe({
      next: (data) => this.branches.set((data['branches'] as BranchSummary[]) ?? []),
      error: () => this.error.set('無法載入分館資料。')
    });
  }

  loadNewCustomerSupportData(): void {
    this.loadOffices();
    this.loadStaffSupportData();
    this.newCustomerContractForm.updatedBy = this.currentStaffId();
    this.newCustomerForm.updatedBy = this.currentStaffId();
    if (!this.newCustomerContractForm.signerStaffId) {
      this.newCustomerContractForm.signerStaffId = this.currentStaffId();
    }
  }

  selectCustomer(id: number): void {
    this.api.customerDetail(id).subscribe({
      next: (value) => {
        this.selectedCustomer.set(value);
        this.customerEditForm = this.formFromCustomer(value);
        this.editingCustomer.set(false);
      },
      error: () => this.error.set('無法載入客戶詳細資料。')
    });
  }

  openCustomerDetail(id: number): void {
    this.router.navigate(['/customers', id]);
  }

  backToCustomerList(): void {
    this.router.navigateByUrl('/customers');
  }

  startEditCustomer(customer?: CustomerSummary): void {
    if (customer && this.selectedCustomerId() !== customer.customer_id) {
      this.api.customerDetail(customer.customer_id).subscribe({
        next: (value) => {
          this.selectedCustomer.set(value);
          this.customerEditForm = this.formFromCustomer(value);
          this.editingCustomer.set(true);
        },
        error: () => this.error.set('無法載入客戶詳細資料。')
      });
      return;
    }
    const detail = this.selectedCustomer();
    if (detail) {
      this.customerEditForm = this.formFromCustomer(detail);
      this.editingCustomer.set(true);
    }
  }

  cancelEditCustomer(): void {
    const detail = this.selectedCustomer();
    if (detail) {
      this.customerEditForm = this.formFromCustomer(detail);
    }
    this.editingCustomer.set(false);
  }

  saveCustomerEdit(): void {
    const customer = this.selectedCustomer();
    if (!customer) {
      return;
    }
    this.saveCustomer(customer.customer_id, this.customerEditForm, '客戶資料已更新。');
  }

  lookupNewCustomerSuggestions(): void {
    const term = this.newCustomerForm.companyName.trim();
    if (!term) {
      this.newCustomerOptions.set([]);
      return;
    }
    this.api.customerLookup(term).subscribe({
      next: (rows) => this.newCustomerOptions.set(rows),
      error: () => this.error.set('無法查詢既有客戶。')
    });
  }

  fillExistingCustomerName(customer: CustomerSummary): void {
    this.newCustomerForm.companyName = customer.company_name;
    this.newCustomerOptions.set([]);
  }

  createCustomer(): void {
    if (!this.newCustomerForm.companyName.trim()) {
      this.error.set('公司名稱為必填。');
      return;
    }
    this.newCustomerForm.updatedBy = this.currentStaffId();
    this.newCustomerContractForm.updatedBy = this.currentStaffId();
    this.syncCustomerRentalSummaryFromContract();
    if (!this.newCustomerContractForm.signerStaffId) {
      this.newCustomerContractForm.signerStaffId = this.currentStaffId();
    }
    this.saving.set(true);
    this.api
      .createCustomerWithContract(
        {
          customer: this.toCustomerPayload(this.newCustomerForm),
          contract: this.toContractPayload(this.newCustomerContractForm)
        },
        this.newCustomerLeaseImage
      )
      .subscribe({
        next: (value) => {
          this.saving.set(false);
          this.error.set('');
          this.success.set('客戶與租約已新增。');
          this.selectedCustomer.set(value);
          this.customerEditForm = this.formFromCustomer(value);
          this.editingCustomer.set(false);
          this.newCustomerForm = emptyCustomerForm();
          this.newCustomerContractForm = { ...emptyContractForm(), updatedBy: this.currentStaffId(), signerStaffId: this.currentStaffId() };
          this.newCustomerLeaseImage = null;
          this.newCustomerOptions.set([]);
          this.loadDashboard();
          this.loadCustomers();
          this.router.navigate(['/customers', value.customer_id]);
        },
        error: () => {
          this.saving.set(false);
          this.error.set('客戶與租約新增失敗，請確認必填欄位與租約狀態。');
        }
      });
  }

  private saveCustomer(id: number | null, form: CustomerForm, message: string): void {
    if (!form.companyName.trim()) {
      this.error.set('公司名稱為必填。');
      return;
    }
    form.updatedBy = this.currentStaffId();
    this.saving.set(true);
    const request = id
      ? this.api.updateCustomer(id, this.toCustomerPayload(form))
      : this.api.createCustomer(this.toCustomerPayload(form));

    request.subscribe({
      next: (value) => {
        this.saving.set(false);
        this.error.set('');
        this.success.set(message);
        this.selectedCustomer.set(value);
        this.customerEditForm = this.formFromCustomer(value);
        this.editingCustomer.set(false);
        if (!id) {
          this.newCustomerForm = emptyCustomerForm();
          this.newCustomerOptions.set([]);
          this.router.navigate(['/customers', value.customer_id]);
        }
        this.loadDashboard();
        this.loadCustomers();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('客戶資料儲存失敗。');
      }
    });
  }

  loadOffices(): void {
    if (this.offices().length) {
      return;
    }
    this.api.offices().subscribe({
      next: (rows) => this.offices.set(rows),
      error: () => this.error.set('無法載入辦公室資料。')
    });
  }

  loadStaffSupportData(): void {
    if (this.branches().length && this.roles().length && this.staffOptions().length) {
      return;
    }
    this.api.metadata().subscribe({
      next: (data) => {
        this.branches.set((data['branches'] as BranchSummary[]) ?? []);
        this.roles.set((data['roles'] as RoleSummary[]) ?? []);
        this.staffOptions.set((data['staff'] as Array<Record<string, unknown>>) ?? []);
      },
      error: () => this.error.set('無法載入職員參考資料。')
    });
  }

  loadStaffOverview(): void {
    this.loadStaffSupportData();
    this.api.staff(this.staffBranchFilter).subscribe({
      next: (rows) => this.staffRows.set(rows),
      error: () => this.error.set('無法載入職員資料。')
    });
  }

  updateStaffRole(row: Record<string, unknown>, rolePermissionId: number): void {
    if (!this.canEditStaff()) {
      return;
    }
    const staffId = Number(row['staff_id']);
    if (!staffId || !rolePermissionId) {
      return;
    }
    this.api.updateStaff(staffId, { rolePermissionId: Number(rolePermissionId) }).subscribe({
      next: () => {
        this.error.set('');
        this.success.set('職員角色權限已更新。');
        this.loadStaffOverview();
      },
      error: () => this.error.set('職員角色權限更新失敗。')
    });
  }

  loadContracts(): void {
    this.api.contracts(this.contractFilters).subscribe({
      next: (rows) => this.contracts.set(rows),
      error: () => this.error.set('無法載入租約清單。')
    });
  }

  resetContractFilters(): void {
    this.contractFilters = {
      companyName: '',
      startDateText: '',
      endDateText: '',
      leaseStatus: ''
    };
    this.loadContracts();
  }

  lookupContractCustomers(): void {
    const term = this.contractCustomerSearch.trim();
    if (!term) {
      this.contractCustomerOptions.set([]);
      return;
    }
    this.api.customerLookup(term).subscribe({
      next: (rows) => this.contractCustomerOptions.set(rows),
      error: () => this.error.set('無法查詢客戶資料。')
    });
  }

  selectContractCustomer(customer: CustomerSummary): void {
    this.contractCustomerSearch = customer.company_name;
    this.contractCustomerOptions.set([]);
    this.newContractForm.customerId = customer.customer_id;
    if (this.newContractForm.rent === null && customer.rent !== null && customer.rent !== undefined) {
      this.newContractForm.rent = Number(customer.rent);
    }
    if (this.newContractForm.deposit === null && customer.deposit !== null && customer.deposit !== undefined) {
      this.newContractForm.deposit = Number(customer.deposit);
    }
  }

  createContract(): void {
    this.saveContract(null, this.newContractForm, '租約已新增。');
  }

  startEditContract(row: Record<string, unknown>): void {
    this.editingContract.set(row);
    this.contractEditForm = this.formFromContract(row);
    this.loadOffices();
  }

  cancelEditContract(): void {
    this.editingContract.set(null);
    this.contractEditForm = emptyContractForm();
  }

  saveContractEdit(): void {
    const row = this.editingContract();
    const id = Number(row?.['contract_id']);
    if (!id) {
      return;
    }
    this.saveContract(id, this.contractEditForm, '租約已更新。');
  }

  private saveContract(id: number | null, form: ContractForm, message: string): void {
    if (!form.customerId) {
      this.error.set('請先選擇客戶。');
      return;
    }
    form.updatedBy = this.currentStaffId();
    this.saving.set(true);
    const request = id
      ? this.api.updateContract(id, this.toContractPayload(form))
      : this.api.createContract(this.toContractPayload(form));

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.error.set('');
        this.success.set(message);
        if (id) {
          this.cancelEditContract();
        } else {
          this.newContractForm = { ...emptyContractForm(), updatedBy: this.currentStaffId() };
          this.contractCustomerSearch = '';
          this.contractCustomerOptions.set([]);
          this.setView('contract-search');
        }
        this.loadDashboard();
        this.loadContracts();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('租約資料儲存失敗。');
      }
    });
  }

  lookupRentCustomers(): void {
    const term = this.rentCustomerSearch.trim();
    if (!term) {
      this.rentCustomerOptions.set([]);
      return;
    }
    this.api.customerLookup(term).subscribe({
      next: (rows) => this.rentCustomerOptions.set(rows),
      error: () => this.error.set('無法查詢客戶資料。')
    });
  }

  selectRentCustomer(customer: CustomerSummary): void {
    this.api.customerDetail(customer.customer_id).subscribe({
      next: (value) => {
        const contract = value.contracts?.[0] as Record<string, unknown> | undefined;
        this.selectedRentCustomer.set(value);
        this.rentCustomerSearch = value.company_name;
        this.rentCustomerOptions.set([]);
        this.rentForm = {
          ...this.rentForm,
          customerId: value.customer_id,
          contractId: Number(contract?.['contract_id'] ?? value.contract_id),
          paymentMonth: this.monthNumberFromInput(this.rentPaymentMonth),
          amount: Number(value.rent ?? 0),
          updatedBy: this.currentStaffId()
        };
      },
      error: () => this.error.set('無法載入客戶合約資料。')
    });
  }

  updateRentPaymentMonth(value: string): void {
    this.rentPaymentMonth = value;
    this.rentForm.paymentMonth = this.monthNumberFromInput(value);
  }

  loadRentPayments(): void {
    this.api.rentPayments(this.rentSearch).subscribe({
      next: (rows) => this.rentPayments.set(rows),
      error: () => this.error.set('無法載入租金紀錄。')
    });
  }

  loadChargeLists(): void {
    this.api.chargeLists().subscribe({
      next: (rows) => this.chargeLists.set(rows),
      error: () => this.error.set('無法載入收費清單。')
    });
  }

  loadRefunds(): void {
    this.api.refunds().subscribe({
      next: (rows) => this.refunds.set(rows),
      error: () => this.error.set('無法載入退款紀錄。')
    });
  }

  loadMetadata(): void {
    this.api.metadata().subscribe({
      next: (data) => {
        this.branches.set((data['branches'] as BranchSummary[]) ?? []);
        this.roles.set((data['roles'] as RoleSummary[]) ?? []);
        this.staffOptions.set((data['staff'] as Array<Record<string, unknown>>) ?? []);
        this.salesTargets.set((data['salesTargets'] as Array<Record<string, unknown>>) ?? []);
      },
      error: () => this.error.set('無法載入系統參考資料。')
    });
  }

  saveRentPayment(): void {
    if (!this.rentForm.customerId || !this.rentForm.contractId) {
      this.error.set('請先用公司名稱搜尋並選擇客戶。');
      return;
    }
    this.rentForm.paymentMonth = this.monthNumberFromInput(this.rentPaymentMonth);
    this.rentForm.updatedBy = this.currentStaffId();
    this.saving.set(true);
    this.api.createRentPayment(this.rentForm as RentPaymentPayload).subscribe({
      next: () => {
        this.saving.set(false);
        this.error.set('');
        window.alert('新增成功');
        this.success.set('租金紀錄已新增。');
        this.resetRentForm();
        this.setView('rent-search');
        this.loadDashboard();
        this.loadRentPayments();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('新增租金繳交紀錄失敗。');
      }
    });
  }

  startEditRentPayment(row: Record<string, unknown>): void {
    if (!this.canEditRent()) {
      return;
    }
    this.editingRentPayment.set(row);
    this.rentEditMonth = this.monthInputFromPaymentMonth(row['payment_month']);
    this.rentEditForm = {
      paymentMonth: this.monthNumberFromInput(this.rentEditMonth),
      paymentDateText: this.textValue(row['payment_date_text']),
      feeStartDateText: this.textValue(row['fee_start_date_text']),
      feeEndDateText: this.textValue(row['fee_end_date_text']),
      amount: row['amount'] === null || row['amount'] === undefined ? undefined : Number(row['amount']),
      receiptNo: this.textValue(row['receipt_no']),
      note: this.textValue(row['note']),
      updatedBy: this.currentStaffId()
    };
  }

  cancelEditRentPayment(): void {
    this.editingRentPayment.set(null);
    this.rentEditForm = {};
    this.rentEditMonth = this.currentMonthValue();
  }

  updateRentEditMonth(value: string): void {
    this.rentEditMonth = value;
    this.rentEditForm.paymentMonth = this.monthNumberFromInput(value);
  }

  saveRentPaymentEdit(): void {
    const row = this.editingRentPayment();
    const id = Number(row?.['rent_payment_id']);
    if (!id) {
      return;
    }
    this.rentEditForm.paymentMonth = this.monthNumberFromInput(this.rentEditMonth);
    this.rentEditForm.updatedBy = this.currentStaffId();
    this.saving.set(true);
    this.api.updateRentPayment(id, this.rentEditForm as RentPaymentPayload).subscribe({
      next: () => {
        this.saving.set(false);
        this.error.set('');
        this.success.set('租金紀錄已更新。');
        this.cancelEditRentPayment();
        this.loadRentPayments();
        this.loadDashboard();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('租金紀錄更新失敗。');
      }
    });
  }

  formatPaymentMonth(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return '-';
    }
    const text = String(value);
    if (/^\d{6}$/.test(text)) {
      return `${text.slice(0, 4)}-${text.slice(4, 6)}`;
    }
    return `${text} 月`;
  }

  displayStatus(value: unknown): string {
    const code = Number(value ?? 0);
    return this.statusOptions.find((option) => option.value === code)?.label ?? '-';
  }

  displayRentalStatus(value: unknown): string {
    const code = Number(value ?? 1);
    return this.rentalStatusOptions.find((option) => option.value === code)?.label ?? '-';
  }

  updateRentalItem(form: CustomerForm, value: string): void {
    form.rentalItem = value;
    form.registrationType = value;
  }

  updateContractRentalItem(form: ContractForm, value: string): void {
    form.rentalItem = value;
  }

  onNewCustomerLeaseImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.newCustomerLeaseImage = input.files?.[0] ?? null;
  }

  money(value: unknown): string {
    const n = Number(value ?? 0);
    return new Intl.NumberFormat('zh-TW', {
      style: 'currency',
      currency: 'TWD',
      maximumFractionDigits: 0
    }).format(n);
  }

  value(row: Record<string, unknown>, key: string): string {
    const value = row[key];
    return value === null || value === undefined || value === '' ? '-' : String(value);
  }

  private finishLogin(user: AuthUser): void {
    sessionStorage.setItem('cmsUser', JSON.stringify(user));
    this.currentUser.set(user);
    this.authMode.set('login');
    this.error.set('');
    this.success.set('');
    this.loadDashboard();
    this.applyRoute(this.router.url === '/' ? '/home' : this.router.url);
  }

  private loadStoredUser(): AuthUser | null {
    const raw = sessionStorage.getItem('cmsUser');
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as AuthUser;
    } catch {
      sessionStorage.removeItem('cmsUser');
      return null;
    }
  }

  private currentStaffId(): number {
    return this.currentUser()?.staff_id ?? 1;
  }

  private textValue(value: unknown): string {
    return value === null || value === undefined ? '' : String(value);
  }

  private formFromContract(row: Record<string, unknown>): ContractForm {
    return {
      customerId: row['customer_id'] === null || row['customer_id'] === undefined ? null : Number(row['customer_id']),
      officeId: row['office_id'] === null || row['office_id'] === undefined ? null : Number(row['office_id']),
      rentalItem: this.textValue(row['rental_item']) || '登記',
      rentalStatus: this.textValue(row['rental_status']) || '登記',
      signedDateText: this.textValue(row['signed_date_text']),
      signerStaffId: row['signer_staff_id'] === null || row['signer_staff_id'] === undefined ? null : Number(row['signer_staff_id']),
      paymentMonths: row['payment_months'] === null || row['payment_months'] === undefined ? null : Number(row['payment_months']),
      startDateText: this.textValue(row['start_date_text']),
      endDateText: this.textValue(row['end_date_text']),
      terminationDateText: this.textValue(row['termination_date_text']),
      rent: row['rent'] === null || row['rent'] === undefined ? null : Number(row['rent']),
      deposit: row['deposit'] === null || row['deposit'] === undefined ? null : Number(row['deposit']),
      leaseStatus: this.textValue(row['lease_status']) || '綁約中',
      updatedBy: this.currentStaffId()
    };
  }

  private toContractPayload(form: ContractForm): ContractPayload {
    return {
      customerId: form.customerId ?? undefined,
      officeId: form.officeId ?? null,
      rentalItem: form.rentalItem,
      rentalStatus: form.rentalStatus,
      signedDateText: form.signedDateText,
      signerStaffId: form.signerStaffId ?? null,
      paymentMonths: form.paymentMonths,
      startDateText: form.startDateText,
      endDateText: form.endDateText,
      terminationDateText: form.terminationDateText,
      rent: form.rent,
      deposit: form.deposit,
      leaseStatus: form.leaseStatus,
      updatedBy: form.updatedBy
    };
  }

  private formFromCustomer(customer: CustomerDetail): CustomerForm {
    const rentalItem = customer.rental_item ?? customer.registration_type ?? '登記';
    return {
      companyName: customer.company_name ?? '',
      taxId: customer.tax_id ?? '',
      status: Number(customer.status ?? 0),
      rentalItem,
      rentalStatus: Number(customer.rental_status ?? 1),
      ownerName: customer.owner_name ?? '',
      ownerBirthday: customer.owner_birthday ?? '',
      contactPerson: customer.contact_person ?? '',
      phone: customer.phone ?? '',
      forwardingAddress: customer.forwarding_address ?? '',
      pettyCash: customer.petty_cash === null || customer.petty_cash === undefined ? null : Number(customer.petty_cash),
      referrer: customer.referrer ?? '',
      notes: customer.notes ?? '',
      registrationType: customer.registration_type ?? rentalItem,
      updatedBy: this.currentStaffId()
    };
  }

  private toCustomerPayload(form: CustomerForm): CustomerPayload {
    return {
      companyName: form.companyName,
      taxId: form.taxId,
      status: form.status,
      rentalItem: form.rentalItem,
      rentalStatus: form.rentalStatus,
      ownerName: form.ownerName,
      ownerBirthday: form.ownerBirthday,
      contactPerson: form.contactPerson,
      phone: form.phone,
      forwardingAddress: form.forwardingAddress,
      pettyCash: form.pettyCash,
      referrer: form.referrer,
      notes: form.notes,
      registrationType: form.rentalItem || form.registrationType,
      updatedBy: form.updatedBy
    };
  }

  private syncCustomerRentalSummaryFromContract(): void {
    this.newCustomerForm.rentalItem = this.newCustomerContractForm.rentalItem;
    this.newCustomerForm.registrationType = this.newCustomerContractForm.rentalItem;
    const status = this.newCustomerContractForm.rentalStatus;
    this.newCustomerForm.rentalStatus = status === '登記+辦公室' ? 2 : status === '辦公室' ? 3 : 1;
  }

  private currentMonthValue(): string {
    const now = new Date();
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
  }

  private monthNumberFromInput(value: string): number {
    return Number(value.replace('-', ''));
  }

  private monthInputFromPaymentMonth(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return this.currentMonthValue();
    }
    const text = String(value);
    if (/^\d{6}$/.test(text)) {
      return `${text.slice(0, 4)}-${text.slice(4, 6)}`;
    }
    if (/^\d{4}-\d{2}$/.test(text)) {
      return text;
    }
    return this.currentMonthValue();
  }

  private applyRoute(url: string): void {
    const path = url.split('?')[0].split('#')[0];
    if (!this.currentUser()) {
      return;
    }
    const customerMatch = path.match(/^\/customers\/(\d+)$/);
    if (customerMatch) {
      this.activeView.set('customer-detail');
      this.error.set('');
      this.success.set('');
      this.loadCustomerSupportData();
      this.selectCustomer(Number(customerMatch[1]));
      return;
    }
    const routeMap: Record<string, ViewKey> = {
      '/home': 'home',
      '/customers': 'customer-search',
      '/customers/new': 'customer-new',
      '/contracts': 'contract-search',
      '/contracts/new': 'contract-new',
      '/rent-payments': 'rent-search',
      '/rent-payments/new': 'rent-new',
      '/offices': 'office-search',
      '/offices/new': 'office-new',
      '/branches': 'branch-management',
      '/staff': 'staff-overview',
      '/charges': 'charges',
      '/refunds': 'refunds',
      '/targets': 'targets'
    };
    this.activateView(routeMap[path] ?? 'home');
  }

  private pathForView(view: ViewKey): string {
    const selectedId = this.selectedCustomerId();
    const routeMap: Record<ViewKey, string> = {
      home: '/home',
      'customer-search': '/customers',
      'customer-new': '/customers/new',
      'customer-detail': selectedId ? `/customers/${selectedId}` : '/customers',
      'contract-search': '/contracts',
      'contract-new': '/contracts/new',
      'rent-search': '/rent-payments',
      'rent-new': '/rent-payments/new',
      'office-search': '/offices',
      'office-new': '/offices/new',
      'branch-management': '/branches',
      'staff-overview': '/staff',
      charges: '/charges',
      refunds: '/refunds',
      targets: '/targets'
    };
    return routeMap[view];
  }

  private resetRentForm(): void {
    this.rentCustomerSearch = '';
    this.rentCustomerOptions.set([]);
    this.selectedRentCustomer.set(null);
    this.rentPaymentMonth = this.currentMonthValue();
    this.rentForm = {
      paymentMonth: this.monthNumberFromInput(this.rentPaymentMonth),
      updatedBy: this.currentStaffId()
    };
  }
}
