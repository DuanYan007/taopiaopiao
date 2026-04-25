/**
 * 淘票票客户端 - 订单相关接口
 * 文件：client-orders.js
 * API路径前缀：/api/client/orders
 */

// API基础路径
const ORDERS_BASE_URL = '/api/client/orders';

/**
 * 创建订单并直接支付（旧逻辑，已废弃）
 * @deprecated 支付流程已改为通过锁座接口返回支付URL
 * @param {number} sessionId - 场次ID
 * @param {number} eventId - 演出ID
 * @param {array} seatIds - 座位ID列表
 * @param {array} seatDetails - 座位详细信息 [{seatId, areaCode, areaName, rowNum, seatNum, price}, ...]
 * @param {number} totalAmount - 订单总金额
 * @returns {Promise<object>} 订单数据
 */
async function createOrder(sessionId, eventId, seatIds, seatDetails, totalAmount) {
    return clientPost(ORDERS_BASE_URL, {
        sessionId: sessionId,
        eventId: eventId,
        seatIds: seatIds,
        seatDetails: seatDetails,
        totalAmount: totalAmount
    });
}

/**
 * 取消订单
 * @param {string} orderNo - 订单号
 * @returns {Promise<boolean>} 是否成功
 */
async function cancelOrder(orderNo) {
    return clientPost(`${ORDERS_BASE_URL}/${orderNo}/cancel`, {});
}

/**
 * 删除订单
 * @param {string} orderNo - 订单号
 * @returns {Promise<boolean>} 是否成功
 */
async function deleteOrder(orderNo) {
    return clientPost(`${ORDERS_BASE_URL}/${orderNo}/delete`, {});
}

/**
 * 查询订单详情
 * @param {string} orderNo - 订单号
 * @returns {Promise<object>} 订单详情
 */
async function getOrderDetail(orderNo) {
    return clientGet(`${ORDERS_BASE_URL}/${orderNo}`);
}

/**
 * 查询用户订单列表
 * @param {object} params - 查询参数
 * @param {number} params.status - 订单状态筛选
 * @param {number} params.page - 页码
 * @param {number} params.pageSize - 每页数量
 * @returns {Promise<object>} 订单列表 { list, total, page, pageSize }
 */
async function getOrderList(params = {}) {
    const defaultParams = {
        page: 1,
        pageSize: 10
    };
    return clientGet(ORDERS_BASE_URL, { ...defaultParams, ...params });
}

/**
 * 订单状态枚举
 */
const ORDER_STATUS = {
    UNPAID: 1,       // 未支付
    PAID: 2,         // 已支付
    CANCELLED: 3,    // 已取消
    REFUNDED: 4      // 已退款
};

/**
 * 订单状态文本
 */
function getOrderStatusText(status) {
    const statusMap = {
        1: '未支付',
        2: '已支付',
        3: '已取消',
        4: '已退款'
    };
    return statusMap[status] || '未知';
}

/**
 * 订单状态颜色类
 */
function getOrderStatusClass(status) {
    const classMap = {
        1: 'status-unpaid',
        2: 'status-paid',
        3: 'status-cancelled',
        4: 'status-refunded'
    };
    return classMap[status] || '';
}

// 导出函数和常量
// export {
//     cancelOrder,
//     getOrderDetail,
//     getOrderList,
//     ORDER_STATUS,
//     getOrderStatusText,
//     getOrderStatusClass
// };
