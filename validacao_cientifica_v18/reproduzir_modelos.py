#!/usr/bin/env python3
"""Independent reproduction of Lacerda Jr. (2024) Figures 21(b), 22,
Johannisson & Agrell (2014) equations (11), (15), (16),
and Lobato XT equations (3.33)-(3.38). No hidden calibration factors.
"""
from pathlib import Path
import csv, json, math
from scipy.integrate import quad, dblquad
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
OUT = Path(__file__).resolve().parent / 'resultados'
OUT.mkdir(exist_ok=True)

# Lacerda Jr. 2024, Table 6 (p. 56): alpha field=0.1 dB/km.
alpha_f = 0.1 * math.log(10.0) / 10.0 / 1000.0
alpha_p = 2 * alpha_f
beta = 21.3e-27
gamma = 1.3e-3
L = 100_000.0
B = 32e9
spacing = 33.6e9
phi = 1.0
n_channels = 15
leff = -math.expm1(-2 * alpha_f * L) / (2 * alpha_f)
xi_h = 8.0 * gamma**2 * leff**2 * (2*alpha_f) / (27.0 * math.pi * beta)

def asinh(x): return math.asinh(x)

# Thesis Eqs. 3.23-3.31, equal PSD all channels, central CUT
psi_sci = xi_h * asinh(math.pi**2 * beta * B**2 / (4*alpha_f))
cor_sci = xi_h * 5.0 * phi / (3.0*alpha_f*L)
psi_xci = 0.0
cor_xci = 0.0
for j in range(-7, 8):
    if j == 0: continue
    delta = abs(j)*spacing
    fac = math.pi**2 * beta * B/(2*alpha_f)
    psi_xci += xi_h*(asinh(fac*(delta+B/2))-asinh(fac*(delta-B/2)))
    cor_xci += xi_h*5.0*phi*B/(6.0*alpha_f*delta*L)

# Normalized NLI = P_NLI/P^3 = (G_NLI * B) / P^3
# When G_i=P/B for every channel, result is sum_kernel/B^2.
raw_gn = (psi_sci+psi_xci)/B**2
raw_egn = max(0.0,(psi_sci+psi_xci-cor_sci-cor_xci)/B**2)
spans = [1,2,3,4,5,6,8,10,15,20,25,30,40,50]
def db(v): return 10.0*math.log10(v) if v>0 else float('-inf')
rows=[]
for n in spans:
    rows.append(dict(spans=n, gn_normalized_w2=raw_gn*n,
        egn_normalized_w2=raw_egn*n,
        gn_db=db(raw_gn*n), egn_db=db(raw_egn*n),
        correction_db=db(raw_gn/raw_egn)))
with (OUT/'reproducao_figuras_21_22.csv').open('w',newline='',encoding='utf-8') as f:
    w=csv.DictWriter(f,fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)

# Qualitative published values visible on FIG 21(b), 22, rounded
# Not claims of exact digitization: use only coarse check +/- 0.7 dB
published_approx={1:(30,28),5:(37,35),10:(40,38),50:(47,45)}
comp=[]
for n,(gn_ref,egn_ref) in published_approx.items():
    comp.extend([dict(fig='21(b)',n=n,model='GN',reference_approx_db=gn_ref,computed_db=db(n*raw_gn),abs_diff_db=abs(db(n*raw_gn)-gn_ref)),
                 dict(fig='21(b)/22',n=n,model='EGN',reference_approx_db=egn_ref,computed_db=db(n*raw_egn),abs_diff_db=abs(db(n*raw_egn)-egn_ref))])
with (OUT/'comparacao_qualitativa_figuras.csv').open('w',newline='',encoding='utf-8') as f:
    w=csv.DictWriter(f,fieldnames=list(comp[0])); w.writeheader(); w.writerows(comp)

fig,ax=plt.subplots(figsize=(8,5))
ax.plot(spans,[r['gn_db'] for r in rows],'k--',label='GN — Eq. 3.23, sem correção')
ax.plot(spans,[r['egn_db'] for r in rows],label='EGN aprox. — Eqs. 3.22 a 3.31')
ax.set_xscale('log'); ax.set_xticks([1,2,5,10,20,50]); ax.set_xticklabels(['1','2','5','10','20','50'])
ax.set_xlabel('Número de spans (100 km cada)'); ax.set_ylabel('NLI normalizada, 10log10(P_NLI/P³) [dB (1/W²)]')
ax.set_title('Reprodução independente — tese, Figuras 21(b) e 22'); ax.grid(alpha=0.25);ax.legend()
fig.tight_layout(); fig.savefig(OUT/'reproducao_figuras_21_22.png',dpi=175);plt.close(fig)

# Johannisson & Agrell, Eq. 11/15 exact rectangle with x,y integration;
# Eq. 16 asymptotic log approximation, applying Gamma_eff=8/9*physical Gamma.
# alpha is POWER loss m^-1 here, unlike alpha_field in thesis.
xi_j=4*math.pi**2*beta/alpha_p
gamma_eff=gamma*8/9

def ti2(x):
    if x==0: return 0.0
    if x>1: return (math.pi/2)*math.log(x) + ti2(1/x)
    return quad(lambda t: math.atan(t)/t if t!=0 else 1.0, 0,x,epsabs=1e-12,epsrel=1e-12)[0]

def j_terms(b,delta,bj):
    rho=xi_j*(b/2)**2
    # F_mm^2 = 4/xi * Ti2(rho)
    f_sci=4/xi_j*ti2(rho)
    # F_mm'^2 = 2/xi [Ti2(x2)-Ti2(x1)]
    f_xci=2/xi_j*(ti2(xi_j*(b/2)*(delta+bj/2))-ti2(xi_j*(b/2)*(delta-bj/2)))
    gi=1.0/b;gj=1.0/bj # for 1 W launched per polarization
    full_sci=(3*gamma_eff**2/alpha_p**2)*f_sci*gi**3
    full_xci=(6*gamma_eff**2/alpha_p**2)*f_xci*gi*gj**2
    pref=3*gamma_eff**2*gi/(2*math.pi*alpha_p*beta)
    log_sci=pref*gi**2*math.log(rho)
    log_xci=pref*gj**2*math.log((delta+bj/2)/(delta-bj/2))
    return dict(sci_11=full_sci,xci_11=full_xci,sci_16=log_sci,xci_16=log_xci,rho=rho)

jr=[]
for b_ghz in [12.5,25.0,28.0,32.0,50.0]:
    a=j_terms(b_ghz*1e9,33.6e9,b_ghz*1e9)
    jr.append(dict(bandwidth_ghz=b_ghz,delta_ghz=33.6,rho=a['rho'],
        sci_eq11_w_hz=a['sci_11'],sci_eq16_w_hz=a['sci_16'],
        xci_eq11_w_hz=a['xci_11'],xci_eq16_w_hz=a['xci_16'],
        sci_ratio_16_over_11=a['sci_16']/a['sci_11'],
        xci_ratio_16_over_11=a['xci_16']/a['xci_11']))
with (OUT/'johannisson_eq11_15_16.csv').open('w',newline='',encoding='utf-8') as f:
    w=csv.DictWriter(f,fieldnames=list(jr[0])); w.writeheader(); w.writerows(jr)

# Verify double integral (Eq 10) for narrow 12.5 / 25 GHz independently
quad_cross=[]
for bw in [12.5e9,25e9]:
    sep=33.6e9
    integrand=lambda y,x: 1.0/(1.0+(xi_j*x*y)**2)
    # rescale x,y into GHz for stable quad
    f_quad=lambda Y,X: 1.0/(1.0+(xi_j*(X*1e9)*(Y*1e9))**2)
    direct,_=dblquad(f_quad,-bw/2/1e9,bw/2/1e9,
        lambda X: sep/1e9-bw/2/1e9,lambda X: sep/1e9+bw/2/1e9,epsabs=1e-8)
    direct*=1e18
    analytic=2/xi_j*(ti2(xi_j*(bw/2)*(sep+bw/2))-ti2(xi_j*(bw/2)*(sep-bw/2)))
    quad_cross.append(dict(bandwidth_ghz=bw/1e9,direct_double_integral=direct,
                    analytical_eq15_integral=analytic,relative_error=abs(direct-analytic)/analytic))
with (OUT/'johannisson_integral_independente.csv').open('w',newline='',encoding='utf-8') as f:
    w=csv.DictWriter(f,fieldnames=list(quad_cross[0])); w.writeheader();w.writerows(quad_cross)

fig,ax=plt.subplots(figsize=(8,5))
ax.plot([r['bandwidth_ghz'] for r in jr],[r['sci_eq11_w_hz'] for r in jr],marker='o',label='SCI Eq. 11/15')
ax.plot([r['bandwidth_ghz'] for r in jr],[r['sci_eq16_w_hz'] for r in jr],marker='s',label='SCI Eq. 16 (assintótica)')
ax.axhline(0,lw=0.7);ax.set_xlabel('Largura do canal [GHz]');ax.set_ylabel('SCI PSD para Ppol = 1 W [W/Hz]')
ax.set_title('Johannisson — efeito da aproximação em canais estreitos');ax.legend();ax.grid(alpha=.25)
fig.tight_layout();fig.savefig(OUT/'johannisson_comparacao.png',dpi=175);plt.close(fig)

# XT thesis Eq 3.33–3.38, Figure 23 with guard excluded
# Example below uses one neighbouring circuit, k,R,beta,pitch independent of simulation config
k=1e-4;radius=.05;beta_c=5.9e6;pitch=41e-6
h=2*k*k*radius/(beta_c*pitch)
iso=2/5 # Figure 23
xt_p=iso*1e-3*h*100e3
xt_ratio=xt_p/1e-3
xt_raw=3/6 # incorrectly including two guard slots in total occupancy
xt=[dict(test='fig23_guard_excluded',overlap=2,interferer_slots=5,iso=iso,h_per_m=h,
         length_km=100,source_power_w=1e-3,xt_power_w=xt_p,xt_linear=xt_ratio,xt_db=db(xt_ratio)),
    dict(test='using_raw_allocated_bands_not_equivalent',overlap=3,interferer_slots=6,
         iso=xt_raw,h_per_m=h,length_km=100,source_power_w=1e-3,
         xt_power_w=xt_raw*1e-3*h*100e3,xt_linear=xt_raw*h*100e3,xt_db=db(xt_raw*h*100e3))]
with (OUT/'crosstalk_figura23.csv').open('w',newline='',encoding='utf-8') as f:
    w=csv.DictWriter(f,fieldnames=list(xt[0])); w.writeheader();w.writerows(xt)

summary={"parameters":{"alpha_field_per_m":alpha_f,"alpha_power_per_m":alpha_p,
                          "beta2_s2_per_m":beta,"gamma_Wminus1_mminus1":gamma,
                          "span_m":L,"bandwidth_hz":B,"spacing_hz":spacing,
                          "channels":15,"phi":phi,"xi":xi_h,"leff_m":leff},
         "gn_normalized_1span_db":db(raw_gn),"egn_normalized_1span_db":db(raw_egn),
         "gn_normalized_50span_db":db(50*raw_gn),"egn_normalized_50span_db":db(50*raw_egn),
         "reference_approx_errors_max_db":max(x['abs_diff_db'] for x in comp),
         "johannisson_narrow":jr[:2],"direct_double_integral":quad_cross,
         "xt_figure23_normalized":iso,"xt_if_guard_included":xt_raw}
(OUT/'resumo.json').write_text(json.dumps(summary,indent=2,ensure_ascii=False),encoding='utf-8')
assert max(x['abs_diff_db'] for x in comp)<0.7, 'Figure 21b/22 coarse agreement fails'
assert all(x['relative_error']<1e-7 for x in quad_cross)
assert abs(raw_egn/raw_gn - 0.6224)<0.01
print('PASS reproduction, Johannisson integrals, and XT analytical reference')
print(json.dumps({k:v for k,v in summary.items() if k.endswith('_db')},indent=2))
