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
    "/api/v1/platform/users/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["update"];
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
    "/api/v1/platform/staff/{id}/dispatch-availability": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["availability"];
        post?: never;
        delete?: never;
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
    "/api/v1/merchant/stores/{storeId}/service-policy": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["policy"];
        put: operations["policy_1"];
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
    "/api/v1/courier/dispatch-availability": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["ownAvailability"];
        put: operations["ownAvailability_1"];
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
    "/api/v1/user/messages/{id}/read": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["read"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/messages/{id}/read": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["read_1"];
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
    "/api/v1/user/auth/password": {
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
    "/api/v1/user/auth/login": {
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
    "/api/v1/platform/users": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["create"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/users/{id}/reset-password": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reset"];
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
    "/api/v1/platform/staff": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** @deprecated */
        get: operations["staff"];
        put?: never;
        post: operations["staffSave"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/staff/{id}/emergency-disable": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["emergency"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/settlements": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["generate"];
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
    "/api/v1/platform/orders/{id}/reassign": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["reassign"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/orders/{id}/exception-cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["exceptionCancel"];
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
        /** @deprecated */
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
    "/api/v1/order-exceptions/{id}/acknowledge": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["acknowledge"];
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
        /** @deprecated */
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
        /** @deprecated */
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
        post: operations["password_1"];
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
        post: operations["login_1"];
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
        /** @deprecated */
        get: operations["wallet"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/wallet/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["wallet_1"];
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
        /** @deprecated */
        get: operations["storeCoupons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/{storeId}/coupons/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["userCoupons"];
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
    "/api/v1/user/stores/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["storeDetail"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/stores/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["storePage"];
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
    "/api/v1/user/search/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["searchPage"];
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
    "/api/v1/user/messages/unread": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["unread"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/messages/unread": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["unread_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/messages/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["messages"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/me/messages/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["messages_1"];
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
        /** @deprecated */
        get: operations["favorites"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/favorites/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["favorites_1"];
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
        /** @deprecated */
        get: operations["platformCoupons"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/coupons/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["userCoupons_1"];
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
    "/api/v1/user/auth/capabilities": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["capabilities"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/auth/account": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["account"];
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
    "/api/v1/platform/users/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["page"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/staff/{id}/active-deliveries/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["active"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/staff/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["staff_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/settlements/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["statement"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/settlements/{id}/entries/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["entries"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/settlements/preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["preview_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/settlements/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["statements"];
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
    "/api/v1/platform/order-exceptions/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["exceptions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/ledger/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["ledger"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/platform/finance/reconciliation": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["reconcile"];
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
    "/api/v1/platform/coupons/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["coupons"];
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
        /** @deprecated */
        get: operations["storeStaff"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/staff/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["staff_2"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/settlements/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["merchantStatement"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/settlements/{id}/entries/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["merchantEntries"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/settlements/preview": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["merchantPreview"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/settlements/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["merchantStatements"];
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
    "/api/v1/merchant/stores/{storeId}/promotions/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["promotions_1"];
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
    "/api/v1/merchant/stores/{storeId}/order-exceptions/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["storeExceptions"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/merchant/stores/{storeId}/ledger/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["merchantLedger"];
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
    "/api/v1/merchant/stores/{storeId}/coupons/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["coupons_1"];
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
    "/api/v1/merchant/stores/page": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["merchantStorePage"];
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
        LocalUserUpdate: {
            name: string;
            enabled?: boolean;
        };
        LocalUserView: {
            id?: string;
            username?: string;
            name?: string;
            enabled?: boolean;
            mustChangePassword?: boolean;
            /** Format: date-time */
            createdAt?: string;
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
        Availability: {
            enabled?: boolean;
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
        Policy: {
            /** Format: int32 */
            acceptMinutes?: number;
            /** Format: int32 */
            prepareMinutes?: number;
            /** Format: int32 */
            dispatchMinutes?: number;
            /** Format: int32 */
            pickupMinutes?: number;
            /** Format: int32 */
            deliveryMinutes?: number;
            /** Format: int32 */
            selfPickupMinutes?: number;
            version?: string;
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
        Action: {
            version?: string;
            reason?: string;
            courierId?: string;
            code?: string;
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
        Password: {
            oldPassword: string;
            password: string;
        };
        LocalUserLogin: {
            username: string;
            password: string;
        };
        DemoLogin: {
            /** Format: int32 */
            account?: number;
        };
        LocalUserCreate: {
            username: string;
            name: string;
            password: string;
        };
        LocalUserResetPassword: {
            password: string;
        };
        Reason: {
            reason: string;
        };
        Generate: {
            storeId?: string;
            /** Format: date */
            businessDate: string;
        };
        FinanceSettlement: {
            id: string;
            storeId: string;
            storeName: string;
            /** Format: date */
            businessDate: string;
            /** Format: date-time */
            periodStart: string;
            /** Format: date-time */
            periodEnd: string;
            paidCents: string;
            refundedCents: string;
            netCents: string;
            entryCount: string;
            carryForward: boolean;
            /** Format: date-time */
            createdAt: string;
        };
        Text: {
            text: string;
        };
        Reassign: {
            version?: string;
            courierId: string;
            reason: string;
        };
        ExceptionCancel: {
            version?: string;
            reason: string;
        };
        Decision: {
            version?: string;
            approve?: boolean;
            reason: string;
        };
        Acknowledge: {
            version?: string;
            reason: string;
        };
        Login: {
            username: string;
            password: string;
        };
        Request: {
            cursor?: string;
            /** Format: int32 */
            limit?: number;
            state?: string;
            query?: string;
        };
        CollectionWallet: {
            id?: string;
            state?: string;
            checkoutId?: string;
            couponTemplateId?: string;
            storeId?: string;
            name?: string;
            kind?: string;
            thresholdCents?: string;
            value?: string;
            capCents?: string;
            /** Format: date-time */
            startsAt?: string;
            /** Format: date-time */
            endsAt?: string;
        };
        PageCollectionWallet: {
            records: components["schemas"]["CollectionWallet"][];
            nextCursor?: string;
            hasMore: boolean;
        };
        PublicReview: {
            id?: string;
            /** Format: int32 */
            rating?: number;
            content?: string;
            reply?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        PublicReviews: {
            stats?: components["schemas"]["ReviewStats"];
            records?: components["schemas"]["PublicReview"][];
        };
        ReviewStats: {
            total?: string;
            rating?: number;
        };
        CollectionCoupon: {
            id?: string;
            storeId?: string;
            name?: string;
            kind?: string;
            thresholdCents?: string;
            value?: string;
            capCents?: string;
            /** Format: int32 */
            total?: number;
            /** Format: int32 */
            claimed?: number;
            /** Format: int32 */
            perUser?: number;
            /** Format: date-time */
            startsAt?: string;
            /** Format: date-time */
            endsAt?: string;
            enabled?: boolean;
        };
        PageCollectionCoupon: {
            records: components["schemas"]["CollectionCoupon"][];
            nextCursor?: string;
            hasMore: boolean;
        };
        StoreResponse: {
            id?: string;
            campusId?: string;
            name?: string;
            enabled?: boolean;
            open?: boolean;
            /** @example 12.00 */
            deliveryFee?: string;
            /** @example 12.00 */
            packagingFee?: string;
            hours?: string[];
            phone?: string;
            version?: string;
            campusName?: string;
            accepting?: boolean;
        };
        StorePageResponse: {
            records?: components["schemas"]["StoreResponse"][];
            nextCursor?: string;
            hasMore?: boolean;
        };
        ComponentResponse: {
            id?: string;
            setmealId?: string;
            dishId?: string;
            name?: string;
            /** @example 12.00 */
            price?: string;
            /** Format: int32 */
            copies?: number;
            currentName?: string;
            /** Format: int32 */
            status?: number;
        };
        FlavorResponse: {
            id?: string;
            name?: string;
            values?: string[];
        };
        ProductResponse: {
            id?: string;
            name?: string;
            categoryId?: string;
            /** @example 12.00 */
            price?: string;
            image?: string;
            description?: string;
            /** Format: int32 */
            status?: number;
            createTime?: string;
            updateTime?: string;
            createUser?: string;
            updateUser?: string;
            /** Format: int32 */
            defaultQuota?: number;
            storeId?: string;
            /** Format: int32 */
            categoryStatus?: number;
            /** @enum {string} */
            itemType?: "DISH" | "SETMEAL";
            storeName?: string;
            flavors?: components["schemas"]["FlavorResponse"][];
            components?: components["schemas"]["ComponentResponse"][];
            /** Format: int32 */
            remaining?: number;
            promotion?: components["schemas"]["PromotionResponse"];
        };
        PromotionResponse: {
            id?: string;
            priceCents?: string;
            endsAt?: string;
        };
        SearchPageResponse: {
            records?: components["schemas"]["ProductResponse"][];
            nextCursor?: string;
            hasMore?: boolean;
        };
        DeliveryResponse: {
            id?: string;
            orderId?: string;
            campusId?: string;
            courierId?: string;
            state?: string;
            version?: string;
        };
        OrderAftersaleResponse: {
            id?: string;
            orderId?: string;
            userId?: string;
            storeId?: string;
            kind?: string;
            state?: string;
            reason?: string;
            decision?: string;
            amountCents?: string;
            feeCents?: string;
            appealed?: boolean;
            createdAt?: string;
            version?: string;
        };
        OrderDetailResponse: {
            id?: string;
            name?: string;
            image?: string;
            orderId?: string;
            dishId?: string;
            setmealId?: string;
            dishFlavor?: string;
            /** Format: int32 */
            number?: number;
            /** @example 12.00 */
            amount?: string;
            lineTotalCents?: string;
            /** Format: int32 */
            refundedQuantity?: number;
            /** Format: int32 */
            reservedQuantity?: number;
        };
        OrderResponse: {
            id?: string;
            number?: string;
            /** Format: int32 */
            status?: number;
            userId?: string;
            addressBookId?: string;
            orderTime?: string;
            checkoutTime?: string;
            /** Format: int32 */
            payMethod?: number;
            /** Format: int32 */
            payStatus?: number;
            /** @example 12.00 */
            amount?: string;
            remark?: string;
            phone?: string;
            address?: string;
            userName?: string;
            consignee?: string;
            cancelReason?: string;
            rejectionReason?: string;
            cancelTime?: string;
            estimatedDeliveryTime?: string;
            deliveryStatus?: boolean;
            deliveryTime?: string;
            /** Format: int32 */
            packAmount?: number;
            /** Format: int32 */
            tablewareNumber?: number;
            tablewareStatus?: boolean;
            requestId?: string;
            version?: string;
            businessDate?: string;
            snapshotSource?: string;
            buildingName?: string;
            room?: string;
            /** @example 12.00 */
            deliveryFee?: string;
            /** @example 12.00 */
            packagingFee?: string;
            payExpiresAt?: string;
            deliveryStartedAt?: string;
            courierId?: string;
            deliveryOverdue?: boolean;
            storeId?: string;
            campusId?: string;
            checkoutId?: string;
            fulfillment?: string;
            ready?: boolean;
            completedAt?: string;
            paidCents?: string;
            refundedCents?: string;
            reservedRefundCents?: string;
            shopDiscountCents?: string;
            platformDiscountCents?: string;
            pickupCode?: string;
            /** Format: int32 */
            pickupAttempts?: number;
            storeName?: string;
            campusName?: string;
            details?: components["schemas"]["OrderDetailResponse"][];
            delivery?: components["schemas"]["DeliveryResponse"];
            aftersales?: components["schemas"]["OrderAftersaleResponse"][];
            refunds?: components["schemas"]["RefundResponse"][];
            reviewed?: boolean;
        };
        RefundResponse: {
            id?: string;
            orderId?: string;
            aftersaleId?: string;
            storeId?: string;
            amountCents?: string;
            state?: string;
            /** Format: int32 */
            attempts?: number;
            nextAttemptAt?: string;
            leaseUntil?: string;
            lastError?: string;
            createdAt?: string;
            succeededAt?: string;
            successTimeSource?: string;
        };
        Message: {
            id?: string;
            orderId?: string;
            storeId?: string;
            eventType?: string;
            summary?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            readAt?: string;
        };
        PageMessage: {
            records: components["schemas"]["Message"][];
            nextCursor?: string;
            hasMore: boolean;
        };
        CollectionFavorite: {
            targetType?: string;
            targetId?: string;
            storeId?: string;
            storeName?: string;
            name?: string;
        };
        PageCollectionFavorite: {
            records: components["schemas"]["CollectionFavorite"][];
            nextCursor?: string;
            hasMore: boolean;
        };
        AuthenticationCapabilities: {
            demoLoginEnabled?: boolean;
            localLoginEnabled?: boolean;
            wechatLoginEnabled?: boolean;
            paymentMode?: string;
            mode?: string;
        };
        LocalProfile: {
            localAccount?: boolean;
            username?: string;
        };
        LocalUsersPage: {
            records?: components["schemas"]["LocalUserView"][];
            nextCursor?: string;
            hasMore?: boolean;
        };
        PageMapStringObject: {
            records: {
                [key: string]: unknown;
            }[];
            nextCursor?: string;
            hasMore: boolean;
        };
        CollectionStaff: {
            id?: string;
            username?: string;
            name?: string;
            role?: string;
            storeId?: string;
            campusId?: string;
            /** Format: int32 */
            status?: number;
            mustChangePassword?: boolean;
            acceptingTasks?: boolean;
        };
        PageCollectionStaff: {
            records: components["schemas"]["CollectionStaff"][];
            nextCursor?: string;
            hasMore: boolean;
        };
        FinanceLedgerLine: {
            id: string;
            sourceType: string;
            sourceId: string;
            orderId: string;
            storeId: string;
            storeName: string;
            amountCents: string;
            /** Format: date-time */
            occurredAt: string;
        };
        PageFinanceLedgerLine: {
            records: components["schemas"]["FinanceLedgerLine"][];
            nextCursor?: string | null;
            hasMore: boolean;
        };
        FinancePreview: {
            storeId: string;
            /** Format: date */
            businessDate: string;
            /** Format: date-time */
            enabledAt: string;
            /** Format: date-time */
            periodStart: string;
            /** Format: date-time */
            periodEnd: string;
            openingNetCents: string;
            paidCents: string;
            refundedCents: string;
            netCents: string;
            entryCount: string;
            commissionCents: string;
            channelFeeCents: string;
            carryForward: boolean;
            partialDay: boolean;
            /** Format: date-time */
            generatedAt: string;
        };
        PageFinanceSettlement: {
            records: components["schemas"]["FinanceSettlement"][];
            nextCursor?: string | null;
            hasMore: boolean;
        };
        Daily: {
            /** Format: date */
            date?: string;
            paidCents?: string;
            paymentCount?: string;
        };
        Metrics: {
            paymentCount?: string;
            childOrderCount?: string;
            paidCents?: string;
            shopDiscountCents?: string;
            platformDiscountCents?: string;
            refundedCents?: string;
            completed?: string;
            completedCents?: string;
            netCents?: string;
        };
        ReportSnapshot: {
            metrics?: components["schemas"]["Metrics"];
            daily?: components["schemas"]["Daily"][];
            stores?: components["schemas"]["StoreTotal"][];
            /** Format: date-time */
            generatedAt?: string;
        };
        StoreTotal: {
            storeId?: string;
            storeName?: string;
            paidCents?: string;
            childOrderCount?: string;
        };
        ExceptionView: {
            id?: string;
            orderId?: string;
            orderNumber?: string;
            storeId?: string;
            campusId?: string;
            kind?: string;
            stage?: string;
            state?: string;
            reason?: string;
            version?: string;
            /** Format: date-time */
            createdAt?: string;
            /** Format: date-time */
            deadlineAt?: string;
            migrated?: boolean;
        };
        PageExceptionView: {
            records: components["schemas"]["ExceptionView"][];
            nextCursor?: string;
            hasMore: boolean;
        };
        FinanceDifference: {
            orderId: string;
            storeId: string;
            orderPaidCents: string;
            orderRefundedCents: string;
            ledgerPaidCents: string;
            ledgerRefundedCents: string;
        };
        FinanceReconciliation: {
            storeId?: string;
            /** Format: date */
            begin: string;
            /** Format: date */
            end: string;
            periodPaidCents: string;
            periodRefundedCents: string;
            openingNetCents: string;
            currentOrderNetCents: string;
            ledgerNetCents: string;
            differenceCount: string;
            differences: components["schemas"]["FinanceDifference"][];
            hasMoreDifferences: boolean;
            /** Format: date-time */
            generatedAt: string;
        };
        Entry: {
            id?: string;
            actorType?: string;
            actorId?: string;
            action?: string;
            targetId?: string;
            detail?: string;
            /** Format: date-time */
            createdAt?: string;
            storeId?: string;
            campusId?: string;
        };
        MerchantReview: {
            id?: string;
            orderId?: string;
            /** Format: int32 */
            rating?: number;
            content?: string;
            reply?: string;
            hidden?: boolean;
            hideReason?: string;
            /** Format: date-time */
            createdAt?: string;
        };
        MerchantReviews: {
            stats?: components["schemas"]["ReviewStats"];
            records?: components["schemas"]["MerchantReview"][];
        };
        CollectionPromotion: {
            id?: string;
            storeId?: string;
            itemType?: string;
            itemId?: string;
            priceCents?: string;
            /** Format: date-time */
            startsAt?: string;
            /** Format: date-time */
            endsAt?: string;
            enabled?: boolean;
        };
        PageCollectionPromotion: {
            records: components["schemas"]["CollectionPromotion"][];
            nextCursor?: string;
            hasMore: boolean;
        };
        MerchantOrderResponse: {
            id?: string;
            number?: string;
            /** Format: int32 */
            status?: number;
            userId?: string;
            addressBookId?: string;
            orderTime?: string;
            checkoutTime?: string;
            /** Format: int32 */
            payMethod?: number;
            /** Format: int32 */
            payStatus?: number;
            /** @example 12.00 */
            amount?: string;
            remark?: string;
            phone?: string;
            address?: string;
            userName?: string;
            consignee?: string;
            cancelReason?: string;
            rejectionReason?: string;
            cancelTime?: string;
            estimatedDeliveryTime?: string;
            deliveryStatus?: boolean;
            deliveryTime?: string;
            /** Format: int32 */
            packAmount?: number;
            /** Format: int32 */
            tablewareNumber?: number;
            tablewareStatus?: boolean;
            requestId?: string;
            version?: string;
            businessDate?: string;
            snapshotSource?: string;
            buildingName?: string;
            room?: string;
            /** @example 12.00 */
            deliveryFee?: string;
            /** @example 12.00 */
            packagingFee?: string;
            payExpiresAt?: string;
            deliveryStartedAt?: string;
            courierId?: string;
            deliveryOverdue?: boolean;
            storeId?: string;
            campusId?: string;
            checkoutId?: string;
            fulfillment?: string;
            ready?: boolean;
            completedAt?: string;
            paidCents?: string;
            refundedCents?: string;
            reservedRefundCents?: string;
            shopDiscountCents?: string;
            platformDiscountCents?: string;
            pickupCode?: string;
            /** Format: int32 */
            pickupAttempts?: number;
            storeName?: string;
            campusName?: string;
            details?: components["schemas"]["OrderDetailResponse"][];
            delivery?: components["schemas"]["DeliveryResponse"];
            aftersales?: components["schemas"]["OrderAftersaleResponse"][];
            refunds?: components["schemas"]["RefundResponse"][];
            reviewed?: boolean;
        };
        CourierDetailResponse: {
            id?: string;
            name?: string;
            /** Format: int32 */
            number?: number;
            image?: string;
            dishFlavor?: string;
        };
        CourierOrderResponse: {
            id?: string;
            number?: string;
            /** Format: int32 */
            status?: number;
            version?: string;
            storeId?: string;
            campusId?: string;
            storeName?: string;
            campusName?: string;
            fulfillment?: string;
            ready?: boolean;
            orderTime?: string;
            buildingName?: string;
            phone?: string;
            address?: string;
            room?: string;
            consignee?: string;
            deliveryStartedAt?: string;
            deliveryTime?: string;
            completedAt?: string;
            deliveryOverdue?: boolean;
            courierId?: string;
            delivery?: components["schemas"]["DeliveryResponse"];
            details?: components["schemas"]["CourierDetailResponse"][];
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
                id: string;
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
                content?: never;
            };
        };
    };
    update: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LocalUserUpdate"];
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
                        data: components["schemas"]["LocalUserView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    staffUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
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
    availability: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Availability"];
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
    platformCouponUpdate: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
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
                id: string;
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
                campusId: string;
                id: string;
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
                storeId: string;
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
    policy: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["Policy"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    policy_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Policy"];
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
                storeId: string;
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
                storeId: string;
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
                storeId: string;
                id: string;
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
                storeId: string;
                type: string;
                id: string;
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
                storeId: string;
                id: string;
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
                storeId: string;
                id: string;
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
    staffUpdate_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
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
    ownAvailability: {
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
                            [key: string]: boolean;
                        };
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    ownAvailability_1: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Availability"];
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
                storeId: string;
                type: string;
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
                content?: never;
            };
        };
    };
    unfavorite: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: string;
                type: string;
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
                content?: never;
            };
        };
    };
    cartChange: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: string;
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
                storeId: string;
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
                id: string;
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
                id: string;
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
                content?: never;
            };
        };
    };
    uploadEvidence: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
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
                id: string;
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
    orderAction: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
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
    read: {
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
                content?: never;
            };
        };
    };
    read_1: {
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
                content?: never;
            };
        };
    };
    claimCoupon: {
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
                beforeId?: string;
                limit?: number;
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
                id: string;
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
                id: string;
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
    login: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LocalUserLogin"];
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
                id: string;
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
                id: string;
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
                content?: never;
            };
        };
    };
    create: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LocalUserCreate"];
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
                        data: components["schemas"]["LocalUserView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    reset: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["LocalUserResetPassword"];
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
                        data: components["schemas"]["LocalUserView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
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
    emergency: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Reason"];
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
    generate: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Generate"];
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
                        data: components["schemas"]["FinanceSettlement"];
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
                id: string;
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
                id: string;
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
                content?: never;
            };
        };
    };
    reassign: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Reassign"];
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
    exceptionCancel: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["ExceptionCancel"];
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
                campusId: string;
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
                campusId: string;
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
                id: string;
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
    acknowledge: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
            };
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Acknowledge"];
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
                storeId: string;
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
                storeId: string;
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
                storeId: string;
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
                storeId: string;
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
                storeId: string;
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
                storeId: string;
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
                storeId: string;
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
                content?: never;
            };
        };
    };
    adminCategories: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: string;
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
                storeId: string;
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
                id: string;
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
                content?: never;
            };
        };
    };
    orderAction_2: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
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
                content?: never;
            };
        };
    };
    decide: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
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
                id: string;
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
    orderAction_1: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                id: string;
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
    password_1: {
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
    login_1: {
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
    wallet_1: {
        parameters: {
            query: {
                q: components["schemas"]["Request"];
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
                        data: components["schemas"]["PageCollectionWallet"];
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
                campusId?: string;
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
            query?: {
                beforeId?: string;
                limit?: number;
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["PublicReviews"];
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
                categoryId?: string;
            };
            header?: never;
            path: {
                storeId: string;
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
                storeId: string;
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
    userCoupons: {
        parameters: {
            query: {
                q: components["schemas"]["Request"];
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["PageCollectionCoupon"];
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
                storeId: string;
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
                storeId: string;
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
    storeDetail: {
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
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["StoreResponse"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storePage: {
        parameters: {
            query?: {
                campusId?: string;
                query?: string;
                open?: boolean;
                cursor?: string;
                limit?: number;
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
                        data: components["schemas"]["StorePageResponse"];
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
                campusId: string;
                query?: string;
                categoryId?: string;
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
    searchPage: {
        parameters: {
            query: {
                campusId: string;
                query?: string;
                categoryId?: string;
                open?: boolean;
                cursor?: string;
                limit?: number;
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
                        data: components["schemas"]["SearchPageResponse"];
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
                beforeId?: string;
                limit?: number;
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
                        data: components["schemas"]["OrderResponse"][];
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
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["OrderResponse"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    unread: {
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
                            [key: string]: string;
                        };
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    unread_1: {
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
                            [key: string]: string;
                        };
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    messages: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
                unread?: boolean;
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
                        data: components["schemas"]["PageMessage"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    messages_1: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
                unread?: boolean;
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
                        data: components["schemas"]["PageMessage"];
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
    favorites_1: {
        parameters: {
            query: {
                q: components["schemas"]["Request"];
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
                        data: components["schemas"]["PageCollectionFavorite"];
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
    userCoupons_1: {
        parameters: {
            query: {
                q: components["schemas"]["Request"];
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
                        data: components["schemas"]["PageCollectionCoupon"];
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
                campusId: string;
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
    capabilities: {
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
                        data: components["schemas"]["AuthenticationCapabilities"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    account: {
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
                        data: components["schemas"]["LocalProfile"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    userAftersales: {
        parameters: {
            query?: {
                beforeId?: string;
                state?: string;
                orderNumber?: string;
                begin?: string;
                end?: string;
                taskId?: string;
                limit?: number;
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
    page: {
        parameters: {
            query?: {
                query?: string;
                enabled?: boolean;
                cursor?: string;
                limit?: number;
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
                        data: components["schemas"]["LocalUsersPage"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    active: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
            };
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
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["PageMapStringObject"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    staff_1: {
        parameters: {
            query: {
                q: components["schemas"]["Request"];
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
                        data: components["schemas"]["PageCollectionStaff"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    statement: {
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
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["FinanceSettlement"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    entries: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
            };
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
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["PageFinanceLedgerLine"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    preview_1: {
        parameters: {
            query: {
                storeId: string;
                date?: string;
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
                        data: components["schemas"]["FinancePreview"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    statements: {
        parameters: {
            query?: {
                storeId?: string;
                begin?: string;
                end?: string;
                cursor?: string;
                limit?: number;
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
                        data: components["schemas"]["PageFinanceSettlement"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    report: {
        parameters: {
            query: {
                campusId?: string;
                storeId?: string;
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
                        data: components["schemas"]["ReportSnapshot"];
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
                campusId?: string;
                storeId?: string;
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
    refunds: {
        parameters: {
            query?: {
                beforeId?: string;
                state?: string;
                orderNumber?: string;
                begin?: string;
                end?: string;
                taskId?: string;
                limit?: number;
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
                        data: components["schemas"]["RefundResponse"][];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    exceptions: {
        parameters: {
            query?: {
                storeId?: string;
                cursor?: string;
                limit?: number;
                state?: string;
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
                        data: components["schemas"]["PageExceptionView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    ledger: {
        parameters: {
            query?: {
                storeId?: string;
                begin?: string;
                end?: string;
                type?: string;
                cursor?: string;
                limit?: number;
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
                        data: components["schemas"]["PageFinanceLedgerLine"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    reconcile: {
        parameters: {
            query: {
                storeId?: string;
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
                        data: components["schemas"]["FinanceReconciliation"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    events: {
        parameters: {
            query?: {
                beforeId?: string;
                state?: string;
                orderNumber?: string;
                begin?: string;
                end?: string;
                taskId?: string;
                limit?: number;
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
    coupons: {
        parameters: {
            query: {
                q: components["schemas"]["Request"];
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
                        data: components["schemas"]["PageCollectionCoupon"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    audit: {
        parameters: {
            query?: {
                beforeId?: string;
                limit?: number;
                actorId?: string;
                action?: string;
                begin?: string;
                end?: string;
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
                        data: components["schemas"]["Entry"][];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    platformAftersales: {
        parameters: {
            query?: {
                beforeId?: string;
                state?: string;
                orderNumber?: string;
                begin?: string;
                end?: string;
                taskId?: string;
                limit?: number;
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
                storeId: string;
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
    staff_2: {
        parameters: {
            query: {
                q: components["schemas"]["Request"];
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["PageCollectionStaff"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    merchantStatement: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                storeId: string;
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
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["FinanceSettlement"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    merchantEntries: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
            };
            header?: never;
            path: {
                storeId: string;
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
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["PageFinanceLedgerLine"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    merchantPreview: {
        parameters: {
            query?: {
                date?: string;
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["FinancePreview"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    merchantStatements: {
        parameters: {
            query?: {
                begin?: string;
                end?: string;
                cursor?: string;
                limit?: number;
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["PageFinanceSettlement"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    adminReviews: {
        parameters: {
            query?: {
                beforeId?: string;
                limit?: number;
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["MerchantReviews"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeRefunds: {
        parameters: {
            query?: {
                beforeId?: string;
                state?: string;
                orderNumber?: string;
                begin?: string;
                end?: string;
                taskId?: string;
                limit?: number;
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["RefundResponse"][];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    promotions_1: {
        parameters: {
            query: {
                q: components["schemas"]["Request"];
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["PageCollectionPromotion"];
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
                storeId: string;
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
                beforeId?: string;
                limit?: number;
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["MerchantOrderResponse"][];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeExceptions: {
        parameters: {
            query?: {
                cursor?: string;
                limit?: number;
                state?: string;
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["PageExceptionView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    merchantLedger: {
        parameters: {
            query?: {
                begin?: string;
                end?: string;
                type?: string;
                cursor?: string;
                limit?: number;
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["PageFinanceLedgerLine"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeEvents: {
        parameters: {
            query?: {
                beforeId?: string;
                state?: string;
                orderNumber?: string;
                begin?: string;
                end?: string;
                taskId?: string;
                limit?: number;
            };
            header?: never;
            path: {
                storeId: string;
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
                storeId: string;
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
    coupons_1: {
        parameters: {
            query: {
                q: components["schemas"]["Request"];
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["PageCollectionCoupon"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    storeAudit: {
        parameters: {
            query?: {
                beforeId?: string;
                limit?: number;
                actorId?: string;
                action?: string;
                begin?: string;
                end?: string;
            };
            header?: never;
            path: {
                storeId: string;
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
                        data: components["schemas"]["Entry"][];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    merchantAftersales: {
        parameters: {
            query?: {
                beforeId?: string;
                state?: string;
                orderNumber?: string;
                begin?: string;
                end?: string;
                taskId?: string;
                limit?: number;
            };
            header?: never;
            path: {
                storeId: string;
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
    merchantStorePage: {
        parameters: {
            query?: {
                campusId?: string;
                query?: string;
                open?: boolean;
                cursor?: string;
                limit?: number;
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
                        data: components["schemas"]["StorePageResponse"];
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
                campusId?: string;
                storeId?: string;
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
                        data: components["schemas"]["ReportSnapshot"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    export_1: {
        parameters: {
            query: {
                campusId?: string;
                storeId?: string;
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
    order: {
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
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["MerchantOrderResponse"];
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
                beforeId?: string;
                limit?: number;
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
                        data: components["schemas"]["CourierOrderResponse"][];
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
                    "*/*": {
                        /** @example OK */
                        code: string;
                        message: string;
                        data: components["schemas"]["CourierOrderResponse"];
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
