#!/usr/bin/env python3
"""Find reference material for a Tekla component's dialog tab (docs/steel/PARITY.md, method steps 0 and 1). No API key needed:
  1. Tekla's own pages: the component's help page on support.tekla.com (2025, 2024) is fetched; its text around the tab name is kept and
     every embedded video / animation (YouTube, Vimeo, mp4, gif) is listed;
  2. YouTube search results (the public results page), Tekla's own channels first (OWN: "Tekla Software" and the like),
     then the others, by how well the title matches.
Gemini's Google Search grounding is not used: this key has no quota for it (2026-11-26).

    python3 tools/video/find.py <tekla help slug, e.g. conn_144_help> "<component>" "<tab>" <out.json> [extra YouTube query ...]
"""
import html, json, os, re, sys, urllib.parse, urllib.request

def get(u, browser=False):   # support.tekla.com answers an empty page to a browser User-Agent, YouTube needs one
    hd = {'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64)'} if browser else {'User-Agent': 'curl/8.5'}
    with urllib.request.urlopen(urllib.request.Request(u, headers=hd), timeout=60) as r: return r.read().decode('utf8', 'replace')
OWN = ('tekla software', 'tekla', 'trimble tekla', 'tekla structures', 'tekla user assistance', 'trimble construction')   # Tekla's own channels

def text_of(h):
    h = re.sub(r'(?s)<(script|style).*?</\1>', '', h); h = re.sub(r'<br\s*/?>|</(p|div|li|tr|h\d|td|th)>', '\n', h)
    return [l.strip() for l in html.unescape(re.sub(r'<[^>]+>', '', h)).split('\n') if l.strip()]

def tekla(slug, tab):
    out = []
    for ver in ('2025', '2024'):
        u = f'https://support.tekla.com/doc/tekla-structures/{ver}/{slug}'
        try: h = get(u)
        except Exception as e: out.append({'url': u, 'error': str(e)}); continue
        L = text_of(h); i = next((k for k, l in enumerate(L) if l.lower() == f'{tab.lower()} tab'), None)
        sec = L[i:i + 80] if i is not None else []
        media = sorted(set(re.findall(r'(https?://[^"\'\s>]+(?:youtube\.com/embed/[\w-]+|youtu\.be/[\w-]+|vimeo\.com/[\w/]+|\.mp4|\.gif))', h)))
        out.append({'url': u, 'title': L[0] if L else '', 'tab_text': sec, 'media': media})
    return out

def youtube(q, comp, tab):
    h = get('https://www.youtube.com/results?search_query=' + urllib.parse.quote_plus(q), browser=True)
    m = re.search(r'var ytInitialData = (\{.*?\});</script>', h); d = json.loads(m.group(1)) if m else {}
    res = []
    def walk(o):
        if isinstance(o, dict):
            v = o.get('videoRenderer')
            if v: res.append({'url': 'https://www.youtube.com/watch?v=' + v.get('videoId', ''), 'title': ''.join(r.get('text', '') for r in v.get('title', {}).get('runs', [])),
                              'channel': ''.join(r.get('text', '') for r in v.get('ownerText', {}).get('runs', [])), 'length': v.get('lengthText', {}).get('simpleText')})
            for x in o.values(): walk(x)
        elif isinstance(o, list):
            for x in o: walk(x)
    walk(d)
    num = re.findall(r'\((\d+)\)', comp); words = [w.lower() for w in re.findall(r'\w+', comp + ' ' + tab) if len(w) > 2]
    for r in res:
        t = r['title'].lower()
        r['tekla_own'] = r['channel'].strip().lower() in OWN
        r['score'] = sum(w in t for w in words) + (2 if num and num[0] in t else 0) + (3 if tab.lower() in t else 0)
    return res

def main(slug, comp, tab, out, *extra):
    qs = [f'Tekla {comp} {tab}', f'Tekla {re.sub(r"[()]", "", comp)} {tab} tab'] + list(extra)
    vids = {}
    for q in qs:
        for r in youtube(q, comp, tab): vids.setdefault(r['url'], r)
    ranked = sorted(vids.values(), key=lambda r: (not r['tekla_own'], -r['score']))
    res = {'component': comp, 'tab': tab, 'tekla_pages': tekla(slug, tab), 'youtube_queries': qs, 'videos': ranked[:25]}
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True); json.dump(res, open(out, 'w'), indent=1)
    for p in res['tekla_pages']: print('Tekla:', p['url'], '| media:', p.get('media'), '| tab text lines:', len(p.get('tab_text', [])), p.get('error', ''))
    for v in res['videos'][:15]: print(('TEKLA ' if v['tekla_own'] else '      '), v['score'], v['url'], '|', v['title'][:70], '|', v['channel'], v['length'])

if __name__ == '__main__':
    main(*sys.argv[1:])
