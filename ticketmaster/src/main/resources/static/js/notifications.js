/**
 * notifications.js — Notification bell
 * =============================================================
 * Depends on: core.js (fetchWithAuth, authHeaders)
 *
 * Everything related to the bell icon in the top bar: fetching, rendering,
 * opening/closing the dropdown panel, and marking notifications read.
 */

// --- 5. Notification Bell ---
let notifPanelOpen = false;

async function refreshNotifications() {
    try {
        const response = await fetchWithAuth('/api/notifications', { headers: authHeaders() });
        if (!response.ok) return;
        const notifications = await response.json();

        const unread = notifications.filter(n => !n.read);
        const badge = document.getElementById('notifBadge');
        if (unread.length > 0) {
            badge.style.display = 'block';
            badge.innerText = unread.length > 9 ? '9+' : unread.length;
        } else {
            badge.style.display = 'none';
        }

        renderNotificationList(notifications);
    } catch (e) { console.error('Notification fetch error:', e); }
}

function renderNotificationList(notifications) {
    const list = document.getElementById('notifList');
    if (!list) return;

    if (notifications.length === 0) {
        list.innerHTML = '<p style="text-align:center; color:var(--g-text-muted); font-size:13px; padding:32px 0;">You\'re all caught up!</p>';
        return;
    }

    list.innerHTML = '';
    notifications.forEach(n => {
        const row = document.createElement('div');
        row.className = 'notif-row' + (!n.read ? ' unread' : '');

        // Icon per notification type — uses the same Material Symbols font already in the page
        const iconMap = {
            TICKET_CREATED:   { icon: 'confirmation_number', color: 'var(--g-blue)' },
            SLA_WARNING:      { icon: 'schedule',             color: 'var(--g-yellow)' },
            SLA_CRITICAL:     { icon: 'alarm',                color: '#E37400' },
            SLA_BREACHED:     { icon: 'warning',              color: 'var(--g-red)' },
            TICKET_REOPENED:  { icon: 'refresh',              color: 'var(--g-yellow)' },
            TICKET_AUTO_CLOSED: { icon: 'lock',               color: 'var(--g-text-muted)' }
        };
        const { icon, color } = iconMap[n.type] || { icon: 'info', color: 'var(--g-text-muted)' };
        const time = new Date(n.createdAt).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });

        row.innerHTML = `
            <div style="width:36px; height:36px; border-radius:50%; background:${color}1A; flex-shrink:0;
                        display:flex; align-items:center; justify-content:center;">
                <span class="material-symbols-rounded" style="font-size:18px; color:${color};">${icon}</span>
            </div>
            <div style="flex:1; min-width:0;">
                <p style="margin:0 0 2px 0; font-size:13px; line-height:1.4; color:var(--g-text-main);">${escapeHtml(n.message)}</p>
                <span style="font-size:11px; color:var(--g-text-muted);">${time}</span>
            </div>
            ${!n.read ? '<div style="width:8px;height:8px;border-radius:50%;background:var(--g-blue);flex-shrink:0;margin-top:6px;"></div>' : ''}
        `;
        list.appendChild(row);
    });
}

function toggleNotificationPanel() {
    const panel = document.getElementById('notifPanel');
    notifPanelOpen = !notifPanelOpen;
    panel.style.display = notifPanelOpen ? 'block' : 'none';
}

async function markAllNotificationsRead() {
    await fetchWithAuth('/api/notifications/mark-all-read', { method: 'PUT', headers: authHeaders() });
    refreshNotifications(); // re-render without the unread styling and badge
}

// Close the panel if the user clicks anywhere outside of it
document.addEventListener('click', (e) => {
    if (notifPanelOpen && !document.getElementById('notifPanel').contains(e.target)
        && !document.getElementById('notifBellBtn').contains(e.target)) {
        document.getElementById('notifPanel').style.display = 'none';
        notifPanelOpen = false;
    }
});