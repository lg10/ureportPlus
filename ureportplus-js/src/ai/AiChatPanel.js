/**
 * AI Chat Panel — interactive multi-turn conversation with question cards,
 * confidence gauge, self-test results, and full conversation memory.
 */
import {tableToXml} from '../Utils.js';

export default class AiChatPanel {
    constructor(context) {
        this.context = context;
        this.isProcessing = false;
        this.isOpen = false;
        this.conversationId = null;
        this.history = [];          // {role, content} — sent to backend each turn
        this.lastQuestion = null;   // current question context (question text + option map)
    }

    init() {
        this._buildPanel();
        this._bindEvents();
        this._checkStatus();
    }

    // ═══════════════════ Status ═══════════════════

    _checkStatus() {
        const _this = this;
        $.ajax({ url: window._server + '/ai/status', type: 'GET',
            success(d) {
                _this.enabled = d && d.enabled;
                _this.statusEl.html(d && d.enabled
                    ? `<span class="ud-ai-dot on"></span>${d.model||'AI'} 就绪`
                    : `<span class="ud-ai-dot off"></span>AI未启用`);
            },
            error() { _this.enabled = false; _this.statusEl.html(`<span class="ud-ai-dot off"></span>不可用`); }
        });
    }

    // ═══════════════════ Build ═══════════════════

    _buildPanel() {
        this.panel = $(`
            <div class="ud-ai-panel" style="display:none">
                <div class="ud-ai-resize-handle" title="拖动调整高度"></div>
                <div class="ud-ai-panel-hdr">
                    <span class="ud-ai-title">⚡ AI 报表助手</span>
                    <span class="ud-ai-status"></span>
                    <span class="ud-ai-conf"></span>
                    <span class="ud-ai-acts">
                        <button class="ud-ai-btn-rst" title="新对话">↺</button>
                        <button class="ud-ai-btn-min" title="最小化">−</button>
                        <button class="ud-ai-btn-cls" title="关闭">×</button>
                    </span>
                </div>
                <div class="ud-ai-sugs"></div>
                <div class="ud-ai-msgs"></div>
                <div class="ud-ai-inp">
                    <textarea class="ud-ai-txt" placeholder="描述要修改的内容…" rows="2"></textarea>
                    <button class="ud-ai-send" title="发送 (Enter)">↑</button>
                </div>
            </div>`);
        this.statusEl  = this.panel.find('.ud-ai-status');
        this.confEl    = this.panel.find('.ud-ai-conf');
        this.sugsEl    = this.panel.find('.ud-ai-sugs');
        this.msgsEl    = this.panel.find('.ud-ai-msgs');
        this.inputEl   = this.panel.find('.ud-ai-txt');
        this.sendBtn   = this.panel.find('.ud-ai-send');
        $('body').append(this.panel);
        if (!$('#ud-ai-css').length) $('<style id="ud-ai-css">').text(this._css()).appendTo('head');

        // Floating ball
        this.fab = $(`<div class="ud-ai-fab" title="AI 助手">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M12 2a4 4 0 0 1 4 4v1h2a2 2 0 0 1 2 2v1.5"/><path d="M12 2a4 4 0 0 0-4 4v1H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V12"/><circle cx="9" cy="10" r="1"/><circle cx="15" cy="10" r="1"/><path d="M9 15c.83.67 1.83 1 3 1s2.17-.33 3-1"/>
            </svg>
        </div>`);
        $('body').append(this.fab);

        // Quick prompts
        this.sugsEl.html(['添加合计行','按字段分组','添加小计','格式化数字','添加图表','插入空行','添加表头']
            .map(l=>`<span class="ud-ai-chip">${l}</span>`).join(''));
    }

    // ═══════════════════ Events ═══════════════════

    _bindEvents() {
        const _=this;
        // Floating ball: click to toggle, drag to move
        let fabDragging = false, fabStartX, fabStartY, fabOrigX, fabOrigY;
        _.fab.on('mousedown', function(e) {
            fabDragging = true;
            fabStartX = e.clientX; fabStartY = e.clientY;
            const off = _.fab.offset();
            fabOrigX = off.left; fabOrigY = off.top;
            $('body').css({userSelect:'none'});
            e.preventDefault();
        });
        $(document).on('mousemove', function(e) {
            if (!fabDragging) return;
            const dx = e.clientX - fabStartX, dy = e.clientY - fabStartY;
            const newX = Math.max(0, Math.min(window.innerWidth - 56, fabOrigX + dx));
            const newY = Math.max(0, Math.min(window.innerHeight - 56, fabOrigY + dy));
            _.fab.css({left: newX + 'px', top: newY + 'px', right: 'auto', bottom: 'auto'});
        }).on('mouseup', function(e) {
            if (!fabDragging) return;
            fabDragging = false;
            $('body').css({userSelect:''});
            // Click if barely moved
            if (Math.abs(e.clientX - fabStartX) < 5 && Math.abs(e.clientY - fabStartY) < 5) {
                _.toggle();
            }
        });

        _.panel.find('.ud-ai-btn-cls').on('click',()=>_.close());
        _.panel.find('.ud-ai-btn-min').on('click',()=>_.toggle());
        _.panel.find('.ud-ai-btn-rst').on('click',()=>_._reset());
        _.sendBtn.on('click',()=>_._send());
        _.inputEl.on('keydown',e=>{ if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();_._send();} });
        _.sugsEl.on('click','.ud-ai-chip',function(){_.inputEl.val($(this).text());_._send();});

        // Resize handle
        const handle = _.panel.find('.ud-ai-resize-handle');
        let dragging = false, startY, startH;
        handle.on('mousedown', function(e) {
            dragging = true; startY = e.clientY; startH = _.panel.height();
            $('body').css({userSelect:'none',cursor:'ns-resize'});
            e.preventDefault();
        });
        $(document).on('mousemove', function(e) {
            if (!dragging) return;
            const newH = Math.max(200, Math.min(window.innerHeight - 100, startH - (e.clientY - startY)));
            _.panel.css('height', newH + 'px');
        }).on('mouseup', function() {
            if (dragging) { dragging = false; $('body').css({userSelect:'',cursor:''}); }
        });
    }

    toggle() {
        if (this.isOpen) {
            this.panel.slideUp(200);
            this.isOpen = false;
            this.fab.css({bottom: '80px', left: '', top: '', right: '24px'});
        } else {
            const panelH = Math.max(this.panel.height(), 400);
            this.panel.slideDown(200);
            this.isOpen = true;
            this.fab.css({bottom: (panelH + 24) + 'px', left: '', top: '', right: '24px'});
            this.inputEl.focus();
        }
    }
    open() { if(!this.isOpen)this.toggle(); }
    openWithPrompt(p) { this.open(); this.inputEl.val(p); }
    close() { this.panel.slideUp(200); this.isOpen=false; this.fab.css({bottom: '80px', left: '', top: '', right: '24px'}); }

    _reset() {
        this.conversationId = null;
        this.history = [];
        this.lastQuestion = null;
        this.convStarted = false;
        this.msgsEl.empty();
        this._addMsg('system', '已开始新对话。AI 现在可以重新了解你的需求。');
    }

    // ═══════════════════ Send / Conversation Loop ═══════════════════

    _send(answerOverride, displayText) {
        if (this.isProcessing) return;
        const text = answerOverride || this.inputEl.val().trim();
        if (!text) return;

        if (!this.enabled) {
            this._addMsg('system','AI 未启用。<br><small>设置 ureportplus.ai.enabled=true 和 api-key</small>');
            return;
        }

        // Record in history with full context
        const userMsg = { role: 'user', content: text };
        if (this.lastQuestion && answerOverride) {
            userMsg.questionContext = {
                question: this.lastQuestion.question,
                options: this.lastQuestion.options
            };
            userMsg.selectedOption = answerOverride;
            this.lastQuestion = null;
        }
        this.history.push(userMsg);

        // Add to DOM - use displayText if provided (e.g. option label), otherwise raw text
        this._addMsg('user', displayText || text);
        if (!answerOverride) this.inputEl.val('');
        this.isProcessing = true;
        this.sendBtn.prop('disabled',true).css('opacity','.5');

        const file = window._reportFile || this.context.fileInfo.getFile();
        const cells = this._getSelectedCells();
        const typing = this._typing();

        const url = this.conversationId
            ? window._server + '/ai/next'
            : window._server + '/ai/start';

        const _=this;
        const payload = JSON.stringify({
            file, prompt: text, answer: text, selectedCells: cells,
            conversationId: _.conversationId, history: _.history
        });

        fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: payload })
        .then(async response => {
            if (!response.ok) throw new Error('HTTP ' + response.status);
            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';
            let thinkEl = null;
            let currentLine = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop(); // keep incomplete line in buffer

                for (const line of lines) {
                    if (!line.trim()) continue;
                    try {
                        const evt = JSON.parse(line);
                        if (evt.type === 'stage') {
                            // Show stage in real-time
                            if (!thinkEl) {
                                thinkEl = $(`<div class="ud-ai-think"></div>`);
                                _._addEl(thinkEl);
                            }
                            const detail = evt.detail || '';
                            const stageEl = $(`<div class="ud-ai-stage-line">
                                <span class="ud-ai-stage-icon">${evt.icon||'•'}</span>
                                <span class="ud-ai-stage-label">${evt.label||''}</span>
                                ${detail ? `<span class="ud-ai-stage-detail"></span>` : ''}
                            </div>`);
                            thinkEl.append(stageEl);
                            if (detail) {
                                const dEl = stageEl.find('.ud-ai-stage-detail');
                                let ci = 0;
                                const t = setInterval(() => {
                                    if (ci < detail.length) { dEl.text(detail.substring(0, ci+30)); ci+=30; }
                                    else clearInterval(t);
                                }, 15);
                            }
                            thinkEl[0].scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                            _._addEl = _._addEl; // noop
                        } else if (evt.type === 'done') {
                            // Final result
                            _._stopTyping(typing);
                            if (thinkEl) setTimeout(() => thinkEl.slideUp(300, () => thinkEl.remove()), 3000);
                            if (evt.error) { _._addMsg('error', evt.error); _._done(); return; }
                            _.convStarted = true;
                            if (evt.conversationId) _.conversationId = evt.conversationId;
                            _._showConfidence(evt.confidence || 0);

                            if (evt.action === 'ask') {
                                _.lastQuestion = { question: evt.question, options: evt.options };
                                _.history.push({ role: 'ai', content: evt.question });
                                _._showQuestion(evt);
                            } else {
                                _.history.push({ role: 'ai', content: evt.explanation || '修改方案' });
                                _._showResult(evt);
                            }
                            _._done();
                        } else if (evt.type === 'error') {
                            _._stopTyping(typing);
                            if (thinkEl) thinkEl.remove();
                            _._addMsg('error', evt.error || 'Unknown error');
                            _._done();
                        }
                    } catch (e) { /* skip malformed lines */ }
                }
            }
        })
        .catch(err => {
            _._stopTyping(typing);
            _._addMsg('error', '请求失败: ' + (err.message || '网络错误'));
            _._done();
        });
    }

    _done() { this.isProcessing=false; this.sendBtn.prop('disabled',false).css('opacity','1'); }

    _showThinking(text) {
        const _=this;
        const el = $(`<div class="ud-ai-think">
            <div class="ud-ai-think-hd">💭 思考中…</div>
            <div class="ud-ai-think-body" style="display:none"></div>
        </div>`);
        this._addEl(el);

        // Typewriter effect: reveal text char by char
        const body = el.find('.ud-ai-think-body');
        const hd = el.find('.ud-ai-think-hd');
        body.text('');
        body.slideDown(150);
        let i = 0;
        const speed = 20; // ms per char
        const timer = setInterval(() => {
            if (i < text.length) {
                body.text(text.substring(0, i + 40)); // chunk 40 chars at a time for speed
                i += 40;
                el[0].scrollIntoView({ behavior: 'smooth', block: 'nearest' });
            } else {
                clearInterval(timer);
                body.text(text);
                hd.html('💭 思考完成 <span class="ud-ai-think-age">刚刚</span>');
                // Auto-collapse after 3s
                setTimeout(() => {
                    el.slideUp(300, () => el.remove());
                }, 3000);
            }
        }, speed);
        el.find('.ud-ai-think-hd').on('click', function() {
            el.stop(true).slideUp(300, () => el.remove());
        });
    }

    // ═══════════════════ Question Card ═══════════════════

    _showQuestion(d) {
        const _=this;
        let opts = '';
        if (d.options && d.options.length) {
            opts = d.options.map((o, i) => {
                const label = o.label || o.value || '';
                const val = o.value || label;
                return `<button class="ud-ai-opt" data-val="${_.escAttr(val)}" data-label="${_.escAttr(label)}">${String.fromCharCode(65+i)}. ${label}</button>`;
            }).join('');
        }
        opts += `<div class="ud-ai-opt-custom"><input class="ud-ai-opt-inp" placeholder="或输入自定义回答…"></div>`;

        const card = $(`<div class="ud-ai-card">
            <div class="ud-ai-card-q">🤔 ${d.question}</div>
            ${opts ? `<div class="ud-ai-card-opts">${opts}</div>` : ''}
        </div>`);

        card.find('.ud-ai-opt').on('click', function() {
            const val = $(this).data('val');
            const label = $(this).data('label');
            card.find('.ud-ai-card-opts').html(`<span class="ud-ai-chosen">✓ ${$(this).text()}</span>`);
            _._send(val, $(this).text());
        });
        card.find('.ud-ai-opt-inp').on('keydown', function(e) {
            if (e.key === 'Enter') {
                const val = $(this).val().trim();
                if (val) { _._send(val); }
            }
        });

        this._addEl(card);
    }

    // ═══════════════════ Result (modifications + self-test) ═══════════════════

    _showResult(d) {
        const _=this;
        let mods = '';
        for (let m of (d.modifications||[])) {
            const icon = {dataset:'📊',expression:'📐',simple:'📝',insertRow:'➕',insertCol:'➕',deleteRow:'➖',
                deleteCol:'➖',mergeCells:'🔗',setStyle:'🎨',setBand:'🏷️'}[m.type]||'❓';
            const val = m.value || (m.datasetId?`${m.datasetId}.${m.aggregate}(${m.property||''})`:'—');
            mods += `<div class="ud-ai-mod">
                <span class="ud-ai-mod-i">${icon}</span>
                <strong>${m.cellName||''}</strong>
                <code>${_.escHtml(val)}</code>
                ${m.explanation?`<span class="ud-ai-mod-d">${m.explanation}</span>`:''}
                <span class="ud-ai-mod-t">${m.type}${m.expand?' · '+m.expand:''}${m.leftParent?' · ←'+m.leftParent:''}</span>
            </div>`;
        }
        // Show structural operations too
        if (d.structuralOps && d.structuralOps.length) {
            for (let op of (d.structuralOps||[])) {
                mods += `<div class="ud-ai-mod">
                    <span class="ud-ai-mod-i">🔧</span>
                    <strong>${op.type}</strong>
                    <code>${_.escHtml(op.description||'')}</code>
                </div>`;
            }
        }

        // Self-test results
        let tests = '';
        if (d.testResults && d.testResults.length) {
            const passed = d.testResults.filter(t=>t.passed).length;
            const total = d.testResults.length;
            tests = `<div class="ud-ai-tests ${passed===total?'ud-ai-tests-ok':'ud-ai-tests-warn'}">
                <div class="ud-ai-tests-hd">🔍 自测: ${passed}/${total} 通过</div>`;
            for (let t of d.testResults) {
                tests += `<div class="ud-ai-test ${t.passed?'ok':'fail'}">
                    <strong>${t.cellName||t.type||''}</strong>: ${(t.checks||[]).join(' · ')}
                </div>`;
            }
            tests += '</div>';
        }

        const warn = (!d.allTestsPassed)
            ? `<div class="ud-ai-warn">⚠ 部分测试未通过，建议检查后手动应用</div>` : '';

        const el = $(`<div class="ud-ai-result">
            ${d.explanation?`<div class="ud-ai-result-hd">${d.explanation}</div>`:''}
            <div class="ud-ai-mods">${mods}</div>
            ${tests}
            ${warn}
            <div class="ud-ai-acts2">
                <button class="btn btn-primary btn-sm ud-ai-apply">✓ 应用</button>
                <button class="btn btn-default btn-sm ud-ai-discard">✕ 放弃</button>
            </div>
        </div>`);

        const applyBtn = el.find('.ud-ai-apply');
        const discardBtn = el.find('.ud-ai-discard');
        const actsRow = el.find('.ud-ai-acts2');

        applyBtn.on('click', function() {
            actsRow.html('<span class="ud-ai-ok">⏳ 应用中…</span>');
            _._apply(d, actsRow);
        });
        discardBtn.on('click', function() {
            actsRow.html('<span class="ud-ai-ccl">已取消</span>');
        });

        this._addEl(el);
    }

    _showConfidence(c) {
        const color = c >= 90 ? '#16a34a' : c >= 70 ? '#d97706' : '#dc2626';
        this.confEl.html(`<span class="ud-ai-conf-gauge"><span class="ud-ai-conf-fill" style="width:${c}%;background:${color}"></span></span> ${c}%`);
    }

    // ═══════════════════ Apply ═══════════════════

    _apply(d, statusEl) {
        const _=this;
        const file = window._reportFile || this.context.fileInfo.getFile();
        $.ajax({ url: window._server+'/ai/apply', type:'POST', contentType:'application/json',
			timeout: 90000,
            data: JSON.stringify({
                file,
                modifications: d.modifications || [],
                structuralOps: d.structuralOps || []
            }),
            success(resp) {
                if (resp.success) {
                    let count = (resp.appliedCells||[]).length + (resp.appliedOps||[]).length;
                    statusEl.html(`<span class="ud-ai-ok">✓ 已应用 ${count} 项</span>`);
                    _._refresh(file);
                } else {
                    statusEl.html(`<span class="ud-ai-fail">✕ ${resp.error||'失败'}</span>`);
                }
            },
            error(xhr, status) {
                const msg = status === 'timeout' ? '请求超时，请重试' :
                    xhr.responseJSON && xhr.responseJSON.error ? xhr.responseJSON.error :
                    '应用失败，请检查网络后重试';
                statusEl.html(`<span class="ud-ai-fail">✕ ${msg}</span>`);
            }
        });
    }

    _refresh(file) {
        const _=this, rt=this.context.reportTable;
        $.ajax({ url: window._server+'/designer/loadReport', type:'POST', data:{file},
            success(def) {
                _.context.reportDef = def;
                if (rt&&rt._buildReportData) rt._buildReportData(def);
                _.context.hot.render();
                // Save to disk and update designer's save status
                const xml = tableToXml(_.context);
                $.ajax({ url: window._server+'/designer/saveReportFile', type:'POST',
                    data: { file, content: xml },
                    success() {
                        // Notify designer to update save status
                        if (window._designer) {
                            window._designer._dirty = false;
                            window._designer._saveStatusEl.text('● 已保存').removeClass('unsaved').addClass('saved');
                        }
                    },
                    error() { /* best-effort: cache still has the changes */ } });
            },
            error() { /* load failed; user will see old data, can retry */ }
        });
    }

    // ═══════════════════ Helpers ═══════════════════

    _getSelectedCells() {
        try {
            const s=this.context.hot.getSelected(); if(!s||!s.length)return[];
            const n=[]; for(let r=s[0];r<=s[2];r++)for(let c=s[1];c<=s[3];c++)n.push(this.context.getCellName(r,c));
            return n;
        } catch(e) { return []; }
    }
    _addMsg(type,text) { this._addEl($(`<div class="ud-ai-msg ${type}"><div class="ud-ai-msg-b">${text}</div></div>`)); }
    _typing() {
        // Backend returns real stages — show a simple waiting indicator
        const el = this._addEl($(`<div class="ud-ai-msg ai"><div class="ud-ai-msg-b ud-ai-typ">
            <span class="ud-ai-stage">⏳ 等待 AI 响应…</span>
            <span class="ud-ai-dots"><span></span><span></span><span></span></span>
        </div></div>`), true);
        return el;
    }
    _stopTyping(el) { if (el) el.remove(); }
    _showStages(stages) {
        if (!stages || !stages.length) return;
        const _ = this;
        const container = $(`<div class="ud-ai-think"></div>`);
        this._addEl(container);

        let idx = 0;
        function showNext() {
            if (idx >= stages.length) {
                // All done — auto-collapse after 3s
                setTimeout(() => container.slideUp(300, () => container.remove()), 3000);
                return;
            }
            const s = stages[idx];
            const icon = s.icon || '•';
            const label = s.label || '';
            const detail = s.detail || '';
            const el = $(`<div class="ud-ai-stage-line">
                <span class="ud-ai-stage-icon">${icon}</span>
                <span class="ud-ai-stage-label">${label}</span>
                ${detail ? `<span class="ud-ai-stage-detail"></span>` : ''}
            </div>`);
            container.append(el);
            el[0].scrollIntoView({ behavior: 'smooth', block: 'nearest' });

            if (detail) {
                const detailEl = el.find('.ud-ai-stage-detail');
                let ci = 0;
                const timer = setInterval(() => {
                    if (ci < detail.length) {
                        detailEl.text(detail.substring(0, ci + 30));
                        ci += 30;
                    } else {
                        clearInterval(timer);
                        idx++;
                        setTimeout(showNext, 200);
                    }
                }, 15);
            } else {
                idx++;
                setTimeout(showNext, 300);
            }
        }
        showNext();
    }
    _addEl(el, tmp) { if(tmp)el.addClass('ud-ai-tmp'); this.msgsEl.append(el); this.msgsEl.scrollTop(this.msgsEl[0].scrollHeight); return el; }
    escHtml(t){const d=document.createElement('div');d.appendChild(document.createTextNode(t));return d.innerHTML;}
    escAttr(t){return t.replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}

    // ═══════════════════ CSS ═══════════════════

    _css(){return`
.ud-ai-panel{position:fixed;bottom:0;left:0;right:0;height:400px;z-index:10000;background:#fff;border-top:1px solid #e2e8f0;box-shadow:0 -4px 20px rgba(0,0,0,.1);display:flex;flex-direction:column;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}
.ud-ai-fab{position:fixed;right:24px;bottom:80px;width:52px;height:52px;border-radius:50%;background:linear-gradient(135deg,#4f46e5,#7c3aed);box-shadow:0 4px 16px rgba(79,70,229,.35);cursor:pointer;z-index:10001;display:flex;align-items:center;justify-content:center;transition:transform .2s,box-shadow .2s;user-select:none}
.ud-ai-fab:hover{transform:scale(1.1);box-shadow:0 6px 24px rgba(79,70,229,.5)}
.ud-ai-fab:active{transform:scale(.95)}

.ud-ai-resize-handle{position:absolute;top:0;left:0;right:0;height:5px;cursor:ns-resize;z-index:10;background:transparent;transition:background .2s}
.ud-ai-resize-handle:hover{background:rgba(37,99,235,.15)}
.ud-ai-resize-handle::after{content:"";position:absolute;left:50%""";"top:50%""";"transform:translate(-50%,-50%);width:30px;height:3px;border-radius:2px;background:#cbd5e1}
.ud-ai-panel-hdr{display:flex;align-items:center;padding:10px 14px;border-bottom:1px solid #e2e8f0;background:#f8fafc;gap:10px;flex-shrink:0}
.ud-ai-title{font-weight:650;font-size:13px;color:#0f172a}
.ud-ai-dot{display:inline-block;width:7px;height:7px;border-radius:50%;margin-right:5px;vertical-align:middle}
.ud-ai-dot.on{background:#16a34a;box-shadow:0 0 4px rgba(22,163,74,.5)}
.ud-ai-dot.off{background:#dc2626}
.ud-ai-status{font-size:11px}
.ud-ai-conf{font-size:11px;display:flex;align-items:center;gap:4px}
.ud-ai-conf-gauge{display:inline-block;width:40px;height:6px;border-radius:3px;background:#e2e8f0;overflow:hidden;vertical-align:middle}
.ud-ai-conf-fill{display:block;height:100%;border-radius:3px;transition:width .4s}
.ud-ai-acts{display:flex;gap:2px;margin-left:auto}
.ud-ai-acts button{border:none;background:none;font-size:18px;color:#94a3b8;cursor:pointer;padding:2px 8px;border-radius:4px}
.ud-ai-acts button:hover{background:#e2e8f0;color:#0f172a}
.ud-ai-btn-rst{font-size:16px}
.ud-ai-sugs{display:flex;gap:6px;padding:8px 14px;overflow-x:auto;flex-shrink:0;border-bottom:1px solid #f1f5f9;background:#fafbfc}
.ud-ai-chip{display:inline-block;padding:3px 10px;border-radius:12px;font-size:11px;color:#475569;background:#f1f5f9;border:1px solid #e2e8f0;cursor:pointer;white-space:nowrap;transition:all .15s;flex-shrink:0}
.ud-ai-chip:hover{background:#eef2ff;color:#4f46e5;border-color:#c7d2fe}
.ud-ai-msgs{flex:1;overflow-y:auto;padding:12px 14px;display:flex;flex-direction:column;gap:8px}
.ud-ai-msg{max-width:92%;animation:udAiIn .25s ease}
@keyframes udAiIn{from{opacity:0;transform:translateY(6px)}to{opacity:1;transform:translateY(0)}}
.ud-ai-msg.user{align-self:flex-end}
.ud-ai-msg.user .ud-ai-msg-b{background:#2563eb;color:#fff;border-radius:14px 14px 4px 14px;padding:8px 14px;font-size:13px;line-height:1.5}
.ud-ai-msg.ai .ud-ai-msg-b{background:#f1f5f9;color:#0f172a;border-radius:14px 14px 14px 4px;padding:10px 14px;font-size:13px;line-height:1.5}
.ud-ai-msg.system .ud-ai-msg-b{background:#fffbeb;color:#92400e;border-radius:10px;padding:10px 14px;font-size:12px;text-align:center}
.ud-ai-msg.error .ud-ai-msg-b{background:#fef2f2;color:#dc2626;border-radius:10px;padding:10px 14px;font-size:12px;text-align:center}
.ud-ai-typ{display:flex;align-items:center;gap:4px;padding:12px 14px}
@keyframes udAiB{0%,60%,100%{transform:translateY(0)}30%{transform:translateY(-6px)}}
.ud-ai-typ .ud-ai-dots span{width:5px;height:5px;border-radius:50%;background:#94a3b8;animation:udAiB 1.4s infinite}
.ud-ai-typ .ud-ai-dots span:nth-child(2){animation-delay:.2s}
.ud-ai-typ .ud-ai-dots span:nth-child(3){animation-delay:.4s}
.ud-ai-stage{font-size:12px;color:#64748b;margin-right:10px;animation:udStageIn .3s ease}
@keyframes udStageIn{from{opacity:0}to{opacity:1}}
@keyframes udThinkIn{from{opacity:0;transform:translateY(-8px)}to{opacity:1;transform:translateY(0)}}
.ud-ai-dots{display:inline-flex;gap:4px;vertical-align:middle}
.ud-ai-dots span{width:5px;height:5px;border-radius:50%;background:#94a3b8;animation:udAiB 1.4s infinite}
.ud-ai-dots span:nth-child(2){animation-delay:.2s}
.ud-ai-dots span:nth-child(3){animation-delay:.4s}
.ud-ai-inp{display:flex;padding:10px 14px;border-top:1px solid #e2e8f0;gap:8px;flex-shrink:0}
.ud-ai-txt{flex:1;border:1px solid #e2e8f0;border-radius:10px;padding:8px 12px;font-size:13px;font-family:inherit;resize:none;outline:none;line-height:1.4}
.ud-ai-txt:focus{border-color:#2563eb;box-shadow:0 0 0 3px rgba(37,99,235,.1)}
.ud-ai-send{width:38px;height:38px;border:none;border-radius:10px;background:#2563eb;color:#fff;cursor:pointer;font-size:16px;display:flex;align-items:center;justify-content:center;flex-shrink:0}
.ud-ai-send:hover{background:#1d4ed8}
.ud-ai-send:disabled{background:#94a3b8;cursor:not-allowed}
.ud-ai-card{background:#f8fafc;border:1px solid #e2e8f0;border-radius:12px;padding:14px}
.ud-ai-card-q{font-size:13px;color:#0f172a;font-weight:500;margin-bottom:10px}
.ud-ai-card-opts{display:flex;flex-wrap:wrap;gap:6px}
.ud-ai-opt{display:inline-block;padding:5px 14px;border-radius:8px;font-size:12px;color:#2563eb;background:#eff6ff;border:1px solid #bfdbfe;cursor:pointer;transition:all .15s}
.ud-ai-opt:hover{background:#2563eb;color:#fff}
.ud-ai-opt-custom{margin-top:8px}
.ud-ai-opt-inp{width:100%;border:1px solid #e2e8f0;border-radius:8px;padding:6px 10px;font-size:12px;font-family:inherit;outline:none}
.ud-ai-opt-inp:focus{border-color:#2563eb}
.ud-ai-chosen{color:#16a34a;font-size:12px;font-weight:500}
.ud-ai-result{font-size:12px}
.ud-ai-result-hd{font-size:12px;color:#475569;margin-bottom:8px;line-height:1.5}
.ud-ai-mods{display:flex;flex-direction:column;gap:5px;margin-bottom:8px}
.ud-ai-mod{display:flex;align-items:center;gap:6px;padding:6px 10px;background:#fff;border:1px solid #e2e8f0;border-radius:8px;flex-wrap:wrap}
.ud-ai-mod-i{font-size:13px}
.ud-ai-mod strong{font-size:12px;color:#1e40af}
.ud-ai-mod code{font-size:11px;background:#f1f5f9;padding:2px 6px;border-radius:4px;color:#334155;max-width:240px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.ud-ai-mod-d{font-size:11px;color:#64748b;flex:1}
.ud-ai-mod-t{font-size:10px;color:#94a3b8;margin-left:auto}
.ud-ai-tests{border-radius:8px;padding:8px 10px;margin-bottom:8px;font-size:11px}
.ud-ai-tests-ok{background:#f0fdf4;border:1px solid #bbf7d0}
.ud-ai-tests-warn{background:#fffbeb;border:1px solid #fde68a}
.ud-ai-tests-hd{font-weight:600;margin-bottom:4px}
.ud-ai-test.ok{color:#16a34a}
.ud-ai-test.fail{color:#dc2626}
.ud-ai-warn{color:#d97706;font-size:11px;margin-bottom:8px;padding:6px 10px;background:#fffbeb;border-radius:6px;border:1px solid #fde68a}
.ud-ai-acts2{margin-top:8px;display:flex;gap:8px}
.ud-ai-ok{color:#16a34a;font-size:12px;font-weight:500}
.ud-ai-fail{color:#dc2626;font-size:12px}
.ud-ai-ccl{color:#94a3b8;font-size:12px}
.ud-ai-think{background:#f0fdf4;border:1px solid #bbf7d0;border-radius:10px;padding:8px 12px;margin-bottom:4px;font-size:12px;animation:udThinkIn .3s ease}
.ud-ai-think-hd{color:#166534;font-weight:500;display:flex;justify-content:space-between;gap:8px}

.ud-ai-think-age{font-size:10px;color:#86efac;font-weight:400}
.ud-ai-think-body{color:#166534;margin-top:6px;line-height:1.5;font-size:12px;white-space:pre-wrap;max-height:120px;overflow-y:auto}
.ud-ai-stage-line{display:flex;align-items:flex-start;gap:6px;padding:3px 0;font-size:12px;animation:udThinkIn .3s ease}
.ud-ai-stage-icon{flex-shrink:0;font-size:13px}
.ud-ai-stage-label{color:#166534;font-weight:500;flex-shrink:0}
.ud-ai-stage-detail{color:#475569;line-height:1.4;word-break:break-all}
.ud-ai-tmp{opacity:.8}
`;}
}
