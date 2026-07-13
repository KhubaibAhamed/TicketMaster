/**
 * ticket-create.js — "Raise Incident" form
 * =============================================================
 * Depends on: core.js, tickets-console.js (refreshDataPipeline, loadCategories)
 *
 * UX notes (redesign pass):
 * - Progressive disclosure: the form is a 2-step wizard. Step 1 only asks for
 *   what only the reporter can know (title + description). Step 2 shows
 *   priority/category with smart defaults already applied, and tucks custom
 *   fields + attachments behind a collapsed "Add more detail" disclosure.
 * - Smart defaults: priority defaults to Medium, category defaults to the
 *   first available option — both clearly marked as defaults so the user
 *   scans and adjusts rather than starting from a blank required field.
 * - Goal-gradient: the progress bar starts at 15% (not 0%) on open, since
 *   picking defaults already did part of the work.
 * - Real-time validation: inline, field-level error messages instead of a
 *   single blocking alert on submit.
 */

const TM_MIN_TITLE_LEN = 5;
const TM_MIN_DESC_LEN = 15;

// --- Wizard open / reset -----------------------------------------------
function openCreateModal() {
    resetWizardUI();
    applySmartDefaults();
    document.getElementById('createModal').showModal();
    document.getElementById('title')?.focus();
}

function resetWizardUI() {
    goToWizardStep(1, /* skipValidation */ true);
    clearFieldError('title');
    clearFieldError('description');
    clearFieldError('category');
    document.getElementById('advancedDetailsPanel')?.removeAttribute('open');
    setWizardProgress(15);
}

function applySmartDefaults() {
    // Priority already defaults to Medium via the selected attribute in markup.
    const priorityChip = document.getElementById('priorityDefaultChip');
    if (priorityChip) priorityChip.style.display = 'inline';

    // Category: default to the first real option once categories are loaded,
    // so the user isn't blocked on a blank required field.
    const categorySelect = document.getElementById('category');
    const categoryChip = document.getElementById('categoryDefaultChip');
    if (categorySelect && !categorySelect.value) {
        const firstRealOption = Array.from(categorySelect.options).find(o => o.value);
        if (firstRealOption) {
            categorySelect.value = firstRealOption.value;
            if (categoryChip) categoryChip.style.display = 'inline';
        }
    }
}

// --- Wizard step navigation ---------------------------------------------
function goToWizardStep(step, skipValidation) {
    if (step === 2 && !skipValidation) {
        const ok = validateStep1();
        if (!ok) return;
    }

    document.getElementById('wizardStep1').style.display = step === 1 ? 'block' : 'none';
    document.getElementById('wizardStep2').style.display = step === 2 ? 'block' : 'none';
    document.getElementById('wizardStepLabel1').classList.toggle('active', step === 1);
    document.getElementById('wizardStepLabel2').classList.toggle('active', step === 2);
    setWizardProgress(step === 1 ? 15 : 60);

    if (step === 2) {
        onCategoryChanged(); // load any custom fields for the (possibly defaulted) category
    }
}

function setWizardProgress(pct) {
    const fill = document.getElementById('wizardProgressFill');
    const bar = document.getElementById('wizardProgressbar');
    if (fill) fill.style.width = pct + '%';
    if (bar) bar.setAttribute('aria-valuenow', String(pct));
}

// --- Real-time / step validation ----------------------------------------
function validateStep1() {
    let valid = true;
    const title = document.getElementById('title').value.trim();
    const description = document.getElementById('description').value.trim();

    if (title.length < TM_MIN_TITLE_LEN) {
        setFieldError('title', `Give it a few more words (at least ${TM_MIN_TITLE_LEN} characters) so agents know what they're looking at.`);
        valid = false;
    }
    if (description.length < TM_MIN_DESC_LEN) {
        setFieldError('description', `A little more detail helps — at least ${TM_MIN_DESC_LEN} characters (what happened, when, who's affected).`);
        valid = false;
    }
    return valid;
}

function validateStep2() {
    let valid = true;
    const category = document.getElementById('category').value;
    if (!category) {
        setFieldError('category', 'Pick a category — pick the closest match if you\'re not sure.');
        valid = false;
    }
    return valid;
}

function setFieldError(fieldId, message) {
    const errorEl = document.getElementById(fieldId + 'Error');
    const inputEl = document.getElementById(fieldId);
    if (errorEl) errorEl.textContent = message;
    if (inputEl) inputEl.setAttribute('aria-invalid', 'true');
}

function clearFieldError(fieldId) {
    const errorEl = document.getElementById(fieldId + 'Error');
    const inputEl = document.getElementById(fieldId);
    if (errorEl) errorEl.textContent = '';
    if (inputEl) inputEl.removeAttribute('aria-invalid');
}

function onCategoryChanged() {
    document.getElementById('categoryAiChip').style.display = 'none';
    document.getElementById('categoryDefaultChip').style.display = 'none';
    clearFieldError('category');
    renderDynamicCustomFields();
}

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
                <span class="material-symbols-rounded" style="font-size:14px; vertical-align:middle;" aria-hidden="true">auto_stories</span>
                Related articles — this might solve it faster than filing a ticket:
            </p>
            ${articles.map(a => `
                <div style="padding:8px 10px; border:1px solid var(--g-border); border-radius:8px; margin-bottom:6px; cursor:pointer;"
                     onclick="viewRelatedArticle(${a.id})" role="button" tabindex="0"
                     onkeypress="if(event.key==='Enter') viewRelatedArticle(${a.id})">
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

    if (!validateStep2()) return;

    const submitBtn = document.getElementById('submitTicketBtn');
    submitBtn.disabled = true;
    submitBtn.textContent = 'Submitting…';
    setWizardProgress(85);

    let response;
    try {
        response = await fetchWithAuth('/api/tickets', {
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
    } catch (err) {
        showToast("Couldn't reach the server. Please check your connection and try again.", 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Submit Ticket';
        setWizardProgress(60);
        return;
    }

    if (!response.ok) {
        showToast('Failed to raise ticket. Please try again.', 'error');
        submitBtn.disabled = false;
        submitBtn.textContent = 'Submit Ticket';
        setWizardProgress(60);
        return;
    }

    setWizardProgress(100);

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
    submitBtn.disabled = false;
    submitBtn.textContent = 'Submit Ticket';
    resetWizardUI();

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

        // A suggestion means the reporter is past step 1's blank-page moment,
        // so take them straight to step 2 where the suggested values live.
        goToWizardStep(2);

        if (result.suggestedCategoryId) {
            const categorySelect = document.getElementById('category');
            if (categorySelect) {
                categorySelect.value = result.suggestedCategoryId;
                document.getElementById('categoryAiChip').style.display = 'inline-flex';
                document.getElementById('categoryDefaultChip').style.display = 'none';
                renderDynamicCustomFields();
            }
        }

        if (result.suggestedPriority) {
            const prioritySelect = document.getElementById('priority');
            if (prioritySelect) {
                prioritySelect.value = result.suggestedPriority;
                document.getElementById('priorityAiChip').style.display = 'inline-flex';
                document.getElementById('priorityDefaultChip').style.display = 'none';
            }
        }

        const pct = result.confidence;
        btn.textContent = pct > 0
            ? `✨ Suggested (${pct}% confidence) — override anytime`
            : '✨ No clear match found — please select manually';

    } catch (err) {
        btn.textContent = '✨ Suggest Category & Priority';
        console.error('Classification error:', err);
        showToast("Couldn't get a suggestion right now — pick manually on the next step.", 'warning');
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
                <label for="${inputId}" style="display:block; font-size:12px; color:var(--g-text-muted); margin-bottom:4px;">${escapeHtml(f.fieldName)}</label>
                <select id="${inputId}" ${req} style="width:100%; padding:8px 10px; border-radius:8px; border:1px solid var(--g-border); font-size:13px;">
                    <option value="">Select...</option>
                    ${opts.map(o => `<option value="${escapeHtml(o)}">${escapeHtml(o)}</option>`).join('')}
                </select>
            </div>`;
        }
        const inputType = f.fieldType === 'NUMBER' ? 'number' : f.fieldType === 'DATE' ? 'date' : 'text';
        return `<div>
            <label for="${inputId}" style="display:block; font-size:12px; color:var(--g-text-muted); margin-bottom:4px;">${escapeHtml(f.fieldName)}</label>
            <input type="${inputType}" id="${inputId}" ${req}
                   style="width:100%; padding:8px 10px; border-radius:8px; border:1px solid var(--g-border); font-size:13px; box-sizing:border-box;">
        </div>`;
    }).join('');

    // Custom fields exist for this category — open the disclosure automatically
    // so the user notices them, rather than hiding required fields silently.
    const hasRequired = currentCustomFieldDefs.some(f => f.required);
    if (hasRequired) {
        const details = document.getElementById('advancedDetailsPanel');
        if (details) details.open = true;
    }
}

function collectCustomFieldValues() {
    const values = {};
    currentCustomFieldDefs.forEach(f => {
        const el = document.getElementById(`customField-${f.id}`);
        if (el && el.value) values[f.id] = el.value;
    });
    return values;
}
