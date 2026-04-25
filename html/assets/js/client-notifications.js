/**
 * 淘票票客户端 - 消息通知页面逻辑
 * 文件：client-notifications.js
 * 页面：notifications.html
 */

// 全局变量
let currentType = 'all';  // 当前筛选类型: all, order, system, activity
let notificationsList = [];

/**
 * 页面初始化
 */
document.addEventListener('DOMContentLoaded', async () => {
    console.log('消息通知页面初始化');

    // 更新用户信息显示
    updateUserInfo();

    // 绑定筛选按钮事件
    bindFilterEvents();

    // 加载消息列表
    await loadNotifications();
});

/**
 * 更新用户信息显示
 */
function updateUserInfo() {
    const userInfoDiv = document.querySelector('.header-actions > div:last-child');
    if (!userInfoDiv) return;

    const user = getCurrentUser();
    if (user) {
        userInfoDiv.innerHTML = `
            <div style="display: flex; align-items: center; gap: 8px;">
                <div style="width: 32px; height: 32px; border-radius: 50%; background: #e3f2fd; display: flex; align-items: center; justify-content: center;">
                    ${user.nickname ? user.nickname.charAt(0) : '用'}
                </div>
                <span>${user.nickname || '用户'}</span>
            </div>
        `;
    } else {
        userInfoDiv.innerHTML = `
            <a href="login.html" class="btn btn-outline btn-small">登录</a>
        `;
    }
}

/**
 * 绑定筛选按钮事件
 */
function bindFilterEvents() {
    const filterContainer = document.querySelector('.main-content .flex[style*="gap: 16px"], .main-content > .container > div[style*="gap: 16px"]');
    if (!filterContainer) return;

    const filterButtons = filterContainer.querySelectorAll('button');
    filterButtons.forEach(btn => {
        btn.addEventListener('click', async () => {
            // 更新按钮样式
            filterButtons.forEach(b => {
                b.classList.remove('btn-primary');
                b.classList.add('btn-secondary');
            });
            btn.classList.remove('btn-secondary');
            btn.classList.add('btn-primary');

            // 更新当前类型
            currentType = btn.dataset.type || 'all';

            // 重新加载
            await loadNotifications();
        });
    });
}

/**
 * 加载消息列表
 */
async function loadNotifications() {
    const container = document.getElementById('notificationsList');
    if (!container) return;

    try {
        // 显示加载状态
        container.innerHTML = `
            <div style="text-align: center; padding: 60px 0;">
                <div class="loading-spinner"></div>
                <div class="loading-text">加载中...</div>
            </div>
        `;

        // 构建请求参数
        const params = {};
        if (currentType !== 'all') {
            params.type = currentType;
        }

        // 获取消息列表
        const result = await clientGet('/api/client/notifications', params);
        notificationsList = result.list || [];

        // 渲染消息列表
        renderNotifications(notificationsList);

    } catch (error) {
        console.error('加载消息列表失败:', error);
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">🔔</div>
                <div class="empty-state-text">${error.message || '加载失败，请刷新页面重试'}</div>
            </div>
        `;
    }
}

/**
 * 渲染消息列表
 */
function renderNotifications(notifications) {
    const container = document.getElementById('notificationsList');
    if (!container) return;

    if (!notifications || notifications.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">🔔</div>
                <div class="empty-state-text">暂无消息</div>
            </div>
        `;
        return;
    }

    const html = notifications.map(item => renderNotificationCard(item)).join('');
    container.innerHTML = html;
}

/**
 * 渲染单个消息卡片
 */
function renderNotificationCard(item) {
    const isUnread = !item.isRead;
    const backgroundStyle = isUnread ? 'background: #f0f7ff;' : '';
    const unreadDot = isUnread ? '<div style="width: 8px; height: 8px; background: #d32f2f; border-radius: 50%; flex-shrink: 0;"></div>' : '';

    // 消息图标和背景色
    const iconConfig = getNotificationIcon(item.type);

    // 消息时间
    const timeText = formatTime(item.createdAt);

    // 操作按钮（如果有）
    let actionButton = '';
    if (item.actionUrl) {
        actionButton = `<a href="${item.actionUrl}" class="btn btn-secondary btn-small">${item.actionText || '查看详情'}</a>`;
    }

    return `
        <div class="list-item" style="padding: 20px; ${backgroundStyle}" onclick="handleReadNotification(${item.id}, this)">
            <div class="flex" style="gap: 16px;">
                <div style="width: 48px; height: 48px; border-radius: 50%; ${iconConfig.background}; display: flex; align-items: center; justify-content: center; flex-shrink: 0; font-size: 24px;">${iconConfig.icon}</div>
                <div style="flex: 1;">
                    <div class="flex-between" style="margin-bottom: 8px;">
                        <span style="font-weight: 600;">${item.title}</span>
                        <span class="text-small text-muted">${timeText}</span>
                    </div>
                    <div style="margin-bottom: 8px;">${item.content}</div>
                    ${actionButton ? `<div>${actionButton}</div>` : ''}
                </div>
                ${unreadDot}
            </div>
        </div>
    `;
}

/**
 * 获取消息图标配置
 */
function getNotificationIcon(type) {
    const iconMap = {
        'sale_start': { icon: '🎉', background: 'background: #e3f2fd;' },
        'order_confirmed': { icon: '✅', background: 'background: #e8f5e9;' },
        'event_reminder': { icon: '⏰', background: 'background: #fff3e0;' },
        'promotion': { icon: '🎁', background: 'background: #f3e5f5;' },
        'ticket_available': { icon: '⚠️', background: 'background: #ffebee;' },
        'system': { icon: '📢', background: 'background: #e3f2fd;' },
        'order_paid': { icon: '💰', background: 'background: #e8f5e9;' },
        'order_cancelled': { icon: '❌', background: 'background: #ffebee;' },
        'refund_success': { icon: '💸', background: 'background: #fff3e0;' }
    };
    return iconMap[type] || { icon: '📢', background: 'background: #e3f2fd;' };
}

/**
 * 格式化时间
 */
function formatTime(dateTime) {
    if (!dateTime) return '';

    const now = new Date();
    const date = new Date(dateTime);
    const diff = now - date;

    // 小于1分钟
    if (diff < 60000) {
        return '刚刚';
    }
    // 小于1小时
    if (diff < 3600000) {
        return Math.floor(diff / 60000) + '分钟前';
    }
    // 今天
    if (date.toDateString() === now.toDateString()) {
        return '今天 ' + String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
    }
    // 昨天
    const yesterday = new Date(now);
    yesterday.setDate(yesterday.getDate() - 1);
    if (date.toDateString() === yesterday.toDateString()) {
        return '昨天 ' + String(date.getHours()).padStart(2, '0') + ':' + String(date.getMinutes()).padStart(2, '0');
    }
    // 更早
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${month}-${day} ${hours}:${minutes}`;
}

/**
 * 处理点击消息（标记为已读）
 */
async function handleReadNotification(notificationId, element) {
    // 查找未读的消息并标记为已读
    const unreadDot = element.querySelector('[style*="background: #d32f2f"]');
    if (unreadDot) {
        try {
            await clientPost(`/api/client/notifications/${notificationId}/read`, {});
            // 移除未读标记
            unreadDot.remove();
            element.style.background = '';
        } catch (error) {
            console.error('标记已读失败:', error);
        }
    }
}

/**
 * 显示提示消息
 */
function showToast(message, type = 'info') {
    alert(message);
}

console.log('client-notifications.js 文件已加载');
