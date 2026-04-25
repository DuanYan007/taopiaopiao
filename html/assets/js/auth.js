/**
 * 淘票票管理后台 - 认证模块
 * 负责Token的存储、获取、清除等管理功能
 */

const TOKEN_KEY = 'admin_token';
const USER_INFO_KEY = 'admin_userInfo';

/**
 * 获取Token
 * @returns {string|null} Token
 */
function getToken() {
    return sessionStorage.getItem(TOKEN_KEY) || localStorage.getItem(TOKEN_KEY);
}

/**
 * 保存Token和用户信息
 * @param {string} token - JWT Token
 * @param {object} userInfo - 用户信息对象
 * @param {boolean} remember - 是否记住登录状态
 */
function saveToken(token, userInfo, remember = false) {
    // 始终保存到sessionStorage
    sessionStorage.setItem(TOKEN_KEY, token);
    sessionStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo));

    // 如果选择记住，同时保存到localStorage
    if (remember) {
        localStorage.setItem(TOKEN_KEY, token);
        localStorage.setItem(USER_INFO_KEY, JSON.stringify(userInfo));
    } else {
        localStorage.removeItem(TOKEN_KEY);
        localStorage.removeItem(USER_INFO_KEY);
    }
}

/**
 * 获取用户信息
 * @returns {object|null} 用户信息对象
 */
function getUserInfo() {
    const userInfoStr = sessionStorage.getItem(USER_INFO_KEY) ||
                        localStorage.getItem(USER_INFO_KEY);
    return userInfoStr ? JSON.parse(userInfoStr) : null;
}

/**
 * 检查是否已登录
 * @returns {boolean} 是否已登录
 */
function isAuthenticated() {
    return !!getToken();
}

/**
 * 清除Token (登出)
 */
function clearToken() {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_INFO_KEY);
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_INFO_KEY);
}

/**
 * 登出并跳转到登录页
 */
async function logout() {
    try {
        const token = getToken();
        if (token) {
            await fetch('/api/admin/auth/logout', {
                method: 'POST',
                headers: {
                    'Authorization': `Bearer ${token}`
                }
            });
        }
    } catch (error) {
        console.error('登出接口调用失败:', error);
    } finally {
        clearToken();
        window.location.href = 'admin-login.html';
    }
}
