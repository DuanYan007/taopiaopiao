/**
 * 淘票票客户端 - 订单中心页面逻辑
 * 文件：client-order-center.js
 * 页面：order-center.html
 */

// 全局变量
var currentPage = 1;
var pageSize = 10;
var totalRecords = 0;
var currentStatus = ''; // 当前筛选的状态，空字符串表示全部

/**
 * 页面初始化
 */
document.addEventListener('DOMContentLoaded', function() {
    console.log('client-order-center.js 初始化');
    updateUserInfo();
    initFilterTabs();
    loadOrderList();
});

/**
 * 更新用户信息显示
 */
function updateUserInfo() {
    var userInfoDiv = document.querySelector('.user-info');
    if (!userInfoDiv) return;

    var user = getCurrentUser();
    if (user) {
        var avatar = document.createElement('div');
        avatar.style.cssText = 'width: 32px; height: 32px; border-radius: 50%; background: #e3f2fd; display: flex; align-items: center; justify-content: center;';
        avatar.textContent = user.nickname ? user.nickname.charAt(0) : '用';

        var nameSpan = document.createElement('span');
        nameSpan.textContent = user.nickname || '用户';

        userInfoDiv.innerHTML = '';
        userInfoDiv.appendChild(avatar);
        userInfoDiv.appendChild(nameSpan);
    } else {
        userInfoDiv.innerHTML = '<a href="login.html" class="btn btn-outline btn-small">登录</a>';
    }
}

/**
 * 初始化筛选标签
 */
function initFilterTabs() {
    var tabs = document.querySelectorAll('.filter-tab');
    for (var i = 0; i < tabs.length; i++) {
        tabs[i].addEventListener('click', function() {
            // 移除所有active状态
            for (var j = 0; j < tabs.length; j++) {
                tabs[j].classList.remove('active');
            }
            // 添加当前active状态
            this.classList.add('active');

            // 更新筛选状态并重新加载
            currentStatus = this.getAttribute('data-status');
            currentPage = 1;
            loadOrderList();
        });
    }
}

/**
 * 加载订单列表
 */
function loadOrderList() {
    console.log('加载订单列表, 页码:', currentPage, '状态:', currentStatus || '全部');

    var loadingState = document.getElementById('loadingState');
    var emptyState = document.getElementById('emptyState');
    var orderList = document.getElementById('orderList');
    var pagination = document.getElementById('pagination');

    // 显示加载状态
    if (loadingState) {
        loadingState.style.display = 'flex';
    }
    if (emptyState) {
        emptyState.style.display = 'none';
    }
    if (orderList) {
        orderList.style.display = 'none';
    }
    if (pagination) {
        pagination.style.display = 'none';
    }

    // 构建请求参数
    var params = {
        page: currentPage,
        pageSize: pageSize
    };

    // 添加状态筛选
    if (currentStatus !== '') {
        params.status = parseInt(currentStatus);
    }

    console.log('请求参数:', params);

    // 调用API获取订单列表
    getOrderList(params)
        .then(function(result) {
            console.log('订单列表响应:', result);

            // 隐藏加载状态
            if (loadingState) {
                loadingState.style.display = 'none';
            }

            // 获取订单列表
            var list = result.list || [];
            totalRecords = result.total || 0;

            console.log('订单数量:', list.length, '总数:', totalRecords);

            // 如果没有订单，显示空状态
            if (list.length === 0) {
                if (emptyState) {
                    emptyState.style.display = 'block';
                    var emptyText = emptyState.querySelector('.empty-state-text');
                    if (emptyText) {
                        emptyText.textContent = currentStatus === '' ? '暂无订单' : '暂无' + getStatusText(parseInt(currentStatus)) + '订单';
                    }
                }
                return;
            }

            // 显示订单列表
            if (orderList) {
                orderList.style.display = 'block';
            }
            renderOrderList(list);

            // 显示分页
            if (pagination) {
                pagination.style.display = 'flex';
            }
            renderPagination();
        })
        .catch(function(error) {
            console.error('加载订单失败:', error);

            if (loadingState) {
                loadingState.style.display = 'none';
            }

            // 显示空状态
            if (emptyState) {
                emptyState.style.display = 'block';
                var emptyText = emptyState.querySelector('.empty-state-text');
                if (emptyText) {
                    emptyText.textContent = '加载失败: ' + error.message;
                }
            }
        });
}

/**
 * 渲染订单列表
 */
function renderOrderList(orders) {
    console.log('渲染订单列表, 数量:', orders.length);

    var orderList = document.getElementById('orderList');
    if (!orderList) return;

    orderList.innerHTML = '';

    for (var i = 0; i < orders.length; i++) {
        var order = orders[i];
        var card = createOrderCard(order);
        orderList.appendChild(card);
    }
}

/**
 * 创建订单卡片
 */
function createOrderCard(order) {
    var status = order.status;
    var statusText = order.statusDesc || getStatusText(status);
    var statusBadgeClass = getStatusBadgeClass(status);

    // 格式化时间
    var createdAt = formatDateTime(order.createdAt);
    var showTime = formatShowTime(order.showTime);

    // 根据订单状态设置透明度
    var opacityStyle = (status === 3 || status === 4) ? 'opacity: 0.7;' : '';

    var card = document.createElement('div');
    card.className = 'order-card';
    card.style.cssText = opacityStyle;

    // 构建订单卡片HTML
    var cardHTML =
        '<div class="order-card-header">' +
            '<div>' +
                '<span style="color: #666; margin-right: 16px;">订单号：' + order.orderNo + '</span>' +
                '<span style="color: #666;">' + createdAt + '</span>' +
            '</div>' +
            '<span class="badge ' + statusBadgeClass + '">' + statusText + '</span>' +
        '</div>' +
        '<div class="order-card-body">' +
            '<div class="flex" style="gap: 20px;">' +
                '<div class="order-item-cover" style="width: 100px; height: 140px; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);"></div>' +
                '<div style="flex: 1;">' +
                    '<div class="order-item-title" style="margin-bottom: 12px;">' + order.eventName + '</div>' +
                    '<div class="text-muted" style="margin-bottom: 8px;">' + showTime + '</div>' +
                    '<div class="text-muted" style="margin-bottom: 8px;">' + order.venueName + '</div>' +
                    '<div class="text-muted">' + order.sessionName + ' × ' + order.seatCount + '张</div>' +
                '</div>' +
                '<div style="text-align: right;">' +
                    '<div class="price price-large"' + (status === 3 ? ' style="color: #999; text-decoration: line-through;"' : '') + '>¥' + order.totalAmount + '</div>' +
                    '<div class="text-small text-muted" style="margin-top: 8px;">' + getStatusSubText(status) + '</div>' +
                '</div>' +
            '</div>' +
        '</div>' +
        '<div class="order-card-footer">' +
            '<div class="text-muted text-small">' + getStatusFooterText(status, order) + '</div>' +
            '<div class="flex" style="gap: 12px;">' + renderOrderActions(order) + '</div>' +
        '</div>';

    card.innerHTML = cardHTML;
    return card;
}

/**
 * 渲染订单操作按钮
 */
function renderOrderActions(order) {
    var status = order.status;
    var actions = '';

    switch (status) {
        case 1: // 未支付
            actions =
                '<button class="btn btn-secondary btn-small order-action-btn" onclick="cancelOrderByNo(\'' + order.orderNo + '\')">取消订单</button>' +
                '<a href="order-confirm.html?orderNo=' + order.orderNo + '" class="btn btn-primary btn-small order-action-btn">立即支付</a>';
            break;
        case 2: // 已支付
            actions =
                '<a href="order-detail.html?orderNo=' + order.orderNo + '" class="btn btn-secondary btn-small order-action-btn">查看详情</a>' +
                '<a href="order-detail.html?orderNo=' + order.orderNo + '" class="btn btn-primary btn-small order-action-btn">查看电子票</a>';
            break;
        case 3: // 已取消
            actions =
                '<button class="btn btn-secondary btn-small order-action-btn" onclick="deleteOrderByNo(\'' + order.orderNo + '\')">删除订单</button>' +
                '<a href="index.html" class="btn btn-primary btn-small order-action-btn">重新购买</a>';
            break;
        case 4: // 已退款
            actions =
                '<button class="btn btn-secondary btn-small order-action-btn" onclick="deleteOrderByNo(\'' + order.orderNo + '\')">删除订单</button>';
            break;
        default:
            actions = '<a href="order-detail.html?orderNo=' + order.orderNo + '" class="btn btn-secondary btn-small order-action-btn">查看详情</a>';
    }

    return actions;
}

/**
 * 渲染分页
 */
function renderPagination() {
    var pagination = document.getElementById('pagination');
    if (!pagination) return;

    var totalPages = Math.ceil(totalRecords / pageSize);

    if (totalPages <= 1) {
        pagination.style.display = 'none';
        return;
    }

    pagination.style.display = 'flex';
    pagination.innerHTML = '';

    // 上一页
    var prevBtn = document.createElement('button');
    prevBtn.className = 'pagination-page' + (currentPage <= 1 ? ' disabled' : '');
    prevBtn.textContent = '‹';
    prevBtn.onclick = function() {
        if (currentPage > 1) {
            currentPage--;
            loadOrderList();
        }
    };
    pagination.appendChild(prevBtn);

    // 页码
    var maxVisiblePages = 5;
    var startPage = Math.max(1, currentPage - Math.floor(maxVisiblePages / 2));
    var endPage = Math.min(totalPages, startPage + maxVisiblePages - 1);

    if (endPage - startPage < maxVisiblePages - 1) {
        startPage = Math.max(1, endPage - maxVisiblePages + 1);
    }

    if (startPage > 1) {
        pagination.appendChild(createPageLink(1));
        if (startPage > 2) {
            var ellipsis1 = document.createElement('span');
            ellipsis1.className = 'pagination-ellipsis';
            ellipsis1.textContent = '...';
            pagination.appendChild(ellipsis1);
        }
    }

    for (var i = startPage; i <= endPage; i++) {
        pagination.appendChild(createPageLink(i));
    }

    if (endPage < totalPages) {
        if (endPage < totalPages - 1) {
            var ellipsis2 = document.createElement('span');
            ellipsis2.className = 'pagination-ellipsis';
            ellipsis2.textContent = '...';
            pagination.appendChild(ellipsis2);
        }
        pagination.appendChild(createPageLink(totalPages));
    }

    // 下一页
    var nextBtn = document.createElement('button');
    nextBtn.className = 'pagination-page' + (currentPage >= totalPages ? ' disabled' : '');
    nextBtn.textContent = '›';
    nextBtn.onclick = function() {
        if (currentPage < totalPages) {
            currentPage++;
            loadOrderList();
        }
    };
    pagination.appendChild(nextBtn);
}

/**
 * 创建页码链接
 */
function createPageLink(page) {
    var link = document.createElement('span');
    link.className = 'pagination-page' + (page === currentPage ? ' active' : '');
    link.textContent = page;
    link.onclick = function() {
        if (page !== currentPage) {
            currentPage = page;
            loadOrderList();
        }
    };
    return link;
}

/**
 * 取消订单
 */
function cancelOrderByNo(orderNo) {
    if (!confirm('确定要取消该订单吗？')) {
        return;
    }

    console.log('取消订单:', orderNo);

    cancelOrder(orderNo)
        .then(function() {
            console.log('取消订单成功');
            alert('订单已取消');
            loadOrderList();
        })
        .catch(function(error) {
            console.error('取消订单失败:', error);
            alert('取消订单失败: ' + error.message);
        });
}

/**
 * 删除订单
 */
function deleteOrderByNo(orderNo) {
    if (!confirm('确定要删除该订单吗？删除后无法恢复。')) {
        return;
    }

    console.log('删除订单:', orderNo);

    clientPost('/api/client/orders/' + orderNo + '/delete', {})
        .then(function() {
            console.log('删除订单成功');
            alert('订单已删除');
            loadOrderList();
        })
        .catch(function(error) {
            console.error('删除订单失败:', error);
            alert('删除订单失败: ' + error.message);
        });
}

/**
 * 获取状态文本
 */
function getStatusText(status) {
    var statusMap = {
        1: '未支付',
        2: '已支付',
        3: '已取消',
        4: '已退款'
    };
    return statusMap[status] || '未知';
}

/**
 * 获取状态徽章类
 */
function getStatusBadgeClass(status) {
    var classMap = {
        1: 'badge-warning',
        2: 'badge-success',
        3: 'badge-secondary',
        4: 'badge-secondary'
    };
    return classMap[status] || 'badge-secondary';
}

/**
 * 获取状态副标题文本
 */
function getStatusSubText(status) {
    var subTextMap = {
        1: '待支付',
        2: '已支付',
        3: '未支付',
        4: '已退款'
    };
    return subTextMap[status] || '';
}

/**
 * 获取底部文本
 */
function getStatusFooterText(status, order) {
    switch (status) {
        case 1: // 未支付
            return '请尽快完成支付，超时订单将自动取消';
        case 2: // 已支付
            return '支付时间：' + formatDateTime(order.payTime || order.updatedAt);
        case 3: // 已取消
            return '取消时间：' + formatDateTime(order.updatedAt);
        case 4: // 已退款
            return '退款时间：' + formatDateTime(order.updatedAt);
        default:
            return '';
    }
}

/**
 * 格式化日期时间
 */
function formatDateTime(dateStr) {
    if (!dateStr) return '';

    var date = new Date(dateStr);
    var year = date.getFullYear();
    var month = String(date.getMonth() + 1).padStart(2, '0');
    var day = String(date.getDate()).padStart(2, '0');
    var hours = String(date.getHours()).padStart(2, '0');
    var minutes = String(date.getMinutes()).padStart(2, '0');

    return year + '-' + month + '-' + day + ' ' + hours + ':' + minutes;
}

/**
 * 格式化演出时间
 */
function formatShowTime(dateStr) {
    if (!dateStr) return '时间待定';

    var date = new Date(dateStr);
    var year = date.getFullYear();
    var month = String(date.getMonth() + 1).padStart(2, '0');
    var day = String(date.getDate()).padStart(2, '0');
    var weekDays = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
    var weekDay = weekDays[date.getDay()];
    var hours = String(date.getHours()).padStart(2, '0');
    var minutes = String(date.getMinutes()).padStart(2, '0');

    return year + '年' + month + '月' + day + '日 ' + weekDay + ' ' + hours + ':' + minutes;
}

console.log('client-order-center.js 文件已加载');
