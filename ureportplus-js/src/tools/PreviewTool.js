/**
 * Created by Jacky.Gao on 2017-01-25.
 */
import Tool from './Tool.js';
import {tableToXml} from '../Utils.js';
import {alert} from '../MsgBox.js';

export default class PreviewTool extends Tool{
    execute(){
    }
    buildButton(){
        const group=$(`<div class="btn-group"></div>`);
        const mainBtn=$(`<button type="button" class="btn btn-default dropdown-toggle" style="border:none;border-radius:0;background: #f8f8f8;padding: 6px 5px;" data-toggle="dropdown" title="${window.i18n.tools.preview.preview}">
            <i class="ureportplus ureportplus-preview" style="color: #0e90d2;"></i>
            <span class="caret"></span>
        </button>`);
        const ul=$(`<ul class="dropdown-menu" role="menu"></ul>`);
        const preview=$(`<li>
                <a href="###">
                    <i class="ureportplus ureportplus-preview" style="color: #0e90d2;"></i> ${window.i18n.tools.preview.view}
                </a>
            </li>`);
        ul.append(preview);
        const _this=this;
        preview.click(function(){
            _this.doPreview();
        });
        const pagingPreview=$(`<li>
                <a href="###">
                    <i class="glyphicon glyphicon-search" style="color: #0e90d2;"></i> ${window.i18n.tools.preview.pagingPreview}
                </a>
            </li>`);
        ul.append(pagingPreview);
        pagingPreview.click(function(){
            _this.doPreview(true);
        });
        group.append(mainBtn);
        group.append(ul);
        return group;
    }
    doPreview(withPaging){
        let targetUrl=window._server+"/preview?_u=p";
        if(withPaging){
            targetUrl+='&_i=1&_r=1';
        }
        const paper=this.context.reportDef.paper;
        if(paper && paper.authEnabled){
            const token=this.getAuthToken();
            if(token){
                targetUrl+='&token='+encodeURIComponent(token);
            }
            if(paper.authParams){
                const paramNames=paper.authParams.split(',');
                for(let name of paramNames){
                    name=name.trim();
                    if(name.length>0){
                        const value=this.getAuthParamValue(name);
                        if(value){
                            targetUrl+='&'+encodeURIComponent(name)+'='+encodeURIComponent(value);
                        }
                    }
                }
            }
        }
        const content=tableToXml(this.context);
        $.ajax({
            url:window._server+"/designer/savePreviewData",
            type:'POST',
            data:{content},
            success:function(){
                let newWindow=window.open(targetUrl,"_blank");
                newWindow.focus();
            },
            error:function(){
                alert(`${window.i18n.tools.preview.previewFail}`);
            }
        });
    }
    getAuthToken(){
        if(typeof window._ureportplusGetAuthToken === 'function'){
            return window._ureportplusGetAuthToken();
        }
        if(window._ureportplusAuthToken){
            return window._ureportplusAuthToken;
        }
        return '';
    }
    getAuthParamValue(paramName){
        if(typeof window._ureportplusGetAuthParam === 'function'){
            return window._ureportplusGetAuthParam(paramName);
        }
        if(window._ureportplusAuthParams && window._ureportplusAuthParams[paramName]){
            return window._ureportplusAuthParams[paramName];
        }
        return '';
    }
    getTitle(){
        return `${window.i18n.tools.preview.preview}`;
    }
    getIcon(){
        return `<i class="ureportplus ureportplus-preview" style="color: #0e90d2;"></i>`;
    }
}