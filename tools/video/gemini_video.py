#!/usr/bin/env python3
"""Ask Gemini about a YouTube video (default model gemini-flash-latest: the free tier has no Pro quota) (reference order 3 in docs/steel/PARITY.md): timestamps and dialog values. The key comes from the
environment (GEMINI_API_KEY) and is never printed or written. The answer is saved as JSON next to the frames.

    python3 tools/video/gemini_video.py <youtube url> <prompt file> <out.json> [model]
"""
import json, os, sys, urllib.request, urllib.error

def ask(url, prompt, model='gemini-flash-latest'):
    key = os.environ['GEMINI_API_KEY']
    body = {'contents': [{'parts': [{'file_data': {'file_uri': url, 'mime_type': 'video/*'}}, {'text': prompt}]}],
            'generationConfig': {'temperature': 0, 'responseMimeType': 'application/json'}}
    req = urllib.request.Request(f'https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent', data=json.dumps(body).encode(),
                                 headers={'Content-Type': 'application/json', 'x-goog-api-key': key})
    import time
    for k in range(6):                                   # 503 (busy) / 429 (per-minute limit): wait and retry
        try:
            with urllib.request.urlopen(req, timeout=900) as r: d = json.load(r); break
        except urllib.error.HTTPError as e:
            if e.code not in (429, 503) or k == 5: raise RuntimeError('Gemini %d: %s' % (e.code, e.read()[:300].decode('utf8', 'replace')))
            time.sleep(20 * (k + 1))
    txt = ''.join(p.get('text', '') for c in d.get('candidates', []) for p in c.get('content', {}).get('parts', []))
    try: return json.loads(txt)
    except Exception: return {'raw': txt}

if __name__ == '__main__':
    url, pf, out = sys.argv[1:4]; model = sys.argv[4] if len(sys.argv) > 4 else 'gemini-flash-latest'
    res = ask(url, open(pf).read(), model); res = {'url': url, 'model': model, 'answer': res}
    os.makedirs(os.path.dirname(out), exist_ok=True); json.dump(res, open(out, 'w'), indent=1); print(json.dumps(res, indent=1)[:6000])
