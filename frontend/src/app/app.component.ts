import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, HostListener, OnInit, ViewChild, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import html2canvas from 'html2canvas';
import {
  CmsApiService,
  AuthUser,
  BonusRule,
  BonusRulePayload,
  BranchPayload,
  BranchSummary,
  ChargeListPayload,
  ChargeListSearchFilters,
  ChargeListSummary,
  ContractPayload,
  ContractSearchFilters,
  CustomerDetail,
  CustomerPayload,
  CustomerSearchFilters,
  CustomerSummary,
  Dashboard,
  OfficePayload,
  OfficeSummary,
  PerformanceBonus,
  PerformanceBonusSearchFilters,
  RoleSummary,
  RentPaymentPayload,
  RefundPayload,
  RefundSearchFilters,
  RefundSummary,
  SalesTarget,
  SalesTargetPayload,
  SalesTargetSearchFilters,
  TaxBureauNoticeBranchInfo,
  TaxBureauNoticeGroup,
  TaxBureauNoticeItem,
  TaxBureauNoticeType,
  RentPaymentSearchFilters
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
  | 'bonus-rules'
  | 'performance-bonuses'
  | 'office-search'
  | 'office-new'
  | 'branch-management'
  | 'tax-bureau-notices';

type NotificationKind = 'expiring' | 'unpaid' | 'incomplete';

type Toast = {
  message: string;
  kind: 'success' | 'error';
};

type BonusRuleTierRow = {
  threshold: number | null;
  amount: number | null;
};

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
  contactBirthday: string;
  phone: string;
  forwardingAddress: string;
  pettyCash: number | null;
  accountantInfo: string;
  accountInfo: string;
  isAgent: boolean;
  relatedCompanyNames: string[];
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
  partnerStaffId: number | null;
  sourceText: string;
  paymentMonths: number | null;
  startDateText: string;
  endDateText: string;
  terminationDateText: string;
  rent: number | null;
  deposit: number | null;
  leaseStatus: string;
  updatedBy: number;
};

type ChargeListForm = {
  customerId: number | null;
  contractId: number | null;
  feeMonth: string;
  managementFee: number;
  electricityFee: number;
  printingFee: number;
  meetingRoomFee: number;
  tax: number;
  advancePayment: number;
  repairFee: number;
  updatedBy: number;
};

type RefundForm = {
  customerId: number | null;
  contractId: number | null;
  chargeListId: number | null;
  refundReason: string;
  adjustmentAmount: number;
  adjustmentNote: string;
  deductionTotal: number;
  paymentMethod: string;
  bankCode: string;
  bankAccount: string;
  bankAccountName: string;
  refundStatus: string;
  refundedAt: string;
  staffId: number;
};

const emptyRefundForm = (): RefundForm => ({
  customerId: null,
  contractId: null,
  chargeListId: null,
  refundReason: '',
  adjustmentAmount: 0,
  adjustmentNote: '',
  deductionTotal: 0,
  paymentMethod: '',
  bankCode: '',
  bankAccount: '',
  bankAccountName: '',
  refundStatus: '草稿',
  refundedAt: '',
  staffId: 1
});

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

type BranchForm = {
  branchName: string;
  companyName: string;
  branchCode: string;
  branchAddress: string;
  taxId: string;
  bankAccount: string;
  bankBranch: string;
  bankAccountName: string;
};

const emptyBranchForm = (): BranchForm => ({
  branchName: '',
  companyName: '',
  branchCode: '',
  branchAddress: '',
  taxId: '',
  bankAccount: '',
  bankBranch: '',
  bankAccountName: ''
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
  contactBirthday: '',
  phone: '',
  forwardingAddress: '',
  pettyCash: null,
  accountantInfo: '',
  accountInfo: '',
  isAgent: false,
  relatedCompanyNames: [''],
  notes: '',
  registrationType: '登記',
  updatedBy: 1
});

const emptyChargeListForm = (): ChargeListForm => ({
  customerId: null,
  contractId: null,
  feeMonth: '',
  managementFee: 0,
  electricityFee: 0,
  printingFee: 0,
  meetingRoomFee: 0,
  tax: 0,
  advancePayment: 0,
  repairFee: 0,
  updatedBy: 1
});

const CHARGE_LIST_LOGO_PATH = 'assets/afw-logo.png';
const CHARGE_LIST_ISSUER_NAME = '全方位商務中心有限公司';
const CHARGE_LIST_BANK_INFO = '華南銀行008(南京東路分行)';
const CHARGE_LIST_BANK_ACCOUNT_NAME = '全方位商務中心有限公司';
const CHARGE_LIST_BANK_ACCOUNT_NUMBER = '112-1011-03671';

const emptyContractForm = (): ContractForm => ({
  customerId: null,
  officeId: null,
  rentalItem: '登記',
  rentalStatus: '登記',
  signedDateText: '',
  signerStaffId: null,
  partnerStaffId: null,
  sourceText: '',
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
  readonly chargeListLogoPath = CHARGE_LIST_LOGO_PATH;
  readonly todayLabel = this.formatTodayLabel();
  readonly chargeListIssuerName = CHARGE_LIST_ISSUER_NAME;
  readonly chargeListBankInfo = CHARGE_LIST_BANK_INFO;
  readonly chargeListBankAccountName = CHARGE_LIST_BANK_ACCOUNT_NAME;
  readonly chargeListBankAccountNumber = CHARGE_LIST_BANK_ACCOUNT_NUMBER;
  readonly roleOptions = ['主管', '督導秘書', '一般秘書'];
  readonly statusOptions = [
    { value: 0, label: '租賃中' },
    { value: 1, label: '解約中' },
    { value: 2, label: '合約已到期' }
  ];
  readonly rentalItemOptions = ['辦公室', '座位', '登記', '聯絡處', '停業'];
  readonly rentalStatusOptions = [
    { value: 1, label: '地址' },
    { value: 2, label: '地址+服務' },
    { value: 3, label: '僅服務' }
  ];
  readonly contractRentalStatusOptions = ['登記', '辦公室', '登記+辦公室', '個人名義'];
  readonly contractStatusOptions = ['綁約中', '已解約'];

  private formatTodayLabel(): string {
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0');
    const day = String(today.getDate()).padStart(2, '0');
    const weekday = ['日', '一', '二', '三', '四', '五', '六'][today.getDay()];
    return `${year}-${month}-${day} (${weekday})`;
  }

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
        { key: 'contract-new', label: '續約租約' }
      ]
    },
    {
      label: '對帳管理',
      children: [
        { key: 'rent-search', label: '查詢對帳' },
        { key: 'rent-new', label: '新增對帳' }
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
      label: '業績管理',
      children: [
        { key: 'targets', label: '業績目標' },
        { key: 'bonus-rules', label: '業績獎金規則' },
        { key: 'performance-bonuses', label: '業績結算' }
      ]
    },
    {
      label: '其他查詢',
      children: [
        { key: 'charges', label: '收費清單' },
        { key: 'refunds', label: '退款紀錄' },
        { key: 'tax-bureau-notices', label: '國稅局通報' }
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
  isNarrowViewport = signal(this.isNarrowWindow());
  desktopSidebarCollapsed = signal(false);
  mobileSidebarOpen = signal(false);
  sidebarOpen = computed(() =>
    this.isNarrowViewport() ? this.mobileSidebarOpen() : !this.desktopSidebarCollapsed()
  );
  dashboard = signal<Dashboard | null>(null);
  expiringNotificationPage = signal(1);
  unpaidNotificationPage = signal(1);
  incompleteNotificationPage = signal(1);
  customers = signal<CustomerSummary[]>([]);
  customerTotal = signal(0);
  customerPage = signal(0);
  customerPageSize = signal(20);
  advancedFiltersOpen = signal(true);
  selectedCustomer = signal<CustomerDetail | null>(null);
  contracts = signal<Array<Record<string, unknown>>>([]);
  offices = signal<OfficeSummary[]>([]);
  branches = signal<BranchSummary[]>([]);
  roles = signal<RoleSummary[]>([]);
  staffOptions = signal<Array<Record<string, unknown>>>([]);
  staffRows = signal<Array<Record<string, unknown>>>([]);
  contractTotal = signal(0);
  contractPage = signal(0);
  contractPageSize = signal(20);
  rentPayments = signal<Array<Record<string, unknown>>>([]);
  rentPaymentTotal = signal(0);
  rentPaymentPage = signal(0);
  rentPaymentPageSize = signal(20);
  chargeListRows = signal<ChargeListSummary[]>([]);
  chargeListTotal = signal(0);
  chargeListPage = signal(0);
  readonly chargeListPageSize = 20;
  editingChargeList = signal<ChargeListSummary | null>(null);
  viewingChargeList = signal<ChargeListSummary | null>(null);
  exportingChargeList = signal<ChargeListSummary | null>(null);
  chargeListPreviewImage = signal<string | null>(null);
  chargeListPreviewRow = signal<ChargeListSummary | null>(null);
  chargeListCustomerOptions = signal<CustomerSummary[]>([]);
  selectedChargeListCustomer = signal<CustomerDetail | null>(null);
  refundRows = signal<RefundSummary[]>([]);
  refundTotal = signal(0);
  refundPage = signal(0);
  readonly refundPageSize = 20;
  editingRefund = signal<RefundSummary | null>(null);
  viewingRefund = signal<RefundSummary | null>(null);
  refundCustomerSearch = '';
  refundCustomerOptions = signal<CustomerSummary[]>([]);
  selectedRefundCustomer = signal<CustomerDetail | null>(null);
  refundImportChargeListId: number | null = null;
  taxNoticeYearMonth = this.currentMonthValue();
  taxNoticeType: TaxBureauNoticeType = 'BOTH';
  taxNoticeGroups = signal<TaxBureauNoticeGroup[]>([]);
  taxNoticeUnassigned = signal<TaxBureauNoticeItem[]>([]);
  taxNoticeSelected = signal<Set<string>>(new Set());
  taxNoticeBranchInfo = signal<Record<number, TaxBureauNoticeBranchInfo>>({});
  taxNoticeLoading = signal(false);
  taxNoticeGenerating = signal(false);
  salesTargetRows = signal<SalesTarget[]>([]);
  salesTargetTotal = signal(0);
  salesTargetPage = signal(0);
  readonly salesTargetPageSize = 20;
  salesTargetFilters: SalesTargetSearchFilters = { branchId: null, targetMonth: null, category: '' };
  newSalesTargetForm: SalesTargetPayload = { branchId: 0, targetMonth: 0, category: '', targetCount: 0, staffId: 0 };
  creatingSalesTarget = signal(false);

  bonusRuleRows = signal<BonusRule[]>([]);
  newBonusRuleForm: BonusRulePayload = {
    ruleName: '', ruleType: 'OFFICE_RENTAL', unitAmount: undefined, percentage: undefined,
    tierConfig: '', periodType: '', description: '', isActive: true, staffId: 0
  };
  creatingBonusRule = signal(false);
  editingBonusRule = signal<BonusRule | null>(null);
  bonusRuleEditForm: BonusRulePayload = {
    ruleName: '', ruleType: 'OFFICE_RENTAL', unitAmount: undefined, percentage: undefined,
    tierConfig: '', periodType: '', description: '', isActive: true, staffId: 0
  };
  readonly bonusRuleTypeOptions: Array<{ value: string; label: string }> = [
    { value: 'OFFICE_RENTAL', label: '一、辦公室出租獎金' },
    { value: 'COMPANY_REGISTRATION', label: '二、公司登記業績獎金' },
    { value: 'TEAMWORK', label: '三、同心獎金' },
    { value: 'FULL_OCCUPANCY', label: '四、滿租獎金' },
    { value: 'BUSINESS_AGENT', label: '五、工商代辦獎金（尚未支援自動結算）' },
    { value: 'REGISTRATION_MULTIPLIER', label: '六、公司登記加乘獎金' },
    { value: 'BRANCH_PERFORMANCE', label: '七、分館績效獎金' },
    { value: 'ANNUAL_PAYMENT', label: '八、公司登記年繳獎金' }
  ];
  readonly bonusRulePeriodTypeOptions: Array<{ value: string; label: string }> = [
    { value: 'PER_TRANSACTION', label: '逐筆合約／收款觸發' },
    { value: 'MONTHLY', label: '按月結算' },
    { value: 'FOUR_MONTH', label: '每 4 個月期間結算' }
  ];
  newBonusRuleTiers: BonusRuleTierRow[] = [{ threshold: null, amount: null }];
  bonusRuleEditTiers: BonusRuleTierRow[] = [{ threshold: null, amount: null }];
  // 每種規則類型結算時只會讀取其中一種計算欄位，新增表單依此只顯示對應欄位（與後端 BonusRuleService 驗證一致）。
  private readonly bonusRuleFieldByType: Record<string, 'unitAmount' | 'percentage' | 'tierConfig'> = {
    OFFICE_RENTAL: 'unitAmount',
    COMPANY_REGISTRATION: 'unitAmount',
    TEAMWORK: 'unitAmount',
    FULL_OCCUPANCY: 'unitAmount',
    BRANCH_PERFORMANCE: 'unitAmount',
    BUSINESS_AGENT: 'unitAmount',
    ANNUAL_PAYMENT: 'percentage',
    REGISTRATION_MULTIPLIER: 'tierConfig'
  };

  performanceBonusRows = signal<PerformanceBonus[]>([]);
  performanceBonusTotal = signal(0);
  staffBonusSummaryRows = signal<Array<{ staffName: string; total: number; count: number }>>([]);
  performanceBonusPage = signal(0);
  readonly performanceBonusPageSize = 20;
  performanceBonusFilters: PerformanceBonusSearchFilters = {
    ruleType: '', period: '', branchId: null, staffId: null
  };
  settleMonthlyYearMonth = this.currentMonthValue();
  settlePeriodYear = new Date().getFullYear();
  settlePeriodQuarter: 1 | 2 | 3 = 1;
  settlingTransactions = signal(false);
  settlingMonthly = signal(false);
  settlingPeriod = signal(false);
  settleResultMessage = signal('');
  newCustomerOptions = signal<CustomerSummary[]>([]);
  rentCustomerOptions = signal<CustomerSummary[]>([]);
  contractCustomerOptions = signal<CustomerSummary[]>([]);
  selectedRentCustomer = signal<CustomerDetail | null>(null);
  officeRows = signal<OfficeSummary[]>([]);
  editingOffice = signal<OfficeSummary | null>(null);
  officeBranchFilter: number | null = null;
  branchRows = signal<BranchSummary[]>([]);
  editingBranch = signal<BranchSummary | null>(null);
  viewingBranch = signal<BranchSummary | null>(null);
  creatingBranch = signal(false);
  loading = signal(false);
  saving = signal(false);
  editingCustomer = signal(false);
  error = signal('');
  success = signal('');
  toast = signal<Toast | null>(null);
  newCustomerFieldErrors = signal<Record<string, string>>({});
  customerEditFieldErrors = signal<Record<string, string>>({});
  private toastTimeoutId: ReturnType<typeof setTimeout> | null = null;
  search = '';
  rentPaymentFilters: RentPaymentSearchFilters = {
    companyName: '',
    taxId: '',
    paymentDateStartText: '',
    paymentDateEndText: ''
  };
  customerFilters: CustomerSearchFilters = {
    companyName: '',
    taxId: '',
    phone: '',
    accountInfo: '',
    ownerName: '',
    branchId: null,
    officeNo: '',
    ownerBirthdayMonth: null,
    contactBirthdayMonth: null
  };
  readonly birthdayMonthOptions = Array.from({ length: 12 }, (_, index) => index + 1);
  readonly notificationPageSize = 5;
  contractFilters: ContractSearchFilters = {
    companyName: '',
    taxId: '',
    startDateText: '',
    endDateText: '',
    leaseStatus: ''
  };
  chargeListFilters: ChargeListSearchFilters = {
    chargeListId: null,
    customerId: null,
    contractId: null,
    feeMonth: '',
    status: null,
    createdBy: null,
    issuedFrom: '',
    issuedTo: ''
  };
  chargeListSortBy = 'issuedAt';
  chargeListSortDir: 'asc' | 'desc' = 'desc';
  chargeListFilterCustomerSearch = '';
  chargeListFilterCustomerOptions = signal<CustomerSummary[]>([]);
  refundFilters: RefundSearchFilters = {
    companyName: '',
    taxId: '',
    dateFrom: '',
    dateTo: '',
    status: ''
  };
  refundSortBy = 'createdAt';
  refundSortDir: 'asc' | 'desc' = 'desc';
  staffBranchFilter: number | null = null;
  contractCustomerSearch = '';
  chargeListCustomerSearch = '';
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
  newCustomerFirstPaymentAmount: number | null = null;
  newCustomerFirstPaymentDateText = '';
  customerNewStep = signal<1 | 2>(1);
  customerEditForm: CustomerForm = emptyCustomerForm();
  newContractForm: ContractForm = emptyContractForm();
  newContractFirstPaymentAmount: number | null = null;
  newContractFirstPaymentDateText = '';
  contractEditForm: ContractForm = emptyContractForm();
  newOfficeForm: OfficeForm = emptyOfficeForm();
  officeEditForm: OfficeForm = emptyOfficeForm();
  newBranchForm: BranchForm = emptyBranchForm();
  branchEditForm: BranchForm = emptyBranchForm();
  editingContract = signal<Record<string, unknown> | null>(null);
  editingRentPayment = signal<Record<string, unknown> | null>(null);
  rentEditForm: Partial<RentPaymentPayload> = {};
  rentForm: Partial<RentPaymentPayload> = {
    paymentMonth: this.monthNumberFromInput(this.rentPaymentMonth),
    updatedBy: this.currentStaffId()
  };
  newChargeListForm: ChargeListForm = {
    ...emptyChargeListForm(),
    feeMonth: this.currentMonthValue()
  };
  chargeListEditForm: ChargeListForm = emptyChargeListForm();
  newRefundForm: RefundForm = emptyRefundForm();
  refundEditForm: RefundForm = emptyRefundForm();

  @ViewChild('chargeListPrintArea') chargeListPrintArea?: ElementRef<HTMLDivElement>;
  @ViewChild('newCompanyNameInput') newCompanyNameInput?: ElementRef<HTMLInputElement>;
  @ViewChild('editCompanyNameInput') editCompanyNameInput?: ElementRef<HTMLInputElement>;

  selectedCustomerId = computed(() => this.selectedCustomer()?.customer_id ?? null);
  sameOwnerCompanies = computed(() => this.selectedCustomer()?.sameOwnerCompanies ?? []);
  relatedCompanies = computed(() => this.selectedCustomer()?.relatedCompanies ?? []);
  pageTitle = computed(() => {
    const titles: Partial<Record<ViewKey, string>> = {
      home: '客戶與租金作業總覽',
      'customer-search': '查詢客戶',
      'contract-search': '查詢租約',
      'rent-search': '查詢對帳'
    };
    return titles[this.activeView()] ?? 'AFW 商務中心';
  });
  pageSubtitle = computed(() => {
    const subtitles: Partial<Record<ViewKey, string>> = {
      home: 'Customer Operations',
      'customer-search': 'Customer Search',
      'contract-search': 'Contract Search',
      'rent-search': 'Reconciliation Search'
    };
    return subtitles[this.activeView()] ?? 'AFW Business Center';
  });
  customerTotalPages = computed(() => Math.max(1, Math.ceil(this.customerTotal() / this.customerPageSize())));
  contractTotalPages = computed(() => Math.max(1, Math.ceil(this.contractTotal() / this.contractPageSize())));
  rentPaymentTotalPages = computed(() => Math.max(1, Math.ceil(this.rentPaymentTotal() / this.rentPaymentPageSize())));
  chargeListTotalPages = computed(() => Math.max(1, Math.ceil(this.chargeListTotal() / this.chargeListPageSize)));
  refundTotalPages = computed(() => Math.max(1, Math.ceil(this.refundTotal() / this.refundPageSize)));
  salesTargetTotalPages = computed(() => Math.max(1, Math.ceil(this.salesTargetTotal() / this.salesTargetPageSize)));
  performanceBonusTotalPages = computed(() => Math.max(1, Math.ceil(this.performanceBonusTotal() / this.performanceBonusPageSize)));

  constructor(private readonly api: CmsApiService, private readonly router: Router) {}

  showToast(message: string, kind: Toast['kind'] = 'success'): void {
    this.toast.set({ message, kind });
    if (this.toastTimeoutId !== null) {
      clearTimeout(this.toastTimeoutId);
    }
    this.toastTimeoutId = setTimeout(() => {
      this.toast.set(null);
      this.toastTimeoutId = null;
    }, 3600);
  }

  clearNewCustomerFieldError(field: string): void {
    const { [field]: _removed, ...remaining } = this.newCustomerFieldErrors();
    this.newCustomerFieldErrors.set(remaining);
  }

  clearCustomerEditFieldError(field: string): void {
    const { [field]: _removed, ...remaining } = this.customerEditFieldErrors();
    this.customerEditFieldErrors.set(remaining);
  }

  private requireNewCustomerCompanyName(): void {
    this.newCustomerFieldErrors.set({ companyName: '公司名稱為必填。' });
    this.error.set('');
    this.customerNewStep.set(1);
    setTimeout(() => this.newCompanyNameInput?.nativeElement.focus(), 0);
  }

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
    this.closeSidebarAfterNavigation();
    this.router.navigateByUrl(this.pathForView(view));
  }

  toggleSidebar(): void {
    if (this.isNarrowViewport()) {
      this.mobileSidebarOpen.update((isOpen) => !isOpen);
      return;
    }
    this.desktopSidebarCollapsed.update((isCollapsed) => !isCollapsed);
  }

  closeSidebarAfterNavigation(): void {
    if (this.isNarrowViewport()) {
      this.mobileSidebarOpen.set(false);
    }
  }

  @HostListener('window:resize')
  onWindowResize(): void {
    this.isNarrowViewport.set(this.isNarrowWindow());
  }

  @HostListener('document:keydown.escape')
  onEscapeKey(): void {
    this.closeSidebarAfterNavigation();
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
      case 'rent-new':
        this.loadRentFromRoute();
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
        this.loadMetadata();
        this.loadChargeLists();
        break;
      case 'refunds':
        this.loadRefunds();
        break;
      case 'targets':
        this.loadStaffSupportData();
        this.loadSalesTargets();
        break;
      case 'bonus-rules':
        this.loadStaffSupportData();
        this.loadBonusRules();
        break;
      case 'performance-bonuses':
        this.loadStaffSupportData();
        this.loadPerformanceBonuses();
        break;
      case 'tax-bureau-notices':
        this.loadTaxBureauNoticePreview();
        break;
    }
  }

  loadDashboard(): void {
    this.api.dashboard().subscribe({
      next: (value) => {
        this.dashboard.set(value);
        this.clampNotificationPages(value);
      },
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
    this.customerTotal.set(0);
    this.customerPage.set(0);
    this.contracts.set([]);
    this.rentPayments.set([]);
    this.chargeListRows.set([]);
    this.chargeListTotal.set(0);
    this.editingChargeList.set(null);
    this.refundRows.set([]);
    this.refundTotal.set(0);
    this.editingRefund.set(null);
    this.viewingRefund.set(null);
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
    this.branchEditForm = emptyBranchForm();
    this.newBranchForm = emptyBranchForm();
    this.creatingBranch.set(true);
  }

  cancelCreateBranch(): void {
    this.creatingBranch.set(false);
    this.newBranchForm = emptyBranchForm();
  }

  createBranch(): void {
    const validationError = this.validateBranchForm(this.newBranchForm);
    if (validationError) {
      this.error.set(validationError);
      return;
    }
    this.saving.set(true);
    const payload: BranchPayload = this.toBranchPayload(this.newBranchForm);
    this.api.createBranch(payload).subscribe({
      next: (created) => {
        this.saving.set(false);
        this.error.set('');
        this.showToast('分館已新增。');
        this.branchRows.update((rows) => [...rows, created]);
        this.branches.update((rows) => [...rows, created]);
        this.cancelCreateBranch();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.branchApiErrorMessage(err, '分館新增失敗。'));
      }
    });
  }

  startEditBranch(branch: BranchSummary): void {
    this.creatingBranch.set(false);
    this.newBranchForm = emptyBranchForm();
    this.editingBranch.set(branch);
    this.branchEditForm = {
      branchName: branch.branch_name ?? '',
      companyName: branch.company_name ?? '',
      branchCode: branch.branch_code ?? '',
      branchAddress: branch.branch_address ?? '',
      taxId: branch.tax_id ?? '',
      bankAccount: branch.bank_account ?? '',
      bankBranch: branch.bank_branch ?? '',
      bankAccountName: branch.bank_account_name ?? ''
    };
  }

  cancelEditBranch(): void {
    this.editingBranch.set(null);
    this.branchEditForm = emptyBranchForm();
  }

  openBranchDetail(branch: BranchSummary): void {
    this.viewingBranch.set(branch);
  }

  closeBranchDetail(): void {
    this.viewingBranch.set(null);
  }

  saveBranchEdit(): void {
    const branch = this.editingBranch();
    if (!branch) return;
    const validationError = this.validateBranchForm(this.branchEditForm);
    if (validationError) {
      this.error.set(validationError);
      return;
    }
    this.saving.set(true);
    const payload: BranchPayload = this.toBranchPayload(this.branchEditForm);
    this.api.updateBranch(branch.branch_id, payload).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.error.set('');
        this.showToast('分館已更新。');
        this.branchRows.update((rows) => rows.map((r) => (r.branch_id === updated.branch_id ? updated : r)));
        this.branches.update((rows) => rows.map((r) => (r.branch_id === updated.branch_id ? updated : r)));
        if (this.viewingBranch()?.branch_id === updated.branch_id) {
          this.viewingBranch.set(updated);
        }
        this.cancelEditBranch();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.branchApiErrorMessage(err, '分館更新失敗。'));
      }
    });
  }

  digitsOnly(value: string, maxLength: number): string {
    return (value ?? '').replace(/\D/g, '').slice(0, maxLength);
  }

  private toBranchPayload(form: BranchForm): BranchPayload {
    return {
      branchName: form.branchName.trim(),
      companyName: form.companyName.trim() || undefined,
      branchCode: form.branchCode.trim() || undefined,
      branchAddress: form.branchAddress.trim() || undefined,
      taxId: form.taxId.trim() || undefined,
      bankAccount: form.bankAccount.trim() || undefined,
      bankBranch: form.bankBranch.trim() || undefined,
      bankAccountName: form.bankAccountName.trim() || undefined
    };
  }

  private validateBranchForm(form: BranchForm): string | null {
    const name = form.branchName.trim();
    if (!name) {
      return '請輸入分館名稱。';
    }
    if (name.length > 100) {
      return '分館名稱長度不可超過 100 個字元。';
    }
    const companyName = form.companyName.trim();
    if (companyName.length > 100) {
      return '公司名稱長度不可超過 100 個字元。';
    }
    const code = form.branchCode.trim();
    if (code && (!/^\d+$/.test(code) || code.length > 50)) {
      return '分館編號僅限數字，長度不可超過 50 個字元。';
    }
    const address = form.branchAddress.trim();
    if (address.length > 255) {
      return '分館地址長度不可超過 255 個字元。';
    }
    const taxId = form.taxId.trim();
    if (taxId && !/^\d{8}$/.test(taxId)) {
      return '統一編號須為 8 碼數字。';
    }
    const bankAccount = form.bankAccount.trim();
    if (bankAccount && (!/^\d+$/.test(bankAccount) || bankAccount.length > 30)) {
      return '銀行帳號僅限數字，長度不可超過 30 個字元。';
    }
    const bankBranch = form.bankBranch.trim();
    if (bankBranch.length > 100) {
      return '銀行分行長度不可超過 100 個字元。';
    }
    const bankAccountName = form.bankAccountName.trim();
    if (bankAccountName.length > 100) {
      return '戶名長度不可超過 100 個字元。';
    }
    return null;
  }

  private branchApiErrorMessage(err: HttpErrorResponse, fallback: string): string {
    const detail = err?.error?.error ?? err?.error?.message;
    if (typeof detail === 'string' && detail) {
      return `${fallback}（${detail}）`;
    }
    if (err?.status) {
      return `${fallback}（HTTP ${err.status}）`;
    }
    return fallback;
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
        this.showToast('辦公室已新增。');
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
        this.showToast('辦公室已更新。');
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
    this.closeSidebarAfterNavigation();
    this.router.navigateByUrl('/home');
  }

  loadCustomers(): void {
    this.loading.set(true);
    this.api.customers(this.customerFilters, this.customerPage(), this.customerPageSize()).subscribe({
      next: (value) => {
        this.customers.set(value.content);
        this.customerTotal.set(value.totalElements);
        this.customerPage.set(value.page);
        this.loading.set(false);
        if (!value.content.length) {
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

  searchCustomers(): void {
    this.customerPage.set(0);
    this.loadCustomers();
  }

  toggleAdvancedFilters(): void {
    this.advancedFiltersOpen.update((isOpen) => !isOpen);
  }

  changeCustomerPageSize(size: number): void {
    const pageSize = Number(size);
    if (![10, 20, 50].includes(pageSize) || pageSize === this.customerPageSize()) {
      return;
    }
    this.customerPageSize.set(pageSize);
    this.customerPage.set(0);
    this.loadCustomers();
  }

  changeCustomerPage(delta: number): void {
    const next = this.customerPage() + delta;
    if (next < 0 || next >= this.customerTotalPages()) {
      return;
    }
    this.customerPage.set(next);
    this.loadCustomers();
  }

  resetCustomerFilters(): void {
    this.customerFilters = {
      companyName: '',
      taxId: '',
      phone: '',
      accountInfo: '',
      ownerName: '',
      branchId: null,
      officeNo: '',
      ownerBirthdayMonth: null,
      contactBirthdayMonth: null
    };
    this.customerPage.set(0);
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

  goToNewCustomerStep(step: 1 | 2): void {
    if (step === 2 && !this.newCustomerForm.companyName.trim()) {
      this.requireNewCustomerCompanyName();
      return;
    }
    this.customerNewStep.set(step);
  }

  cancelNewCustomer(): void {
    if (this.hasNewCustomerChanges() && !window.confirm('尚未儲存的資料將會遺失，確定要取消嗎？')) {
      return;
    }
    this.resetNewCustomerFlow();
    this.router.navigateByUrl('/customers');
  }

  selectCustomer(id: number): void {
    this.api.customerDetail(id).subscribe({
      next: (value) => {
        this.selectedCustomer.set(value);
        this.customerEditForm = this.formFromCustomer(value);
        this.customerEditFieldErrors.set({});
        this.editingCustomer.set(false);
      },
      error: () => this.error.set('無法載入客戶詳細資料。')
    });
  }

  openCustomerDetail(id: number): void {
    this.router.navigate(['/customers', id]);
  }

  backToCustomerList(): void {
    this.selectedCustomer.set(null);
    this.editingCustomer.set(false);
    this.router.navigateByUrl('/customers');
  }

  startEditCustomer(customer?: CustomerSummary): void {
    if (customer && this.selectedCustomerId() !== customer.customer_id) {
      this.api.customerDetail(customer.customer_id).subscribe({
        next: (value) => {
          this.selectedCustomer.set(value);
          this.customerEditForm = this.formFromCustomer(value);
          this.customerEditFieldErrors.set({});
          this.editingCustomer.set(true);
        },
        error: () => this.error.set('無法載入客戶詳細資料。')
      });
      return;
    }
    const detail = this.selectedCustomer();
    if (detail) {
      this.customerEditForm = this.formFromCustomer(detail);
      this.customerEditFieldErrors.set({});
      this.editingCustomer.set(true);
    }
  }

  cancelEditCustomer(): void {
    const detail = this.selectedCustomer();
    if (detail) {
      this.customerEditForm = this.formFromCustomer(detail);
    }
    this.customerEditFieldErrors.set({});
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
    this.clearNewCustomerFieldError('companyName');
  }

  addRelatedCompany(): void {
    this.newCustomerForm.relatedCompanyNames.push('');
  }

  removeRelatedCompany(index: number): void {
    if (this.newCustomerForm.relatedCompanyNames.length === 1) {
      this.newCustomerForm.relatedCompanyNames[0] = '';
      return;
    }
    this.newCustomerForm.relatedCompanyNames.splice(index, 1);
  }

  trackByIndex(index: number): number {
    return index;
  }

  createCustomer(): void {
    if (!this.newCustomerForm.companyName.trim()) {
      this.requireNewCustomerCompanyName();
      return;
    }
    this.clearNewCustomerFieldError('companyName');
    if (!this.validContractStaff(this.newCustomerContractForm)) {
      return;
    }
    const hasFirstPaymentAmount = this.newCustomerFirstPaymentAmount !== null;
    const hasFirstPaymentDate = Boolean(this.newCustomerFirstPaymentDateText);
    if (hasFirstPaymentAmount !== hasFirstPaymentDate) {
      this.error.set('本次繳款金額與繳款日期必須一起填寫。');
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
          contract: this.toContractPayload(this.newCustomerContractForm),
          firstPaymentAmount: this.newCustomerFirstPaymentAmount,
          firstPaymentDateText: this.newCustomerFirstPaymentDateText
        },
        this.newCustomerLeaseImage
      )
      .subscribe({
        next: (value) => {
          this.saving.set(false);
          this.error.set('');
          this.showToast('客戶與租約已新增。');
          this.selectedCustomer.set(value);
          this.customerEditForm = this.formFromCustomer(value);
          this.editingCustomer.set(false);
          this.resetNewCustomerFlow();
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
      if (id !== null) {
        this.customerEditFieldErrors.set({ companyName: '公司名稱為必填。' });
        this.error.set('');
        setTimeout(() => this.editCompanyNameInput?.nativeElement.focus(), 0);
      } else {
        this.requireNewCustomerCompanyName();
      }
      return;
    }
    if (id !== null) {
      this.clearCustomerEditFieldError('companyName');
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
        this.showToast(message);
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
        this.showToast('職員角色權限已更新。');
        this.loadStaffOverview();
      },
      error: () => this.error.set('職員角色權限更新失敗。')
    });
  }

  canManageBonusRules(): boolean {
    return this.currentUser()?.canManageBonusRules === true;
  }

  // ---------- 業績目標 ----------

  loadSalesTargets(): void {
    const filters: SalesTargetSearchFilters = {
      ...this.salesTargetFilters,
      page: this.salesTargetPage(),
      pageSize: this.salesTargetPageSize
    };
    this.api.salesTargets(filters).subscribe({
      next: (result) => {
        this.salesTargetRows.set(result.content);
        this.salesTargetTotal.set(result.totalElements);
      },
      error: () => this.error.set('無法載入業績目標資料。')
    });
  }

  searchSalesTargets(): void {
    this.salesTargetPage.set(0);
    this.loadSalesTargets();
  }

  startCreateSalesTarget(): void {
    this.newSalesTargetForm = {
      branchId: this.currentUser()?.branch_id ?? 0,
      targetMonth: 0,
      category: '',
      targetCount: 0,
      staffId: this.currentStaffId()
    };
    this.creatingSalesTarget.set(true);
  }

  cancelCreateSalesTarget(): void {
    this.creatingSalesTarget.set(false);
  }

  createSalesTarget(): void {
    if (!this.newSalesTargetForm.branchId || !this.newSalesTargetForm.targetMonth || !this.newSalesTargetForm.category) {
      this.error.set('分館、月份、類別為必填欄位。');
      return;
    }
    this.saving.set(true);
    const payload: SalesTargetPayload = { ...this.newSalesTargetForm, staffId: this.currentStaffId() };
    this.api.createSalesTarget(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.error.set('');
        this.showToast('業績目標已新增。');
        this.creatingSalesTarget.set(false);
        this.loadSalesTargets();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.branchApiErrorMessage(err, '業績目標新增失敗。'));
      }
    });
  }

  // ---------- 業績獎金規則 ----------

  loadBonusRules(): void {
    this.api.bonusRules().subscribe({
      next: (rows) => this.bonusRuleRows.set(rows),
      error: () => this.error.set('無法載入業績獎金規則。')
    });
  }

  startCreateBonusRule(): void {
    this.editingBonusRule.set(null);
    this.newBonusRuleForm = {
      ruleName: '', ruleType: 'OFFICE_RENTAL', unitAmount: undefined, percentage: undefined,
      tierConfig: '', periodType: '', description: '', isActive: true, staffId: this.currentStaffId()
    };
    this.newBonusRuleTiers = [{ threshold: null, amount: null }];
    this.creatingBonusRule.set(true);
  }

  cancelCreateBonusRule(): void {
    this.creatingBonusRule.set(false);
  }

  bonusRuleFieldMode(ruleType: string): 'unitAmount' | 'percentage' | 'tierConfig' {
    return this.bonusRuleFieldByType[ruleType] ?? 'unitAmount';
  }

  bonusRulePeriodTypeLabel(periodType: string | null): string {
    if (!periodType) {
      return '-';
    }
    return this.bonusRulePeriodTypeOptions.find((option) => option.value === periodType)?.label ?? periodType;
  }

  addNewBonusRuleTier(): void {
    this.newBonusRuleTiers.push({ threshold: null, amount: null });
  }

  removeNewBonusRuleTier(index: number): void {
    this.newBonusRuleTiers.splice(index, 1);
    if (!this.newBonusRuleTiers.length) {
      this.newBonusRuleTiers.push({ threshold: null, amount: null });
    }
  }

  addEditBonusRuleTier(): void {
    this.bonusRuleEditTiers.push({ threshold: null, amount: null });
  }

  removeEditBonusRuleTier(index: number): void {
    this.bonusRuleEditTiers.splice(index, 1);
    if (!this.bonusRuleEditTiers.length) {
      this.bonusRuleEditTiers.push({ threshold: null, amount: null });
    }
  }

  private serializeBonusRuleTiers(tiers: BonusRuleTierRow[]): string {
    const valid = tiers
      .filter((tier) => tier.threshold != null && tier.amount != null)
      .map((tier) => ({ threshold: tier.threshold, amount: tier.amount }))
      .sort((a, b) => (a.threshold ?? 0) - (b.threshold ?? 0));
    return JSON.stringify(valid);
  }

  private parseBonusRuleTiers(tierConfig: string | null): BonusRuleTierRow[] {
    if (tierConfig) {
      try {
        const parsed = JSON.parse(tierConfig);
        if (Array.isArray(parsed) && parsed.length) {
          return parsed.map((tier) => ({ threshold: tier?.threshold ?? null, amount: tier?.amount ?? null }));
        }
      } catch {
        // 舊資料格式不是合法 JSON 時，改用空白列讓使用者重新填寫
      }
    }
    return [{ threshold: null, amount: null }];
  }

  onNewBonusRuleTypeChange(): void {
    const mode = this.bonusRuleFieldMode(this.newBonusRuleForm.ruleType);
    if (mode !== 'unitAmount') {
      this.newBonusRuleForm.unitAmount = undefined;
    }
    if (mode !== 'percentage') {
      this.newBonusRuleForm.percentage = undefined;
    }
    if (mode !== 'tierConfig') {
      this.newBonusRuleForm.tierConfig = '';
    } else {
      this.newBonusRuleTiers = [{ threshold: null, amount: null }];
    }
  }

  createBonusRule(): void {
    if (!this.newBonusRuleForm.ruleName || !this.newBonusRuleForm.ruleType) {
      this.error.set('規則名稱、規則類型為必填欄位。');
      return;
    }
    const mode = this.bonusRuleFieldMode(this.newBonusRuleForm.ruleType);
    if (mode === 'tierConfig' && !this.newBonusRuleTiers.some((tier) => tier.threshold != null && tier.amount != null)) {
      this.error.set('請至少填寫一個完整的級距（門檻數量與獎金金額）。');
      return;
    }
    this.saving.set(true);
    const payload: BonusRulePayload = {
      ...this.newBonusRuleForm,
      unitAmount: mode === 'unitAmount' ? this.newBonusRuleForm.unitAmount : undefined,
      percentage: mode === 'percentage' ? this.newBonusRuleForm.percentage : undefined,
      tierConfig: mode === 'tierConfig' ? this.serializeBonusRuleTiers(this.newBonusRuleTiers) : '',
      staffId: this.currentStaffId()
    };
    this.api.createBonusRule(payload).subscribe({
      next: () => {
        this.saving.set(false);
        this.error.set('');
        this.showToast('業績獎金規則已新增。');
        this.creatingBonusRule.set(false);
        this.loadBonusRules();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.branchApiErrorMessage(err, '業績獎金規則新增失敗。'));
      }
    });
  }

  bonusRuleTypeLabel(ruleType: string): string {
    return this.bonusRuleTypeOptions.find((option) => option.value === ruleType)?.label ?? ruleType;
  }

  startEditBonusRule(rule: BonusRule): void {
    this.creatingBonusRule.set(false);
    this.editingBonusRule.set(rule);
    this.bonusRuleEditForm = {
      ruleName: rule.rule_name,
      ruleType: rule.rule_type,
      unitAmount: rule.unit_amount ?? undefined,
      percentage: rule.percentage ?? undefined,
      tierConfig: rule.tier_config ?? '',
      periodType: rule.period_type ?? '',
      description: rule.description ?? '',
      isActive: rule.is_active,
      staffId: this.currentStaffId()
    };
    this.bonusRuleEditTiers = this.parseBonusRuleTiers(rule.tier_config);
  }

  cancelEditBonusRule(): void {
    this.editingBonusRule.set(null);
  }

  onEditBonusRuleTypeChange(): void {
    const mode = this.bonusRuleFieldMode(this.bonusRuleEditForm.ruleType);
    if (mode !== 'unitAmount') {
      this.bonusRuleEditForm.unitAmount = undefined;
    }
    if (mode !== 'percentage') {
      this.bonusRuleEditForm.percentage = undefined;
    }
    if (mode !== 'tierConfig') {
      this.bonusRuleEditForm.tierConfig = '';
    } else if (!this.bonusRuleEditTiers.length) {
      this.bonusRuleEditTiers = [{ threshold: null, amount: null }];
    }
  }

  saveBonusRuleEdit(): void {
    const rule = this.editingBonusRule();
    if (!rule) {
      return;
    }
    if (!this.bonusRuleEditForm.ruleName || !this.bonusRuleEditForm.ruleType) {
      this.error.set('規則名稱、規則類型為必填欄位。');
      return;
    }
    const mode = this.bonusRuleFieldMode(this.bonusRuleEditForm.ruleType);
    if (mode === 'tierConfig' && !this.bonusRuleEditTiers.some((tier) => tier.threshold != null && tier.amount != null)) {
      this.error.set('請至少填寫一個完整的級距（門檻數量與獎金金額）。');
      return;
    }
    this.saving.set(true);
    const payload: BonusRulePayload = {
      ...this.bonusRuleEditForm,
      unitAmount: mode === 'unitAmount' ? this.bonusRuleEditForm.unitAmount : undefined,
      percentage: mode === 'percentage' ? this.bonusRuleEditForm.percentage : undefined,
      tierConfig: mode === 'tierConfig' ? this.serializeBonusRuleTiers(this.bonusRuleEditTiers) : '',
      staffId: this.currentStaffId()
    };
    this.api.updateBonusRule(rule.bonus_rule_id, payload).subscribe({
      next: (updated) => {
        this.saving.set(false);
        this.error.set('');
        this.showToast('業績獎金規則已更新。');
        this.bonusRuleRows.update((rows) => rows.map((r) => (r.bonus_rule_id === updated.bonus_rule_id ? updated : r)));
        this.cancelEditBonusRule();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.branchApiErrorMessage(err, '業績獎金規則更新失敗。'));
      }
    });
  }

  // ---------- 業績結算查詢／觸發 ----------

  loadPerformanceBonuses(): void {
    const filters: PerformanceBonusSearchFilters = {
      ...this.performanceBonusFilters,
      page: this.performanceBonusPage(),
      pageSize: this.performanceBonusPageSize
    };
    this.api.performanceBonuses(filters).subscribe({
      next: (result) => {
        this.performanceBonusRows.set(result.content);
        this.performanceBonusTotal.set(result.totalElements);
      },
      error: () => this.error.set('無法載入業績結算資料。')
    });
    this.loadStaffBonusSummary();
  }

  private loadStaffBonusSummary(): void {
    const filters: PerformanceBonusSearchFilters = {
      ruleType: this.performanceBonusFilters.ruleType,
      period: this.performanceBonusFilters.period,
      branchId: this.performanceBonusFilters.branchId,
      page: 0,
      pageSize: 200
    };
    this.api.performanceBonuses(filters).subscribe({
      next: (result) => {
        const totals = new Map<string, { staffName: string; total: number; count: number }>();
        for (const row of result.content) {
          const staffName = row.staff_name || '未指定祕書';
          const entry = totals.get(staffName) ?? { staffName, total: 0, count: 0 };
          entry.total += Number(row.bonus_amount || 0);
          entry.count += 1;
          totals.set(staffName, entry);
        }
        this.staffBonusSummaryRows.set(Array.from(totals.values()).sort((a, b) => b.total - a.total));
      }
    });
  }

  searchPerformanceBonuses(): void {
    this.performanceBonusPage.set(0);
    this.loadPerformanceBonuses();
  }

  syncTransactionBonuses(): void {
    this.settlingTransactions.set(true);
    this.settleResultMessage.set('');
    this.api.syncTransactionBonuses(this.currentStaffId()).subscribe({
      next: (result) => {
        this.settlingTransactions.set(false);
        this.showToast('逐筆獎金同步完成。');
        this.settleResultMessage.set(
          `新增 ${result.createdCount} 筆；已記錄跳過 ${result.skippedAlreadyRecorded} 筆；缺少結束日期跳過 ${result.skippedMissingEndDate} 筆；` +
          (result.skippedNoActiveRule.length ? `尚未設定規則：${result.skippedNoActiveRule.join('、')}` : '規則皆已設定')
        );
        this.loadPerformanceBonuses();
      },
      error: (err: HttpErrorResponse) => {
        this.settlingTransactions.set(false);
        this.error.set(this.branchApiErrorMessage(err, '逐筆獎金同步失敗。'));
      }
    });
  }

  settleMonthlyBonuses(): void {
    if (!this.settleMonthlyYearMonth) {
      this.error.set('請選擇月份。');
      return;
    }
    this.settlingMonthly.set(true);
    this.settleResultMessage.set('');
    this.api.settleMonthlyBonuses(this.settleMonthlyYearMonth, this.currentStaffId()).subscribe({
      next: (result) => {
        this.settlingMonthly.set(false);
        this.showToast('滿租獎金月結完成。');
        this.settleResultMessage.set(
          `${result.period}：新增 ${result.createdCount} 筆` +
          (result.skippedBranches.length ? `；無祕書可入帳分館：${result.skippedBranches.join('、')}` : '')
        );
        this.loadPerformanceBonuses();
      },
      error: (err: HttpErrorResponse) => {
        this.settlingMonthly.set(false);
        this.error.set(this.branchApiErrorMessage(err, '滿租獎金月結失敗。'));
      }
    });
  }

  settlePeriodBonuses(): void {
    const period = `${this.settlePeriodYear}-P${this.settlePeriodQuarter}`;
    this.settlingPeriod.set(true);
    this.settleResultMessage.set('');
    this.api.settlePeriodBonuses(period, this.currentStaffId()).subscribe({
      next: (result) => {
        this.settlingPeriod.set(false);
        this.showToast('期間結算完成。');
        this.settleResultMessage.set(
          `${result.period}：新增 ${result.createdCount} 筆` +
          (result.skippedBranches.length ? `；無祕書可入帳分館：${result.skippedBranches.join('、')}` : '') +
          (result.unassignedContractCount ? `；無法判斷分館的合約：${result.unassignedContractCount} 筆` : '')
        );
        this.loadPerformanceBonuses();
      },
      error: (err: HttpErrorResponse) => {
        this.settlingPeriod.set(false);
        this.error.set(this.branchApiErrorMessage(err, '期間結算失敗。'));
      }
    });
  }

  loadContracts(): void {
    this.loading.set(true);
    this.api.contracts(this.contractFilters, this.contractPage(), this.contractPageSize()).subscribe({
      next: (value) => {
        this.contracts.set(value.content);
        this.contractTotal.set(value.totalElements);
        this.contractPage.set(value.page);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('無法載入租約清單。');
      }
    });
  }

  searchContracts(): void {
    this.contractPage.set(0);
    this.loadContracts();
  }

  changeContractPageSize(size: number): void {
    const pageSize = Number(size);
    if (![10, 20, 50].includes(pageSize) || pageSize === this.contractPageSize()) {
      return;
    }
    this.contractPageSize.set(pageSize);
    this.contractPage.set(0);
    this.loadContracts();
  }

  changeContractPage(delta: number): void {
    const next = this.contractPage() + delta;
    if (next < 0 || next >= this.contractTotalPages()) {
      return;
    }
    this.contractPage.set(next);
    this.loadContracts();
  }

  resetContractFilters(): void {
    this.contractFilters = {
      companyName: '',
      taxId: '',
      startDateText: '',
      endDateText: '',
      leaseStatus: ''
    };
    this.contractPage.set(0);
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
    this.newContractFirstPaymentAmount = null;
    this.newContractFirstPaymentDateText = '';
    this.newContractForm = {
      ...emptyContractForm(),
      customerId: customer.customer_id,
      signerStaffId: this.currentStaffId(),
      updatedBy: this.currentStaffId()
    };
    this.api.latestContract(customer.customer_id).subscribe({
      next: (latest) => {
        if (!latest['contract_id']) {
          return;
        }
        this.newContractForm = {
          ...this.newContractForm,
          officeId: latest['office_id'] === null || latest['office_id'] === undefined ? null : Number(latest['office_id']),
          rentalItem: this.textValue(latest['rental_item']) || '登記',
          rentalStatus: this.textValue(latest['rental_status']) || '登記',
          signerStaffId:
            latest['signer_staff_id'] === null || latest['signer_staff_id'] === undefined
              ? this.currentStaffId()
              : Number(latest['signer_staff_id']),
          partnerStaffId:
            latest['partner_staff_id'] === null || latest['partner_staff_id'] === undefined
              ? null
              : Number(latest['partner_staff_id']),
          sourceText: this.textValue(latest['source_text']),
          paymentMonths:
            latest['payment_months'] === null || latest['payment_months'] === undefined
              ? null
              : Number(latest['payment_months']),
          rent: latest['rent'] === null || latest['rent'] === undefined ? null : Number(latest['rent']),
          deposit: latest['deposit'] === null || latest['deposit'] === undefined ? null : Number(latest['deposit'])
        };
      },
      error: () => this.error.set('無法載入最新租約資料。')
    });
  }

  hasValidNewContractPayment(): boolean {
    const hasAmount = this.newContractFirstPaymentAmount !== null
      && this.newContractFirstPaymentAmount !== undefined;
    const hasDate = this.newContractFirstPaymentDateText.trim() !== '';
    if (hasAmount !== hasDate) {
      this.error.set('本次繳款金額與繳款日期必須一起填寫。');
      return false;
    }
    if (hasAmount && (!Number.isFinite(Number(this.newContractFirstPaymentAmount))
      || Number(this.newContractFirstPaymentAmount) <= 0)) {
      this.error.set('本次繳款金額必須大於 0。');
      return false;
    }
    return true;
  }

  createContract(): void {
    if (!this.hasValidNewContractPayment()) {
      return;
    }
    this.saveContract(null, this.newContractForm, '租約已續約。');
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
    if (!this.validContractStaff(form)) {
      return;
    }
    form.updatedBy = this.currentStaffId();
    this.saving.set(true);
    const request = id
      ? this.api.updateContract(id, this.toContractPayload(form))
      : this.api.createContractWithFirstPayment({
          contract: this.toContractPayload(form),
          firstPaymentAmount: this.newContractFirstPaymentAmount,
          firstPaymentDateText: this.newContractFirstPaymentDateText
        });

    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.error.set('');
        this.showToast(message);
        if (id) {
          this.cancelEditContract();
        } else {
          this.newContractForm = { ...emptyContractForm(), updatedBy: this.currentStaffId() };
          this.newContractFirstPaymentAmount = null;
          this.newContractFirstPaymentDateText = '';
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

  openReconciliation(
    item:
      | Dashboard['notifications']['incompleteContracts'][number]
      | Dashboard['notifications']['unpaidRent'][number]
  ): void {
    const amount = 'suggested_amount' in item ? item.suggested_amount : null;
    this.router.navigate(['/rent-payments/new'], {
      queryParams: {
        customerId: item.customer_id,
        contractId: item.contract_id,
        amount: amount ?? undefined
      }
    });
  }

  pagedNotifications<T>(items: T[], kind: NotificationKind): T[] {
    const page = this.notificationPage(kind);
    const start = (page - 1) * this.notificationPageSize;
    return items.slice(start, start + this.notificationPageSize);
  }

  notificationTotalPages(items: unknown[]): number {
    return Math.max(1, Math.ceil(items.length / this.notificationPageSize));
  }

  notificationPage(kind: NotificationKind): number {
    switch (kind) {
      case 'expiring':
        return this.expiringNotificationPage();
      case 'unpaid':
        return this.unpaidNotificationPage();
      case 'incomplete':
        return this.incompleteNotificationPage();
    }
  }

  changeNotificationPage(kind: NotificationKind, delta: number, itemCount: number): void {
    const totalPages = Math.max(1, Math.ceil(itemCount / this.notificationPageSize));
    this.setNotificationPage(kind, Math.min(totalPages, Math.max(1, this.notificationPage(kind) + delta)));
  }

  private setNotificationPage(kind: NotificationKind, page: number): void {
    switch (kind) {
      case 'expiring':
        this.expiringNotificationPage.set(page);
        break;
      case 'unpaid':
        this.unpaidNotificationPage.set(page);
        break;
      case 'incomplete':
        this.incompleteNotificationPage.set(page);
        break;
    }
  }

  private clampNotificationPages(dashboard: Dashboard): void {
    const groups: Array<[NotificationKind, unknown[]]> = [
      ['expiring', dashboard.notifications.expiringContracts],
      ['unpaid', dashboard.notifications.unpaidRent],
      ['incomplete', dashboard.notifications.incompleteContracts]
    ];
    groups.forEach(([kind, items]) => {
      const totalPages = this.notificationTotalPages(items);
      this.setNotificationPage(kind, Math.min(this.notificationPage(kind), totalPages));
    });
  }

  private loadRentFromRoute(): void {
    const query = this.router.parseUrl(this.router.url).queryParams;
    const customerId = Number(query['customerId']);
    const contractId = Number(query['contractId']);
    const suggestedAmount = Number(query['amount']);
    if (!customerId || !contractId) {
      return;
    }
    this.api.customerDetail(customerId).subscribe({
      next: (customer) => {
        const contract = customer.contracts.find((row) => Number(row['contract_id']) === contractId);
        if (!contract) {
          this.error.set('找不到通知所對應的租約。');
          return;
        }
        this.selectedRentCustomer.set(customer);
        this.rentCustomerSearch = customer.company_name;
        this.rentCustomerOptions.set([]);
        this.rentForm = {
          customerId,
          contractId,
          paymentMonth: this.monthNumberFromInput(this.rentPaymentMonth),
          amount: Number.isFinite(suggestedAmount)
            ? suggestedAmount
            : contract['rent'] === null || contract['rent'] === undefined
              ? undefined
              : Number(contract['rent']),
          updatedBy: this.currentStaffId()
        };
      },
      error: () => this.error.set('無法載入待對帳客戶資料。')
    });
  }

  updateRentPaymentMonth(value: string): void {
    this.rentPaymentMonth = value;
    this.rentForm.paymentMonth = this.monthNumberFromInput(value);
  }

  loadRentPayments(): void {
    this.loading.set(true);
    this.api.rentPayments(this.rentPaymentFilters, this.rentPaymentPage(), this.rentPaymentPageSize()).subscribe({
      next: (value) => {
        this.rentPayments.set(value.content);
        this.rentPaymentTotal.set(value.totalElements);
        this.rentPaymentPage.set(value.page);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.error.set('無法載入對帳紀錄。');
      }
    });
  }

  searchRentPayments(): void {
    this.rentPaymentPage.set(0);
    this.loadRentPayments();
  }

  changeRentPaymentPageSize(size: number): void {
    const pageSize = Number(size);
    if (![10, 20, 50].includes(pageSize) || pageSize === this.rentPaymentPageSize()) {
      return;
    }
    this.rentPaymentPageSize.set(pageSize);
    this.rentPaymentPage.set(0);
    this.loadRentPayments();
  }

  changeRentPaymentPage(delta: number): void {
    const next = this.rentPaymentPage() + delta;
    if (next < 0 || next >= this.rentPaymentTotalPages()) {
      return;
    }
    this.rentPaymentPage.set(next);
    this.loadRentPayments();
  }

  resetRentPaymentFilters(): void {
    this.rentPaymentFilters = {
      companyName: '',
      taxId: '',
      paymentDateStartText: '',
      paymentDateEndText: ''
    };
    this.rentPaymentPage.set(0);
    this.loadRentPayments();
  }

  loadChargeLists(): void {
    this.api.chargeLists({
      ...this.chargeListFilters,
      page: this.chargeListPage(),
      pageSize: this.chargeListPageSize,
      sortBy: this.chargeListSortBy,
      sortDir: this.chargeListSortDir
    }).subscribe({
      next: (result) => {
        this.chargeListRows.set(result.content);
        this.chargeListTotal.set(result.totalElements);
      },
      error: () => this.error.set('無法載入收費清單。')
    });
  }

  resetChargeListFilters(): void {
    this.chargeListFilters = {
      chargeListId: null,
      customerId: null,
      contractId: null,
      feeMonth: '',
      status: null,
      createdBy: null,
      issuedFrom: '',
      issuedTo: ''
    };
    this.chargeListFilterCustomerSearch = '';
    this.chargeListFilterCustomerOptions.set([]);
    this.chargeListPage.set(0);
    this.loadChargeLists();
  }

  lookupChargeListFilterCustomers(): void {
    const term = this.chargeListFilterCustomerSearch.trim();
    if (!term) {
      this.chargeListFilterCustomerOptions.set([]);
      this.chargeListFilters.customerId = null;
      return;
    }
    this.api.customerLookup(term).subscribe({
      next: (rows) => this.chargeListFilterCustomerOptions.set(rows),
      error: () => this.error.set('無法查詢客戶資料。')
    });
  }

  selectChargeListFilterCustomer(customer: CustomerSummary): void {
    this.chargeListFilterCustomerSearch = customer.company_name;
    this.chargeListFilterCustomerOptions.set([]);
    this.chargeListFilters.customerId = customer.customer_id;
  }

  searchChargeLists(): void {
    this.chargeListPage.set(0);
    this.loadChargeLists();
  }

  changeChargeListPage(delta: number): void {
    const next = this.chargeListPage() + delta;
    if (next < 0 || next >= this.chargeListTotalPages()) {
      return;
    }
    this.chargeListPage.set(next);
    this.loadChargeLists();
  }

  toggleChargeListSort(column: string): void {
    if (this.chargeListSortBy === column) {
      this.chargeListSortDir = this.chargeListSortDir === 'asc' ? 'desc' : 'asc';
    } else {
      this.chargeListSortBy = column;
      this.chargeListSortDir = 'asc';
    }
    this.chargeListPage.set(0);
    this.loadChargeLists();
  }

  lookupChargeListCustomers(): void {
    const term = this.chargeListCustomerSearch.trim();
    if (!term) {
      this.chargeListCustomerOptions.set([]);
      return;
    }
    this.api.customerLookup(term).subscribe({
      next: (rows) => this.chargeListCustomerOptions.set(rows),
      error: () => this.error.set('無法查詢客戶資料。')
    });
  }

  selectChargeListCustomer(customer: CustomerSummary): void {
    this.api.customerDetail(customer.customer_id).subscribe({
      next: (value) => {
        this.selectedChargeListCustomer.set(value);
        this.chargeListCustomerSearch = value.company_name;
        this.chargeListCustomerOptions.set([]);
        this.newChargeListForm.customerId = value.customer_id;
        const firstContract = value.contracts?.[0] as Record<string, unknown> | undefined;
        this.newChargeListForm.contractId = firstContract ? Number(firstContract['contract_id']) : null;
      },
      error: () => this.error.set('無法載入客戶租約資料。')
    });
  }

  chargeListPreviewTotal(form: ChargeListForm): number {
    return (
      Number(form.managementFee || 0) +
      Number(form.electricityFee || 0) +
      Number(form.printingFee || 0) +
      Number(form.meetingRoomFee || 0) +
      Number(form.tax || 0) +
      Number(form.advancePayment || 0) +
      Number(form.repairFee || 0)
    );
  }

  normalizeChargeListFeeField(
    event: Event,
    form: ChargeListForm,
    field: 'managementFee' | 'electricityFee' | 'printingFee' | 'meetingRoomFee' | 'tax' | 'advancePayment' | 'repairFee'
  ): void {
    const input = event.target as HTMLInputElement;
    const normalized = Math.max(0, Number(input.value) || 0);
    form[field] = normalized;
    input.value = String(normalized);
  }

  createChargeList(): void {
    if (!this.newChargeListForm.customerId || !this.newChargeListForm.contractId) {
      this.error.set('請先搜尋並選擇客戶與租約。');
      return;
    }
    if (!this.newChargeListForm.feeMonth) {
      this.error.set('請填寫費用月份。');
      return;
    }
    if (!window.confirm('確定要新增這筆收費清單嗎？')) {
      return;
    }
    this.newChargeListForm.updatedBy = this.currentStaffId();
    this.saving.set(true);
    this.api.createChargeList(this.toChargeListPayload(this.newChargeListForm)).subscribe({
      next: () => {
        this.saving.set(false);
        this.error.set('');
        this.showToast('收費清單已新增。');
        this.newChargeListForm = {
          ...emptyChargeListForm(),
          feeMonth: this.currentMonthValue()
        };
        this.chargeListCustomerSearch = '';
        this.selectedChargeListCustomer.set(null);
        this.loadChargeLists();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('收費清單新增失敗，請確認必填欄位與金額是否正確。');
      }
    });
  }

  startEditChargeList(row: ChargeListSummary): void {
    this.editingChargeList.set(row);
    this.chargeListEditForm = {
      customerId: row.customer_id,
      contractId: row.contract_id,
      feeMonth: row.fee_month,
      managementFee: Number(row.management_fee ?? 0),
      electricityFee: Number(row.electricity_fee ?? 0),
      printingFee: Number(row.printing_fee ?? 0),
      meetingRoomFee: Number(row.meeting_room_fee ?? 0),
      tax: Number(row.tax ?? 0),
      advancePayment: Number(row.advance_payment ?? 0),
      repairFee: Number(row.repair_fee ?? 0),
      updatedBy: this.currentStaffId()
    };
  }

  cancelEditChargeList(): void {
    this.editingChargeList.set(null);
    this.chargeListEditForm = emptyChargeListForm();
  }

  openChargeListDetail(row: ChargeListSummary): void {
    this.viewingChargeList.set(row);
  }

  closeChargeListDetail(): void {
    this.viewingChargeList.set(null);
  }

  saveChargeListEdit(): void {
    const row = this.editingChargeList();
    if (!row) {
      return;
    }
    if (!this.chargeListEditForm.feeMonth) {
      this.error.set('請填寫費用月份。');
      return;
    }
    if (!window.confirm('確定要儲存這筆收費清單的修改嗎？')) {
      return;
    }
    this.chargeListEditForm.updatedBy = this.currentStaffId();
    this.saving.set(true);
    this.api.updateChargeList(row.charge_list_id, this.toChargeListPayload(this.chargeListEditForm)).subscribe({
      next: () => {
        this.saving.set(false);
        this.error.set('');
        this.showToast('收費清單已更新。');
        this.cancelEditChargeList();
        this.loadChargeLists();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('收費清單更新失敗，請確認費用月份與金額是否正確。');
      }
    });
  }

  displayChargeListStatus(value: unknown): string {
    return Number(value) === 1 ? '已結清' : '未結清';
  }

  formatFeePeriod(row: ChargeListSummary): string {
    return row.fee_month || '-';
  }

  chargeListGrandTotal(row: ChargeListSummary): number {
    return Number(row.contract_rent ?? 0) + Number(row.total_amount ?? 0);
  }

  formatDateTime(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return '-';
    }
    const date = new Date(String(value).replace(' ', 'T'));
    if (Number.isNaN(date.getTime())) {
      return '-';
    }
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  formatRocDate(value: unknown): string {
    if (value === null || value === undefined || value === '') {
      return '-';
    }
    const date = new Date(String(value));
    if (Number.isNaN(date.getTime())) {
      return '-';
    }
    const rocYear = date.getFullYear() - 1911;
    return `${rocYear}年${date.getMonth() + 1}月${date.getDate()}日`;
  }

  formatRocFeeMonth(feeMonth: unknown): string {
    const match = /^(\d{4})-(\d{2})$/.exec(String(feeMonth ?? ''));
    if (!match) {
      return '-';
    }
    const rocYear = Number(match[1]) - 1911;
    const month = Number(match[2]);
    return `${rocYear} 年 ${month} 月 1 日`;
  }

  exportChargeListImage(row: ChargeListSummary): void {
    this.exportingChargeList.set(row);
    setTimeout(() => {
      const element = this.chargeListPrintArea?.nativeElement;
      if (!element) {
        this.exportingChargeList.set(null);
        return;
      }
      html2canvas(element, { backgroundColor: '#ffffff', scale: 2 })
        .then((canvas) => {
          this.chargeListPreviewImage.set(canvas.toDataURL('image/png'));
          this.chargeListPreviewRow.set(row);
          this.exportingChargeList.set(null);
        })
        .catch(() => {
          this.error.set('收費清單圖片匯出失敗。');
          this.exportingChargeList.set(null);
        });
    }, 0);
  }

  downloadChargeListPreview(): void {
    const dataUrl = this.chargeListPreviewImage();
    const row = this.chargeListPreviewRow();
    if (!dataUrl || !row) {
      return;
    }
    const link = document.createElement('a');
    link.download = `charge-list-${row.charge_list_id}.png`;
    link.href = dataUrl;
    link.click();
  }

  closeChargeListPreview(): void {
    this.chargeListPreviewImage.set(null);
    this.chargeListPreviewRow.set(null);
  }

  private toChargeListPayload(form: ChargeListForm): ChargeListPayload {
    return {
      customerId: form.customerId ?? undefined,
      contractId: form.contractId ?? undefined,
      feeMonth: form.feeMonth,
      managementFee: form.managementFee,
      electricityFee: form.electricityFee,
      printingFee: form.printingFee,
      meetingRoomFee: form.meetingRoomFee,
      tax: form.tax,
      advancePayment: form.advancePayment,
      repairFee: form.repairFee,
      updatedBy: form.updatedBy
    };
  }

  loadTaxBureauNoticePreview(): void {
    this.taxNoticeLoading.set(true);
    this.api.taxBureauNoticePreview(this.taxNoticeYearMonth, this.taxNoticeType).subscribe({
      next: (preview) => {
        this.taxNoticeLoading.set(false);
        this.taxNoticeGroups.set(preview.groups);
        this.taxNoticeUnassigned.set(preview.unassigned);
        const selected = new Set<string>();
        preview.groups.forEach((group) =>
          group.items.forEach((item) => selected.add(this.taxNoticeItemKey(item)))
        );
        this.taxNoticeSelected.set(selected);
        const info = { ...this.taxNoticeBranchInfo() };
        preview.groups.forEach((group) => {
          if (!info[group.branchId]) {
            info[group.branchId] = this.loadStoredTaxNoticeBranchInfo(group.branchId);
          }
        });
        this.taxNoticeBranchInfo.set(info);
      },
      error: () => {
        this.taxNoticeLoading.set(false);
        this.error.set('無法載入國稅局通報名單。');
      }
    });
  }

  taxNoticeItemKey(item: TaxBureauNoticeItem): string {
    return `${item.contractId}-${item.moveType}`;
  }

  isTaxNoticeItemSelected(item: TaxBureauNoticeItem): boolean {
    return this.taxNoticeSelected().has(this.taxNoticeItemKey(item));
  }

  toggleTaxNoticeItem(item: TaxBureauNoticeItem): void {
    const key = this.taxNoticeItemKey(item);
    const next = new Set(this.taxNoticeSelected());
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    this.taxNoticeSelected.set(next);
  }

  toggleTaxNoticeGroup(group: TaxBureauNoticeGroup, checked: boolean): void {
    const next = new Set(this.taxNoticeSelected());
    group.items.forEach((item) => {
      const key = this.taxNoticeItemKey(item);
      if (checked) {
        next.add(key);
      } else {
        next.delete(key);
      }
    });
    this.taxNoticeSelected.set(next);
  }

  isTaxNoticeGroupFullySelected(group: TaxBureauNoticeGroup): boolean {
    return group.items.length > 0 && group.items.every((item) => this.isTaxNoticeItemSelected(item));
  }

  toggleTaxNoticeGroupSelection(group: TaxBureauNoticeGroup): void {
    this.toggleTaxNoticeGroup(group, !this.isTaxNoticeGroupFullySelected(group));
  }

  taxNoticeBranchInfoFor(branchId: number): TaxBureauNoticeBranchInfo {
    return this.taxNoticeBranchInfo()[branchId] ?? { branchId, taxOfficeName: '', responsiblePerson: '', contactPhone: '' };
  }

  updateTaxNoticeBranchInfo(branchId: number, field: 'taxOfficeName' | 'responsiblePerson' | 'contactPhone', value: string): void {
    const info = { ...this.taxNoticeBranchInfo() };
    const current = info[branchId] ?? { branchId, taxOfficeName: '', responsiblePerson: '', contactPhone: '' };
    const updated = { ...current, [field]: value };
    info[branchId] = updated;
    this.taxNoticeBranchInfo.set(info);
    this.storeTaxNoticeBranchInfo(updated);
  }

  hasTaxNoticeSelection(): boolean {
    return this.taxNoticeSelected().size > 0;
  }

  generateTaxBureauNotice(): void {
    const selected = this.taxNoticeSelected();
    const items = this.taxNoticeGroups()
      .flatMap((group) => group.items)
      .filter((item) => selected.has(this.taxNoticeItemKey(item)))
      .map((item) => ({ contractId: item.contractId, moveType: item.moveType }));
    if (!items.length) {
      this.error.set('請至少選擇一筆要通報的公司。');
      return;
    }
    const branchIds = new Set(this.taxNoticeGroups().map((group) => group.branchId));
    const branchInfo = Array.from(branchIds).map((branchId) => this.taxNoticeBranchInfoFor(branchId));

    this.taxNoticeGenerating.set(true);
    this.api.generateTaxBureauNotice({ yearMonth: this.taxNoticeYearMonth, items, branchInfo }).subscribe({
      next: (blob) => {
        this.taxNoticeGenerating.set(false);
        this.error.set('');
        this.success.set('國稅局通報檔案已產生。');
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `國稅局通報_${this.taxNoticeYearMonth}.xlsx`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => {
        this.taxNoticeGenerating.set(false);
        this.error.set('國稅局通報檔案產生失敗。');
      }
    });
  }

  private taxNoticeBranchInfoStorageKey(branchId: number): string {
    return `cmsTaxNoticeBranchInfo:${branchId}`;
  }

  private loadStoredTaxNoticeBranchInfo(branchId: number): TaxBureauNoticeBranchInfo {
    try {
      const raw = localStorage.getItem(this.taxNoticeBranchInfoStorageKey(branchId));
      if (!raw) {
        return { branchId, taxOfficeName: '', responsiblePerson: '', contactPhone: '' };
      }
      const parsed = JSON.parse(raw);
      return {
        branchId,
        taxOfficeName: parsed.taxOfficeName ?? '',
        responsiblePerson: parsed.responsiblePerson ?? '',
        contactPhone: parsed.contactPhone ?? ''
      };
    } catch {
      return { branchId, taxOfficeName: '', responsiblePerson: '', contactPhone: '' };
    }
  }

  private storeTaxNoticeBranchInfo(info: TaxBureauNoticeBranchInfo): void {
    try {
      localStorage.setItem(this.taxNoticeBranchInfoStorageKey(info.branchId), JSON.stringify(info));
    } catch {
      // localStorage unavailable (e.g. private browsing) — form still works for this session.
    }
  }

  loadRefunds(): void {
    this.api.refunds({
      ...this.refundFilters,
      page: this.refundPage(),
      pageSize: this.refundPageSize,
      sortBy: this.refundSortBy,
      sortDir: this.refundSortDir
    }).subscribe({
      next: (result) => {
        this.refundRows.set(result.content);
        this.refundTotal.set(result.totalElements);
      },
      error: () => this.error.set('無法載入退款紀錄。')
    });
  }

  resetRefundFilters(): void {
    this.refundFilters = { companyName: '', taxId: '', dateFrom: '', dateTo: '', status: '' };
    this.refundPage.set(0);
    this.loadRefunds();
  }

  searchRefunds(): void {
    this.refundPage.set(0);
    this.loadRefunds();
  }

  changeRefundPage(delta: number): void {
    const next = this.refundPage() + delta;
    if (next < 0 || next >= this.refundTotalPages()) {
      return;
    }
    this.refundPage.set(next);
    this.loadRefunds();
  }

  changeSalesTargetPage(delta: number): void {
    const next = this.salesTargetPage() + delta;
    if (next < 0 || next >= this.salesTargetTotalPages()) {
      return;
    }
    this.salesTargetPage.set(next);
    this.loadSalesTargets();
  }

  changePerformanceBonusPage(delta: number): void {
    const next = this.performanceBonusPage() + delta;
    if (next < 0 || next >= this.performanceBonusTotalPages()) {
      return;
    }
    this.performanceBonusPage.set(next);
    this.loadPerformanceBonuses();
  }

  canReviewRefund(): boolean {
    return this.currentUser()?.canReviewRefund === true;
  }

  lookupRefundCustomers(): void {
    const term = this.refundCustomerSearch.trim();
    if (!term) {
      this.refundCustomerOptions.set([]);
      return;
    }
    this.api.customerLookup(term).subscribe({
      next: (rows) => this.refundCustomerOptions.set(rows),
      error: () => this.error.set('無法查詢客戶資料。')
    });
  }

  selectRefundCustomer(customer: CustomerSummary): void {
    this.api.customerDetail(customer.customer_id).subscribe({
      next: (value) => {
        this.selectedRefundCustomer.set(value);
        this.refundCustomerSearch = value.company_name;
        this.refundCustomerOptions.set([]);
        this.newRefundForm.customerId = value.customer_id;
        const firstContract = value.contracts?.[0] as Record<string, unknown> | undefined;
        this.newRefundForm.contractId = firstContract ? Number(firstContract['contract_id']) : null;
      },
      error: () => this.error.set('無法載入客戶租約資料。')
    });
  }

  importChargeListIntoRefund(form: RefundForm): void {
    if (!this.refundImportChargeListId) {
      this.error.set('請輸入收費清單編號。');
      return;
    }
    this.api.importChargeListForRefund(this.refundImportChargeListId).subscribe({
      next: (result) => {
        form.chargeListId = result.chargeListId;
        form.deductionTotal = Number(result.deductionTotal ?? 0);
        if (!form.customerId) {
          form.customerId = result.customerId;
        }
        if (!form.contractId && result.contractId) {
          form.contractId = result.contractId;
        }
        this.error.set('');
        this.success.set(`已帶入收費清單金額，原始應退金額 ${this.money(result.baseAmount)}，扣款總額 ${this.money(result.deductionTotal)}。`);
      },
      error: () => this.error.set('找不到該收費清單。')
    });
  }

  createRefund(): void {
    if (!this.newRefundForm.customerId || !this.newRefundForm.contractId) {
      this.error.set('請先搜尋並選擇客戶與租約。');
      return;
    }
    if (!this.newRefundForm.refundReason.trim()) {
      this.error.set('請填寫退款原因。');
      return;
    }
    if (!window.confirm('確定要新增這筆退款資料嗎？')) {
      return;
    }
    this.newRefundForm.staffId = this.currentStaffId();
    this.saving.set(true);
    this.api.createRefund(this.toRefundPayload(this.newRefundForm)).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.error.set('');
        this.success.set(result.message ?? '退款資料已新增。');
        this.newRefundForm = emptyRefundForm();
        this.refundCustomerSearch = '';
        this.selectedRefundCustomer.set(null);
        this.refundImportChargeListId = null;
        this.loadRefunds();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.branchApiErrorMessage(err, '退款資料新增失敗，請確認必填欄位與金額是否正確。'));
      }
    });
  }

  startEditRefund(row: RefundSummary): void {
    this.editingRefund.set(row);
    this.refundEditForm = {
      customerId: row.customer_id,
      contractId: row.contract_id,
      chargeListId: row.charge_list_id,
      refundReason: row.refund_reason ?? '',
      adjustmentAmount: Number(row.adjustment_amount ?? 0),
      adjustmentNote: row.adjustment_note ?? '',
      deductionTotal: Number(row.deduction_total ?? 0),
      paymentMethod: row.payment_method ?? '',
      bankCode: row.bank_code ?? '',
      bankAccount: row.bank_account ?? '',
      bankAccountName: row.bank_account_name ?? '',
      refundStatus: row.refund_status,
      refundedAt: row.refunded_at ?? '',
      staffId: this.currentStaffId()
    };
  }

  markRefunded(row: RefundSummary): void {
    this.startEditRefund(row);
    this.refundEditForm.refundStatus = '已退款';
  }

  submitRefundForReview(row: RefundSummary): void {
    if (!window.confirm('確定要將這筆退款送交主管審核嗎？')) {
      return;
    }
    const payload = this.toRefundPayload({
      customerId: row.customer_id,
      contractId: row.contract_id,
      chargeListId: row.charge_list_id,
      refundReason: row.refund_reason ?? '',
      adjustmentAmount: Number(row.adjustment_amount ?? 0),
      adjustmentNote: row.adjustment_note ?? '',
      deductionTotal: Number(row.deduction_total ?? 0),
      paymentMethod: row.payment_method ?? '',
      bankCode: row.bank_code ?? '',
      bankAccount: row.bank_account ?? '',
      bankAccountName: row.bank_account_name ?? '',
      refundStatus: '待審核',
      refundedAt: row.refunded_at ?? '',
      staffId: this.currentStaffId()
    });
    this.api.updateRefund(row.refund_id, payload).subscribe({
      next: (result) => {
        this.error.set('');
        this.success.set('已送交主管審核。');
        if (this.viewingRefund()?.refund_id === result.refund_id) {
          this.viewingRefund.set(result);
        }
        this.loadRefunds();
      },
      error: (err: HttpErrorResponse) => this.error.set(this.branchApiErrorMessage(err, '送交審核失敗。'))
    });
  }

  cancelEditRefund(): void {
    this.editingRefund.set(null);
    this.refundEditForm = emptyRefundForm();
  }

  saveRefundEdit(): void {
    const row = this.editingRefund();
    if (!row) {
      return;
    }
    if (!window.confirm('確定要儲存這筆退款資料的修改嗎？')) {
      return;
    }
    this.refundEditForm.staffId = this.currentStaffId();
    this.saving.set(true);
    this.api.updateRefund(row.refund_id, this.toRefundPayload(this.refundEditForm)).subscribe({
      next: (result) => {
        this.saving.set(false);
        this.error.set('');
        this.success.set(result.message ?? '退款資料已更新。');
        this.cancelEditRefund();
        this.loadRefunds();
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.error.set(this.branchApiErrorMessage(err, '退款資料更新失敗，請確認欄位是否正確。'));
      }
    });
  }

  cancelRefund(row: RefundSummary): void {
    if (!window.confirm('確定要取消這筆退款資料嗎？')) {
      return;
    }
    this.api.cancelRefund(row.refund_id, this.currentStaffId()).subscribe({
      next: (result) => {
        this.error.set('');
        this.success.set('退款已取消。');
        if (this.viewingRefund()?.refund_id === result.refund_id) {
          this.viewingRefund.set(result);
        }
        this.loadRefunds();
      },
      error: (err: HttpErrorResponse) => this.error.set(this.branchApiErrorMessage(err, '取消退款失敗。'))
    });
  }

  openRefundDetail(row: RefundSummary): void {
    this.viewingRefund.set(row);
  }

  closeRefundDetail(): void {
    this.viewingRefund.set(null);
  }

  reviewRefund(row: RefundSummary): void {
    if (!window.confirm('確定要審核通過這筆退款嗎？')) {
      return;
    }
    this.api.reviewRefund(row.refund_id, this.currentStaffId()).subscribe({
      next: (result) => {
        if (this.viewingRefund()?.refund_id === result.refund_id) {
          this.viewingRefund.set(result);
        }
        this.error.set('');
        this.success.set('退款已審核通過。');
        this.loadRefunds();
      },
      error: (err: HttpErrorResponse) => this.error.set(this.branchApiErrorMessage(err, '審核失敗。'))
    });
  }

  private toRefundPayload(form: RefundForm): RefundPayload {
    return {
      customerId: form.customerId ?? undefined,
      contractId: form.contractId ?? undefined,
      chargeListId: form.chargeListId ?? undefined,
      refundReason: form.refundReason,
      adjustmentAmount: form.adjustmentAmount,
      adjustmentNote: form.adjustmentNote,
      deductionTotal: form.deductionTotal,
      paymentMethod: form.paymentMethod,
      bankCode: form.bankCode,
      bankAccount: form.bankAccount,
      bankAccountName: form.bankAccountName,
      refundStatus: form.refundStatus,
      refundedAt: form.refundedAt,
      staffId: form.staffId
    };
  }

  loadMetadata(): void {
    this.api.metadata().subscribe({
      next: (data) => {
        this.branches.set((data['branches'] as BranchSummary[]) ?? []);
        this.roles.set((data['roles'] as RoleSummary[]) ?? []);
        this.staffOptions.set((data['staff'] as Array<Record<string, unknown>>) ?? []);
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
        this.showToast('對帳紀錄已新增。');
        this.resetRentForm();
        this.setView('rent-search');
        this.loadDashboard();
        this.loadRentPayments();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('新增對帳紀錄失敗。');
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
        this.showToast('對帳紀錄已更新。');
        this.cancelEditRentPayment();
        this.loadRentPayments();
        this.loadDashboard();
      },
      error: () => {
        this.saving.set(false);
        this.error.set('對帳紀錄更新失敗。');
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

  displayLeaseStatus(value: unknown): string {
    const status = value === null || value === undefined || value === '' ? '-' : String(value);
    return status === '綁約中' ? '續約中' : status;
  }

  displayCustomerLeaseStatus(value: unknown): string {
    return String(value) === '已解約' ? '已解約' : '續約中';
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

  private validContractStaff(form: ContractForm): boolean {
    if (form.signerStaffId !== null && form.signerStaffId === form.partnerStaffId) {
      this.error.set('簽約人與合作夥伴必須是不同職員。');
      return false;
    }
    return true;
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

  printMoney(value: unknown): string {
    const n = Number(value ?? 0);
    return `NT$${new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 0 }).format(n)}`;
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

  private isNarrowWindow(): boolean {
    return typeof window !== 'undefined' && window.innerWidth <= 1100;
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
      partnerStaffId:
        row['partner_staff_id'] === null || row['partner_staff_id'] === undefined ? null : Number(row['partner_staff_id']),
      sourceText: this.textValue(row['source_text']),
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
      partnerStaffId: form.partnerStaffId ?? null,
      sourceText: form.sourceText,
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
      contactBirthday: customer.contact_birthday ?? '',
      phone: customer.phone ?? '',
      forwardingAddress: customer.forwarding_address ?? '',
      pettyCash: customer.petty_cash === null || customer.petty_cash === undefined ? null : Number(customer.petty_cash),
      accountantInfo: customer.accountant_info ?? customer.referrer ?? '',
      accountInfo: customer.account_info ?? '',
      isAgent: customer.is_agent === true,
      relatedCompanyNames: [''],
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
      contactBirthday: form.contactBirthday,
      phone: form.phone,
      forwardingAddress: form.forwardingAddress,
      pettyCash: form.pettyCash,
      referrer: form.accountantInfo,
      accountantInfo: form.accountantInfo,
      accountInfo: form.accountInfo,
      isAgent: form.isAgent,
      relatedCompanyNames: form.relatedCompanyNames.map((name) => name.trim()).filter(Boolean),
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

  private hasNewCustomerChanges(): boolean {
    const customer = this.newCustomerForm;
    const contract = this.newCustomerContractForm;
    const contractDefaults = emptyContractForm();

    return Boolean(
      customer.companyName.trim() ||
        customer.taxId.trim() ||
        customer.ownerName.trim() ||
        customer.ownerBirthday ||
        customer.contactPerson.trim() ||
        customer.contactBirthday ||
        customer.phone.trim() ||
        customer.forwardingAddress.trim() ||
        customer.pettyCash !== null ||
        customer.accountantInfo.trim() ||
        customer.accountInfo.trim() ||
        customer.isAgent ||
        customer.relatedCompanyNames.some((name) => name.trim()) ||
        customer.notes.trim() ||
        contract.officeId !== null ||
        contract.rentalItem !== contractDefaults.rentalItem ||
        contract.rentalStatus !== contractDefaults.rentalStatus ||
        contract.signedDateText ||
        (contract.signerStaffId !== null && contract.signerStaffId !== this.currentStaffId()) ||
        contract.partnerStaffId !== null ||
        contract.sourceText.trim() ||
        contract.paymentMonths !== null ||
        contract.startDateText ||
        contract.endDateText ||
        contract.terminationDateText ||
        contract.rent !== null ||
        contract.deposit !== null ||
        contract.leaseStatus !== contractDefaults.leaseStatus ||
        this.newCustomerFirstPaymentAmount !== null ||
        this.newCustomerFirstPaymentDateText ||
        this.newCustomerLeaseImage !== null
    );
  }

  private resetNewCustomerFlow(): void {
    this.newCustomerForm = emptyCustomerForm();
    this.newCustomerContractForm = {
      ...emptyContractForm(),
      updatedBy: this.currentStaffId(),
      signerStaffId: this.currentStaffId()
    };
    this.newCustomerLeaseImage = null;
    this.newCustomerFirstPaymentAmount = null;
    this.newCustomerFirstPaymentDateText = '';
    this.newCustomerOptions.set([]);
    this.newCustomerFieldErrors.set({});
    this.customerNewStep.set(1);
  }

  monthNumberToInputValue(value: number | null | undefined): string {
    if (!value) {
      return '';
    }
    const text = String(value);
    return /^\d{6}$/.test(text) ? `${text.slice(0, 4)}-${text.slice(4, 6)}` : '';
  }

  monthInputValueToNumber(value: string): number {
    return value ? Number(value.replace('-', '')) : 0;
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
      '/targets': 'targets',
      '/bonus-rules': 'bonus-rules',
      '/performance-bonuses': 'performance-bonuses',
      '/tax-bureau-notices': 'tax-bureau-notices'
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
      targets: '/targets',
      'bonus-rules': '/bonus-rules',
      'performance-bonuses': '/performance-bonuses',
      'tax-bureau-notices': '/tax-bureau-notices'
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
