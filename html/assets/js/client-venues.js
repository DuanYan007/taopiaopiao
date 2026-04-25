/**
 * 淘票票客户端 - 场馆相关接口
 * 文件：client-venues.js
 * API路径前缀：/api/client/venues
 */

// API基础路径
const VENUES_BASE_URL = '/api/client/venues';

/**
 * 场馆分页查询
 * @param {object} params - 查询参数
 * @param {string} params.keyword - 关键词搜索（场馆名称）
 * @param {string} params.city - 城市筛选
 * @param {string} params.district - 区域筛选
 * @param {number} params.page - 页码，默认1
 * @param {number} params.pageSize - 每页条数，默认10
 * @returns {Promise<object>} 返回数据 { list, total, page, pageSize }
 */
async function getVenueList(params = {}) {
    const defaultParams = {
        page: 1,
        pageSize: 10
    };
    return clientGet(VENUES_BASE_URL, { ...defaultParams, ...params });
}

/**
 * 场馆详情查询
 * @param {number} id - 场馆ID
 * @returns {Promise<object>} 场馆详情对象
 */
async function getVenueDetail(id) {
    return clientGet(`${VENUES_BASE_URL}/${id}`);
}

// 导出函数（如果使用模块化）
// export { getVenueList, getVenueDetail };
