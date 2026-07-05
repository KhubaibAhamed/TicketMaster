/**
 * ticket-create.js — "Raise Incident" form
 * =============================================================
 * Depends on: core.js, tickets-console.js (refreshDataPipeline, loadCategories)
 *
 * The create-ticket modal end to end: form submit handler, char counter,
 * attachment label, AI category/priority suggestion, live "Related articles"
 * search-as-you-type, and dynamically rendered custom fields per category.
 */

function updateCharCounter() {
    const textarea = document.getElementById('description');
    const counter  = document.getElementById('descCharCounter');
    if (!textarea || !counter) return;
    const len = textarea.value.length;
    counter.innerText = len + ' / 1000';
    counter.className = 'char-counter';
    if (len >= 1000) counter.classList.add('at-limit');
    else if (len >= 850) counter.classList.add('near-limit');
}

function updateCreateAttachLabel(input) {
    const label = document.getElementById('createAttachLabel');
    if (!label) return;
    if (input.files.length === 0) {
        label.innerText = 'Attach files (optional, max 10 MB each)';
    } else if (input.files.length === 1) {
        label.innerText = input.files[0].name;
    } else {
        label.innerText = `${input.files.length} files selected`;
    }
}

// --- Related articles live search (Phase C) ---
let kbSearchDebounceTimer = null;

function scheduleRelatedArticlesSearch() {
    clearTimeout(kbSearchDebounceTimer);
    kbSearchDebounceTimer = setTimeout(searchRelatedArticles, 500);
}

async function searchRelatedArticles() {
    const title = document.getElementById('title')?.value?.trim() || '';
    const description = document.getElementById('description')?.value?.trim() || '';
    const query = (title + ' ' + description).trim();
    const panel = document.getElementById('relatedArticlesPanel');
    if (!panel) return;

    if (query.length < 5) {
        panel.style.display = 'none';
        return;
    }

    try {
        const res = await fetchWithAuth(`/api/kb/articles/search?q=${encodeURIComponent(query)}`, { headers: authHeaders() });
        if (!res.ok) { panel.style.display = 'none'; return; }
        const articles = await res.json();

        if (!articles.length) {
            panel.style.display = 'none';
            return;
        }

        panel.innerHTML = `
            <p style="margin:0 0 8px 0; font-size:12px; font-weight:600; color:var(--g-text-muted);">
                <span class="material-symbols-rounded" style="font-size:14px; vertical-align:middle;">auto_stories</span>
                Related articles — this might solve it faster than filing a ticket:
            </p>
            ${articles.map(a => `
                <div style="padding:8px 10px; border:1px solid var(--g-border); border-radius:8px; margin-bottom:6px; cursor:pointer;"
                     onclick="viewRelatedArticle(${a.id})">
                    <span style="font-size:13px; font-weight:500; color:var(--g-blue);">${escapeHtml(a.title)}</span>
                </div>
            `).join('')}
        `;
        panel.style.display = 'block';
        window.__kbSearchResults = articles; // cache for the click handler below
    } catch (e) {
        console.error('KB search failed:', e);
        panel.style.display = 'none';
    }
}

function viewRelatedArticle(articleId) {
    const article = (window.__kbSearchResults || []).find(a => a.id === articleId);
    if (!article) return;
    alert(`${article.title}\n\n${article.body}`); // simple viewer — swap for a nicer modal if desired later
}

// --- Forms & Modals ---
document.getElementById('ticketForm').addEventListener('submit', async (e) => {
    e.preventDefault();

    // 1. Capture the response to get the newly created ticket's ID
    const response = await fetchWithAuth('/api/tickets', {
        method: 'POST', headers: authHeaders(),
        body: JSON.stringify({
            title: document.getElementById('title').value,
            description: document.getElementById('description').value,
            priority: document.getElementById('priority').value,
            category: { id: Number(document.getElementById('category').value) },
            status: 'NEW',
            customFieldValues: collectCustomFieldValues()
        })
    });

    if (!response.ok) {
        showToast('Failed to raise ticket. Please try again.', 'error');
        return;
    }

    // 2. Extract the actual ticket data from the server
    const createdTicket = await response.json();
    const newTicketId = createdTicket.id;

    const fileInput = document.getElementById('createAttachInput');
    const files = fileInput?.files;

    // Reset the form
    document.getElementById('ticketForm').reset();
    document.getElementById('dynamicCustomFieldsContainer').innerHTML = '';
    currentCustomFieldDefs = [];
    updateCharCounter();
    updateCreateAttachLabel(document.getElementById('createAttachInput'));
    document.getElementById('createModal').close();

    // 3. Upload attachments using the REAL new ticket ID
    if (files && files.length > 0) {
        let uploaded = 0;
        for (const file of files) {
            const fd = new FormData();
            fd.append('file', file);
            const uploadRes = await fetchWithAuth(`/api/tickets/${newTicketId}/attachments`, {
                method: 'POST', credentials: 'include', body: fd
            });
            if (uploadRes.ok) uploaded++;
        }
        showToast(`Ticket #${newTicketId} raised with ${uploaded} attachment(s).`, 'success');
    } else {
        showToast(`Ticket #${newTicketId} raised successfully.`, 'success');
    }

    switchView('viewConsole', document.querySelectorAll('.nav-item')[2]);
    await refreshDataPipeline();
    await loadConsolePage(0, '');
});

async function suggestClassification() {
    const title = document.getElementById('title')?.value?.trim();
    const description = document.getElementById('description')?.value?.trim();

    if (!title) {
        showToast('Please enter a title first.', 'warning');
        return;
    }

    const btn = document.getElementById('suggestBtn');
    btn.textContent = '⏳ Analysing...';
    btn.disabled = true;

    try {
        const response = await fetch('/api/tickets/suggest', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'include',
            body: JSON.stringify({ title, description })
        });

        if (!response.ok) throw new Error('Suggestion request failed');

        const result = await response.json();

        if (result.suggestedCategoryId) {
            const categorySelect = document.getElementById('category');
            if (categorySelect) {
                categorySelect.value = result.suggestedCategoryId;
                document.getElementById('categoryAiChip').style.display = 'inline-flex';
            }
        }

        if (result.suggestedPriority) {
            const prioritySelect = document.getElementById('priority');
            if (prioritySelect) {
                prioritySelect.value = result.suggestedPriority;
                document.getElementById('priorityAiChip').style.display = 'inline-flex';
            }
        }

        const pct = result.confidence;
        btn.textContent = pct > 0
            ? `✨ Suggested (${pct}% confidence) — override anytime`
            : '✨ No clear match found — please select manually';

    } catch (err) {
        btn.textContent = '✨ Suggest Category & Priority';
        console.error('Classification error:', err);
    } finally {
        btn.disabled = false;
    }
}

// --- Dynamic custom fields at ticket creation ---
let currentCustomFieldDefs = [];

async function renderDynamicCustomFields() {
    const categoryId = document.getElementById('category').value;
    const container = document.getElementById('dynamicCustomFieldsContainer');
    if (!categoryId || !container) { if (container) container.innerHTML = ''; currentCustomFieldDefs = []; return; }

    try {
        const res = await fetchWithAuth(`/api/custom-fields/by-category/${categoryId}`, { headers: authHeaders() });
        currentCustomFieldDefs = res.ok ? await res.json() : [];
    } catch (e) {
        console.error(e);
        currentCustomFieldDefs = [];
    }

    if (!currentCustomFieldDefs.length) { container.innerHTML = ''; return; }

    container.innerHTML = currentCustomFieldDefs.map(f => {
        const inputId = `customField-${f.id}`;
        const req = f.required ? 'required' : '';
        if (f.fieldType === 'SELECT') {
            const opts = (f.options || '').split(',').map(o => o.trim()).filter(Boolean);
            return `<div>
                <label style="display:block; font-size:12px; color:var(--g-text-muted); margin-bottom:4px;">${escapeHtml(f.fieldName)}</label>
                <select id="${inputId}" ${req} style="width:100%; padding:8px 10px; border-radius:8px; border:1px solid var(--g-border); font-size:13px;">
                    <option value="">Select...</option>
                    ${opts.map(o => `<option value="${escapeHtml(o)}">${escapeHtml(o)}</option>`).join('')}
                </select>
            </div>`;
        }
        const inputType = f.fieldType === 'NUMBER' ? 'number' : f.fieldType === 'DATE' ? 'date' : 'text';
        return `<div>
            <label style="display:block; font-size:12px; color:var(--g-text-muted); margin-bottom:4px;">${escapeHtml(f.fieldName)}</label>
            <input type="${inputType}" id="${inputId}" ${req}
                   style="width:100%; padding:8px 10px; border-radius:8px; border:1px solid var(--g-border); font-size:13px; box-sizing:border-box;">
        </div>`;
    }).join('');
}

function collectCustomFieldValues() {
    const values = {};
    currentCustomFieldDefs.forEach(f => {
        const el = document.getElementById(`customField-${f.id}`);
        if (el && el.value) values[f.id] = el.value;
    });
    return values;
}