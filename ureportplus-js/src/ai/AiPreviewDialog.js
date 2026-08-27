/**
 * AI Preview Dialog - shows before/after comparison of AI modifications.
 */
export default class AiPreviewDialog {
    show(modifications, onApply, onCancel) {
        let modsHtml = '<table class="table table-condensed" style="margin:0">';
        modsHtml += '<thead><tr><th>单元格</th><th>类型</th><th>值/表达式</th><th>说明</th></tr></thead><tbody>';

        for (let mod of modifications) {
            modsHtml += '<tr>';
            modsHtml += '<td><strong>' + this._esc(mod.cellName) + '</strong></td>';
            modsHtml += '<td>' + this._esc(mod.type || '-') + '</td>';
            modsHtml += '<td style="max-width:300px"><code style="font-size:11px">' +
                       this._esc(mod.value || mod.property || '-') + '</code></td>';
            modsHtml += '<td style="color:#64748b">' + this._esc(mod.explanation || '') + '</td>';
            modsHtml += '</tr>';
        }
        modsHtml += '</tbody></table>';

        const dialog = $(`
            <div class="modal fade" tabindex="-1" role="dialog">
                <div class="modal-dialog modal-lg">
                    <div class="modal-content">
                        <div class="modal-header">
                            <button type="button" class="close" data-dismiss="modal">&times;</button>
                            <h4 class="modal-title">AI 修改预览 - ${modifications.length} 个变更</h4>
                        </div>
                        <div class="modal-body" style="max-height:400px;overflow-y:auto">
                            ${modsHtml}
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-default" data-dismiss="modal">取消</button>
                            <button type="button" class="btn btn-primary ai-preview-apply">确认应用</button>
                        </div>
                    </div>
                </div>
            </div>
        `);

        dialog.find('.ai-preview-apply').on('click', function() {
            dialog.data('applied', true);
            dialog.modal('hide');
            if (onApply) onApply();
        });

        dialog.on('hidden.bs.modal', function() {
            dialog.remove();
            if (onCancel && !dialog.data('applied')) onCancel();
        });

        dialog.modal('show');
    }

    _esc(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.appendChild(document.createTextNode(String(text)));
        return div.innerHTML;
    }
}
