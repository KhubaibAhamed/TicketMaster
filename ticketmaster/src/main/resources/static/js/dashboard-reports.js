/**
 * dashboard-reports.js — Dashboard charts & Reports page
 * =============================================================
 * Depends on: core.js. Uses the Chart.js library (loaded via CDN in index.html).
 *
 * Covers two related but distinct views:
 *   1. The Dashboard Overview tabbed chart panel (status/category/priority charts,
 *      SLA metric, per-developer widget, volume trend).
 *   2. The Reports page (Agent Performance table, CSAT summary card, date-range
 *      presets).
 */

async function loadCsatSummary() {
    const el = document.getElementById('csatSummaryText');
    if (!el) return;
    try {
        const res = await fetchWithAuth('/api/csat/summary', { headers: authHeaders() });
        if (!res.ok) { el.textContent = 'Unable to load CSAT data.'; return; }
        const data = await res.json();
        el.textContent = data.averageRating != null
            ? `${data.averageRating} / 5 average, from ${data.totalRatings} rating(s).`
            : `No ratings submitted yet (${data.totalRatings} sent, 0 responded).`;
    } catch (e) {
        console.error(e);
        el.textContent = 'Unable to load CSAT data.';
    }
}

async function loadAgentPerformance() {
    const tbody = document.getElementById('agentPerfBody');
    if (!tbody) return;
    renderSkeletonRows(tbody, 6, 4, ['70%', '30%', '30%', '40%', '40%', '30%']);

    const from = document.getElementById('reportFrom')?.value;
    const to   = document.getElementById('reportTo')?.value;

    const params = new URLSearchParams();
    if (from) params.set('from', from);
    if (to)   params.set('to', to);
    const query = params.toString() ? `?${params.toString()}` : '';

    updateReportRangeLabel(from, to);

    try {
        const res = await fetchWithAuth(`/api/reports/agent-performance${query}`, {
            headers: authHeaders()
        });
        if (!res.ok) return;
        const agents = await res.json();

        if (!agents.length) {
            tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--g-text-muted); padding:32px;">No agent data yet. Assign tickets to agents to see metrics.</td></tr>';
            return;
        }

        tbody.innerHTML = agents.map(a => {
            const slaColor = a.slaComplianceRate >= 80 ? 'var(--g-green)'
                : a.slaComplianceRate >= 60 ? 'var(--g-yellow)'
                    : 'var(--g-red)';
            const resolutionRate = a.totalAssigned > 0
                ? Math.round((a.totalResolved / a.totalAssigned) * 100) : 0;

            return `
                <tr>
                    <td>
                        <div style="display:flex; align-items:center; gap:10px;">
                            <div style="width:32px; height:32px; border-radius:50%; background:var(--g-blue-light);
                                        color:var(--g-blue); display:flex; align-items:center; justify-content:center;
                                        font-weight:600; font-size:13px; flex-shrink:0;">
                                ${escapeHtml(a.agent[0].toUpperCase())}
                            </div>
                            <span style="font-weight:500;">${escapeHtml(a.agent)}</span>
                        </div>
                    </td>
                    <td style="text-align:center; font-weight:600; color:var(--g-blue);">${a.totalAssigned}</td>
                    <td style="text-align:center;">
                        <span style="font-weight:600; color:var(--g-green);">${a.totalResolved}</span>
                        <span style="font-size:11px; color:var(--g-text-muted);"> (${resolutionRate}%)</span>
                    </td>
                    <td style="text-align:center; color:var(--g-text-main);">
                        ${a.avgResolutionHours != null ? a.avgResolutionHours + 'h' : '—'}
                    </td>
                    <td style="text-align:center; color:var(--g-text-main);">
                        ${a.avgFirstResponseHours != null ? a.avgFirstResponseHours + 'h' : '—'}
                    </td>
                    <td style="text-align:center;">
                        <span style="font-weight:600; color:${slaColor};">${a.slaComplianceRate}%</span>
                    </td>
                </tr>
            `;
        }).join('');
    } catch (e) {
        console.error('Failed to load agent performance:', e);
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center; color:var(--g-red); padding:32px;">Failed to load report.</td></tr>';
    }
}

function toDateInputValue(date) {
    // yyyy-MM-dd, in local time (not UTC) so it matches what <input type="date"> expects
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
}

function setReportRangePreset(days) {
    const to = new Date();
    const from = new Date();
    from.setDate(from.getDate() - (days - 1));

    document.getElementById('reportFrom').value = toDateInputValue(from);
    document.getElementById('reportTo').value   = toDateInputValue(to);
    loadAgentPerformance();
}

function clearReportRange() {
    document.getElementById('reportFrom').value = '';
    document.getElementById('reportTo').value   = '';
    loadAgentPerformance();
}

function updateReportRangeLabel(from, to) {
    const label = document.getElementById('reportRangeLabel');
    if (!label) return;
    if (!from && !to) {
        label.innerText = 'Showing: all time';
    } else if (from && to) {
        label.innerText = `Showing: ${from} to ${to}`;
    } else if (from) {
        label.innerText = `Showing: from ${from}`;
    } else {
        label.innerText = `Showing: through ${to}`;
    }
}

async function loadVolumeTrend(days) {
    activeVolumeDays = days || 30;

    // Highlight active button
    ['vol7', 'vol30', 'vol90'].forEach(id => {
        const btn = document.getElementById(id);
        if (btn) btn.style.fontWeight = id === 'vol' + activeVolumeDays ? '700' : '400';
    });

    try {
        const res = await fetchWithAuth(`/api/reports/volume?days=${activeVolumeDays}`, {
            headers: authHeaders()
        });
        if (!res.ok) return;
        const data = await res.json();

        const labels = data.map(d => {
            const date = new Date(d.date);
            return date.toLocaleDateString('en-IN', { month: 'short', day: 'numeric' });
        });
        const counts = data.map(d => d.count);

        const ctx = document.getElementById('volumeChart');
        if (!ctx) return;
        if (volumeChartInstance) volumeChartInstance.destroy();

        volumeChartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels,
                datasets: [{
                    label: 'Tickets raised',
                    data: counts,
                    borderColor: '#1A73E8',
                    backgroundColor: 'rgba(26,115,232,0.08)',
                    borderWidth: 2,
                    pointBackgroundColor: '#1A73E8',
                    pointRadius: 4,
                    pointHoverRadius: 6,
                    tension: 0.3,
                    fill: true
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: ctx => ` ${ctx.raw} ticket${ctx.raw !== 1 ? 's' : ''}`
                        }
                    }
                },
                scales: {
                    x: {
                        ticks: { font: { family: 'Inter', size: 11 }, color: '#5F6368' },
                        grid: { display: false },
                        border: { display: false }
                    },
                    y: {
                        beginAtZero: true,
                        ticks: {
                            font: { family: 'Inter', size: 11 },
                            color: '#5F6368',
                            precision: 0
                        },
                        grid: { color: '#F1F3F4' },
                        border: { display: false }
                    }
                }
            }
        });
    } catch (e) {
        console.error('Volume trend error:', e);
    }
}

// --- 3a. Dashboard Charts (tabbed panel) ---
function renderDashboardCharts(tickets) {
    renderSlaMetric(tickets);
    switchChartTab(activeChartTab, null, tickets);
}

function renderSlaMetric(tickets) {
    const withDeadline = tickets.filter(t => t.slaDueDate);
    if (withDeadline.length === 0) {
        document.getElementById('metricSla').innerText = 'N/A';
        return;
    }
    const compliant = withDeadline.filter(t => !t.slaBreached).length;
    const pct = Math.round((compliant / withDeadline.length) * 100);
    document.getElementById('metricSla').innerText = `${pct}%`;
    const barColor = pct >= 80 ? '#1E8E3E' : pct >= 60 ? '#F9AB00' : '#D93025';
    document.getElementById('slaProgressBar').style.width = `${pct}%`;
    document.getElementById('slaProgressBar').style.background = barColor;
    document.getElementById('metricSla').style.color = barColor;
}

function renderDevPersonalWidget(tickets) {
    const widget = document.getElementById('devPersonalWidget');
    if (!widget || widget.style.display === 'none') return;

    const currentUser = localStorage.getItem('jwt_username');
    if (!currentUser) return;

    const myTickets    = tickets.filter(t => t.assignedAgent === currentUser);
    const myOpen       = myTickets.filter(t => !['RESOLVED', 'CLOSED'].includes(t.status));
    const myWithSla    = myTickets.filter(t => t.slaDueDate);
    const myCompliant  = myWithSla.filter(t => !t.slaBreached);
    const slaRate      = myWithSla.length > 0
        ? Math.round((myCompliant.length / myWithSla.length) * 100) + '%' : 'N/A';

    const today        = new Date().toDateString();
    const resolvedToday = myTickets.filter(t =>
        t.status === 'RESOLVED' && t.resolvedAt &&
        new Date(t.resolvedAt).toDateString() === today
    ).length;

    document.getElementById('devOpenCount').innerText    = myOpen.length;
    document.getElementById('devSlaRate').innerText      = slaRate;
    document.getElementById('devResolvedToday').innerText = resolvedToday;
}

function switchChartTab(tab, el, tickets) {
    // Update active tab highlight
    activeChartTab = tab;
    if (el) {
        document.querySelectorAll('.chart-tab').forEach(t => t.classList.remove('active'));
        el.classList.add('active');
    }
    // Use live data or fall back to currentTickets if called from a tab click
    const data = tickets || currentTickets;
    if (tab === 'status')    renderStatusChart(data);
    if (tab === 'category')  renderCategoryChart(data);
    if (tab === 'priority')  renderPriorityChart(data);
}

function buildChart(type, labels, values, colors, opts = {}) {
    const ctx = document.getElementById('mainChart');
    if (!ctx) return;
    if (mainChartInstance) mainChartInstance.destroy();

    const baseFont = { family: 'Inter', size: 12 };
    const borderRadius = type === 'bar' ? 6 : 0;

    mainChartInstance = new Chart(ctx, {
        type,
        data: {
            labels,
            datasets: [{
                data: values,
                backgroundColor: type === 'doughnut' ? colors : colors.map(c => c + 'CC'),
                borderColor: type === 'doughnut' ? '#FFFFFF' : colors,
                borderWidth: type === 'doughnut' ? 3 : 2,
                borderRadius: type !== 'doughnut' ? borderRadius : undefined,
                hoverOffset: type === 'doughnut' ? 6 : undefined,
                borderSkipped: false
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            cutout: type === 'doughnut' ? '68%' : undefined,
            indexAxis: opts.horizontal ? 'y' : 'x',
            plugins: {
                legend: {
                    display: type === 'doughnut',
                    position: 'right',
                    labels: {
                        font: baseFont,
                        color: '#5F6368',
                        padding: 16,
                        usePointStyle: true,
                        pointStyleWidth: 8
                    }
                },
                tooltip: {
                    callbacks: {
                        label: ctx => ` ${ctx.label}: ${ctx.raw} ticket${ctx.raw !== 1 ? 's' : ''}`
                    }
                }
            },
            scales: type !== 'doughnut' ? {
                x: {
                    ticks: { font: baseFont, color: '#5F6368', precision: 0 },
                    grid: { color: '#F1F3F4' },
                    border: { display: false }
                },
                y: {
                    ticks: { font: baseFont, color: '#202124' },
                    grid: { display: false },
                    border: { display: false }
                }
            } : {}
        }
    });
}

function renderStatusChart(tickets) {
    const order  = ['OPEN', 'IN_PROGRESS', 'NEW', 'PENDING', 'REOPENED', 'RESOLVED', 'CLOSED'];
    const colors = { OPEN:'#4285F4', IN_PROGRESS:'#F9AB00', NEW:'#1A73E8',
        PENDING:'#FBBC04', REOPENED:'#D93025', RESOLVED:'#1E8E3E', CLOSED:'#137333' };
    const counts = {};
    order.forEach(s => counts[s] = 0);
    tickets.forEach(t => { if (counts[t.status] !== undefined) counts[t.status]++; });
    const active = order.filter(s => counts[s] > 0);
    if (!active.length) return;
    buildChart('doughnut', active, active.map(s => counts[s]), active.map(s => colors[s]));
}

function renderCategoryChart(tickets) {
    const counts = {};
    tickets.forEach(t => {
        const name = t.category ? t.category.name : 'Uncategorized';
        counts[name] = (counts[name] || 0) + 1;
    });
    const sorted  = Object.entries(counts).sort((a, b) => b[1] - a[1]);
    const labels  = sorted.map(([k]) => k);
    const values  = sorted.map(([, v]) => v);
    const palette = ['#1A73E8','#34A853','#FBBC04','#EA4335','#9334E6','#00ACC1'];
    buildChart('bar', labels, values, labels.map((_, i) => palette[i % palette.length]), { horizontal: true });
}

function renderPriorityChart(tickets) {
    const counts = { HIGH: 0, MEDIUM: 0, LOW: 0 };
    tickets.forEach(t => { if (counts[t.priority] !== undefined) counts[t.priority]++; });
    const labels = Object.keys(counts).filter(p => counts[p] > 0);
    const colors = { HIGH: '#D93025', MEDIUM: '#F9AB00', LOW: '#1E8E3E' };
    buildChart('bar', labels, labels.map(p => counts[p]), labels.map(p => colors[p]));
}