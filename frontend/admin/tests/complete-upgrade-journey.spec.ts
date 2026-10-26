import { randomUUID } from 'node:crypto';
import fs from 'node:fs/promises';
import { test, expect, call, password } from './helpers';
import type { Page, BrowserContext, Locator } from '@playwright/test';
import type { Session } from '../../contracts/models';
import type { Checkout, Order, CursorPage, Aftersale } from '../../contracts/platform';

test('complete upgrade: four real roles use independent identity, delivery safeguards, messages and simulated ledger', async ({ browser, request, adminSession }, testInfo) => {
  test.setTimeout(360000);
  expect(process.env.CI === 'true' || new URL(String(testInfo.project.use.baseURL)).port !== '18083').toBe(true);
  const suffix = randomUUID().slice(0,8), a = adminSession.token;
  const names = { campus:'完整升级校区-'+suffix,store:'完整升级餐厅-'+suffix,building:'原始楼栋-'+suffix,
    renamed:'更名楼栋-'+suffix,product:'升级体验餐-'+suffix,coupon:'升级体验券-'+suffix,user:'upgrade_'+suffix };
  const contexts: BrowserContext[] = [], pageErrors: { role:string; message:string }[] = [];
  const unexpectedHttp: {role:string;method:string;path:string;status:number;code:string}[] = [];
  const expectedDenials: {role:string;method:string;path:string;status:number;code:string}[] = [];
  const networkChecks: Promise<void>[] = [], checkpoints: {name:string;[key:string]:unknown}[] = [];
  const initialUserPassword='Upgrade-Initial!'+randomUUID(), changedUserPassword='Upgrade-Changed!'+randomUUID();
  const resetUserPassword='Upgrade-Reset!'+randomUUID(), renewedUserPassword='Upgrade-Renewed!'+randomUUID();
  let retiredUserToken:string|undefined;
  async function pageFor(role:string,mobile=false) {
    const context=await browser.newContext({baseURL:testInfo.project.use.baseURL,
      viewport:mobile?{width:390,height:844}:{width:1440,height:1000},timezoneId:'Asia/Shanghai'});
    contexts.push(context);const page=await context.newPage();
    page.on('pageerror',error=>pageErrors.push({role,message:error.message}));
    page.on('response',response=>{
      const url=new URL(response.url()); if(response.status()<400 || !url.pathname.startsWith('/api/v1/')) return;
      networkChecks.push((async()=>{
        let code='UNREADABLE';try {code=String((await response.json()).code);} catch {}
        const record={role,method:response.request().method(),path:url.pathname,status:response.status(),code};
        if(role==='customer' && retiredUserToken && response.request().headers().authorization==='Bearer '+retiredUserToken && record.method==='GET' && record.path.startsWith('/api/v1/user/') && record.status===401 && code==='UNAUTHORIZED') expectedDenials.push(record);
        else unexpectedHttp.push(record);
      })());
    });return page;
  }
  async function capture(page:Page,name:string) {
    for(const field of await page.locator('input[type="password"]').all()) if(await field.isVisible()) await field.fill('');
    const output=testInfo.outputPath(name+'.png');await page.screenshot({path:output,fullPage:true});
    await testInfo.attach(name,{path:output,contentType:'image/png'});
  }
  async function ui<T>(page:Page,path:string,method:string,action:()=>Promise<unknown>) {
    const received=page.waitForResponse(response=>new URL(response.url()).pathname==='/api/v1'+path && response.request().method()===method);
    await action();const response=await received;const body=await response.json();
    expect(response.ok(),path+': '+String(body.code)).toBeTruthy();expect(body.code).toBe('OK');return body.data as T;
  }
  async function choose(page:Page,trigger:Locator,option:string) {
    await trigger.click();await expect(page.locator('.uni-picker-custom.uni-picker-toggle')).toBeVisible();
    const confirm=page.locator('.uni-picker-action-confirm:visible');
    if(await confirm.count()) {
      const container=page.locator('.uni-picker-custom:visible'),options=container.locator('.uni-picker-select .uni-picker-item');
      const target=await options.evaluateAll((items,text)=>items.findIndex(item=>item.textContent?.includes(text)),option);
      let selected=await options.evaluateAll(items=>items.findIndex(item=>item.classList.contains('selected')));
      expect(target).toBeGreaterThanOrEqual(0);await container.locator('uni-picker-view-column').hover();
      while(selected!==target) {const direction=Math.sign(target-selected);await page.mouse.wheel(0,direction*40);selected+=direction;await expect(options.nth(selected)).toHaveClass(/selected/);}
      await confirm.click();
    } else await page.locator('.uni-picker-item:visible').filter({hasText:option}).click();
  }
  async function staffLogin(page:Page,username:string,secret:string) {
    await page.goto('/admin/');await page.getByLabel('账户',{exact:true}).fill(username);await page.getByLabel('密码',{exact:true}).fill(secret);
    const result=await ui<Session>(page,'/admin/auth/login','POST',()=>page.getByLabel('密码',{exact:true}).press('Enter'));
    await expect(page.getByRole('button',{name:'退出',exact:true})).toBeVisible();return result;
  }
  async function firstStaff(page:Page,username:string,initial:string) {
    expect((await staffLogin(page,username,initial)).mustChangePassword).toBe(true);
    const changed='Upgrade-Staff!'+randomUUID();await page.getByLabel('原密码',{exact:true}).fill(initial);
    await page.getByLabel('新密码（12至128个字符）',{exact:true}).fill(changed);
    await ui(page,'/admin/auth/password','POST',()=>page.getByRole('button',{name:'更新并重新登录',exact:true}).click());
    return staffLogin(page,username,changed);
  }
  async function userLogin(page:Page,secret:string) {
    await page.goto('/app/pages/login/index');await page.locator('uni-input[aria-label="独立账号"] input').fill(names.user);
    await page.locator('uni-input[aria-label="账号密码"] input').fill(secret);
    return ui<Session>(page,'/user/auth/login','POST',()=>page.getByRole('button',{name:'登录独立账号',exact:true}).click());
  }
  async function userChange(page:Page,old:string,changed:string) {
    await expect(page.getByText('首次登录，请修改初始密码',{exact:true})).toBeVisible();
    await page.locator('uni-input[aria-label="原密码"] input').fill(old);await page.locator('uni-input[aria-label="新密码"] input').fill(changed);
    await page.locator('uni-input[aria-label="确认新密码"] input').fill(changed);
    await ui(page,'/user/auth/password','POST',()=>page.getByRole('button',{name:'保存新密码',exact:true}).click());
    await expect(page.getByRole('button',{name:'登录独立账号',exact:true})).toBeVisible();
  }
  async function selectStore(page:Page) {
    const select=page.locator('.topbar .el-select').first();
    for(let round=0;round<20;round++) {
      await select.click();const option=page.getByRole('option',{name:names.campus+' / '+names.store,exact:true});
      if(await option.count()) {await option.click();return;}
      await page.keyboard.press('Escape');await page.getByRole('button',{name:'加载更多店铺',exact:true}).click();
    }throw new Error('Target store was not reachable through store pagination');
  }
  try {
    // API writes prepare only catalog, campus, building, quota and staff fixtures.
    const campus=await call<{id:string}>(request,'/platform/campuses',a,'POST',{name:names.campus,enabled:true});
    const store=await call<{id:string}>(request,'/platform/stores',a,'POST',{campusId:campus.id,name:names.store,enabled:true,open:true,deliveryFeeCents:0,packagingFeeCents:0,hours:['00:00-24:00'],phone:''});
    const building=await call<{id:string}>(request,'/platform/campuses/'+campus.id+'/buildings',a,'POST',{name:names.building,enabled:true});
    const category=await call<{id:string}>(request,'/merchant/stores/'+store.id+'/categories',a,'POST',{name:'升级餐品',type:1,sort:0,enabled:true});
    const product=await call<{id:string}>(request,'/merchant/stores/'+store.id+'/products/DISH',a,'POST',{name:names.product,categoryId:category.id,price:'10.00',enabled:true,description:'仅隔离验收使用',image:'/demo-images/dish.svg',flavors:[],components:[]});
    const businessDate=new Date(Date.now()+8*3600000).toISOString().slice(0,10);
    await call(request,'/merchant/stores/'+store.id+'/quotas?date='+businessDate,a,'PUT',{dishId:product.id,total:20,defaultQuota:true});
    const localTime=(value:number)=>new Date(value+8*3600000).toISOString().slice(0,19);
    const coupon=await call<{id:string}>(request,'/merchant/stores/'+store.id+'/coupons',a,'POST',{name:names.coupon,kind:'REDUCTION',thresholdCents:0,value:100,capCents:null,total:5,perUser:1,startsAt:localTime(Date.now()-3600000),endsAt:localTime(Date.now()+86400000),enabled:true});
    const staff:Record<string,{id:string;username:string;initial:string}>={};
    for(const [key,role] of [['manager','SHOP_MANAGER'],['courier','DELIVERER']]) {
      const username='upgrade_'+key+'_'+suffix,initial='Upgrade-StaffInitial!'+randomUUID();
      const result=await call<{id:string}>(request,'/platform/staff',a,'POST',{username,name:'升级'+(key==='manager'?'店长':'配送员')+suffix,role,storeId:key==='manager'?store.id:null,campusId:key==='courier'?campus.id:null,enabled:true,password:initial});
      staff[key]={id:result.id,username,initial};
    }
    const platform=await pageFor('platform'),manager=await pageFor('manager'),courier=await pageFor('courier'),customer=await pageFor('customer',true);
    await staffLogin(platform,'admin',password());await platform.goto('/admin/users');
    await platform.getByRole('button',{name:'创建独立测试用户',exact:true}).click();
    const create=platform.getByRole('dialog',{name:'创建独立测试用户',exact:true});
    await create.getByLabel('独立账号',{exact:true}).fill(names.user);await create.getByLabel('显示名称',{exact:true}).fill('升级测试用户'+suffix);
    await create.getByLabel('初始密码',{exact:true}).fill(initialUserPassword);
    const created=await ui<{id:string}>(platform,'/platform/users','POST',()=>create.getByRole('button',{name:'保存用户',exact:true}).click());
    await expect(create).toBeHidden();await capture(platform,'01-platform-created-independent-user');
    expect((await userLogin(customer,initialUserPassword)).mustChangePassword).toBe(true);
    await userChange(customer,initialUserPassword,changedUserPassword);const user=await userLogin(customer,changedUserPassword);expect(user.mustChangePassword).toBe(false);
    const u=user.token;expect(user.id).toBe(created.id);
    await expect(customer.getByText('校园好店，',{exact:false})).toBeVisible();
    await choose(customer,customer.getByText('默认校区 ▾',{exact:true}),names.campus);
    await expect(customer.getByText(names.store+' ▾',{exact:true})).toBeVisible();
    await customer.goto('/app/pages/account/index');
    const couponCard=customer.locator('uni-view.card').filter({has:customer.getByText(names.coupon+' · 店铺券',{exact:true})});
    await ui(customer,'/user/coupons/'+coupon.id+'/claim','POST',()=>couponCard.getByText('领取',{exact:true}).click());
    await expect(customer.getByText(names.coupon,{exact:true})).toBeVisible();
    await firstStaff(manager,staff.manager.username,staff.manager.initial);
    await manager.goto('/admin/service-operations');await manager.getByRole('spinbutton').first().fill('1');
    await ui(manager,'/merchant/stores/'+store.id+'/service-policy','PUT',()=>manager.getByRole('button',{name:'保存服务时限',exact:true}).click());
    const courierSession=await firstStaff(courier,staff.courier.username,staff.courier.initial);

    await customer.goto('/app/pages/addresses/index');await customer.getByText('新增地址',{exact:true}).click();
    await choose(customer,customer.getByText('校区：',{exact:false}),names.campus);await choose(customer,customer.getByText('楼栋：',{exact:false}),names.building);
    const addressFields=customer.locator('uni-view.modal input');await addressFields.nth(0).fill('UPGRADE-401');await addressFields.nth(1).fill('升级验收用户');await addressFields.nth(2).fill('13800000000');
    await ui(customer,'/user/addresses','POST',()=>customer.getByText('保存地址',{exact:true}).click());
    async function orderFromPages(quantity:number,delivery:boolean) {
      await customer.goto('/app/pages/menu/index');
      const meal=customer.locator('uni-view.card').filter({has:customer.getByText(names.product,{exact:true})});
      for(let n=0;n<quantity;n++) {
        await meal.getByText('选餐',{exact:true}).click();
        await ui(customer,'/user/stores/'+store.id+'/cart/items','POST',()=>customer.getByText('加入本店购物车',{exact:true}).click());
        await expect(customer.locator('uni-view.modal')).toHaveCount(0);
      }
      await customer.goto('/app/pages/cart/index');const cart=customer.locator('uni-view.card').filter({has:customer.getByText(names.campus+' · '+names.store,{exact:true})});
      if(await cart.getByText('选择此店',{exact:true}).count()) await cart.getByText('选择此店',{exact:true}).click();
      await customer.getByText('合并结算 1 家店',{exact:true}).click();
      if(delivery) {await choose(customer,customer.getByText('到店自取 ▾',{exact:true}),'校区配送');await choose(customer,customer.getByText('选择本校区收货地址',{exact:false}),'UPGRADE-401');}
      await ui(customer,'/user/checkouts/preview','POST',()=>customer.getByText('计算报价',{exact:true}).click());
      const checkout=await ui<Checkout>(customer,'/user/checkouts','POST',()=>customer.getByText('提交订单，随后模拟支付',{exact:true}).click());
      const card=customer.locator('uni-view.card').filter({has:customer.getByText('待支付结算单 '+checkout.id,{exact:true})});
      await ui(customer,'/user/checkouts/'+checkout.id+'/pay','POST',()=>card.getByText('整批模拟支付',{exact:true}).click());
      const paid=await call<Checkout>(request,'/user/checkouts/'+checkout.id,u);expect(paid.state).toBe('PAID');return paid.orders[0];
    }
    const deliveryOrder=await orderFromPages(2,true),id=deliveryOrder.id;
    await manager.goto('/admin/orders?orderId='+id);
    for(const [action,label] of [['accept','接单备餐'],['ready','备餐完成'],['publish','发布抢单任务']])
      await ui(manager,'/merchant/orders/'+id+'/actions/'+action,'POST',()=>manager.getByRole('button',{name:label,exact:true}).click());
    await capture(manager,'02-manager-prepared-published-order');

    await platform.goto('/admin/operations/buildings');await platform.locator('section.panel .el-select').first().click();
    await platform.getByRole('option',{name:names.campus,exact:true}).click();
    await platform.locator('.el-table__body tr').filter({hasText:names.building}).getByRole('button',{name:'修改名称',exact:true}).click();
    await platform.getByPlaceholder('楼栋新名称').fill(names.renamed);
    await ui(platform,'/platform/campuses/'+campus.id+'/buildings/'+building.id,'PUT',()=>platform.getByRole('button',{name:'保存楼栋名称',exact:true}).click());
    await courier.goto('/admin/orders');const poolRow=courier.locator('.el-table__body tr').filter({hasText:names.store}).filter({has:courier.getByRole('button',{name:'抢单',exact:true})});
    await expect(poolRow.getByText(names.building,{exact:true})).toBeVisible();
    const tasks=await call<{id:string;orderId:string;buildingName:string}[]>(request,'/courier/pool',courierSession.token);const task=tasks.find(row=>row.orderId===id)!;expect(task.buildingName).toBe(names.building);
    async function courierAvailability(enabled:boolean) {
      await courier.getByRole('button',{name:'接单设置',exact:true}).click();const dialog=courier.getByRole('dialog',{name:'配送接单设置',exact:true});
      const toggle=dialog.getByRole('switch');if((await toggle.isChecked())!==enabled) await dialog.locator('.el-switch__core').click();
      await ui(courier,'/courier/dispatch-availability','PUT',()=>dialog.getByRole('button',{name:'保存接单设置',exact:true}).click());
    }
    await courierAvailability(false);
    await expect(poolRow.getByRole('button',{name:'抢单',exact:true})).toBeDisabled();
    await courierAvailability(true);
    await ui(courier,'/courier/tasks/'+task.id+'/claim','POST',()=>poolRow.getByRole('button',{name:'抢单',exact:true}).click());
    await courier.locator('.el-table__body tr').filter({hasText:deliveryOrder.number}).getByRole('button',{name:'处理订单',exact:true}).click();
    await expect(courier.getByText('升级验收用户 13800000000',{exact:false})).toBeVisible();
    await ui(courier,'/courier/orders/'+id+'/actions/pickup','POST',()=>courier.getByRole('button',{name:'确认取餐',exact:true}).click());
    await ui(courier,'/courier/orders/'+id+'/actions/complete','POST',()=>courier.getByRole('button',{name:'确认送达',exact:true}).click());
    const privateAfterDelivery=await call<Record<string,unknown>>(request,'/courier/orders/'+id,courierSession.token);
    for(const field of ['phone','address','room','consignee','amount','paidCents','refundedCents','aftersales','refunds']) expect(privateAfterDelivery).not.toHaveProperty(field);
    await expect(courier.getByText('13800000000',{exact:false})).toHaveCount(0);await capture(courier,'03-courier-delivered-with-private-history');
    checkpoints.push({name:'independent identity, page payment, building snapshot, paused assignment UI and courier privacy',userId:user.id,orderId:id,paidCents:deliveryOrder.paidCents});

    // The second real order waits for its one-minute acceptance deadline while refund/message/finance UI runs.
    const timeoutOrder=await orderFromPages(1,false);
    await customer.goto('/app/pages/orders/index');const orderCard=customer.locator('uni-view.card').filter({has:customer.getByText(new RegExp(deliveryOrder.number))});
    await orderCard.getByText('部分退款',{exact:true}).click();const refundDialog=customer.locator('uni-view.modal');
    await refundDialog.locator('input').fill('1');await refundDialog.locator('textarea').fill('升级验收：退一份商品');
    const aftersale=await ui<{id:string}>(customer,'/user/orders/'+id+'/aftersales','POST',()=>refundDialog.getByText('确认提交',{exact:true}).click());
    await manager.goto('/admin/operations/aftersales');await manager.getByPlaceholder('完整订单号',{exact:true}).fill(deliveryOrder.number);await manager.getByRole('button',{name:'筛选',exact:true}).click();
    await manager.getByPlaceholder('本次处理说明',{exact:true}).fill('同意退一份商品，核验模拟流水');
    const aftersaleCard=manager.locator('article').filter({hasText:'申请 '+aftersale.id+' ·'});
    await ui(manager,'/merchant/aftersales/'+aftersale.id+'/decision','POST',()=>aftersaleCard.getByRole('button',{name:'同意退款 / 取消',exact:true}).click());
    await expect.poll(async()=>(await call<Order>(request,'/user/orders/'+id,u)).refundedCents,{timeout:20000}).toBe('1000');
    const savedAftersale=(await call<Aftersale[]>(request,'/user/aftersales',u)).find(row=>row.id===aftersale.id)!;expect(savedAftersale.state).toBe('REFUNDED');

    await customer.goto('/app/pages/messages/index');await expect(customer.getByText('站内消息',{exact:true})).toBeVisible();
    const unread=await call<{count:string}>(request,'/user/messages/unread',u);expect(Number(unread.count)).toBeGreaterThan(0);
    const messages=await call<CursorPage<{id:string;readAt?:string|null}>>(request,'/user/messages/page?limit=50',u);
    expect(messages.records.some(row=>!row.readAt)).toBe(true);
    const messageCard=customer.locator('uni-view.card').filter({has:customer.getByText('订单 ID：'+id,{exact:true})}).first();
    await expect(messageCard).toBeVisible();
    const marked=customer.waitForResponse(response=>/^\/api\/v1\/user\/messages\/\d+\/read$/.test(new URL(response.url()).pathname) && response.request().method()==='POST');
    await customer.getByText('标记已读',{exact:true}).first().click();const markedResponse=await marked;expect(markedResponse.ok()).toBe(true);
    const markedId=new URL(markedResponse.url()).pathname.split('/').at(-2)!;
    await expect.poll(async()=>{
      const saved=await call<CursorPage<{id:string;readAt?:string|null}>>(request,'/user/messages/page?limit=50',u);return Boolean(saved.records.find(row=>row.id===markedId)?.readAt);
    }).toBe(true);
    await capture(customer,'04-customer-refund-and-offline-messages');
    await manager.goto('/admin/reports');await expect(manager.getByText('数据快照：',{exact:false})).toBeVisible();
    await capture(manager,'05-manager-report-snapshot');
    await manager.goto('/admin/finance');await expect(manager.getByTestId('finance-preview')).toBeVisible();
    await expect(manager.getByTestId('finance-preview')).toContainText('¥30.00');await expect(manager.getByTestId('finance-preview')).toContainText('¥10.00');
    await capture(manager,'05-manager-simulated-ledger-preview');
    await platform.goto('/admin/finance');await selectStore(platform);await platform.getByRole('button',{name:'运行对账',exact:true}).click();
    await expect(platform.getByTestId('finance-reconciliation')).toContainText('账目一致');
    await capture(platform,'06-platform-reconciliation');

    await platform.goto('/admin/service-operations');
    const exceptionRow=()=>platform.locator('.el-table__body tr').filter({hasText:timeoutOrder.number});
    await expect(async()=>{
      await platform.getByRole('button',{name:'刷新',exact:true}).click();
      await expect(exceptionRow()).toBeVisible({timeout:1000});
    }).toPass({timeout:85000,intervals:[5000]});
    await exceptionRow().getByRole('button',{name:'确认处理',exact:true}).click();
    const followup=platform.getByRole('dialog',{name:'确认跟进异常',exact:true});await followup.getByPlaceholder('说明已联系的人员和下一步处理安排').fill('已联系商家，马上接单备餐');
    const exception=await call<CursorPage<{id:string;orderId:string}>>(request,'/platform/order-exceptions/page?storeId='+store.id+'&state=OPEN',a);
    const exceptionId=exception.records.find(row=>row.orderId===timeoutOrder.id)!.id;
    await ui(platform,'/order-exceptions/'+exceptionId+'/acknowledge','POST',()=>followup.getByRole('button',{name:'确认处理',exact:true}).click());
    await manager.goto('/admin/orders?orderId='+timeoutOrder.id);
    await ui(manager,'/merchant/orders/'+timeoutOrder.id+'/actions/accept','POST',()=>manager.getByRole('button',{name:'接单备餐',exact:true}).click());
    await expect.poll(async()=>{
      const resolved=await call<CursorPage<{id:string;state:string}>>(request,'/platform/order-exceptions/page?storeId='+store.id+'&state=RESOLVED',a);return resolved.records.some(row=>row.id===exceptionId);
    },{timeout:20000}).toBe(true);
    checkpoints.push({name:'page partial refund, persisted unread messages, ledger preview, reconciliation and real one-minute exception followup',refundedCents:'1000',timeoutOrderId:timeoutOrder.id,exceptionId});

    await platform.goto('/admin/users');const userRow=platform.locator('.el-table__body tr').filter({hasText:names.user});
    await userRow.getByRole('button',{name:'重置密码',exact:true}).click();const reset=platform.getByRole('dialog',{name:'重置账号密码',exact:true});
    await reset.getByLabel('重置密码',{exact:true}).fill(resetUserPassword);retiredUserToken=u;
    await ui(platform,'/platform/users/'+user.id+'/reset-password','POST',()=>reset.getByRole('button',{name:'确认重置',exact:true}).click());
    const oldSession=await request.get('/api/v1/user/auth/me',{headers:{Authorization:'Bearer '+u}});expect(oldSession.status()).toBe(401);
    expectedDenials.push({role:'verification',method:'GET',path:'/api/v1/user/auth/me',status:401,code:(await oldSession.json()).code});
    await customer.getByText('刷新消息',{exact:true}).click();
    await expect.poll(()=>customer.evaluate(()=>sessionStorage.getItem('campus-user'))).toBeNull();
    expect((await userLogin(customer,resetUserPassword)).mustChangePassword).toBe(true);
    await userChange(customer,resetUserPassword,renewedUserPassword);const renewed=await userLogin(customer,renewedUserPassword);expect(renewed.mustChangePassword).toBe(false);
    expect((await call<{id:string}>(request,'/user/auth/me',renewed.token)).id).toBe(user.id);
    await capture(customer,'07-user-password-reset-recovered');await capture(platform,'08-platform-user-reset-finished');
    checkpoints.push({name:'platform reset revokes prior session and customer changes password through real page'});
    await Promise.all(networkChecks);expect(pageErrors).toEqual([]);expect(unexpectedHttp).toEqual([]);
    expect(expectedDenials.some(row=>row.code==='UNAUTHORIZED')).toBe(true);
  } catch(error) {
    for(let index=0;index<contexts.length;index++) {const page=contexts[index].pages()[0];if(page) await capture(page,'failure-role-'+index).catch(()=>{});}throw error;
  } finally {
    await Promise.all(networkChecks);
    const evidence=testInfo.outputPath('complete-upgrade-evidence.json');
    await fs.writeFile(evidence,JSON.stringify({sourceRevision:process.env.CANDIDATE_SHA||process.env.GITHUB_SHA||'local',
      fixtureBoundary:'Only campus/store/building/product/quota/coupon/staff use setup APIs. User creation/login/passwords/coupon claim, cart/checkout/mock payment, service policy, building rename, delivery availability/claim/transitions, refund, message reads and exception acknowledgement use real pages. APIs otherwise verify read-only snapshots.',
      browserContexts:contexts.length,pageErrors,unexpectedHttp,expectedDenials,checkpoints},null,2));
    await testInfo.attach('complete-upgrade-evidence',{path:evidence,contentType:'application/json'});
    for(const context of contexts) await context.close();
  }
});
