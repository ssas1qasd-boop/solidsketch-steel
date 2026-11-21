# Usage: python3 tools/inventory/chains.py reference/solidsketch_v69.html
# Prints the table rows for docs/steel/LIVE-FUNCTIONS.md (patched function chains in the steel code).
import re,sys,collections,bisect
t=open(sys.argv[1],encoding='utf-8',errors='replace').read()
a=t.find('// ===================== v32 · Steel')
s=t[a:]
lines_off=[0]
for m in re.finditer('\n',t): lines_off.append(m.end())
ln=lambda p: bisect.bisect_right(lines_off,p)
names=set(re.findall(r'\bfunction\s+((?:st|dw|tk|nc)[A-Z]\w*)',s))
defs=collections.defaultdict(list)
for m in re.finditer(r'\bfunction\s+((?:st|dw|tk|nc)[A-Z]\w*)\s*\(',s): defs[m.group(1)].append(('def',ln(a+m.start())))
for m in re.finditer(r'(?<![\w.])((?:st|dw|tk|nc)[A-Z]\w*)\s*=\s*(?:async\s+)?(?:function|\()',s):
    if m.group(1) in names: defs[m.group(1)].append(('reassign',ln(a+m.start())))
for m in re.finditer(r'const\s+(\w+)\s*=\s*((?:st|dw|tk|nc)[A-Z]\w*)\s*;',s): defs[m.group(2)].append(('saved-as '+m.group(1),ln(a+m.start())))
hdr=[(ln(a+m.start()),m.group(1).strip()) for m in re.finditer(r'// =+ (v\d+[^=\n]{0,90})',s)]
def chap(l):
    c=''
    for hl,h in hdr:
        if hl<=l: c=h.split('·')[0].strip()
    return c
chained={k:v for k,v in defs.items() if len(v)>1}
print(len(names),'functions;',len(chained),'with several definitions or wrappers')
for k in sorted(chained, key=lambda k:-len(chained[k])):
    v=sorted(chained[k],key=lambda x:x[1])
    print(f"| `{k}` | {len(v)} | " + ' → '.join(f"{w} L{l} ({chap(l)})" for w,l in v) + ' |')
