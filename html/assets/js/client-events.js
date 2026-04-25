/**
 * 淘票票客户端 - 演出相关接口
 * 文件：client-events.js
 * API路径前缀：/api/client/events
 */

// API基础路径
const EVENTS_BASE_URL = '/api/client/events';

/**
 * 演出分页查询
 * @param {object} params - 查询参数
 * @param {string} params.keyword - 关键词搜索（演出名称、艺人）
 * @param {string} params.city - 城市筛选
 * @param {string} params.type - 演出类型筛选
 * @param {string} params.status - 状态筛选
 * @param {number} params.page - 页码，默认1
 * @param {number} params.pageSize - 每页条数，默认10
 * @returns {Promise<object>} 返回数据 { list, total, page, pageSize }
 */
async function getEventList(params = {}) {
    const defaultParams = {
        page: 1,
        pageSize: 10
    };
    return clientGet(EVENTS_BASE_URL, { ...defaultParams, ...params });
}

/**
 * 演出详情查询
 * @param {number} id - 演出ID
 * @returns {Promise<object>} 演出详情对象
 */
async function getEventDetail(id) {
    return clientGet(`${EVENTS_BASE_URL}/${id}`);
}

/**
 * 查询演出的场次列表
 * @param {number} eventId - 演出ID
 * @param {object} params - 查询参数
 * @param {string} params.status - 状态筛选
 * @param {number} params.page - 页码，默认1
 * @param {number} params.pageSize - 每页条数，默认10
 * @returns {Promise<object>} 返回数据 { list, total, page, pageSize, totalPages }
 */
async function getEventSessions(eventId, params = {}) {
    const defaultParams = {
        page: 1,
        pageSize: 10
    };
    return clientGet(`${EVENTS_BASE_URL}/${eventId}/sessions`, { ...defaultParams, ...params });
}

// 导出函数（如果使用模块化）
// export { getEventList, getEventDetail, getEventSessions };
