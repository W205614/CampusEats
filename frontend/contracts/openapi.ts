export interface paths {
    "/api/v1/user/addresses/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["addressUpdate"];
        post?: never;
        delete: operations["addressDelete"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/coupons/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["platformCouponUpdate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/campuses/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["campusUpdate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/campuses/{campusId}/buildings/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["buildingUpdate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["storeUpdate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/quotas": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["quotas"];
        put: operations["quota"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/promotions/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["promotionUpdate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/products/{type}/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["productUpdate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/coupons/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["storeCouponUpdate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/categories/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["categoryUpdate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/staff/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["staffUpdate"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/staff/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["staffUpdate_1"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/{storeId}/favorites/{type}/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["favorite"];
        delete: operations["unfavorite"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/{storeId}/cart/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["cartChange"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/{storeId}/cart/clear": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["clearCart"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/orders/{id}/review": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["review"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/orders/{id}/reorder": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reorder"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/orders/{id}/reminder": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reminder"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/orders/{id}/evidence": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["uploadEvidence"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/orders/{id}/aftersales": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["apply"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/coupons/{id}/claim": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["claimCoupon"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/checkouts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["checkouts"];
        put?: never;
        post: operations["submit"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/checkouts/{id}/pay": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["pay"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/checkouts/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["checkoutCancel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/checkouts/preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["preview"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/auth/wechat": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["wx"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/auth/demo": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["demo"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/aftersales/{id}/withdraw": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["withdraw"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/aftersales/{id}/appeal": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["appeal"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/addresses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["addresses"];
        put?: never;
        post: operations["address"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/addresses/{id}/default": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["addressDefault"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/stores": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["store"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/reviews/{id}/hide": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["hide"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/orders/{id}/unassign": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["unassign"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/orders/{id}/reset-pickup": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["resetPickup"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/coupons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminPlatformCoupons"];
        put?: never;
        post: operations["platformCoupon"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/coupons/{id}/stop": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["stopPlatformCoupon"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/campuses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminCampuses"];
        put?: never;
        post: operations["campus"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/campuses/{campusId}/buildings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminBuildings"];
        put?: never;
        post: operations["building"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/aftersales/{id}/decision": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["rule"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/uploads": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["productUpload"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/promotions": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["promotions"];
        put?: never;
        post: operations["promotion"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/products/{type}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["product"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/coupons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminStoreCoupons"];
        put?: never;
        post: operations["storeCoupon"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/coupons/{id}/stop": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["stopStoreCoupon"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/categories": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminCategories"];
        put?: never;
        post: operations["category"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/staff": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["staff"];
        put?: never;
        post: operations["staffSave"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/staff": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["staffSave_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/reviews/{id}/reply": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reply"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/refunds/{id}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["refundRetry"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/orders/{id}/actions/{action}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["orderAction"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/courier/orders/{id}/actions/{action}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["orderAction_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/orders/{id}/actions/{action}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["orderAction_2"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/events/{id}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["eventRetry"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/aftersales/{id}/decision": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["decide"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/courier/tasks/{id}/claim": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["claimTask"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/ws-ticket": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["ticket"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/auth/password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["password"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["logout"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/auth/logout": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["logout_1"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/auth/login": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["login"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/wallet": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["wallet"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["stores"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/{storeId}/reviews": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["reviews"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/{storeId}/products": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["products"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/{storeId}/coupons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["storeCoupons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/{storeId}/categories": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["categories"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/{storeId}/cart": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["cart"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/search": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["search"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/orders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["userOrders"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/favorites": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["favorites"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/coupons": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["platformCoupons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/checkouts/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["checkout"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/checkouts/by-request/{key}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["checkoutKey"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/carts": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["carts"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/campuses": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["campuses"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/campuses/{campusId}/buildings": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["buildings"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/aftersales": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["userAftersales"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/refunds": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["refunds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/events": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["events"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/audit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["audit"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/aftersales": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["platformAftersales"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminStores"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/staff": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["storeStaff"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/reviews": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminReviews"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/refunds": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["storeRefunds"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/products": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["adminProducts"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/orders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["storeOrders"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/events": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["storeEvents"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/couriers": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["couriers"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/audit": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["storeAudit"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/aftersales": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["merchantAftersales"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/reports/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["export"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/reports/export": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["export_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["report"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["report_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/orders/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["order"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/courier/orders/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["order_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/orders/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["order_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/evidence/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["evidence"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/courier/pool": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["pool"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/courier/orders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["courierOrders"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/auth/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["me"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/auth/me": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["me_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        Address: {
            buildingId: string;
            room: string;
            consignee: string;
            phone: string;
        };
        Coupon: {
            name: string;
            kind: string;
            thresholdCents?: string;
            value?: string;
            capCents?: string;
            /** Format: int32 */
            total?: number;
            /** Format: int32 */
            perUser?: number;
            /** Format: date-time */
            startsAt: string;
            /** Format: date-time */
            endsAt: string;
            enabled?: boolean;
        };
        Campus: {
            name: string;
            enabled?: boolean;
        };
        Building: {
            name: string;
            enabled?: boolean;
        };
        Store: {
            campusId?: string;
            name: string;
            enabled?: boolean;
            open?: boolean;
            deliveryFeeCents?: string;
            packagingFeeCents?: string;
            hours: string[];
            phone?: string;
        };
        Quota: {
            dishId: string;
            /** Format: int32 */
            total?: number;
            defaultQuota?: boolean;
        };
        Promotion: {
            itemType: string;
            itemId?: string;
            priceCents?: string;
            /** Format: date-time */
            startsAt: string;
            /** Format: date-time */
            endsAt: string;
            enabled?: boolean;
        };
        Component: {
            dishId: string;
            /** Format: int32 */
            copies?: number;
        };
        Flavor: {
            name: string;
            values: string[];
        };
        Product: {
            name: string;
            categoryId: string;
            /** @example 12.00 */
            price: string;
            enabled?: boolean;
            description?: string;
            image?: string;
            flavors?: components["schemas"]["Flavor"][];
            components?: components["schemas"]["Component"][];
        };
        Category: {
            name: string;
            /** Format: int32 */
            type?: number;
            /** Format: int32 */
            sort?: number;
            enabled?: boolean;
        };
        Staff: {
            username: string;
            name: string;
            role: string;
            storeId?: string;
            campusId?: string;
            enabled?: boolean;
            password?: string;
        };
        CartChange: {
            cartVersion?: string;
            itemType?: string;
            itemId?: string;
            flavors?: {
                [key: string]: string;
            };
            /** Format: int32 */
            delta?: number;
        };
        Version: {
            version: string;
        };
        Review: {
            /** Format: int32 */
            rating?: number;
            content: string;
        };
        Aftersale: {
            kind: string;
            reason: string;
            items?: components["schemas"]["RefundLine"][];
            evidenceIds?: string[];
        };
        RefundLine: {
            detailId?: string;
            /** Format: int32 */
            quantity?: number;
        };
        Checkout: {
            stores: components["schemas"]["Selection"][];
            platformCouponId?: string;
            quoteHash?: string;
            remark?: string;
        };
        Selection: {
            storeId?: string;
            cartVersion?: string;
            fulfillment: string;
            addressId?: string;
            couponId?: string;
        };
        Line: {
            cartId?: string;
            itemType?: string;
            itemId?: string;
            name?: string;
            image?: string;
            flavors?: string;
            /** Format: int32 */
            quantity?: number;
            normalCents?: string;
            activityCents?: string;
            paidCents?: string;
        };
        Part: {
            storeId?: string;
            campusId?: string;
            storeName?: string;
            campusName?: string;
            cartVersion?: string;
            fulfillment?: string;
            addressId?: string;
            address?: {
                [key: string]: unknown;
            };
            items?: components["schemas"]["Line"][];
            subtotalCents?: string;
            shopDiscountCents?: string;
            platformDiscountCents?: string;
            deliveryCents?: string;
            packagingCents?: string;
            totalCents?: string;
            couponId?: string;
            settingsVersion?: string;
        };
        Quote: {
            stores?: components["schemas"]["Part"][];
            platformCouponId?: string;
            totalCents?: string;
            businessDate?: string;
            quoteHash?: string;
            rules?: {
                [key: string]: unknown;
            }[];
        };
        WxLogin: {
            code: string;
        };
        SessionView: {
            token?: string;
            id?: string;
            name?: string;
            /** @enum {string} */
            role?: "PLATFORM_ADMIN" | "SHOP_MANAGER" | "ADMIN" | "OPERATOR" | "DELIVERER" | "USER";
            mustChangePassword?: boolean;
            expiresAt?: string;
            storeId?: string;
            campusId?: string;
        };
        DemoLogin: {
            /** Format: int32 */
            account?: number;
        };
        Text: {
            text: string;
        };
        Action: {
            version?: string;
            reason?: string;
            courierId?: string;
            code?: string;
        };
        Decision: {
            version?: string;
            approve?: boolean;
            reason: string;
        };
        Password: {
            oldPassword: string;
            password: string;
        };
        Login: {
            username: string;
            password: string;
        };
        Actor: {
            id?: string;
            type?: string;
            role?: string;
            jti?: string;
            mustChangePassword?: boolean;
            storeId?: string;
            campusId?: string;
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    addressUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Address"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    addressDelete: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    platformCouponUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Coupon"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    campusUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Campus"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    buildingUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                campusId: number;
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Building"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    storeUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Store"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    quotas: {
        parameters: {
            query: {
                date: string;
            };
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    quota: {
        parameters: {
            query: {
                date: string;
            };
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Quota"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    promotionUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Promotion"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    productUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
                type: string;
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Product"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    storeCouponUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Coupon"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    categoryUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Category"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    staffUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Staff"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    staffUpdate_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Staff"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    favorite: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
                type: string;
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    unfavorite: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
                type: string;
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    cartChange: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CartChange"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    clearCart: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    review: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Review"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    reorder: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    reminder: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    uploadEvidence: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": {
                    /** Format: binary */
                    file: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    apply: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Aftersale"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    claimCoupon: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    checkouts: {
        parameters: {
            query?: {
                beforeId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    submit: {
        parameters: {
            query?: never;
            header: {
                "Idempotency-Key": string;
            };
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Checkout"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    pay: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    checkoutCancel: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    preview: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Checkout"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["Quote"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    wx: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["WxLogin"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["SessionView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    demo: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["DemoLogin"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["SessionView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    withdraw: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    appeal: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    addresses: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    address: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Address"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    addressDefault: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    store: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Store"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    hide: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Text"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    unassign: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Action"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    resetPickup: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    adminPlatformCoupons: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    platformCoupon: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Coupon"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    stopPlatformCoupon: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    adminCampuses: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    campus: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Campus"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    adminBuildings: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                campusId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    building: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                campusId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Building"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    rule: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Decision"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    productUpload: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: {
            content: {
                "application/json": {
                    /** Format: binary */
                    file: string;
                };
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    promotions: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    promotion: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Promotion"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    product: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
                type: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Product"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    adminStoreCoupons: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeCoupon: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Coupon"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    stopStoreCoupon: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    adminCategories: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    category: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Category"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    staff: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    staffSave: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Staff"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    staffSave_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Staff"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    reply: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Text"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    refundRetry: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    orderAction: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                action: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Action"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    orderAction_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                action: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Action"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    orderAction_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
                action: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Action"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    eventRetry: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    decide: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Decision"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    claimTask: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Version"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    ticket: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: {
                            [key: string]: unknown;
                        };
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    password: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Password"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    logout: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    logout_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Login"];
            };
        };
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["SessionView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    wallet: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    stores: {
        parameters: {
            query?: {
                campusId?: number;
                query?: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    reviews: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    products: {
        parameters: {
            query?: {
                query?: string;
                categoryId?: number;
            };
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeCoupons: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    categories: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    cart: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    search: {
        parameters: {
            query: {
                campusId: number;
                query?: string;
                categoryId?: number;
                open?: boolean;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    userOrders: {
        parameters: {
            query?: {
                beforeId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    favorites: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    platformCoupons: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    checkout: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    checkoutKey: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                key: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    carts: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    campuses: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    buildings: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                campusId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    userAftersales: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    refunds: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    events: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    audit: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    platformAftersales: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    adminStores: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeStaff: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    adminReviews: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeRefunds: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    adminProducts: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeOrders: {
        parameters: {
            query?: {
                beforeId?: number;
            };
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeEvents: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    couriers: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeAudit: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    merchantAftersales: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    export: {
        parameters: {
            query: {
                campusId?: number;
                storeId?: number;
                begin: string;
                end: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": string;
                };
            };
        };
    };
    export_1: {
        parameters: {
            query: {
                campusId?: number;
                storeId?: number;
                begin: string;
                end: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": string;
                };
            };
        };
    };
    report: {
        parameters: {
            query: {
                campusId?: number;
                storeId?: number;
                begin: string;
                end: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    report_1: {
        parameters: {
            query: {
                campusId?: number;
                storeId?: number;
                begin: string;
                end: string;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    order: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    order_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    order_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: number;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    evidence: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "image/png": string;
                };
            };
        };
    };
    pool: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    courierOrders: {
        parameters: {
            query?: {
                beforeId?: number;
            };
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: Record<string, never>;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    me: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["Actor"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    me_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description OK */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["Actor"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
}
