#!/usr/bin/env python3
"""Re-read a component's video with ONE named Gemini model (no fallback to a lighter model), in two passes (docs/steel/PARITY.md,
two-video method step 1; docs/steel/HANDOFF.md step 1):
  pass 1: the whole video at low resolution, 0.5 fps -> where each dialog tab, the status bar and the symbol are seen (mm:ss);
  pass 2: the given pieces (60-90 s) at high resolution, 1 fps -> every field's label and value, the status bar text and the symbol colour,
          each with its time.
Each answer records the model asked for and the modelVersion Google reports (they can differ). The key comes from GEMINI_API_KEY and is
never printed or written.
"mode": "agentic" (owner, 2026-11-28) reads each piece with Gemini's agentic video understanding (Interactions API, vlib.agentic): the
model navigates to the piece itself; the piece's time range is given in the prompt, since agentic mode refuses offsets. "static" (the
default) is the GenerateContent read with start/end offsets, 1 fps, high resolution. Each piece records its token usage.
    python3 tools/video/reread.py <job.json> <out.json>
job.json = {"url": "...", "component": "...", "model": "gemini-3.5-flash", "mode": "static" | "agentic", "pass1": true, "context": "...",
            "pieces": [{"start": "mm:ss", "end": "mm:ss", "ask": "what to look for in this piece"}]}
Model order (owner 2026-11-28, vlib.rotate): 3.6 / 3.7 Flash with agentic reads first; when their daily free quota ends (or they stay
busy), the other full Flash models (3.8, 3.5, 3-flash-preview) with static reads and time-range clipping. Never Flash-Lite. Every answer records the model asked, the model served and the mode.
Pieces already in <out.json> with an answer are not asked again (re-run after a 429 / 503). Pieces are asked 70 s apart
("pause" in job.json): the free tier allows only a few requests per minute.
"""
import json, os, sys, time
sys.path.insert(0, os.path.dirname(__file__)); import vlib

P1 = '''This video shows Tekla Structures. Context: {context}
Watch the whole video and list, with times (mm:ss), every moment that matters for the component "{comp}":
- when the parts are picked and which profile names are visible (property pane, labels, typed text, narrator);
- each time a tab of the "{comp}" dialog is open (tab name, start / end) and the values typed;
- every Modify / Apply / OK press;
- every status bar message (bottom left of the window) after the component exists: its exact text and time;
- the component's symbol (the small cone / sphere at the joint): its colour (green / yellow / red) and when it is seen.
Answer JSON: {{"members": [{{"role": "...", "profile": "...", "t": "mm:ss", "how": "...", "sure": true}}],
 "tabs": [{{"tab": "...", "start": "mm:ss", "end": "mm:ss", "values": "..."}}], "presses": [{{"t": "mm:ss", "button": "..."}}],
 "status_bar": [{{"t": "mm:ss", "text": "...", "sure": true}}], "symbol": [{{"t": "mm:ss", "colour": "...", "selected": false, "sure": true}}],
 "units": "mm or inch", "notes": "..."}}'''

P2 = '''This part of a video shows Tekla Structures. Context: {context}
Look at it at full resolution. In this piece: {ask}
Report only what you can read on screen; mark every unsure reading with "sure": false; give the time (mm:ss of the WHOLE video) of each reading.
For the dialog: every input box, list and check box of each tab shown (label as written, or the number on the picture) and its value exactly as
shown (blank if empty); for each value the LAST state in this piece, and changes as separate entries.
Answer JSON: {{"members": [{{"role": "...", "profile": "...", "t": "mm:ss", "how": "...", "sure": true}}],
 "fields": [{{"tab": "...", "label": "...", "value": "...", "t": "mm:ss", "sure": true}}], "presses": [{{"t": "mm:ss", "button": "..."}}],
 "status_bar": [{{"t": "mm:ss", "text": "exact text", "sure": true}}],
 "symbol": [{{"t": "mm:ss", "colour": "green / yellow / red / not visible", "selected": false, "how": "...", "sure": true}}],
 "geometry": "what the joint looks like (plates, bolts, where they sit)", "notes": "..."}}'''

AG = """Look ONLY at the part of the video from {a} to {b} (mm:ss): go to it directly, zoom into the dialog and the status bar as needed, and
ignore the rest of the video. """

def sec(s): return int(vlib.mmss(s))

def main(job_f, out_f):
    job = json.load(open(job_f)); url = job['url']; model = job.get('model', 'gemini-3.5-flash')
    res = json.load(open(out_f)) if os.path.exists(out_f) else {'url': url, 'component': job['component'], 'model_asked': model, 'pass1': None, 'pass2': []}
    done = {tuple(p['seconds']) for p in res['pass2'] if 'error' not in p}
    res['pass2'] = [p for p in res['pass2'] if 'error' not in p]
    vid = {'file_uri': url, 'mime_type': 'video/*'}
    if job.get('pass1') and not (res.get('pass1') and 'error' not in res['pass1']):
        n = len(vlib.SERVED)
        try:
            used, (t, _) = vlib.rotate(model, lambda m: vlib.call([{'file_data': vid, 'video_metadata': {'fps': 0.5}},
                             {'text': P1.format(context=job['context'], comp=job['component'])}], media_res='MEDIA_RESOLUTION_LOW', model=m))
            res['pass1'] = {'model_asked': used, 'model_served': vlib.SERVED[-1], 'usage': vlib.USAGE[-1], 'read': vlib.parse_json(t)}
        except Exception as e: res['pass1'] = {'model_asked': model, 'error': str(e)[:1500]}
        json.dump(res, open(out_f, 'w'), indent=1)
    agentic = job.get('mode') == 'agentic'
    for pc in job.get('pieces', []):
        a, b = sec(pc['start']), sec(pc['end'])
        if (a, b) in done: continue
        if pc is not job['pieces'][0] or res.get('pass1'): time.sleep(job.get('pause', 70))   # free tier: a few requests per minute
        seg = {'file_data': vid, 'video_metadata': {'start_offset': f'{a}s', 'end_offset': f'{b}s', 'fps': pc.get('fps', 1)}}
        n = len(vlib.SERVED)
        try:
            def one(m):   # agentic where the model takes it (the time range goes into the prompt: agentic mode takes no offsets), else static
                if agentic and m in vlib.AGENTIC_MODELS:
                    t, info = vlib.agentic(url, AG.format(a=pc['start'], b=pc['end']) + P2.format(context=job['context'], ask=pc['ask']), m)
                    return t, dict(info, mode='agentic')
                t, _ = vlib.call([seg, {'text': P2.format(context=job['context'], ask=pc['ask'])}], media_res='MEDIA_RESOLUTION_HIGH', model=m)
                return t, {'mode': 'static'}
            used, (t, info) = vlib.rotate(model, one)
            rec = {'seconds': [a, b], 'span': [pc['start'], pc['end']], 'ask': pc['ask'], 'model_asked': used,
                   'model_served': vlib.SERVED[-1], 'usage': vlib.USAGE[-1], **info,
                   'read': vlib.parse_json(t)}
        except Exception as e:
            rec = {'seconds': [a, b], 'span': [pc['start'], pc['end']], 'ask': pc['ask'], 'model_asked': model, 'error': str(e)[:1500]}
        res['pass2'].append(rec); json.dump(res, open(out_f, 'w'), indent=1)
        print(pc['start'], pc['end'], rec.get('model_served'), 'ERROR ' + rec['error'][:200] if 'error' in rec else 'ok', flush=True)
    res['pass2'].sort(key=lambda p: p['seconds']); res.setdefault('rotations', []).extend(vlib.ROTATIONS); json.dump(res, open(out_f, 'w'), indent=1)
    print('pass1', (res.get('pass1') or {}).get('model_served'), 'error' if 'error' in (res.get('pass1') or {}) else 'ok')

if __name__ == '__main__': main(*sys.argv[1:3])
