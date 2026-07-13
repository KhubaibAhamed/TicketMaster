/**
 * notifications.js — Notification bell
 * =============================================================
 * Depends on: core.js (fetchWithAuth, authHeaders), dashboard-reports.js (brandColor),
 * ticket-detail.js (openTicketDetail)
 *
 * Everything related to the bell icon in the top bar: fetching, rendering,
 * opening/closing the dropdown panel, and marking notifications read.
 */

// --- 5. Notification Bell ---
let notifPanelOpen = false;
let latestNotifications = [];

async function refreshNotifications() {
    try {
        const response = await fetchWithAuth('/api/notifications', { headers: authHeaders() });
        if (!response.ok) return;
        const notifications = await response.json();
        latestNotifications = notifications;

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

// A notification a person can't act on or follow anywhere is a dead end —
// every type gets its own icon/color so severity and kind are both visible
// at a glance, and every row is clickable through to its ticket.
const NOTIF_ICON_MAP = {
    TICKET_CREATED:       { icon: 'confirmation_number', color: '--g-blue' },
    TICKET_ASSIGNED:      { icon: 'assignment_ind',       color: '--g-blue-dark' },
    TICKET_RESOLVED:      { icon: 'task_alt',             color: '--g-green' },
    TICKET_REOPENED:      { icon: 'refresh',              color: '--g-yellow' },
    TICKET_AUTO_CLOSED:   { icon: 'lock',                 color: '--g-text-muted' },
    TICKET_MERGED:        { icon: 'call_merge',           color: '--tm-signal' },
    SLA_WARNING:          { icon: 'schedule',             color: '--g-yellow' },
    SLA_CRITICAL:         { icon: 'alarm',                color: '--g-orange' },
    SLA_BREACHED:         { icon: 'warning',              color: '--g-red' },
    ESCALATION_TRIGGERED: { icon: 'trending_up',          color: '--g-red' }
};

function formatRelativeTime(dateStr) {
    const date = new Date(dateStr);
    const seconds = Math.floor((Date.now() - date.getTime()) / 1000);
    if (seconds < 60) return 'Just now';
    if (seconds < 3600) return Math.floor(seconds / 60) + 'm ago';
    if (seconds < 86400) return Math.floor(seconds / 3600) + 'h ago';
    if (seconds < 172800) return 'Yesterday';
    if (seconds < 604800) return Math.floor(seconds / 86400) + 'd ago';
    return date.toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
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
        row.setAttribute('role', 'button');
        row.setAttribute('tabindex', '0');
        row.title = new Date(n.createdAt).toLocaleString();

        const mapEntry = NOTIF_ICON_MAP[n.type] || { icon: 'info', color: '--g-text-muted' };
        // Resolve the CSS variable to a real hex value up front — concatenating
        // an alpha suffix onto the literal string "var(--g-blue)" produces
        // invalid CSS and silently drops the whole background declaration.
        const color = brandColor(mapEntry.color, '#3D5170');
        const icon = mapEntry.icon;
        const time = formatRelativeTime(n.createdAt);

        row.innerHTML = `
            <div style="width:36px; height:36px; border-radius:50%; background:${color}1A; flex-shrink:0;
                        display:flex; align-items:center; justify-content:center;">
                <span class="material-symbols-rounded" style="font-size:18px; color:${color};" aria-hidden="true">${icon}</span>
            </div>
            <div style="flex:1; min-width:0;">
                <p style="margin:0 0 2px 0; font-size:13px; line-height:1.4; color:var(--g-text-main);">${escapeHtml(n.message)}</p>
                <span style="font-size:11px; color:var(--g-text-muted);">${time}</span>
            </div>
            ${!n.read ? '<div style="width:8px;height:8px;border-radius:50%;background:var(--g-blue);flex-shrink:0;margin-top:6px;"></div>' : ''}
        `;
        row.onclick = () => openNotification(n);
        row.onkeypress = (e) => { if (e.key === 'Enter') openNotification(n); };
        list.appendChild(row);
    });
}

// Clicking a notification marks just that one read and, if it points at a
// ticket, takes you straight there — the whole point of a notification is
// to get you to the thing it's about, not just to acknowledge you saw it.
async function openNotification(n) {
    toggleNotificationPanel(false);

    if (!n.read) {
        try {
            await fetchWithAuth(`/api/notifications/${n.id}/read`, { method: 'PUT', headers: authHeaders() });
        } catch (e) { console.error('Failed to mark notification read:', e); }
        refreshNotifications();
    }

    if (n.relatedTicketId) {
        openTicketDetail(n.relatedTicketId);
    }
}

function toggleNotificationPanel(forceState) {
    const panel = document.getElementById('notifPanel');
    notifPanelOpen = typeof forceState === 'boolean' ? forceState : !notifPanelOpen;
    panel.style.display = notifPanelOpen ? 'block' : 'none';
}

async function markAllNotificationsRead() {
    await fetchWithAuth('/api/notifications/mark-all-read', { method: 'PUT', headers: authHeaders() });
    refreshNotifications(); // re-render without the unread styling and badge
}

// Close the panel if the user clicks anywhere outside of it, or presses Escape
document.addEventListener('click', (e) => {
    if (notifPanelOpen && !document.getElementById('notifPanel').contains(e.target)
        && !document.getElementById('notifBellBtn').contains(e.target)) {
        toggleNotificationPanel(false);
    }
});
document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && notifPanelOpen) toggleNotificationPanel(false);
});