"""Tests the JSON/HTML contracts of Launcher 1.23.1 without modifying simulator files.
Run: python launcher/tests/test_schema_contract.py
"""
from pathlib import Path
import json,re,sys,subprocess
root=Path(__file__).resolve().parents[1]
html=(root/'web/index.html').read_text(encoding='utf-8')
js=(root/'web/js/schema.js').read_text(encoding='utf-8')
app=(root/'web/js/app.js').read_text(encoding='utf-8')
validate=(root/'web/js/validate.js').read_text(encoding='utf-8')
template=json.loads((root/'templates/physicalLayer').read_text(encoding='utf-8'))
assert 'js/bulk.js' in html and (root/'web/js/bulk.js').is_file()
for required in ['btnExitBulk','btnSelect','caseSticky','caseSaved']:
    assert f'id="{required}"' in html, required
assert 'muxInsertionLoss' in template and 'demuxInsertionLoss' in template
assert template['muxInsertionLoss'] == template['switchInsertionLoss']
assert template['demuxInsertionLoss'] == template['switchInsertionLoss']
for field in ['opticalBudget.txMuxLossDb','opticalBudget.rxDemuxLossDb',
              'opticalBudget.bypassLossDb','opticalBudget.residualAseReceiverW',
              'pich.launchPowerDbm','qkdKeyDemand.profile','qkdKeyDemand.preferGeneratorFixedRate']:
    assert field in js,field
assert 'out.opticalBudget' in app
assert 'out.pich' in app
assert 'Number.isFinite(qp.opticalBudget[key])' in validate
assert 'physicalLayerModel' in js and 'EGN aproximado' in js
for src in re.findall(r'<script src="([^"]+)"></script>',html):
    assert (root/'web'/src).exists(),src
print('PASS: 1.23.1 schema, HTML, templates, QKD fields')

for field in ['johannissonNliMode','habibiNliMode','nliNarrowbandGHz',
              'habibiEgnQuadratureOrder','habibiEgnReceiverOrder']:
    assert field in js and field in template,field
for removed in ['johannissonSciUseIntegral','johannissonXciUseIntegral',
                'habibiEgnFallbackEnabled','habibiEgnNarrowbandGHz','johannissonUseIntegral']:
    assert removed not in js and removed not in template,removed
print('PASS: current NLI modes only, old options removed')

# QoT/XT/gain selector semantics and visible millisecond control (no duplicated alias).
assert "0: '0 — separado', 1: '1 — junto'" in js
assert "0: '0 — limiar OSNR', 1: '1 — BER a partir de OSNR'" in js
assert "0: '0 — ganho fixo', 1: '1 — ganho saturado'" in js
assert 'Habibi permanece o EGN aproximado da tese de Jurandir.' not in js
assert "'qkdKeyDemand.updateIntervalsMiliseconds'" in js
assert "'qkdKeyDemand.updateIntervalsMiliseconds': { label: 'Intervalos de renovação (milissegundos)', unit: 'ms', default: [64, 128, 256], help:" in js
assert "'qkdKeyDemand.updateIntervalsMilliseconds': { label: 'Intervalos de renovação (milissegundos)', unit: 'ms', default: [64, 128, 256], hiddenInForm: true" in js
forms=(root/'web/js/forms.js').read_text(encoding='utf-8')
assert 'hintFor(schema, path.concat([k])).hiddenInForm' in forms
assert '!schema.fields[k].hiddenInForm' in forms
assert 'berThreshold' in js and 'berThreshold' in validate
assert 'berModel' in js and 'berModel' in validate
print('PASS: Launcher 1.24.4 QoT, XT, saturation and visible millisecond control')

# New three-mode GN/EGN approximation switches.
for key in ['johannissonNliMode', 'habibiNliMode', 'nliNarrowbandGHz']:
    assert key in js and key in template, key
assert template['johannissonNliMode'] == 'adaptive'
assert template['habibiNliMode'] == 'adaptive'
assert template['nliNarrowbandGHz'] == 25.0
assert 'approximate' in js and 'integral' in js and 'adaptive' in js
assert 'johannissonNliMode' in validate and 'habibiNliMode' in validate
print('PASS: Johannisson/Habibi three integration modes')
