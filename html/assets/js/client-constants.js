/**
 * 淘票票客户端 - 数据字典常量
 * 文件：client-constants.js
 */

/**
 * 演出状态映射
 * 值说明参考API文档 6.1 演出状态
 */
const EVENT_STATUS_MAP = {
    'on_sale': { text: '在售', class: 'badge-success' },
    'sold_out': { text: '售罄', class: 'badge-danger' }
    // draft、cancelled、ended 客户端不可见，无需映射
};

/**
 * 演出类型映射
 * 数据库类型值: concert, theatre, exhibition, sports, music, kids, dance
 */
const EVENT_TYPE_MAP = {
    'concert': '演唱会',
    'theatre': '话剧歌剧',
    'exhibition': '展览休闲',
    'sports': '体育赛事',
    'music': '音乐会',
    'kids': '儿童亲子',
    'dance': '舞蹈芭蕾'
};

/**
 * 场次状态映射
 * 值说明参考API文档 6.3 场次状态
 */
const SESSION_STATUS_MAP = {
    'not_started': { text: '未开始', class: 'badge-primary' },
    'on_sale': { text: '在售', class: 'badge-success' },
    'sold_out': { text: '售罄', class: 'badge-danger' },
    'selling_soon': { text: '即将开售', class: 'badge-warning' },
    'ongoing': { text: '进行中', class: 'badge-info' }
    // ended、cancelled 客户端不可见，无需映射
};

/**
 * 选座方式映射
 * 值说明参考API文档 6.4 选座方式
 */
const SEAT_SELECTION_MODE_MAP = {
    'select': '自选座',
    'auto': '系统自动选座'
};

/**
 * 格式化日期时间
 * @param {string} dateTime - 日期时间字符串 (yyyy-MM-dd HH:mm:ss)
 * @returns {string} 格式化后的日期时间
 */
function formatDateTime(dateTime) {
    if (!dateTime) return '-';
    const date = new Date(dateTime);
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day} ${hours}:${minutes}`;
}

/**
 * 格式化日期
 * @param {string} date - 日期字符串 (yyyy-MM-dd)
 * @returns {string} 格式化后的日期
 */
function formatDate(date) {
    if (!date) return '-';
    const d = new Date(date);
    const year = d.getFullYear();
    const month = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

/**
 * 格式化价格
 * @param {number} price - 价格
 * @returns {string} 格式化后的价格
 */
function formatPrice(price) {
    if (price == null || price === undefined) return '-';
    return `¥${price}`;
}

/**
 * 计算价格区间
 * @param {Array} ticketTiers - 票档数组
 * @returns {string} 价格区间字符串
 */
function getPriceRange(ticketTiers) {
    if (!ticketTiers || ticketTiers.length === 0) return '-';
    const prices = ticketTiers.map(tier => tier.price).filter(p => p != null);
    if (prices.length === 0) return '-';
    const minPrice = Math.min(...prices);
    const maxPrice = Math.max(...prices);
    return minPrice === maxPrice ? `¥${minPrice}` : `¥${minPrice} - ¥${maxPrice}`;
}

/**
 * 格式化座位数
 * @param {number} count - 座位数
 * @returns {string} 格式化后的座位数
 */
function formatSeatCount(count) {
    if (count == null || count === undefined) return '-';
    return count.toLocaleString();
}

// 导出常量和函数（如果使用模块化）
// export {
//     EVENT_STATUS_MAP,
//     EVENT_TYPE_MAP,
//     SESSION_STATUS_MAP,
//     SEAT_SELECTION_MODE_MAP,
//     formatDateTime,
//     formatDate,
//     formatPrice,
//     getPriceRange,
//     formatSeatCount
// };
