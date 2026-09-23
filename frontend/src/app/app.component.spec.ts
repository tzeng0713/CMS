import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
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

  it('opens the password recovery form from login', () => {
    component.openForgotPassword();

    expect(component.authMode()).toBe('forgot');
  });

  it('blocks a password reset when the new passwords do not match', () => {
    component.resetPasswordForm = {
      token: 'reset-token',
      password: 'new-password',
      confirmPassword: 'different-password'
    };

    component.completePasswordReset();

    expect(component.error()).toBe('兩次輸入的新密碼不一致。');
    expect(component.authBusy()).toBeFalse();
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

  it('keeps registration validation beside the specific invalid field', () => {
    component.registerForm = {
      account: '',
      password: 'short',
      staffName: '',
      email: 'not-an-email'
    };

    component.register();

    expect(component.registrationFieldErrors()).toEqual({
      account: '請輸入帳號。',
      password: '密碼至少需要 8 個字元。',
      staffName: '請輸入職員名稱。',
      email: '請輸入有效的信箱格式。'
    });
    expect(component.error()).toBe('');
  });

  it('maps duplicate registration responses to their matching field', () => {
    const api = {
      register: () => throwError(() => new HttpErrorResponse({
        status: 409,
        error: { fieldErrors: { account: '此帳號已被使用，請改用其他帳號。' } }
      }))
    } as unknown as CmsApiService;
    const registrationComponent = new AppComponent(api, {} as Router);
    registrationComponent.registerForm = {
      account: 'already-used',
      password: 'secret123',
      staffName: '測試職員',
      email: 'new-account@cms.test'
    };

    registrationComponent.register();

    expect(registrationComponent.registrationFieldErrors()).toEqual({
      account: '此帳號已被使用，請改用其他帳號。'
    });
    expect(registrationComponent.error()).toBe('');
  });

  it('clears stale new customer validation when re-entering the page', () => {
    spyOn(component, 'loadActiveViewData');
    component.activeView.set('customer-search');
    component.newCustomerFieldErrors.set({ companyName: '公司名稱為必填。' });

    const activateView = (component as unknown as { activateView: (view: 'customer-new') => void }).activateView;
    activateView.call(component, 'customer-new');

    expect(component.newCustomerFieldErrors()).toEqual({});
  });

  it('stores operation feedback as a toast', () => {
    component.showToast('新增成功');

    expect(component.toast()).toEqual({ message: '新增成功', kind: 'success' });
    expect(component.success()).toBe('');
  });

  it('places operation toasts in the lower-right corner', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([{ path: 'home', component: AppComponent }]), { provide: CmsApiService, useValue: { dashboard: () => of(testDashboard()) } }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(testUser());
    view.showToast('儲存成功');
    fixture.detectChanges();

    const container = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.toast-container');
    expect(getComputedStyle(container!).right).toBe('24px');
    expect(getComputedStyle(container!).bottom).toBe('24px');
    fixture.destroy();
  });

  it('animates a toast when it appears', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([{ path: 'home', component: AppComponent }]), { provide: CmsApiService, useValue: { dashboard: () => of(testDashboard()) } }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(testUser());
    view.showToast('儲存成功');
    fixture.detectChanges();

    const toast = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.toast');
    expect(getComputedStyle(toast!).animationName).toContain('toast-enter');
    fixture.destroy();
  });

  it('plays the toast exit state before removing the message', fakeAsync(() => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([{ path: 'home', component: AppComponent }]), { provide: CmsApiService, useValue: { dashboard: () => of(testDashboard()) } }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(testUser());
    view.showToast('儲存成功');
    fixture.detectChanges();

    tick(3400);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.toast')?.classList.contains('toast-leaving')).toBeTrue();

    tick(240);
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('.toast')).toBeNull();
    fixture.destroy();
  }));

  it('renders branded login controls with both account recovery and account request actions', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: {} }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(null);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const logo = page.querySelector<HTMLImageElement>('.auth-logo');
    const passwordToggle = page.querySelector<HTMLButtonElement>('button[aria-label="顯示密碼"]');
    const supportActions = Array.from(page.querySelectorAll<HTMLButtonElement>('.auth-support-action'));

    expect(logo?.getAttribute('src')).toBe('assets/LOGO1.jpg');
    expect(passwordToggle).not.toBeNull();
    expect(supportActions.map((button) => button.textContent?.trim())).toEqual(['申請帳號', '忘記密碼']);
    fixture.destroy();
  });

  it('uses a stable centered layout for every authentication screen', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: {} }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(null);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const authPage = page.querySelector<HTMLElement>('.auth-page');
    const authCard = page.querySelector<HTMLElement>('.auth-card');

    expect(getComputedStyle(authPage!).getPropertyValue('scrollbar-gutter')).toBe('stable both-edges');
    expect(getComputedStyle(authCard!).justifySelf).toBe('center');
    expect(getComputedStyle(authCard!).alignSelf).toBe('center');
    fixture.destroy();
  });

  it('renders a field-level registration error below the invalid input', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: {} }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(null);
    view.authMode.set('register');
    fixture.detectChanges();

    view.register();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const accountInput = page.querySelector<HTMLInputElement>('#registerAccount');
    expect(accountInput?.classList.contains('field-invalid')).toBeTrue();
    expect(page.querySelector('#registerAccountError')?.textContent?.trim()).toBe('請輸入帳號。');
    fixture.destroy();
  });

  it('switches the login password between masked and visible text', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: {} }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(null);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const passwordInput = page.querySelector<HTMLInputElement>('input[name="loginPassword"]');
    const passwordToggle = page.querySelector<HTMLButtonElement>('button[aria-label="顯示密碼"]');
    passwordToggle!.click();
    fixture.detectChanges();

    expect(passwordInput?.type).toBe('text');
    expect(page.querySelector('button[aria-label="隱藏密碼"]')).not.toBeNull();
    fixture.destroy();
  });

  it('opens the account assistance panel from the forgot-password action', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: {} }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(null);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const forgotPassword = Array.from(page.querySelectorAll<HTMLButtonElement>('.auth-support-action'))
      .find((button) => button.textContent?.trim() === '忘記密碼');
    forgotPassword!.click();
    fixture.detectChanges();

    expect(page.querySelector('.auth-help-panel')?.textContent).toContain('輸入帳號或信箱後，我們會寄送重設連結。');
    expect(page.querySelector('button.auth-back-action')?.textContent?.trim()).toBe('返回登入');
    fixture.destroy();
  });

  it('keeps only the logo on account support flows', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: {} }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(null);
    view.authMode.set('forgot');
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.auth-logo')).not.toBeNull();
    expect(page.querySelector('.auth-eyebrow')).toBeNull();
    expect(page.querySelector('#auth-heading')).toBeNull();
    expect(page.querySelector('.auth-card')?.getAttribute('aria-labelledby')).toBeNull();
    fixture.destroy();
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

  it('shows an import preview on the new reconciliation page and blocks confirmation when a row has an error', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: { dashboard: () => of(testDashboard()) } }]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance as unknown as {
      currentUser: { set(value: AuthUser): void };
      activeView: { set(value: 'rent-new'): void };
      rentPaymentImportPreview: { set(value: unknown): void };
    };
    view.currentUser.set(testUser());
    view.activeView.set('rent-new');
    view.rentPaymentImportPreview.set({
      fileName: '九月對帳.xlsx',
      totalRows: 2,
      validRows: 1,
      errorRows: 1,
      rows: [
        {
          rowNumber: 2,
          companyName: '測試客戶有限公司',
          paymentMonth: '2026-09',
          paymentDateText: '2026-09-05',
          feeStartDateText: '',
          feeEndDateText: '',
          amount: '12000',
          receiptNo: 'R-001',
          note: '',
          valid: true,
          errors: []
        },
        {
          rowNumber: 3,
          companyName: '不存在的客戶',
          paymentMonth: '2026-09',
          paymentDateText: '2026-09-05',
          feeStartDateText: '',
          feeEndDateText: '',
          amount: '8000',
          receiptNo: 'R-002',
          note: '',
          valid: false,
          errors: ['找不到客戶']
        }
      ]
    });
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.rent-payment-import-summary')?.textContent).toContain('需修正 1 筆');
    expect(page.querySelector('.rent-payment-import-table')?.textContent).toContain('找不到客戶');
    const submit = page.querySelector<HTMLButtonElement>('.rent-payment-import-actions .customer-search-submit');
    expect(submit?.textContent?.trim()).toBe('新增 1 筆對帳');
    expect(submit?.disabled).toBeTrue();
    fixture.destroy();
  });

  it('clears the imported reconciliation preview when the user discards the current file', () => {
    const importUi = component as unknown as {
      rentPaymentImportPreview: { set(value: unknown): void; (): unknown };
      clearRentPaymentImport(): void;
    };
    importUi.rentPaymentImportPreview.set({ fileName: '九月對帳.xlsx', rows: [] });

    importUi.clearRentPaymentImport();

    expect(importUi.rentPaymentImportPreview()).toBeNull();
  });

  it('clears an imported reconciliation preview when re-entering the new reconciliation page', () => {
    const importUi = component as unknown as {
      activeView: { set(value: 'rent-search'): void };
      rentPaymentImportPreview: { set(value: unknown): void; (): unknown };
      loadActiveViewData(): void;
      activateView(view: 'rent-new'): void;
    };
    spyOn(importUi, 'loadActiveViewData');
    importUi.activeView.set('rent-search');
    importUi.rentPaymentImportPreview.set({ fileName: '九月對帳.xlsx', rows: [] });

    importUi.activateView('rent-new');

    expect(importUi.rentPaymentImportPreview()).toBeNull();
  });

  it('submits a valid imported preview as one batch then returns to reconciliation search', () => {
    const preview = {
      fileName: '九月對帳.xlsx',
      totalRows: 1,
      validRows: 1,
      errorRows: 0,
      rows: [{
        rowNumber: 2,
        companyName: '測試客戶有限公司',
        paymentMonth: '2026-09',
        paymentDateText: '2026-09-05',
        feeStartDateText: '',
        feeEndDateText: '',
        amount: '12000',
        receiptNo: 'R-001',
        note: '',
        valid: true,
        errors: []
      }]
    };
    const importApi = component as unknown as {
      api: { importRentPayments: (payload: unknown) => ReturnType<typeof of> };
      rentPaymentImportPreview: { set(value: unknown): void; (): unknown };
    };
    const importSpy = jasmine.createSpy('importRentPayments').and.returnValue(of({ createdCount: 1 }));
    importApi.api.importRentPayments = importSpy;
    component.currentUser.set(testUser());
    importApi.rentPaymentImportPreview.set(preview);
    spyOn(component, 'loadDashboard');
    spyOn(component, 'loadRentPayments');

    component.confirmRentPaymentImport();

    expect(importSpy).toHaveBeenCalledWith({ rows: preview.rows, updatedBy: 1 });
    expect(importApi.rentPaymentImportPreview()).toBeNull();
    expect(navigations).toEqual(['/rent-payments']);
    expect(component.toast()?.message).toBe('已新增 1 筆對帳資料。');
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

  it('uses the new-page titles and English subtitles in the shared top header', () => {
    const header = component as unknown as { pageSubtitle: () => string };

    component.activeView.set('contract-new');
    expect(component.pageTitle()).toBe('續約租約');
    expect(header.pageSubtitle()).toBe('Renew Contract');

    component.activeView.set('rent-new');
    expect(component.pageTitle()).toBe('新增對帳');
    expect(header.pageSubtitle()).toBe('New Reconciliation');
  });

  it('renders the new customer English heading', () => {
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
    expect(page.querySelector('.customer-new-eyebrow')?.textContent?.trim()).toBe('New Customer');
    fixture.destroy();
  });

  it('keeps only the step navigation actions in the new customer footer', () => {
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
    view.activeView.set('customer-new');
    fixture.detectChanges();

    const footer = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.customer-new-actions');
    expect(footer).not.toBeNull();
    expect(getComputedStyle(footer!).justifyContent).toBe('flex-end');
    expect(footer?.textContent).not.toContain('取消');
    expect(footer?.textContent).not.toContain('儲存草稿');
    expect(footer?.querySelector('.next-step-button')).not.toBeNull();
    fixture.destroy();
  });

  it('labels the customer table action column as 動作 while keeping row actions as 查看', () => {
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
    view.activeView.set('customer-search');
    view.customers.set([
      {
        customer_id: 1,
        company_name: '測試客戶有限公司',
        owner_name: '王小明',
        rental_item: '登記',
        lease_status: '續約中',
        office_no: null
      } as never
    ]);
    view.customerTotal.set(1);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const header = page.querySelector<HTMLElement>('.customer-table .simple-row.header');
    const rowAction = page.querySelector<HTMLButtonElement>('.customer-table .simple-row:not(.header) button');
    expect(header?.textContent).toContain('動作');
    expect(header?.textContent).not.toContain('查看');
    expect(rowAction?.textContent?.trim()).toBe('查看');
    fixture.destroy();
  });

  it('keeps the tax ID input aligned with the company input when its validation message is visible', () => {
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
    view.activeView.set('customer-new');
    view.newCustomerFieldErrors.set({ companyName: '公司名稱為必填。' });
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const companyInput = page.querySelector<HTMLInputElement>('input[name="newCompanyName"]');
    const taxIdInput = page.querySelector<HTMLInputElement>('input[name="newTaxId"]');

    expect(companyInput).not.toBeNull();
    expect(taxIdInput).not.toBeNull();
    expect(taxIdInput!.getBoundingClientRect().top).toBe(companyInput!.getBoundingClientRect().top);
    fixture.destroy();
  });

  it('uses a styled lease-file picker and shows the chosen filename', () => {
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
    view.activeView.set('customer-new');
    view.customerNewStep.set(2);
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const fileInput = page.querySelector<HTMLInputElement>('input[name="newCustomerContractImage"]');
    const filePickerButton = page.querySelector<HTMLLabelElement>('.file-upload-button');
    const fileName = page.querySelector<HTMLElement>('.file-upload-name');

    expect(fileInput).not.toBeNull();
    expect(filePickerButton?.htmlFor).toBe(fileInput!.id);
    expect(getComputedStyle(filePickerButton!).backgroundColor).toBe('rgb(255, 255, 255)');
    expect(fileName?.textContent?.trim()).toBe('尚未選擇檔案');

    const selectedFile = new File(['lease'], 'UAT-lease.pdf', { type: 'application/pdf' });
    Object.defineProperty(fileInput!, 'files', { value: [selectedFile] });
    fileInput!.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(fileName?.textContent?.trim()).toBe('UAT-lease.pdf');
    fixture.destroy();
  });

  it('uses the staff title and customer-style paging controls on the staff overview', () => {
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
    view.activeView.set('staff-overview');
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const heading = page.querySelector<HTMLElement>('.page-heading');

    expect(heading?.querySelector('p')?.textContent?.trim()).toBe('Staff Management');
    expect(heading?.querySelector('h1')?.textContent?.trim()).toBe('職員總覽');
    expect(page.querySelector('.staff-search-panel')).not.toBeNull();
    expect(page.querySelector('select[name="staffPageSize"]')).not.toBeNull();
    expect(page.querySelector('[aria-label="職員搜尋結果分頁"]')).not.toBeNull();
    fixture.destroy();
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

  it('presents contract editing in a spaced neutral surface', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([{ path: 'home', component: AppComponent }]),
        {
          provide: CmsApiService,
          useValue: {
            dashboard: () => of(testDashboard()),
            contracts: () => of({ content: [], totalElements: 0, page: 0, pageSize: 20 }),
            offices: () => of([])
          }
        }
      ]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(testUser());
    view.activeView.set('contract-search');
    view.editingContract.set({
      contract_id: 12,
      company_name: '青禾品牌設計有限公司',
      office_no: 'A-201',
      payment_months: 12,
      start_date_text: '2026-01-01',
      end_date_text: '2026-12-31',
      termination_date_text: '-',
      rent: 2500,
      deposit: 2500
    });
    fixture.detectChanges();

    const editPanel = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.rent-edit-panel');
    expect(editPanel?.classList.contains('contract-edit-panel')).toBeTrue();
    expect(editPanel?.querySelector('h3')?.textContent?.trim()).toBe('青禾品牌設計有限公司');
    expect(editPanel?.querySelector('.panel-header p')).toBeNull();
    expect(getComputedStyle(editPanel!).marginTop).toBe('24px');
    expect(getComputedStyle(editPanel!).backgroundColor).toBe('rgb(255, 255, 255)');
    expect(getComputedStyle(editPanel!).boxShadow).toBe('none');
    fixture.destroy();
  });

  it('presents rent payment editing in a spaced neutral surface', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([{ path: 'home', component: AppComponent }]),
        {
          provide: CmsApiService,
          useValue: {
            dashboard: () => of(testDashboard()),
            rentPayments: () => of({ content: [], totalElements: 0, page: 0, pageSize: 20 })
          }
        }
      ]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(testUser());
    view.activeView.set('rent-search');
    view.editingRentPayment.set({
      rent_payment_id: 18,
      company_name: '日心傢飾設計有限公司',
      payment_month: 6,
      payment_date_text: '2026-06-19'
    });
    fixture.detectChanges();

    const editPanel = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('.rent-payment-edit-panel');
    expect(editPanel).not.toBeNull();
    expect(editPanel?.classList.contains('contract-edit-panel')).toBeTrue();
    expect(editPanel?.querySelector('.panel-header h3')?.textContent?.trim()).toBe('日心傢飾設計有限公司');
    expect(getComputedStyle(editPanel!).marginTop).toBe('24px');
    expect(getComputedStyle(editPanel!).backgroundColor).toBe('rgb(255, 255, 255)');
    expect(getComputedStyle(editPanel!).boxShadow).toBe('none');
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
      email: 'admin@cms.test',
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
      canReviewRefund: true,
      canManageBonusRules: true,
      canManageBackup: true
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

  it('uses white text for home notification count badges', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([]), { provide: CmsApiService, useValue: { dashboard: () => of(testDashboard()) } }]
    }).createComponent(AppComponent);
    fixture.componentInstance.currentUser.set(testUser());
    fixture.componentInstance.activeView.set('home');
    fixture.componentInstance.dashboard.set(testDashboard());
    fixture.detectChanges();

    const badge = fixture.nativeElement.querySelector('.notification-heading strong') as HTMLElement;
    expect(getComputedStyle(badge).color).toBe('rgb(255, 255, 255)');
    fixture.destroy();
  });

  it('groups customer detail and edit fields into the new-customer style sections', () => {
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
    view.activeView.set('customer-detail');
    view.selectedCustomer.set({
      customer_id: 5,
      company_name: '測試客戶有限公司',
      owner_name: '王小明',
      branch_name: null,
      office_no: null,
      lease_status: '綁約中',
      rental_status: '登記',
      contracts: [],
      rentPayments: []
    } as never);
    fixture.detectChanges();

    let page = fixture.nativeElement as HTMLElement;
    expect(page.querySelectorAll('.customer-detail-readonly .customer-form-section').length).toBe(2);

    view.editingCustomer.set(true);
    fixture.detectChanges();
    page = fixture.nativeElement as HTMLElement;
    expect(page.querySelectorAll('.customer-detail-edit-form .customer-form-section').length).toBe(2);
    expect(page.querySelector('.customer-detail-edit-form .checkbox-label.wide')).not.toBeNull();
    fixture.destroy();
  });

  it('keeps renewal submission in the company-name validation state until a customer is selected', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        {
          provide: CmsApiService,
          useValue: {
            dashboard: () => of(testDashboard()),
            offices: () => of([]),
            metadata: () => of({ branches: [], roles: [], staff: [] }),
            customerLookup: () => of([])
          }
        }
      ]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(testUser());
    view.activeView.set('contract-new');
    fixture.detectChanges();

    view.createContract();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const companyInput = page.querySelector<HTMLInputElement>('input[name="contractCustomerSearch"]');
    expect(companyInput?.classList.contains('field-invalid')).toBeTrue();
    expect(page.querySelector('.contract-customer-combobox .field-error')?.textContent?.trim())
      .toBe('公司名稱為必填。');

    companyInput!.value = '測試客戶';
    companyInput!.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(page.querySelector('.contract-customer-combobox .field-error')).toBeNull();

    view.newContractForm.customerId = 1;
    companyInput!.value = '';
    companyInput!.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(view.newContractForm.customerId).toBeNull();

    view.createContract();
    fixture.detectChanges();
    expect(page.querySelector('.contract-customer-combobox .field-error')?.textContent?.trim())
      .toBe('公司名稱為必填。');
    fixture.destroy();
  });

  it('renders renewal customer lookup as a searchable dropdown control', () => {
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
    view.activeView.set('contract-new');
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    expect(page.querySelector('.contract-customer-combobox')).not.toBeNull();
    expect(page.querySelector('button[aria-label="展開客戶選單"]')).not.toBeNull();
    fixture.destroy();
  });

  it('centers the renewal submit action within the form panel', () => {
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
    view.activeView.set('contract-new');
    fixture.detectChanges();

    const actions = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('form.customer-form .form-actions.wide');
    expect(actions).not.toBeNull();
    expect(getComputedStyle(actions!).justifyContent).toBe('center');
    fixture.destroy();
  });

  it('uses the customer-search button style for the renewal confirmation action', () => {
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
    view.activeView.set('contract-new');
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.contract-form-actions button');
    expect(button?.textContent?.trim()).toBe('確認');
    expect(button?.classList.contains('customer-search-submit')).toBeTrue();
    expect(getComputedStyle(button!).minWidth).toBe('96px');
    expect(getComputedStyle(button!).height).toBe('44px');
    fixture.destroy();
  });

  it('toggles and dismisses the renewal customer dropdown', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        {
          provide: CmsApiService,
          useValue: {
            dashboard: () => of(testDashboard()),
            offices: () => of([]),
            metadata: () => of({ branches: [], roles: [], staff: [] }),
            customerLookup: () => of([])
          }
        }
      ]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(testUser());
    view.activeView.set('contract-new');
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const trigger = page.querySelector<HTMLButtonElement>('button[aria-label="展開客戶選單"]');
    trigger!.click();
    fixture.detectChanges();
    expect(view.contractCustomerDropdownOpen()).toBeTrue();

    trigger!.click();
    fixture.detectChanges();
    expect(view.contractCustomerDropdownOpen()).toBeFalse();

    view.openContractCustomerDropdown();
    fixture.detectChanges();
    document.body.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    fixture.detectChanges();
    expect(view.contractCustomerDropdownOpen()).toBeFalse();
    fixture.destroy();
  });

  it('shows only company names in renewal customer options', () => {
    const fixture = TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([]),
        {
          provide: CmsApiService,
          useValue: {
            dashboard: () => of(testDashboard()),
            offices: () => of([]),
            metadata: () => of({ branches: [], roles: [], staff: [] }),
            customerLookup: () => of([
              { customer_id: 1, company_name: '測試客戶有限公司', owner_name: '王小明' }
            ])
          }
        }
      ]
    }).createComponent(AppComponent);
    const view = fixture.componentInstance;
    view.currentUser.set(testUser());
    view.activeView.set('contract-new');
    fixture.detectChanges();

    view.openContractCustomerDropdown();
    fixture.detectChanges();

    const page = fixture.nativeElement as HTMLElement;
    const option = page.querySelector<HTMLButtonElement>('#contract-customer-options button');
    expect(option?.textContent?.trim()).toBe('測試客戶有限公司');
    expect(option?.textContent).not.toContain('負責人');
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
    email: 'test@cms.test',
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
    canReviewRefund: true,
    canManageBonusRules: true,
    canManageBackup: true
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
