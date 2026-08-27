/**
 * Created by Jacky.Gao on 2017-01-31.
 */
import CrossTabWidget from '../widget/CrossTabWidget.js';
import ChartWidget from '../widget/ChartWidget.js';

export function afterRenderer(td,row,col,prop,value,cellProperties){
    if(!this.context){
        return;
    }
    let cellDef=this.context.getCell(row,col);
    if(!cellDef){
        return;
    }

    const cellStyle=cellDef.cellStyle,cellValue=cellDef.value;
    const $td=$(td),valueType=cellValue.type;
    let tip='';
    if(valueType==='dataset'){
        tip=cellValue.datasetName+"."+cellValue.aggregate+"(";
        let prop=cellValue.property;
        tip+=prop+")";
        if($td.html()===''){
            $td.html(tip);
        }
    }else if(valueType==='expression'){
        tip=cellValue.value || '';
        if($td.html()===''){
            $td.html(tip);
        }
    }else if(valueType==='image'){
        tip=`${window.i18n.table.render.image}`+cellValue.value;
        const imagePath=window._server+'/res/ureportplus-asserts/icons/image.svg';
        const image=$(`<img src="${imagePath}" width="20px">`);
        $td.empty();
        $td.append(image);
    }else if(valueType==='slash'){
        tip=`${window.i18n.table.render.slash}`;
        if(!cellDef.crossTabWidget){
            cellDef.crossTabWidget=new CrossTabWidget(this.context,row,col,cellDef);
        }
        cellDef.crossTabWidget.doDraw(cellDef,row,col);
    }else if(valueType==='zxing'){
        let imagePath=window._server+'/res/ureportplus-asserts/icons/qrcode.svg';
        tip=`${window.i18n.table.render.qrcode}`;
        if(cellValue.category==='barcode'){
            tip=`${window.i18n.table.render.barcode}`;
            imagePath=window._server+'/res/ureportplus-asserts/icons/barcode.svg';
        }
        const width=cellValue.width,height=cellValue.height;
        const image=$(`<img src="${imagePath}" width="${width}px" height="${height}px">`);
        $td.empty();
        $td.append(image);
    }else if(valueType==='chart'){
        tip=`${window.i18n.table.render.chart}`;
        if(!cellDef.chartWidget){
            cellDef.chartWidget=new ChartWidget($td,cellDef);
        }
        cellDef.chartWidget.renderChart($td,this.context,row,col);
    }else{
        tip=cellValue.value || "";
        if($td.html()===''){
            $td.html(tip);
        }
    }
    $td.prop('title',tip);
    if(valueType==="simple"){
        let text=$td.text();
        if(text && text!==""){
            text=text.replace(new RegExp('<','gm'),'&lt;');
            text=text.replace(new RegExp('>','gm'),'&gt;');
            text=text.replace(new RegExp('\r\n','gm'),'<br>');
            text=text.replace(new RegExp('\n','gm'),'<br>');
            text=text.replace(new RegExp(' ','gm'),'&nbsp;');
            $td.html(text);
        }
    }
    $td.css({'word-break':'break-all','line-height':'normal','white-space':'nowrap',padding:'0 1px'});
    if(cellDef.expand === 'Down'){
        let url=window._server+"/res/ureportplus-asserts/icons/expr-expand-down.svg";
        if(valueType==='dataset'){
            url=window._server+"/res/ureportplus-asserts/icons/expand-down.svg";
        }
        $td.prepend(`<image src="${url}"></image>`);
    }else if(cellDef.expand === 'Right'){
        let url=window._server+"/res/ureportplus-asserts/icons/expr-expand-right.svg";
        if(valueType==='dataset'){
            url=window._server+"/res/ureportplus-asserts/icons/expand-right.svg";
        }
        $td.prepend(`<image src="${url}" style="display: block;"></image>`);
    }else{
        if(valueType==='dataset'){
            let url=window._server+"/res/ureportplus-asserts/icons/property.svg";
            $td.prepend(`<image src="${url}" style="display: inline-block;"></image>`);
        }else if(valueType==='expression'){
            let url=window._server+"/res/ureportplus-asserts/icons/expression.svg";
            $td.prepend(`<image src="${url}" style="display: inline-block;"></image>`);
        }
    }
    if(cellStyle.align){
        $td.css("text-align",cellStyle.align);
    }
    if(cellStyle.valign){
        $td.css("vertical-align",cellStyle.valign);
    }
    if(cellStyle.bold){
        $td.css("font-weight",'bold');
    }
    if(cellStyle.italic){
        $td.css("font-style",'italic');
    }
    if(cellStyle.underline){
        $td.css("text-decoration",'underline');
    }
    if(cellStyle.forecolor){
        $td.css("color","rgb("+cellStyle.forecolor+")");
    }
    if(cellStyle.bgcolor){
        $td.css("background-color","rgb("+cellStyle.bgcolor+")");
    }
    if(cellStyle.fontSize){
        $td.css("font-size",cellStyle.fontSize+"pt");
    }
    if(cellStyle.fontFamily){
        $td.css("font-family",cellStyle.fontFamily);
    }
    if(cellStyle.lineHeight){
        $td.css('line-height',cellStyle.lineHeight);
    }else{
        $td.css('line-height','');
    }

    // Border rendering — consistent 4-direction handling
    function applyBorder(dir, borderObj){
        if(!borderObj || borderObj==='' || borderObj.style==='none' || !borderObj.width || parseInt(borderObj.width)===0){
            $td.css('border-'+dir, '');
        }else{
            const w=parseInt(borderObj.width)||1;
            const s=borderObj.style||'solid';
            const c=borderObj.color||'0,0,0';
            $td.css('border-'+dir, s+' '+w+'px rgb('+c+')');
        }
    }
    applyBorder('left',   cellStyle.leftBorder);
    applyBorder('right',  cellStyle.rightBorder);
    applyBorder('top',    cellStyle.topBorder);
    applyBorder('bottom', cellStyle.bottomBorder);
};