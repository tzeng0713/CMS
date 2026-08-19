import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthUser, CmsApiService, Dashboard } from './core/cms-api.service';
import { AppComponent } from './app.component';

describe('new customer flow', () => {
  let navigations: string[];
  let component: AppComponent;

  beforeEach(() => {
    navigations = [];
    const router = {
      navigateByUrl: (url: string) => {
        navigations.push(url);
        return Promise.resolve(true);
      }
    } as unknown as Router;

    const api = {
      customers: () => of({ content: [], totalElements: 0, page: 0, pageSize: 20 })
    } as unknown as CmsApiService;

    component = new AppComponent(api, router);
  });

  it('moves to the contract step without clearing customer entries', () => {
    component.newCustomerForm.companyName = '測試客戶有限公司';

    component.goToNewCustomerStep(2);

    expect(component.customerNewStep()).toBe(2);
    expect(component.newCustomerForm.companyName).toBe('測試客戶有限公司');
  });

  it('discards an unsaved customer flow and returns to the customer list after confirmation', () => {
    component.newCustomerForm.companyName = '測試客戶有限公司';
    component.newCustomerContractForm.rent = 12000;
    component.goToNewCustomerStep(2);
    spyOn(window, 'confirm').and.returnValue(true);

    component.cancelNewCustomer();

    expect(component.newCustomerForm.companyName).toBe('');
    expect(component.newCustomerContractForm.rent).toBeNull();
    expect(component.customerNewStep()).toBe(1);
    expect(navigations).toEqual(['/customers']);
  });

  it('keeps entries in place when unsaved cancellation is declined', () => {
    component.newCustomerForm.companyName = '測試客戶有限公司';
    spyOn(window, 'confirm').and.returnValue(false);

    component.cancelNewCustomer();

    expect(component.newCustomerForm.companyName).toBe('測試客戶有限公司');
    expect(navigations).toEqual([]);
  });

  it('keeps required company validation beside the new customer field', () => {
    component.createCustomer();

    expect(component.newCustomerFieldErrors()['companyName']).toBe('公司名稱為必填。');
    expect(component.error()).toBe('');
  });

  it('clears the company field error when the user edits the field', () => {
    component.newCustomerFieldErrors.set({ companyName: '公司名稱為必填。' });

    component.clearNewCustomerFieldError('companyName');

    expect(component.newCustomerFieldErrors()).toEqual({});
  });

  it('stores operation feedback as a toast', () => {
    component.showToast('新增成功');

    expect(component.toast()).toEqual({ message: '新增成功', kind: 'success' });
    expect(component.success()).toBe('');
  });

  it('clears the selected customer before returning to the customer overview', () => {
    component.selectedCustomer.set({ customer_id: 6 } as never);

    component.backToCustomerList();

    expect(component.selectedCustomer()).toBeNull();
    expect(navigations).toEqual(['/customers']);
  });

  it('shows renewal wording for the stored bound-contract status', () => {
    expect(component.displayLeaseStatus('綁約中')).toBe('續約中');
    expect(component.displayLeaseStatus('已解約')).toBe('已解約');
  });

  it('clears the contract tax id together with the other contract filters', () => {
    const filters = component.contractFilters as ContractFiltersWithTaxId;
    filters.taxId = 'TEST-TAX-ID';
    spyOn(component, 'loadContracts');

    component.resetContractFilters();

    expect((component.contractFilters as ContractFiltersWithTaxId).taxId).toBe('');
  });

  it('initializes reconciliation filters as empty criteria', () => {
    const rentSearchUi = component as unknown as {
      rentPaymentFilters: RentPaymentFiltersWithFields;
    };

    expect(rentSearchUi.rentPaymentFilters).toEqual({
      companyName: '',
      taxId: '',
      paymentDateStartText: '',
      paymentDateEndText: ''
    });
  });

  it('clears all reconciliation filters and reloads results', () => {
    const rentSearchUi = component as unknown as {
      rentPaymentFilters: RentPaymentFiltersWithFields;
      resetRentPaymentFilters: () => void;
    };
    rentSearchUi.rentPaymentFilters.companyName = 'AFW';
    rentSearchUi.rentPaymentFilters.taxId = '12345678';
    rentSearchUi.rentPaymentFilters.paymentDateStartText = '2026-08-01';
    rentSearchUi.rentPaymentFilters.paymentDateEndText = '2026-08-31';
    spyOn(component, 'loadRentPayments');

    rentSearchUi.resetRentPaymentFilters();

    expect(rentSearchUi.rentPaymentFilters).toEqual({
      companyName: '',
      taxId: '',
      paymentDateStartText: '',
      paymentDateEndText: ''
    });
    expect(component.loadRentPayments).toHaveBeenCalled();
  });

  it('reduces non-terminated lease statuses to renewal wording', () => {
    const customerSearchStatus = component as unknown as {
      displayCustomerLeaseStatus: (status: unknown) => string;
    };

    expect(customerSearchStatus.displayCustomerLeaseStatus('有效中')).toBe('續約中');
  });

  it('toggles advanced customer filters without clearing the query', () => {
    const customerSearchUi = component as unknown as {
      advancedFiltersOpen: () => boolean;
      toggleAdvancedFilters: () => void;
    };
    component.customerFilters.companyName = 'AFW';

    customerSearchUi.toggleAdvancedFilters();

    expect(customerSearchUi.advancedFiltersOpen()).toBeFalse();
    expect(component.customerFilters.companyName).toBe('AFW');
  });

  it('resets to the first customer page when page size changes', () => {
    const customerSearchUi = component as unknown as {
      customerPageSize: () => number;
      changeCustomerPageSize: (size: number) => void;
    };
    component.customerPage.set(2);

    customerSearchUi.changeCustomerPageSize(50);

    expect(customerSearchUi.customerPageSize()).toBe(50);
    expect(component.customerPage()).toBe(0);
  });

  it('resets the contract search to the first server page when submitting a new query', () => {
    const contractSearch = component as unknown as {
      contractPage: { (): number; set(value: number): void };
      searchContracts: () => void;
    };
    contractSearch.contractPage.set(2);
    spyOn(component, 'loadContracts');

    contractSearch.searchContracts();

    expect(contractSearch.contractPage()).toBe(0);
    expect(component.loadContracts).toHaveBeenCalled();
  });

  it('resets the reconciliation search to the first server page when clearing filters', () => {
    const reconciliationSearch = component as unknown as {
      rentPaymentPage: { (): number; set(value: number): void };
      resetRentPaymentFilters: () => void;
    };
    reconciliationSearch.rentPaymentPage.set(2);
    spyOn(component, 'loadRentPayments');

    reconciliationSearch.resetRentPaymentFilters();

    expect(reconciliationSearch.rentPaymentPage()).toBe(0);
    expect(component.loadRentPayments).toHaveBeenCalled();
  });

  it('uses the contract and reconciliation titles in the shared top header', () => {
    const header = component as unknown as { pageSubtitle: () => string };

    component.activeView.set('contract-search');
    expect(component.pageTitle()).toBe('查詢租約');
    expect(header.pageSubtitle()).toBe('Contract Search');

    component.activeView.set('rent-search');
    expect(component.pageTitle()).toBe('查詢對帳');
    expect(header.pageSubtitle()).toBe('Reconciliation Search');
  });

  it('renders contract and reconciliation pager arrows as customer-style outline icon buttons', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        {
          provide: CmsApiService,
          useValue: {
            dashboard: () => of(testDashboard()),
            offices: () => of([]),
            metadata: () => of({ branches: [], roles: [], staff: [] })
          }
        }
      ]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;

    view.currentUser.set(testUser());
    fixture.detectChanges();

    view.contractTotal.set(21);
    view.activeView.set('contract-search');
    fixture.detectChanges();
    expectPagerIconButtons(fixture.nativeElement as HTMLElement);

    view.rentPaymentTotal.set(21);
    view.activeView.set('rent-search');
    fixture.detectChanges();
    expectPagerIconButtons(fixture.nativeElement as HTMLElement);
    fixture.destroy();
  });

  it('renders reconciliation payment dates as a concise date range', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: { dashboard: () => of(testDashboard()) } }]
    }).createComponent(AppComponent);
    fixture.componentInstance.currentUser.set(testUser());
    fixture.detectChanges();
    fixture.componentInstance.activeView.set('rent-search');
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const dateRange = page.querySelector<HTMLElement>('.compact-date-range');
    expect(dateRange?.textContent).toContain('繳款日期區間');
    expect(dateRange?.textContent).not.toContain('起始日期');
    expect(dateRange?.textContent).not.toContain('結束日期');
    expect(dateRange?.querySelector('.compact-date-separator')?.textContent).toBe('～');
    expect(dateRange?.querySelectorAll('input[type="date"]').length).toBe(2);

    const dateLegend = dateRange?.querySelector<HTMLElement>('legend');
    expect(getComputedStyle(dateLegend!).top).toBe('-6px');
    fixture.destroy();
  });

  it('collapses the desktop sidebar and closes the mobile drawer after navigation', () => {
    const navigation = component as unknown as {
      isNarrowViewport: { set(value: boolean): void };
      sidebarOpen: () => boolean;
      toggleSidebar: () => void;
      closeSidebarAfterNavigation: () => void;
    };

    navigation.isNarrowViewport.set(false);
    expect(navigation.sidebarOpen()).toBeTrue();
    navigation.toggleSidebar();
    expect(navigation.sidebarOpen()).toBeFalse();

    navigation.isNarrowViewport.set(true);
    expect(navigation.sidebarOpen()).toBeFalse();
    navigation.toggleSidebar();
    expect(navigation.sidebarOpen()).toBeTrue();
    navigation.closeSidebarAfterNavigation();
    expect(navigation.sidebarOpen()).toBeFalse();
  });

  it('groups the sidebar trigger with signed-in actions in a dedicated header bar', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: { dashboard: () => of(testDashboard()) } }]
    }).createComponent(AppComponent);
    fixture.componentInstance.currentUser.set(testUser());
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const header = page.querySelector<HTMLElement>('.topbar.topbar-surface');
    expect(header).not.toBeNull();
    expect(header!.querySelector('.sidebar-toggle')).not.toBeNull();
    expect(header!.querySelector('.topbar-actions .user-chip')).not.toBeNull();
    expect(header!.querySelector('.topbar-actions button')).not.toBeNull();
    fixture.destroy();
  });

  it('uses the shared header trigger instead of a duplicate menu button on the new customer page', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        {
          provide: CmsApiService,
          useValue: {
            dashboard: () => of(testDashboard()),
            offices: () => of([]),
            metadata: () => of({ branches: [], roles: [], staff: [] })
          }
        }
      ]
    }).createComponent(AppComponent);
    fixture.componentInstance.currentUser.set(testUser());
    fixture.componentInstance.activeView.set('customer-new');
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelectorAll('.sidebar-toggle').length).toBe(1);
    fixture.destroy();
  });

  it('renders the full dashboard when the user returns home', async () => {
    const dashboard: Dashboard = {
      customers: 99,
      activeContracts: 16,
      officeCustomers: 16,
      registrationCustomers: 83,
      rentPayments: 0,
      refunds: 0,
      monthlyRentAmount: 0,
      latestPayments: [],
      notifications: {
        expiringContracts: [{ company_name: '測試到期客戶', end_date_text: '2026-08-31', rental_item: '辦公室' }],
        unpaidRent: [],
        incompleteContracts: []
      }
    };
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        { provide: CmsApiService, useValue: { dashboard: () => of(dashboard) } }
      ]
    }).createComponent(AppComponent);
    fixture.componentInstance.currentUser.set({
      staff_id: 1,
      staff_name: '系統主管',
      account: 'admin',
      branch_id: null,
      branch_name: null,
      role_permission_id: 1,
      role_name: '主管',
      scope: 'all',
      canCreateRent: true,
      canEditRent: true,
      canEditStaff: true,
      canCreateOffice: true,
      canEditAllBranches: true,
      canViewAllOffices: true,
      canManageBranch: true,
      canReviewRefund: true
    } satisfies AuthUser);
    fixture.componentInstance.dashboard.set(dashboard);

    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.textContent).toContain('Customer Operations');
    expect(page.textContent).toContain('客戶與租金作業總覽');
    expect(page.textContent).toContain('系統通知');
    expect(page.textContent).toContain('今日待辦提醒');
    expect(page.textContent).toContain('測試到期客戶');
    fixture.destroy();
  });

  it('requires renewal payment amount and date to be provided together', () => {
    component.newContractFirstPaymentAmount = 12000;

    expect(component.hasValidNewContractPayment()).toBeFalse();
    expect(component.error()).toBe('本次繳款金額與繳款日期必須一起填寫。');
  });
});

type ContractFiltersWithTaxId = {
  taxId: string;
};

type RentPaymentFiltersWithFields = {
  companyName: string;
  taxId: string;
  paymentDateStartText: string;
  paymentDateEndText: string;
};

function expectPagerIconButtons(page: HTMLElement): void {
  const previous = page.querySelector<HTMLButtonElement>('button[aria-label="上一頁"]');
  const next = page.querySelector<HTMLButtonElement>('button[aria-label="下一頁"]');

  expect(previous?.classList.contains('ghost')).toBeTrue();
  expect(previous?.classList.contains('compact-button')).toBeTrue();
  expect(previous?.classList.contains('pager-icon')).toBeTrue();
  expect(previous?.querySelector('svg')).not.toBeNull();
  expect(next?.classList.contains('ghost')).toBeTrue();
  expect(next?.classList.contains('compact-button')).toBeTrue();
  expect(next?.classList.contains('pager-icon')).toBeTrue();
  expect(next?.querySelector('svg')).not.toBeNull();
}

function testUser(): AuthUser {
  return {
    staff_id: 1,
    staff_name: '測試主管',
    account: 'test',
    branch_id: null,
    branch_name: null,
    role_permission_id: 1,
    role_name: '主管',
    scope: 'all',
    canCreateRent: true,
    canEditRent: true,
    canEditStaff: true,
    canCreateOffice: true,
    canEditAllBranches: true,
    canViewAllOffices: true,
    canManageBranch: true,
    canReviewRefund: true
  };
}

function testDashboard(): Dashboard {
  return {
    customers: 0,
    activeContracts: 0,
    officeCustomers: 0,
    registrationCustomers: 0,
    rentPayments: 0,
    refunds: 0,
    monthlyRentAmount: 0,
    latestPayments: [],
    notifications: { expiringContracts: [], unpaidRent: [], incompleteContracts: [] }
  };
}
