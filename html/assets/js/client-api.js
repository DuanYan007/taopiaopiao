/**
 * 淘票票客户端 - API请求封装
 * 统一处理请求、响应格式
 */

/**
 * 统一的API请求函数
 * @param {string} url - 请求路径
 * @param {object} options - 请求配置
 * @returns {Promise<any>} 响应数据
 */
async function clientRequest(url, options = {}) {
    const headers = {
        'Content-Type': 'application/json',
        ...options.headers
    };

    // 添加 X-User-Id 请求头 (从用户信息获取)
    const user = getCurrentUser?.();
    if (user?.id) {
        headers['X-User-Id'] = user.id;
    }

    // 添加 Authorization 请求头 (如果有 Token)
    const token = getToken?.();
    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    try {
        const response = await fetch(url, {
            ...options,
            headers
        });

        // 检查 HTTP 状态码
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        // 检查响应内容类型
        const contentType = response.headers.get('content-type');
        if (!contentType || !contentType.includes('application/json')) {
            // 返回的不是 JSON，可能是 HTML 错误页面
            const text = await response.text();
            console.error('API返回非JSON内容:', text.substring(0, 200));
            throw new Error('后端服务未响应或返回格式错误，请确认后端服务已启动');
        }

        const result = await response.json();

        // 打印完整响应用于调试
        console.log('API响应:', url, result);

        // 处理业务错误
        if (result.code !== 200) {
            const errorMsg = result.msg || '请求失败';
            console.error('业务错误 - code:', result.code, 'msg:', errorMsg, 'data:', result.data);
            throw new Error(errorMsg);
        }

        return result.data;

    } catch (error) {
        console.error('API请求错误:', url, error);
        throw error;
    }
}

/**
 * GET请求
 * @param {string} url - 请求路径
 * @param {object} params - 查询参数
 * @returns {Promise<any>} 响应数据
 */
async function clientGet(url, params = {}) {
    const queryString = new URLSearchParams(params).toString();
    const fullUrl = queryString ? `${url}?${queryString}` : url;
    return await clientRequest(fullUrl, { method: 'GET' });
}

/**
 * POST请求
 * @param {string} url - 请求路径
 * @param {object} data - 请求数据
 * @returns {Promise<any>} 响应数据
 */
function clientPost(url, data = {}) {
    return clientRequest(url, {
        method: 'POST',
        body: JSON.stringify(data)
    });
}

/**
 * PUT请求
 * @param {string} url - 请求路径
 * @param {object} data - 请求数据
 * @returns {Promise<any>} 响应数据
 */
function clientPut(url, data = {}) {
    return clientRequest(url, {
        method: 'PUT',
        body: JSON.stringify(data)
    });
}

/**
 * DELETE请求
 * @param {string} url - 请求路径
 * @returns {Promise<any>} 响应数据
 */
function clientDelete(url) {
    return clientRequest(url, { method: 'DELETE' });
}
