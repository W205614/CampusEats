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
    "/api/v1/admin/shop": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["shop"];
        put: operations["configure"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/quotas": {
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
    "/api/v1/admin/products/{type}/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["updateProduct"];
        post?: never;
        delete: operations["deleteProduct"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/employees/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["updateEmployee"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/categories/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put: operations["updateCategory"];
        post?: never;
        delete: operations["deleteCategory"];
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/buildings/{id}": {
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
    "/api/v1/user/orders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["orders"];
        put?: never;
        post: operations["submit"];
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
    "/api/v1/user/orders/{id}/pay": {
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
    "/api/v1/user/orders/{id}/cancel": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["cancel"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/checkout/preview": {
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
    "/api/v1/user/cart/items": {
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
    "/api/v1/user/cart/clear": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["clear"];
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
    "/api/v1/admin/uploads": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["upload"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/tasks/{type}/{id}/retry": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["retry"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/products/{type}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["products"];
        put?: never;
        post: operations["product"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/orders/{id}/{action}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get?: never;
        put?: never;
        post: operations["action"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/employees": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["employees"];
        put?: never;
        post: operations["employee"];
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/categories": {
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
    "/api/v1/admin/buildings": {
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
    "/api/v1/user/orders/by-request/{key}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["byKey"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/menu/items": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["menu"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/menu/categories": {
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
    "/api/v1/user/cart": {
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
    "/api/v1/user/buildings": {
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
    "/api/v1/admin/tasks/{type}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["list"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/user/shop": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["shop_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/reports": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["reports"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/reports/export": {
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
    "/api/v1/user/orders/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["detail"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/orders/{id}": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["detail_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/orders": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["orders_1"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/dashboard": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        get: operations["dashboard"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/admin/couriers": {
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
    "/api/v1/admin/audit": {
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
        Shop: {
            open?: boolean;
            /** @example 12.00 */
            deliveryFee: string;
            /** @example 12.00 */
            packagingFee: string;
            hours: string[];
            phone?: string;
        };
        Quota: {
            dishId: string;
            /** Format: int32 */
            total?: number;
            defaultQuota?: boolean;
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
        Employee: {
            username: string;
            name: string;
            role: string;
            enabled?: boolean;
            password?: string;
        };
        Category: {
            name: string;
            /** Format: int32 */
            type?: number;
            /** Format: int32 */
            sort?: number;
            enabled?: boolean;
        };
        Building: {
            name: string;
            enabled?: boolean;
        };
        Submit: {
            addressId: string;
            cartVersion: string;
            quoteHash: string;
            remark?: string;
        };
        DetailView: {
            id?: string;
            name?: string;
            /** Format: int32 */
            number?: number;
            /** @example 12.00 */
            amount?: string;
            dishFlavor?: string;
            image?: string;
        };
        OrderView: {
            id?: string;
            number?: string;
            version?: string;
            /** Format: int32 */
            status?: number;
            /** Format: int32 */
            payStatus?: number;
            /** @example 12.00 */
            amount?: string;
            /** Format: date-time */
            orderTime?: string;
            consignee?: string;
            phone?: string;
            address?: string;
            buildingName?: string;
            room?: string;
            remark?: string;
            courierId?: string;
            deliveryOverdue?: boolean;
            details?: components["schemas"]["DetailView"][];
            refund?: components["schemas"]["RefundView"];
            snapshotSource?: string;
            requestId?: string;
            /** @example 12.00 */
            deliveryFee?: string;
            /** @example 12.00 */
            packagingFee?: string;
        };
        RefundView: {
            id?: string;
            state?: string;
            /** Format: int32 */
            attempts?: number;
            lastError?: string;
        };
        Version: {
            version: string;
        };
        OrderAction: {
            version: string;
            reason?: string;
            courierId?: string;
        };
        Preview: {
            addressId: string;
            cartVersion: string;
        };
        CartItemView: {
            id?: string;
            itemType?: string;
            itemId?: string;
            /** Format: int32 */
            quantity?: number;
            flavors?: {
                [key: string]: string;
            };
            name?: string;
            /** @example 12.00 */
            price?: string;
            image?: string;
            /** Format: int32 */
            status?: number;
        };
        Quote: {
            cartVersion?: string;
            items?: components["schemas"]["CartItemView"][];
            subtotal?: string;
            /** @example 12.00 */
            deliveryFee?: string;
            /** @example 12.00 */
            packagingFee?: string;
            total?: string;
            quoteHash?: string;
            businessDate?: string;
        };
        CartChange: {
            cartVersion: string;
            itemType: string;
            itemId: string;
            flavors?: {
                [key: string]: string;
            };
            /** Format: int32 */
            delta?: number;
        };
        Cart: {
            cartVersion?: string;
            items?: components["schemas"]["CartItemView"][];
        };
        WxLogin: {
            code: string;
        };
        SessionView: {
            token?: string;
            id?: string;
            name?: string;
            /** @enum {string} */
            role?: "ADMIN" | "OPERATOR" | "DELIVERER" | "USER";
            mustChangePassword?: boolean;
            expiresAt?: string;
        };
        DemoLogin: {
            /** Format: int32 */
            account?: number;
        };
        Password: {
            oldPassword: string;
            password: string;
        };
        Login: {
            username: string;
            password: string;
        };
        ComponentView: {
            dishId?: string;
            /** Format: int32 */
            copies?: number;
            name?: string;
            currentName?: string;
            /** Format: int32 */
            status?: number;
        };
        FlavorView: {
            id?: string;
            name?: string;
            values?: string[];
        };
        ProductView: {
            id?: string;
            name?: string;
            categoryId?: string;
            /** @example 12.00 */
            price?: string;
            image?: string;
            description?: string;
            /** Format: int32 */
            status?: number;
            /** Format: int32 */
            remaining?: number;
            itemType?: string;
            flavors?: components["schemas"]["FlavorView"][];
            components?: components["schemas"]["ComponentView"][];
            /** Format: int32 */
            defaultQuota?: number;
        };
        CategoryView: {
            id?: string;
            name?: string;
            /** Format: int32 */
            type?: number;
            /** Format: int32 */
            sort?: number;
            /** Format: int32 */
            status?: number;
        };
        BuildingView: {
            id?: string;
            name?: string;
            enabled?: boolean;
        };
        AddressView: {
            id?: string;
            buildingId?: string;
            buildingName?: string;
            room?: string;
            consignee?: string;
            phone?: string;
            isDefault?: boolean;
            enabled?: boolean;
        };
        ShopView: {
            open?: boolean;
            accepting?: boolean;
            /** @example 12.00 */
            deliveryFee?: string;
            /** @example 12.00 */
            packagingFee?: string;
            hours?: string[];
            phone?: string;
        };
        PageOrderView: {
            total?: string;
            records?: components["schemas"]["OrderView"][];
        };
        EmployeeView: {
            id?: string;
            name?: string;
            username?: string;
            role?: string;
            /** Format: int32 */
            status?: number;
            mustChangePassword?: boolean;
        };
        Actor: {
            id?: string;
            type?: string;
            role?: string;
            jti?: string;
            mustChangePassword?: boolean;
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
    shop: {
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
                        data: components["schemas"]["ShopView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    configure: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Shop"];
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
                        }[];
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
            path?: never;
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
    updateProduct: {
        parameters: {
            query?: never;
            header?: never;
            path: {
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
    deleteProduct: {
        parameters: {
            query?: never;
            header?: never;
            path: {
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
    updateEmployee: {
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
                "application/json": components["schemas"]["Employee"];
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
    updateCategory: {
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
    deleteCategory: {
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
    buildingUpdate: {
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
    orders: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
                status?: number;
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
                        data: components["schemas"]["PageOrderView"];
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
                "application/json": components["schemas"]["Submit"];
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
                        data: components["schemas"]["OrderView"];
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
                        data: components["schemas"]["OrderView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    cancel: {
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
                "application/json": components["schemas"]["OrderAction"];
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
                        data: components["schemas"]["OrderView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
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
                "application/json": components["schemas"]["Preview"];
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
    cartChange: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
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
                        data: components["schemas"]["Cart"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    clear: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
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
                        data: components["schemas"]["Cart"];
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
                        data: components["schemas"]["AddressView"][];
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
    upload: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
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
    retry: {
        parameters: {
            query?: never;
            header?: never;
            path: {
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
    products: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                type: string;
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
                        data: components["schemas"]["ProductView"][];
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
    action: {
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
                "application/json": components["schemas"]["OrderAction"];
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
                        data: components["schemas"]["OrderView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    employees: {
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
                        data: components["schemas"]["EmployeeView"][];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    employee: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["Employee"];
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
    adminCategories: {
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
                        data: components["schemas"]["CategoryView"][];
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
            path?: never;
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
    adminBuildings: {
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
                        data: components["schemas"]["BuildingView"][];
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
            path?: never;
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
    byKey: {
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
                        data: components["schemas"]["OrderView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    menu: {
        parameters: {
            query: {
                categoryId: number;
                type: string;
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
                        data: components["schemas"]["ProductView"][];
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
                        data: components["schemas"]["CategoryView"][];
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
                        data: components["schemas"]["Cart"];
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
                        data: components["schemas"]["BuildingView"][];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    list: {
        parameters: {
            query?: never;
            header?: never;
            path: {
                type: string;
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
                        data: {
                            [key: string]: unknown;
                        }[];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    shop_1: {
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
                        data: components["schemas"]["ShopView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    reports: {
        parameters: {
            query: {
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
    export: {
        parameters: {
            query: {
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
                        /** Format: byte */
                        data: string;
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    detail: {
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
                        data: components["schemas"]["OrderView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    detail_1: {
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
                        data: components["schemas"]["OrderView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    orders_1: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
                status?: number;
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
                        data: components["schemas"]["PageOrderView"];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
    dashboard: {
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
    couriers: {
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
                        }[];
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
    audit: {
        parameters: {
            query?: {
                page?: number;
                size?: number;
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
                        data: {
                            [key: string]: unknown;
                        }[];
                        /** Format: uuid */
                        requestId: string;
                    };
                };
            };
        };
    };
}
