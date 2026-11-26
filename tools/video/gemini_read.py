#!/usr/bin/env python3
"""Read a component dialog tab's values from a YouTube video with Gemini, in two passes (docs/steel/PARITY.md, method step 1):
  pass 1: the whole video at low resolution, 0.5 fps -> the time spans where the tab is open (mm:ss);
  pass 2: each span again at high resolution (mediaResolution HIGH, videoMetadata start/end offsets, 2 fps) -> every field's
          label and value as shown, with the time it was read.
    python3 tools/video/gemini_read.py read <youtube url> "<component>" "<tab>" <out.json>
    python3 tools/video/gemini_read.py summary <dir with read_*.json> <VIDEO.txt> [site.json]
summary writes the values read per source and which of them two sources agree on (two videos, or a video and Tekla's site:
site.json = {"source": "...", "values": {label: value}}).
"""
import glob, json, os, sys
sys.path.insert(0, os.path.dirname(__file__)); import vlib

def read(url, comp, tab, out):
    vid = {'file_data': {'file_uri': url, 'mime_type': 'video/*'}}
    p1 = f'''This video shows Tekla Structures. Find every time span where the dialog of "{comp}" is open on its "{tab}" tab.
Answer JSON: {{"spans": [{{"start": "mm:ss", "end": "mm:ss", "note": "what is done"}}], "language": "...", "units": "mm or inch"}}. Empty list if never shown.'''
    # pass 1 at low resolution and 0.5 fps: only the minutes are needed (a whole video at full resolution exceeds the free tier's tokens per minute)
    t1, _ = vlib.call([{'file_data': vid['file_data'], 'video_metadata': {'fps': 0.5}}, {'text': p1}], media_res='MEDIA_RESOLUTION_LOW'); r1 = vlib.parse_json(t1)
    res = {'url': url, 'component': comp, 'tab': tab, 'model': vlib.MODEL, 'pass1': r1, 'pass2': []}
    for sp in (r1.get('spans') or [])[:6]:
        try: a, b = vlib.mmss(sp['start']), vlib.mmss(sp['end'])
        except Exception: continue
        a = max(0, a - 3); b = b + 3
        seg = {'file_data': vid['file_data'], 'video_metadata': {'start_offset': f'{int(a)}s', 'end_offset': f'{int(b)}s', 'fps': 2}}
        p2 = f'''Read the "{tab}" tab of the "{comp}" dialog in this part of the video, at full resolution. For every input box, list, check box and
picture dimension: its label (as written, or the number on the picture) and its value exactly as shown (blank if empty), and the time.
Only report what you can read; mark unsure readings. Answer JSON: {{"fields": [{{"label": "...", "value": "...", "t": "mm:ss", "sure": true}}]}}'''
        try: t2, _ = vlib.call([seg, {'text': p2}], media_res='MEDIA_RESOLUTION_HIGH'); r2 = vlib.parse_json(t2)
        except Exception as e: r2 = {'error': str(e)}
        res['pass2'].append({'span': [sp.get('start'), sp.get('end')], 'read': r2})
    res['models_used'] = sorted(set(vlib.USED))
    os.makedirs(os.path.dirname(out) or '.', exist_ok=True); json.dump(res, open(out, 'w'), indent=1)
    print(json.dumps(res, indent=1)[:4000])

def norm(v): return str(v).strip().lower().replace(' ', '').replace('mm', '')

def summary(d, out, site=None):
    srcs = []
    for f in sorted(glob.glob(os.path.join(d, 'read_*.json'))):
        r = json.load(open(f)); vals = {}
        for p in r.get('pass2', []):
            for fl in (p.get('read') or {}).get('fields', []) or []:
                if fl.get('value') not in (None, ''): vals.setdefault(str(fl.get('label')).strip(), set()).add(str(fl.get('value')).strip())
        srcs.append((r['url'], r.get('pass1', {}).get('spans', []), vals))
    if site: s = json.load(open(site)); srcs.append((s['source'], [], {k: {str(v)} for k, v in s['values'].items()}))
    L = ['Values read (tools/video/gemini_read.py; pass 2 at high resolution on the spans where the tab is open).', '']
    for u, spans, vals in srcs:
        L.append(f'Source: {u}'); L.append('  spans: ' + (', '.join(f"{s.get('start')}-{s.get('end')}" for s in spans) or '-'))
        for k in sorted(vals): L.append(f'  {k}: {" / ".join(sorted(vals[k]))}')
        L.append('')
    L.append('Agreement (the same label and value in two sources):')
    labels = {}
    for u, _, vals in srcs:
        for k, vs in vals.items():
            for v in vs: labels.setdefault((k.lower(), norm(v)), set()).add(u)
    agreed = sorted((k, v, us) for (k, v), us in labels.items() if len(us) >= 2)
    L += [f'  {k} = {v}  ({len(us)} sources)' for k, v, us in agreed] or ['  none']
    open(out, 'w').write('\n'.join(L) + '\n'); print('\n'.join(L))

if __name__ == '__main__':
    if sys.argv[1] == 'read': read(*sys.argv[2:6])
    else: summary(*sys.argv[2:5])
