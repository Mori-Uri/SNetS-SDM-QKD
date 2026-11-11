/* Static schema and normalization regression: no browser/network required. */
const fs = require('fs'), vm = require('vm'), path = require('path');
const root = path.resolve(__dirname, '..');
const schema = fs.readFileSync(path.join(root,'web/js/schema.js'),'utf8');
const app = fs.readFileSync(path.join(root,'web/js/app.js'),'utf8');
const validate = fs.readFileSync(path.join(root,'web/js/validate.js'),'utf8');
function check(x, m) { if(!x) throw Error(m); }
check(schema.includes("0: '0 — limiar OSNR'"), 'QoT selector must say limiar OSNR');
check(schema.includes("'qkd.pichOsnrCheckEnabled'"), 'PICh optical QoT canonical name');
check(!schema.includes("'qkd.pichSnrCheckEnabled':"), 'no old PICh form field');
check(app.includes('App.normalizeLegacyOsnrFields(f, JSON.parse(text))'), 'JSON editor migration on load');
check(validate.includes('não informe OSNR e SNR simultaneamente'), 'ambiguous threshold rejection');
check(validate.includes('pichOsnrCheckEnabled e pichSnrCheckEnabled simultaneamente'), 'ambiguous PICh gate rejection');
const start = app.indexOf('  App.normalizeLegacyOsnrFields = function');
const end = app.indexOf('  // ================================================================== serialization', start);
check(start >= 0 && end > start, 'normalizer present');
const vmApp = {App:{}};
vm.runInNewContext(app.slice(start, end), vmApp);
const oldNetwork = {modulations:[{name:'QPSK',SNR:8.95}],qkd:{pichSnrCheckEnabled:false}};
const migrated = vmApp.App.normalizeLegacyOsnrFields('network',oldNetwork);
check(migrated.modulations[0].OSNR === 8.95 && !('SNR' in migrated.modulations[0]), 'OSNR key migration');
check(migrated.qkd.pichOsnrCheckEnabled === false && !('pichSnrCheckEnabled' in migrated.qkd), 'PICh gate migration');
const both = {modulations:[{SNR:10,OSNR:11}],qkd:{pichSnrCheckEnabled:false,pichOsnrCheckEnabled:true}};
vmApp.App.normalizeLegacyOsnrFields('network',both);
check(both.modulations[0].SNR === 10 && both.modulations[0].OSNR === 11, 'ambiguous keys must not be silently discarded');
console.log('PASS LauncherOSNRNamingContract');
