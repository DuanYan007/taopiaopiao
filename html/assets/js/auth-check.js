/**
 * 淘票票管理后台 - 登录验证脚本
 * 在所有需要登录的页面中引入此脚本
 * 功能: 检查登录状态,未登录则跳转登录页
 */

(function() {
    'use strict';

    // 页面加载时检查登录状态
    window.addEventListener('DOMContentLoaded', function() {
        // 检查是否已登录
        const token = sessionStorage.getItem('admin_token') ||
                     localStorage.getItem('admin_token');

        if (!token) {
            // 未登录,跳转到登录页
            window.location.href = 'admin-login.html';
            return;
        }

        // 已登录,初始化页面
        initAdminPage();
    });

    /**
     * 初始化管理页面
     */
    function initAdminPage() {
        // 显示用户信息
        const userInfoStr = sessionStorage.getItem('admin_userInfo') ||
                           localStorage.getItem('admin_userInfo');

        if (userInfoStr) {
            try {
                const userInfo = JSON.parse(userInfoStr);

                // 更新侧边栏用户信息
                const userNameEl = document.querySelector('.admin-sidebar-footer .flex div div');
                if (userNameEl) {
                    userNameEl.textContent = userInfo.realName || userInfo.username;
                }

                // 更新用户邮箱
                const userEmailEl = document.querySelector('.admin-sidebar-footer .flex div .text-small');
                if (userEmailEl && userInfo.email) {
                    userEmailEl.textContent = userInfo.email;
                }
            } catch (e) {
                console.error('解析用户信息失败:', e);
            }
        }

        // 绑定登出按钮
        const logoutBtn = document.querySelector('.logout-btn');
        if (logoutBtn) {
            logoutBtn.addEventListener('click', function(e) {
                e.preventDefault();
                logout();
            });
        }

        // 监听其他标签页的登出事件
        window.addEventListener('storage', function(e) {
            if (e.key === 'admin_token' && !e.newValue) {
                // 其他标签页登出,当前页也跳转登录页
                window.location.href = 'admin-login.html';
            }
        });
    }

    /**
     * 登出函数
     */
    async function logout() {
        try {
            const token = sessionStorage.getItem('admin_token') ||
                         localStorage.getItem('admin_token');

            if (token) {
                await fetch('/api/admin/auth/logout', {
                    method: 'POST',
                    headers: {
                        'Authorization': 'Bearer ' + token
                    }
                });
            }
        } catch (error) {
            console.error('登出接口调用失败:', error);
        } finally {
            // 清除本地存储
            sessionStorage.removeItem('admin_token');
            sessionStorage.removeItem('admin_userInfo');
            localStorage.removeItem('admin_token');
            localStorage.removeItem('admin_userInfo');

            // 跳转到登录页
            window.location.href = 'admin-login.html';
        }
    }
})();
