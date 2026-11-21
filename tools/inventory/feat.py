# Usage: python3 tools/inventory/feat.py reference/solidsketch_v69.html
# Writes FEATURES.md (copy it to docs/steel/FEATURES.md).
import re,sys,json,collections
t=open(sys.argv[1],encoding='utf-8',errors='replace').read()
J=json.JSONDecoder()
i=t.find('const TK = [')+len('const TK = '); TK,_=J.raw_decode(t[i:])
i=t.find('const ST_CAT = ')+len('const ST_CAT = '); CAT,_=J.raw_decode(t[i:])
tkd=re.findall(r'TKD\.(T\w+) = ',t)
fam=collections.Counter(re.match(r'[A-Za-z]+',k).group(0) for k in CAT)
hdr=[m.group(1).strip() for m in re.finditer(r'// =+ (v(?:3[2-9]|[4-6]\d)[a-z]?[^=\n]{0,110})',t)]
btn=sorted(set(re.findall(r"stBtn\('([^']{2,30})'",t)))
o=['# Steel features in web build v69 (inventory)\n']
o.append('## Chapters of the steel code\n'); o+=[f'- {h}' for h in hdr]
o.append('\n## Profiles (`ST_CAT`, %d entries)\n' % len(CAT)); o.append(', '.join(f'{k} {v}' for k,v in fam.most_common()))
o.append(f'\n## Components with full Tekla dialogs (`TKD`, {len(tkd)})\n' + ', '.join(tkd) + '\n')
o.append(f'## Tekla component catalog (`TK`, {len(TK)} components)\n')
g=collections.defaultdict(list)
for e in TK: g[e['grp']].append(e)
o.append('| Group | Count | Family | Components (Tekla number · name) |\n|---|---|---|---|')
for k,v in sorted(g.items(), key=lambda x:-len(x[1])):
    o.append(f"| {k} | {len(v)} | {','.join(sorted(set(e['fam'] for e in v)))} | " + '; '.join(f"{e['n']} · {e['name']}" for e in v) + ' |')
o.append('\n## Steel UI buttons (`stBtn` labels)\n' + ' · '.join(btn) + '\n')
open('FEATURES.md','w').write('\n'.join(o)); print(len(TK),'components,',len(CAT),'profiles,',len(tkd),'dialogs')
