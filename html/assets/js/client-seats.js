/**
 * 淘票票客户端 - 座位模板相关接口
 * 文件：client-seats.js
 * API路径前缀：/api/client/seat-templates
 */

// API基础路径
const SEAT_TEMPLATES_BASE_URL = '/api/client/seat-templates';

/**
 * 获取座位布局
 * @param {number} templateId - 模板ID
 * @returns {Promise<object>} 座位布局数据 { templateId, templateName, venueId, layoutType, layoutData }
 */
async function getSeatLayout(templateId) {
    return clientGet(`${SEAT_TEMPLATES_BASE_URL}/${templateId}/layout`);
}

/**
 * 获取秒杀座位布局（新接口）
 * 一次性返回座位布局、状态和价格信息
 * @param {number} sessionId - 场次ID
 * @returns {Promise<object>} 座位数据 { sessionId, meta, areas }
 */
async function getSeckillLayout(sessionId) {
    return clientGet(`/api/seckill/${sessionId}/layout`);
}

/**
 * 解析布局数据 JSON 字符串
 * @param {string} layoutDataStr - JSON 字符串格式的布局数据
 * @returns {object} 解析后的布局对象
 */
function parseLayoutData(layoutDataStr) {
    if (!layoutDataStr) return null;
    try {
        return JSON.parse(layoutDataStr);
    } catch (e) {
        console.error('解析布局数据失败:', e);
        return null;
    }
}

/**
 * 获取座位状态对应的 CSS 类名
 * @param {string} status - 座位状态 (available, sold, locked, unavailable, selected)
 * @returns {string} CSS 类名
 */
function getSeatStatusClass(status) {
    const statusMap = {
        'available': 'seat-available',
        'sold': 'seat-sold',
        'locked': 'seat-locked',
        'unavailable': 'seat-unavailable',
        'selected': 'seat-selected'
    };
    return statusMap[status] || 'seat-available';
}

/**
 * 获取座位状态对应的颜色
 * @param {string} status - 座位状态
 * @returns {string} 颜色值
 */
function getSeatStatusColor(status) {
    const colorMap = {
        'available': '#1976d2',
        'sold': '#999',
        'locked': '#f57c00',
        'unavailable': '#595959',
        'selected': '#1976d2'
    };
    return colorMap[status] || '#1976d2';
}

/**
 * 座位状态枚举
 */
const SEAT_STATUS = {
    AVAILABLE: 'available',    // 可售
    SOLD: 'sold',              // 已售
    LOCKED: 'locked',          // 已锁定（他人正在购买）
    UNAVAILABLE: 'unavailable',// 不可用
    SELECTED: 'selected'       // 已选（当前用户选中）
};

/**
 * 锁定座位
 * @param {number} sessionId - 场次ID
 * @param {number} eventId - 演出ID
 * @param {number} userId - 用户ID
 * @param {array} seatIds - 座位ID列表
 * @param {number} expireSeconds - 锁定时长(秒)，默认900(15分钟)
 * @param {number} unitPrice - 座位单价（前端已确保所有选中座位价格一致）
 * @returns {Promise<object>} 锁座结果 { success, code, message, lockedSeats, expireTime, orderNo, orderStatus, paymentStatus, nextPollMs, payUrl? }
 */
async function lockSeats(sessionId, eventId, userId, seatIds, expireSeconds = 900, unitPrice) {
    const params = {
        sessionId: sessionId,
        eventId: eventId,
        userId: userId,
        seatIds: seatIds,
        expireSeconds: expireSeconds
    };
    // 如果传入了单价，添加到请求参数
    if (unitPrice !== undefined && unitPrice !== null) {
        params.unitPrice = unitPrice;
    }
    return clientPost('/api/seckill/lock', params);
}

// 导出函数和常量
// export {
//     getSeatLayout,
//     parseLayoutData,
//     getSeatStatusClass,
//     getSeatStatusColor,
//     SEAT_STATUS,
//     lockSeats
// };
