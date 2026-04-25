/**
 * 淘票票客户端 - 场次相关接口
 * 文件：client-sessions.js
 * API路径前缀：/api/client/sessions
 */

// API基础路径
const SESSIONS_BASE_URL = '/api/client/sessions';

/**
 * 场次分页查询
 * @param {object} params - 查询参数
 * @param {string} params.keyword - 关键词搜索（场次名称）
 * @param {number} params.eventId - 演出ID筛选
 * @param {number} params.venueId - 场馆ID筛选
 * @param {string} params.status - 状态筛选
 * @param {number} params.page - 页码，默认1
 * @param {number} params.pageSize - 每页条数，默认10
 * @returns {Promise<object>} 返回数据 { list, total, page, pageSize, totalPages }
 */
async function getSessionList(params = {}) {
    const defaultParams = {
        page: 1,
        pageSize: 10
    };
    return clientGet(SESSIONS_BASE_URL, { ...defaultParams, ...params });
}

/**
 * 场次详情查询
 * @param {number} id - 场次ID
 * @returns {Promise<object>} 场次详情对象
 */
async function getSessionDetail(id) {
    return clientGet(`${SESSIONS_BASE_URL}/${id}`);
}

/**
 * 获取场次的座位列表
 * @param {number} sessionId - 场次ID
 * @returns {Promise<object>} 座位列表 { sessionId, seats: [] }
 */
async function getSessionSeats(sessionId) {
    return clientGet(`${SESSIONS_BASE_URL}/${sessionId}/seats`);
}

// 导出函数（如果使用模块化）
// export { getSessionList, getSessionDetail, getSessionSeats };
