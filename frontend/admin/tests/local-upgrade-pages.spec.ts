import { test, expect, type Page } from '@playwright/test';

// Changed interaction boundaries only. These mocked transports are not real-role acceptance.
const store = { id:'1',campusId:'1',campusName:'测试校区',name:'测试店铺',enabled:true,open:true,accepting:true,hours:['00:00-24:00'],deliveryFee:'0.00',packagingFee:'0.00',version:'0' };
const courier = { id:'12',username:'courier-ui',name:'配送员甲',role:'DELIVERER',campusId:'1',storeId:null,status:1,mustChangePassword:false,acceptingTasks:true };
const delivery = { id:'123',number:'HANDOVER-123',status:4,version:'3',storeId:'1',campusId:'1' };
async function fixture(page: Page, user = false, anonymous = false) {
  await page.addInitScript(({ user, anonymous }) => {
    if (anonymous) { sessionStorage.removeItem('campus-user'); return; }
    sessionStorage.setItem(user?'campus-user':'campus-admin',JSON.stringify({id:'90',token:'local-upgrade-ui',name:'交互测试',role:user?'USER':'PLATFORM_ADMIN',mustChangePassword:false}));
    localStorage.setItem('campus-store','1');
  },{user,anonymous});
  await page.route('**/api/v1/**',route => {
    const path=new URL(route.request().url()).pathname;
    const data=path.endsWith('/stores/page')?{records:[store],nextCursor:null,hasMore:false}
      :path.endsWith('/campuses')?[{id:'1',name:'测试校区',enabled:true}]
      :path.endsWith('/messages/unread')?{count:'0'}
      :path.endsWith('/auth/capabilities')?{demoLoginEnabled:true,localLoginEnabled:true,wechatLoginEnabled:false,paymentMode:'MOCK',mode:'DEMO'}
      :path.endsWith('/auth/account')?{localAccount:true,username:'ui-user'}
      :path.endsWith('/page')?{records:[],nextCursor:null,hasMore:false}:[];
    return route.fulfill({json:{code:'OK',data}});
  });
}

test('platform stops new assignments and opens outstanding work before handover',async({page}) => {
  await fixture(page); let enabled=true; const writes:unknown[]=[];
  await page.route('**/api/v1/platform/staff/page*',route => route.fulfill({json:{code:'OK',data:{records:[{...courier,acceptingTasks:enabled}],nextCursor:null,hasMore:false}}}));
  await page.route('**/api/v1/platform/staff/12/dispatch-availability',route => {
    writes.push(route.request().postDataJSON());enabled=false;return route.fulfill({json:{code:'OK',data:null}});
  });
  await page.route('**/api/v1/platform/staff/12/active-deliveries/page*',route => route.fulfill({json:{code:'OK',data:{records:[delivery],nextCursor:null,hasMore:false}}}));
  await page.goto('/admin/operations/staff');
  await page.getByRole('button',{name:'停止接新单',exact:true}).click();
  await expect(page.getByRole('button',{name:'开启接新单',exact:true})).toBeVisible();
  expect(writes).toEqual([{enabled:false}]);
  await page.getByRole('button',{name:'未完成任务',exact:true}).click();
  await expect(page.getByText('HANDOVER-123',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'查看订单并交接',exact:true}).click();
  await expect(page).toHaveURL(/\/admin\/orders\?orderId=123$/);
});

test('emergency disable requires a reason and preserves active deliveries for recovery',async({page}) => {
  await fixture(page); let disabled=false; const writes:unknown[]=[];
  await page.route('**/api/v1/platform/staff/page*',route => route.fulfill({json:{code:'OK',data:{records:[{...courier,status:disabled?0:1}],nextCursor:null,hasMore:false}}}));
  await page.route('**/api/v1/platform/staff/12/emergency-disable',route => {
    writes.push(route.request().postDataJSON());disabled=true;return route.fulfill({json:{code:'OK',data:null}});
  });
  await page.route('**/api/v1/platform/staff/12/active-deliveries/page*',route => route.fulfill({json:{code:'OK',data:{records:[delivery],nextCursor:null,hasMore:false}}}));
  await page.goto('/admin/operations/staff');
  await page.getByRole('button',{name:'紧急停用',exact:true}).click();
  await expect(page.getByText('将立即撤销该配送员的会话并禁止新分配。在途任务保留并进入异常队列，请随后逐笔交接。',{exact:true})).toBeVisible();
  await page.getByPlaceholder('填写紧急停用原因').fill('测试离岗，交由平台改派');
  await page.getByRole('button',{name:'确认紧急停用',exact:true}).click();
  await expect(page.getByText('HANDOVER-123',{exact:true})).toBeVisible();
  expect(writes).toEqual([{reason:'测试离岗，交由平台改派'}]);
});

test('handover conflict keeps the staff edit form and does not retry disabling',async({page}) => {
  await fixture(page);let writes=0;
  await page.route('**/api/v1/platform/staff/page*',route => route.fulfill({json:{code:'OK',data:{records:[courier],nextCursor:null,hasMore:false}}}));
  await page.route('**/api/v1/platform/staff/12',route => {
    writes++;return route.fulfill({status:409,json:{code:'COURIER_HANDOVER_REQUIRED',message:'请先移交未完成任务'}});
  });
  await page.goto('/admin/operations/staff');
  await page.getByRole('button',{name:'编辑 / 重置密码',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'员工与权限',exact:true});
  await dialog.locator('.el-switch__core').click();
  await dialog.getByRole('button',{name:'保存',exact:true}).click();
  await expect(dialog.getByText('该配送员仍有未结束任务。请先完成交接，再保存人员变更；本次表单已保留。',{exact:true})).toBeVisible();
  await expect(dialog.getByRole('button',{name:'查看未完成任务',exact:true})).toBeVisible();
  expect(writes).toBe(1);
});

test('wallet load-more failure retains results and retries the same cursor',async({page}) => {
  await fixture(page,true);let fail=true;const cursors:(string|null)[]=[];
  const coupon=(id:string)=>({id,name:'分页券'+id,state:'AVAILABLE',storeId:null,kind:'REDUCTION',value:'100',thresholdCents:'0',endsAt:'2099-01-01T00:00:00'});
  await page.route('**/api/v1/user/wallet/page*',route => {
    const cursor=new URL(route.request().url()).searchParams.get('cursor');cursors.push(cursor);
    if(cursor&&fail){fail=false;return route.fulfill({status:503,json:{code:'UNAVAILABLE',message:'钱包暂时不可用'}});}
    return route.fulfill({json:{code:'OK',data:{records:[coupon(cursor?'2':'1')],nextCursor:cursor?null:'next-wallet',hasMore:!cursor}}});
  });
  await page.goto('/app/pages/account/index');
  await expect(page.getByText('分页券1',{exact:true})).toBeVisible();
  await page.getByText('加载更多优惠券',{exact:true}).click();
  await expect(page.getByText('钱包暂时不可用',{exact:true})).toBeVisible();
  await expect(page.getByText('分页券1',{exact:true})).toBeVisible();
  await page.getByText('加载更多优惠券',{exact:true}).click();
  await expect(page.getByText('分页券2',{exact:true})).toBeVisible();
  expect(cursors).toEqual([null,'next-wallet','next-wallet']);
});

test('local first login remains on password screen until password change and re-login',async({page}) => {
  await fixture(page,true,true);
  let changed=false,passwordWrites=0;
  await page.route('**/api/v1/user/auth/login',route=>route.fulfill({json:{code:'OK',data:{id:'90',token:changed?'renewed':'first-login',name:'独立用户',role:'USER',mustChangePassword:!changed}}}));
  await page.route('**/api/v1/user/auth/password',route=>{changed=true;passwordWrites++;return route.fulfill({json:{code:'OK',data:null}});});
  await page.goto('/app/pages/login/index');
  await page.locator('uni-input[aria-label="独立账号"] input').fill('ui-user');
  await page.locator('uni-input[aria-label="账号密码"] input').fill('Initial-Ui!2026');
  await page.getByText('登录独立账号',{exact:true}).click();
  await expect(page.getByText('首次登录，请修改初始密码',{exact:true})).toBeVisible();
  await expect(page).toHaveURL(/\/app\/pages\/login\/index$/);
  await page.locator('uni-input[aria-label="原密码"] input').fill('Initial-Ui!2026');
  await page.locator('uni-input[aria-label="新密码"] input').fill('Changed-Ui!2026');
  await page.locator('uni-input[aria-label="确认新密码"] input').fill('Changed-Ui!2026');
  await page.getByText('保存新密码',{exact:true}).click();
  await expect(page.getByText('登录独立账号',{exact:true})).toBeVisible();
  expect(passwordWrites).toBe(1);
  expect(await page.evaluate(()=>sessionStorage.getItem('campus-user'))).toBeNull();
});
