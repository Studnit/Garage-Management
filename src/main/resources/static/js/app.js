/* City Car Services - UI behaviour (no inline scripts: works with a strict CSP) */
(function () {
  'use strict';
  var doc = document;

  /* ---------- toasts ---------- */
  function toast(type, message, sticky) {
    var box = doc.getElementById('toasts');
    if (!box) { return; }
    var t = doc.createElement('div');
    t.className = 'toast ' + type;
    t.setAttribute('role', type === 'error' ? 'alert' : 'status');
    var span = doc.createElement('span');
    span.textContent = message;
    var close = doc.createElement('button');
    close.type = 'button';
    close.setAttribute('aria-label', 'Dismiss');
    close.textContent = '×';
    close.addEventListener('click', function () { t.remove(); });
    t.appendChild(span);
    t.appendChild(close);
    box.appendChild(t);
    if (!sticky) { setTimeout(function () { t.remove(); }, type === 'error' ? 9000 : 5500); }
  }
  window.garageToast = toast;

  function readFlash() {
    var items = doc.querySelectorAll('.toast-src li');
    items.forEach(function (li) { toast(li.getAttribute('data-type') || 'info', li.textContent, li.getAttribute('data-type') === 'error'); });
    var noscript = doc.querySelector('.flash-fallback');
    if (noscript && items.length) { noscript.classList.add('hidden'); }
  }

  /* ---------- confirm dialog ---------- */
  function confirmDialog(message, onOk) {
    var dlg = doc.getElementById('confirm-dialog');
    if (typeof HTMLDialogElement === 'undefined' || (dlg && typeof dlg.showModal !== 'function')) {
      if (window.confirm(message)) { onOk(); }
      return;
    }
    if (!dlg) {
      dlg = doc.createElement('dialog');
      dlg.id = 'confirm-dialog';
      dlg.innerHTML = '<div class="dlg"><h3>Please confirm</h3><p class="cd-msg"></p>' +
        '<div class="btn-row"><button type="button" class="btn btn-outline" data-cd="cancel">Cancel</button>' +
        '<button type="button" class="btn btn-primary" data-cd="ok">Confirm</button></div></div>';
      doc.body.appendChild(dlg);
    }
    dlg.querySelector('.cd-msg').textContent = message;
    var ok = dlg.querySelector('[data-cd=ok]');
    var cancel = dlg.querySelector('[data-cd=cancel]');
    function done(run) {
      ok.onclick = null; cancel.onclick = null;
      dlg.close();
      if (run) { onOk(); }
    }
    ok.onclick = function () { done(true); };
    cancel.onclick = function () { done(false); };
    dlg.showModal();
    cancel.focus();
  }

  /* ---------- clicks: nav toggle, print, copy, row links ---------- */
  doc.addEventListener('click', function (e) {
    var el = e.target;
    if (!el.closest) { return; }
    if (el.closest('[data-toggle-nav]')) { doc.body.classList.toggle('nav-open'); return; }
    if (el.classList.contains('backdrop')) { doc.body.classList.remove('nav-open'); return; }
    if (el.closest('[data-print]')) { window.print(); return; }
    var copy = el.closest('[data-copy]');
    if (copy && navigator.clipboard) {
      navigator.clipboard.writeText(copy.getAttribute('data-copy')).then(function () { toast('success', 'Copied to clipboard'); });
      return;
    }
    var setAll = el.closest('[data-set-all]');
    if (setAll) {
      var form = setAll.closest('form');
      var val = setAll.getAttribute('data-set-all');
      form.querySelectorAll('input[type=radio][value=' + val + ']').forEach(function (r) { r.checked = true; });
      form.dispatchEvent(new Event('change', { bubbles: true }));
      return;
    }
    var row = el.closest('tr[data-href]');
    if (row && !el.closest('a, button, input, select, textarea, label, form')) {
      window.location.href = row.getAttribute('data-href');
    }
  });

  /* ---------- forms: confirm + loading state ---------- */
  doc.addEventListener('submit', function (e) {
    var form = e.target;
    if (!form || form.tagName !== 'FORM') { return; }
    var submitter = e.submitter;
    var msg = (submitter && submitter.getAttribute('data-confirm')) || form.getAttribute('data-confirm');
    if (msg && !form.__confirmed) {
      e.preventDefault();
      confirmDialog(msg, function () {
        form.__confirmed = true;
        if (form.requestSubmit) { form.requestSubmit(submitter || undefined); } else { form.submit(); }
      });
      return;
    }
    form.__confirmed = false;
    if (form.hasAttribute('data-no-loading')) { return; }
    setTimeout(function () {
      form.querySelectorAll('button[type=submit], button:not([type]), input[type=submit]').forEach(function (b) {
        b.classList.add('is-loading');
        b.disabled = true;
      });
    }, 0);
  });
  window.addEventListener('pageshow', function () {
    doc.querySelectorAll('.is-loading').forEach(function (b) { b.classList.remove('is-loading'); b.disabled = false; });
  });

  /* ---------- table filter & sort ---------- */
  doc.addEventListener('input', function (e) {
    var f = e.target.closest ? e.target.closest('[data-filter]') : null;
    if (!f) { return; }
    var table = doc.querySelector(f.getAttribute('data-filter'));
    if (!table) { return; }
    var q = f.value.trim().toLowerCase();
    var shown = 0;
    table.querySelectorAll('tbody tr').forEach(function (tr) {
      var hide = q && tr.textContent.toLowerCase().indexOf(q) === -1;
      tr.classList.toggle('hidden', !!hide);
      if (!hide) { shown++; }
    });
    var counter = doc.querySelector('[data-filter-count]');
    if (counter) { counter.textContent = shown; }
  });

  function cellValue(tr, idx) {
    var td = tr.children[idx];
    var text = td ? td.textContent.trim() : '';
    var num = parseFloat(text.replace(/[^0-9.\-]/g, ''));
    return { text: text.toLowerCase(), num: isNaN(num) || !/[0-9]/.test(text) ? null : num };
  }
  doc.addEventListener('click', function (e) {
    var th = e.target.closest ? e.target.closest('th[data-sort]') : null;
    if (!th) { return; }
    var table = th.closest('table');
    var idx = Array.prototype.indexOf.call(th.parentNode.children, th);
    var asc = !th.classList.contains('sorted-asc');
    table.querySelectorAll('th').forEach(function (h) { h.classList.remove('sorted-asc', 'sorted-desc'); });
    th.classList.add(asc ? 'sorted-asc' : 'sorted-desc');
    var body = table.tBodies[0];
    var rows = Array.prototype.slice.call(body.rows);
    rows.sort(function (a, b) {
      var x = cellValue(a, idx), y = cellValue(b, idx);
      var r = (x.num !== null && y.num !== null) ? x.num - y.num : x.text.localeCompare(y.text);
      return asc ? r : -r;
    });
    rows.forEach(function (r) { body.appendChild(r); });
  });

  /* ---------- job line form ---------- */
  function lineTypeChanged() {
    var sel = doc.getElementById('lineType');
    if (!sel) { return; }
    var isPart = sel.value === 'PART';
    doc.querySelectorAll('.only-part').forEach(function (n) { n.classList.toggle('hidden', !isPart); });
    doc.querySelectorAll('.not-part').forEach(function (n) { n.classList.toggle('hidden', isPart); });
    var qty = doc.getElementById('lineQtyLabel');
    if (qty) { qty.textContent = sel.value === 'LABOUR' ? 'Hours' : 'Quantity'; }
    var price = doc.getElementById('linePriceLabel');
    if (price) { price.textContent = sel.value === 'LABOUR' ? 'Rate per hour (₹)' : 'Unit price (₹, excl. GST)'; }
  }
  doc.addEventListener('change', function (e) {
    var t = e.target;
    if (t.id === 'lineType') { lineTypeChanged(); }
    if (t.id === 'partId') {
      var opt = t.options[t.selectedIndex];
      var price = doc.getElementById('unitPrice');
      var gst = doc.getElementById('gstRate');
      var desc = doc.getElementById('lineDesc');
      if (opt && opt.value) {
        if (price) { price.value = opt.getAttribute('data-price') || ''; }
        if (gst) { gst.value = opt.getAttribute('data-gst') || ''; }
        if (desc && !desc.value) { desc.placeholder = opt.getAttribute('data-name') || ''; }
        var stock = doc.getElementById('partStock');
        if (stock) { stock.textContent = 'In stock: ' + (opt.getAttribute('data-stock') || '0'); }
      }
    }
    if (t.id === 'pickupRequired') {
      var f = doc.getElementById('pickupAddressField');
      if (f) { f.classList.toggle('hidden', !t.checked); }
    }
    var est = t.closest ? t.closest('.est-form') : null;
    if (est) { updateApproved(est); }
  });

  function updateApproved(form) {
    var out = form.querySelector('[data-approved-total]');
    if (!out) { return; }
    var sum = 0;
    form.querySelectorAll('input[type=radio][value=APPROVED]:checked').forEach(function (r) {
      sum += parseFloat(r.getAttribute('data-total') || '0');
    });
    out.textContent = '₹' + sum.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
  }

  /* ---------- init ---------- */
  doc.addEventListener('DOMContentLoaded', function () {
    readFlash();
    lineTypeChanged();
    var pr = doc.getElementById('pickupRequired');
    if (pr) {
      var f = doc.getElementById('pickupAddressField');
      if (f) { f.classList.toggle('hidden', !pr.checked); }
    }
    doc.querySelectorAll('.est-form').forEach(updateApproved);
    doc.querySelectorAll('input[data-autofocus]').forEach(function (i) { i.focus(); });
  });
})();
