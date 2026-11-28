#!/usr/bin/env python3
"""Screen candidate videos: which Tekla component (number and name) each one uses, and when its dialog is open (docs/steel/PARITY.md,
two-video method step 1: finding the videos). One low-resolution whole-video request per video; no values are taken from it.
    python3 tools/video/screen.py <out.json> <model> <component, e.g. "End plate (101)"> <video id> [...]
"""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__)); import vlib

P = '''This video shows Tekla Structures. Which steel connection components does the presenter add or edit (component number in brackets, e.g.
"End plate (144)", and name, as shown in the catalog, the dialog title or the model)? For each: the time span its dialog is open, the tabs
shown, and whether values are typed. Is "{comp}" used? Answer JSON: {{"components": [{{"number": "...", "name": "...", "start": "mm:ss",
"end": "mm:ss", "tabs": ["..."], "values_typed": true, "how_seen": "dialog title / catalog / spoken"}}], "uses_target": false,
"target_evidence": "...", "tekla_version": "...", "units": "mm or inch", "language": "..."}}'''

def main(out, model, comp, *ids):
    res = json.load(open(out)) if os.path.exists(out) else {'component': comp, 'videos': {}}
    for i, v in enumerate(ids):
        if v in res['videos'] and 'error' not in res['videos'][v]: continue
        if i: time.sleep(40)
        n = len(vlib.SERVED)
        try:
            t, _ = vlib.call([{'file_data': {'file_uri': f'https://www.youtube.com/watch?v={v}', 'mime_type': 'video/*'}, 'video_metadata': {'fps': 0.25}},
                              {'text': P.format(comp=comp)}], media_res='MEDIA_RESOLUTION_LOW', model=model)
            res['videos'][v] = {'model_asked': model, 'model_served': vlib.SERVED[n] if len(vlib.SERVED) > n else None, 'read': vlib.parse_json(t)}
        except Exception as e: res['videos'][v] = {'model_asked': model, 'error': str(e)[:1500]}
        json.dump(res, open(out, 'w'), indent=1); r = res['videos'][v]
        print(v, r.get('model_served'), 'ERROR ' + r['error'][:120] if 'error' in r else json.dumps(r['read'])[:500], flush=True)

if __name__ == '__main__': main(*sys.argv[1:])
