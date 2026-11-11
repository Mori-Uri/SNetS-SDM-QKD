/* Contract-level DOM-independent rendering regression for launcher QoT selectors. */
'use strict';
const fs = require('fs');
const vm = require('vm');
const path = require('path');
const root = path.resolve(__dirname, '..');
const simulation = {qkdKeyDemand: {profile:'custom', keySizeBits:256,
  updateIntervalsMiliseconds:[64,128,256]}};
const dummy = () => {};
function node(kind, ...children) {
  const o = {kind, children:[], classList:{add:dummy, toggle:dummy},
    addEventListener:dummy, appendChild(c){this.children.push(c);return c;},
    append(...cs){this.children.push(...cs);return this;}};
  o.append(...children);
  return o;
}
const app = {
  state:{model:{simulation},base:{},catalog:{options:{},notes:{}}},
  el:node, clear:dummy,
  getPath(obj,route){return route.reduce((v,k)=>v?.[k],obj);},
  isPlainObject(v){return !!v && typeof v==='object'&&!Array.isArray(v);},
  deepEqual(a,b){return JSON.stringify(a)===JSON.stringify(b);},
  parseNumber:Number,fmtNum:String,setValue:dummy, refreshDirty:dummy,renderTab:dummy,
};
const context = {window:{App:app}};
for (const file of ['schema.js','forms.js']) {
  vm.runInNewContext(fs.readFileSync(path.join(root,'web/js',file),'utf8'),context,{filename:file});
}
function verify(predicate,message) {if(!predicate) throw new Error(message);}
const s=app.schema.physicalLayer.fields;
verify(s.crosstalkModel.enum[0].includes('separado') && s.crosstalkModel.enum[1].includes('junto'), 'crosstalk selectors');
verify(s.typeOfTestQoT.enum[0].includes('OSNR') && s.typeOfTestQoT.enum[1].includes('BER'), 'QoT selectors');
verify(s.berModel.enum.jurandir.includes('Jurandir') && s.berModel.enum.legacyRectangularAwgn.includes('Legado'), 'BER model selectors');
verify(s.typeOfAmplifierGain.enum[0].includes('fixo') && s.typeOfAmplifierGain.enum[1].includes('saturado'), 'gain selectors');
const original = JSON.stringify(app.state.model.simulation);
const rendered = app.forms.renderForm('simulation');
function dump(x) {if (Array.isArray(x)) return x.map(dump).join(' ');
  return x && typeof x === 'object' ? [x.kind, ...(Array.isArray(x.children) ? x.children : [])].map(dump).join(' ') : String(x||''); }
const text = dump(rendered);
verify(text.includes('updateIntervalsMiliseconds'), 'millisecond control not rendered in the form');
verify(!text.includes('updateIntervalsMilliseconds'), 'duplicated millisecond alias rendered in the form');
verify(!text.includes('updateIntervalsMileseconds'), 'obsolete milessegundos control rendered in the form');
verify(JSON.stringify(app.state.model.simulation)===original, 'form rendering modified legacy JSON');
verify(app.state.model.simulation.qkdKeyDemand.updateIntervalsMiliseconds[0]===64, 'data lost');
// The correctly spelled legacy alias must remain in JSON but not duplicate the control.
app.state.model.simulation={qkdKeyDemand:{profile:'custom',updateIntervalsMilliseconds:[64,128,256]}};
const aliasJSON=JSON.stringify(app.state.model.simulation);
const aliasText=dump(app.forms.renderForm('simulation'));
verify(aliasText.includes('updateIntervalsMilliseconds'), 'legacy milliseconds field should remain editable');
verify(!aliasText.includes('updateIntervalsMiliseconds'), 'duplicate millisecond editor rendered');
verify(aliasText.split('Intervalos de renovação (milissegundos)').length === 2, 'millisecond editor duplicated');
verify(JSON.stringify(app.state.model.simulation)===aliasJSON, 'alias modified by rendering');
console.log('PASS Launcher 1.24.4: selectors, visible milliseconds, no duplicated alias, JSON preserved');
