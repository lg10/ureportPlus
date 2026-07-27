/**
 * AI Chat Panel — interactive multi-turn conversation with question cards,
 * confidence gauge, and self-test results display.
 */
import {tableToXml} from '../Utils.js';

export default class AiChatPanel {
    constructor(context) {
        this.context = context;
        this.isProcessing = false;
        this.isOpen = false;
        this.history = [];
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
                <div class="ud-ai-panel-hdr">
                    <span class="ud-ai-title">⚡ AI 报表助手</span>
                    <span class="ud-ai-status"></span>
                    <span class="ud-ai-conf"></span>
                    <span class="ud-ai-acts">
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

        // Toolbar button
        this.toggleBtn = $(`<button class="ud-toolbar-btn" title="AI 助手" style="margin-left:auto"><i class="glyphicon glyphicon-flash" style="color:#2563eb"></i></button>`);
        const tb = $('.ud-toolbar');
        if (tb.length) { tb.append('<span class="ud-toolbar-sep">'); tb.append(this.toggleBtn); }

        // Quick prompts
        this.sugsEl.html(['添加合计行','按字段分组','添加小计','格式化数字','添加图表']
            .map(l=>`<span class="ud-ai-chip">${l}</span>`).join(''));
    }

    // ═══════════════════ Events ═══════════════════

    _bindEvents() {
        const _=this;
        _.toggleBtn.on('click',()=>_.toggle());
        _.panel.find('.ud-ai-btn-cls').on('click',()=>_.close());
        _.panel.find('.ud-ai-btn-min').on('click',()=>_.toggle());
        _.sendBtn.on('click',()=>_._send());
        _.inputEl.on('keydown',e=>{ if(e.key==='Enter'&&!e.shiftKey){e.preventDefault();_._send();} });
        _.sugsEl.on('click','.ud-ai-chip',function(){_.inputEl.val($(this).text());_._send();});
    }

    toggle() { this.isOpen ? this.panel.slideUp(200) : this.panel.slideDown(200); this.isOpen=!this.isOpen; if(this.isOpen)this.inputEl.focus(); }
    open() { if(!this.isOpen)this.toggle(); }
    openWithPrompt(p) { this.open(); this.inputEl.val(p); }
    close() { this.panel.slideUp(200); this.isOpen=false; }

    // ═══════════════════ Send / Conversation Loop ═══════════════════

    _send(answerOverride) {
        if (this.isProcessing) return;
        const text = answerOverride || this.inputEl.val().trim();
        if (!text) return;

        if (!this.enabled) {
            this._addMsg('system','AI 未启用。<br><small>设置 ureport.ai.enabled=true 和 api-key</small>');
            return;
        }

        this._addMsg('user', text);
        if (!answerOverride) this.inputEl.val('');
        this.isProcessing = true;
        this.sendBtn.prop('disabled',true).css('opacity','.5');

        const file = window._reportFile || this.context.fileInfo.getFile();
        const cells = this._getSelectedCells();
        const typing = this._typing();

        const url = this.convStarted
            ? window._server + '/ai/next'
            : window._server + '/ai/start';

        const _=this;
        $.ajax({ url, type:'POST', contentType:'application/json',
            data: JSON.stringify({ file, prompt: text, answer: text, selectedCells: cells }),
            success(d) {
                typing.remove();
                if (d.error) { _._addMsg('error',d.error); _._done(); return; }
                _.convStarted = true;
                _._showConfidence(d.confidence || 0);

                if (d.action === 'ask') {
                    _._showQuestion(d);
                } else {
                    _._showResult(d);
                }
                _._done();
            },
            error(xhr) {
                typing.remove();
                _._addMsg('error', '请求失败: ' + ((xhr.responseJSON||{}).error || xhr.statusText));
                _._done();
            }
        });
    }

    _done() { this.isProcessing=false; this.sendBtn.prop('disabled',false).css('opacity','1'); }

    // ═══════════════════ Question Card ═══════════════════

    _showQuestion(d) {
        const _=this;
        let opts = '';
        if (d.options && d.options.length) {
            opts = d.options.map(o =>
                `<button class="ud-ai-opt" data-val="${_.escAttr(o.value||o.label)}">${o.label}</button>`
            ).join('');
        }
        opts += `<div class="ud-ai-opt-custom"><input class="ud-ai-opt-inp" placeholder="或输入自定义回答…"></div>`;

        const card = $(`<div class="ud-ai-card">
            <div class="ud-ai-card-q">🤔 ${d.question}</div>
            ${opts ? `<div class="ud-ai-card-opts">${opts}</div>` : ''}
        </div>`);

        card.find('.ud-ai-opt').on('click', function() {
            const val = $(this).data('val');
            card.find('.ud-ai-card-opts').html(`<span class="ud-ai-chosen">✓ ${$(this).text()}</span>`);
            _._addMsg('user', val);
            _._send(val);
        });
        card.find('.ud-ai-opt-inp').on('keydown', function(e) {
            if (e.key === 'Enter') {
                const val = $(this).val().trim();
                if (val) { _._addMsg('user', val); _._send(val); }
            }
        });

        this._addEl(card);
    }

    // ═══════════════════ Result (modifications + self-test) ═══════════════════

    _showResult(d) {
        const _=this;
        let mods = '';
        for (let m of (d.modifications||[])) {
            const icon = {dataset:'📊',expression:'📐',simple:'📝'}[m.type]||'❓';
            const val = m.value || (m.datasetId?`${m.datasetId}.${m.aggregate}(${m.property||''})`:'—');
            mods += `<div class="ud-ai-mod">
                <span class="ud-ai-mod-i">${icon}</span>
                <strong>${m.cellName}</strong>
                <code>${_.escHtml(val)}</code>
                ${m.explanation?`<span class="ud-ai-mod-d">${m.explanation}</span>`:''}
                <span class="ud-ai-mod-t">${m.type}${m.expand?' · '+m.expand:''}${m.leftParent?' · ←'+m.leftParent:''}</span>
            </div>`;
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
                    <strong>${t.cellName}</strong>: ${(t.checks||[]).join(' · ')}
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

        el.find('.ud-ai-apply').on('click', function() {
            _._apply(d.modifications, $(this));
        });
        el.find('.ud-ai-discard').on('click', function() {
            el.find('.ud-ai-acts2').html('<span class="ud-ai-ccl">已取消</span>');
        });

        this._addEl(el);
    }

    _showConfidence(c) {
        const color = c >= 90 ? '#16a34a' : c >= 70 ? '#d97706' : '#dc2626';
        this.confEl.html(`<span class="ud-ai-conf-gauge"><span class="ud-ai-conf-fill" style="width:${c}%;background:${color}"></span></span> ${c}%`);
    }

    // ═══════════════════ Apply ═══════════════════

    _apply(mods, btn) {
        const _=this;
        const file = window._reportFile || this.context.fileInfo.getFile();
        btn.prop('disabled',true).text('应用中…');
        $.ajax({ url: window._server+'/ai/apply', type:'POST', contentType:'application/json',
            data: JSON.stringify({ file, modifications: mods }),
            success(d) {
                if (d.success) {
                    btn.replaceWith(`<span class="ud-ai-ok">✓ 已应用 ${d.appliedCells.length} 项</span>`);
                    _._refresh(file);
                } else btn.replaceWith(`<span class="ud-ai-fail">失败</span>`);
            },
            error() { btn.prop('disabled',false).text('应用修改'); }
        });
    }

    _refresh(file) {
        const _=this, rt=this.context.reportTable;
        $.ajax({ url: window._server+'/designer/loadReport', type:'POST', data:{file},
            success(def) {
                _.context.reportDef = def;
                if (rt&&rt._buildReportData) rt._buildReportData(def);
                _.context.hot.render();
                const xml = tableToXml(_.context);
                $.ajax({ url: window._server+'/designer/saveReportFile', type:'POST',
                    data: { file, content: encodeURIComponent(xml) } });
            }
        });
    }

    // ═══════════════════ Helpers ═══════════════════

    _getSelectedCells() {
        const s=this.context.hot.getSelected(); if(!s||!s.length)return[];
        const n=[]; for(let r=s[0];r<=s[2];r++)for(let c=s[1];c<=s[3];c++)n.push(this.context.getCellName(r,c));
        return n;
    }
    _addMsg(type,text) { this._addEl($(`<div class="ud-ai-msg ${type}"><div class="ud-ai-msg-b">${text}</div></div>`)); }
    _typing() { return this._addEl($(`<div class="ud-ai-msg ai"><div class="ud-ai-msg-b ud-ai-typ"><span></span><span></span><span></span></div></div>`), true); }
    _addEl(el, tmp) { if(tmp)el.addClass('ud-ai-tmp'); this.msgsEl.append(el); this.msgsEl.scrollTop(this.msgsEl[0].scrollHeight); return el; }
    escHtml(t){const d=document.createElement('div');d.appendChild(document.createTextNode(t));return d.innerHTML;}
    escAttr(t){return t.replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;').replace(/>/g,'&gt;');}

    // ═══════════════════ CSS ═══════════════════

    _css(){return`
.ud-ai-panel{position:fixed;bottom:0;left:0;right:0;height:400px;z-index:10000;background:#fff;border-top:1px solid #e2e8f0;box-shadow:0 -4px 20px rgba(0,0,0,.1);display:flex;flex-direction:column;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","PingFang SC","Microsoft YaHei",sans-serif}
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
.ud-ai-typ{display:flex;gap:4px;padding:12px 14px}
.ud-ai-typ span{width:7px;height:7px;border-radius:50%;background:#94a3b8;animation:udAiB 1.4s infinite}
.ud-ai-typ span:nth-child(2){animation-delay:.2s}
.ud-ai-typ span:nth-child(3){animation-delay:.4s}
@keyframes udAiB{0%,60%,100%{transform:translateY(0)}30%{transform:translateY(-6px)}}
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
.ud-ai-tmp{opacity:.8}
`;}
}
