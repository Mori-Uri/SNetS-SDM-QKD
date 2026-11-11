'use strict';
const fs=require('fs'),path=require('path'),vm=require('vm');
const root=path.resolve(__dirname,'..');
const src=(file)=>fs.readFileSync(path.join(root,'web/js',file),'utf8');
const app={};
vm.runInNewContext(src('schema.js'),{window:{App:app}});
const multi=app.schema.qkdPhysicalLayer.fields;
const defaults=multi.multiband.default;
const assert=(condition,msg)=>{if(!condition)throw new Error(msg)};
assert(!Object.prototype.hasOwnProperty.call(defaults,'bidirectionalFiber'),'former bidirectional default retained');
assert(!Object.prototype.hasOwnProperty.call(multi,'multiband.bidirectionalFiber'),'bidirectional selector retained');
assert(multi['multiband.intercoreRaman'].help.includes('copropagante'),'intercore Raman help not adapted');
assert(src('validate.js').includes("hasOwnProperty.call(mb, 'bidirectionalFiber')"),
       'old true/false field should be rejected explicitly');
console.log('PASS Launcher 1.25.0: only directed fibres, obsolete key rejected');
