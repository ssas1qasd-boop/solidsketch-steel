"""Shared Gemini call for tools/video (reference order 3 in docs/steel/PARITY.md). The key comes from GEMINI_API_KEY and is never
printed or written. Retries on 429 / 503 / dropped connections."""
import json, os, time, urllib.request, urllib.error, http.client

MODEL = os.environ.get('GEMINI_MODEL', 'gemini-flash-latest')
FALLBACK = None   # owner 2026-11-28: no Flash-Lite fallback for dialog values (Flash-Lite only for screening); see rotate() for the full models
USED = []
USAGE = []    # usage per answer: GenerateContent usageMetadata, or the Interactions API's usage (agentic)
SERVED = []   # the modelVersion Google reports for each answer (it can differ from the requested name)

def call(parts, tools=None, json_out=True, media_res=None, model=None, tries=4):
    for m in ([model] if model else [MODEL]):
        try: r = _call(parts, tools, json_out, media_res, m, tries); USED.append(m); return r
        except RuntimeError as e:
            if '503' not in str(e) and 'connection' not in str(e) and 'PerDay' not in str(e): raise
            last = e
    raise last

def _call(parts, tools, json_out, media_res, model, tries):
    body = {'contents': [{'parts': parts}], 'generationConfig': {'temperature': 0}}
    if json_out and not tools: body['generationConfig']['responseMimeType'] = 'application/json'
    if media_res: body['generationConfig']['mediaResolution'] = media_res
    if tools: body['tools'] = tools
    req = urllib.request.Request(f'https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent', data=json.dumps(body).encode(),
                                 headers={'Content-Type': 'application/json', 'x-goog-api-key': os.environ['GEMINI_API_KEY']})
    err = None
    for k in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=900) as r: d = json.load(r); break
        except urllib.error.HTTPError as e:
            err = 'Gemini %d: %s' % (e.code, e.read()[:1500].decode('utf8', 'replace'))
            if e.code not in (429, 500, 503): raise RuntimeError(err)
            if e.code == 429 and 'PerDay' in err: raise RuntimeError(err)   # daily quota of this model used up: the caller falls back
            if e.code == 429: time.sleep(65)   # free tier: tokens per minute
        except (http.client.RemoteDisconnected, ConnectionError, TimeoutError, urllib.error.URLError) as e:
            err = 'connection: %s' % e
        time.sleep(20 * (k + 1))
    else: raise RuntimeError(err)
    SERVED.append(d.get('modelVersion') or model); USAGE.append({'api': 'generateContent', **(d.get('usageMetadata') or {})})
    cand = (d.get('candidates') or [{}])[0]
    txt = ''.join(p.get('text', '') for p in cand.get('content', {}).get('parts', []))
    meta = cand.get('groundingMetadata') or {}
    return txt, meta

def parse_json(txt):
    t = txt.strip()
    if t.startswith('```'): t = t.split('\n', 1)[1].rsplit('```', 1)[0]
    try: return json.loads(t)
    except Exception:
        i, j = t.find('{'), t.rfind('}')
        try: return json.loads(t[i:j + 1]) if i >= 0 else {'raw': txt}
        except Exception: return {'raw': txt}

def mmss(s):
    s = str(s).strip(); p = [float(x) for x in s.split(':')]
    return p[0] * 60 + p[1] if len(p) == 2 else p[0] * 3600 + p[1] * 60 + p[2] if len(p) == 3 else p[0]


def agentic(url, prompt, model, tries=4):
    """Agentic video understanding (Gemini API, Sept 2026): the Interactions API with "processing": "agentic" on the video part; the
    model navigates the video itself (processing_call / processing_result steps) instead of reading every frame. Clipping by
    start/end offset is refused in this mode ('agentic' is not supported for processing.type: only 'static' takes offsets), so the
    time range goes into the prompt. Returns (text, steps summary); appends the served model and the usage to SERVED / USAGE."""
    body = {'model': model, 'input': [{'type': 'text', 'text': prompt}, {'type': 'video', 'uri': url, 'mime_type': 'video/*', 'processing': 'agentic'}]}
    req = urllib.request.Request('https://generativelanguage.googleapis.com/v1beta/interactions', data=json.dumps(body).encode(),
                                 headers={'Content-Type': 'application/json', 'x-goog-api-key': os.environ['GEMINI_API_KEY']})
    err = None
    for k in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=900) as r: d = json.load(r); break
        except urllib.error.HTTPError as e:
            err = 'Gemini %d: %s' % (e.code, e.read()[:1500].decode('utf8', 'replace'))
            if e.code not in (429, 500, 503) or (e.code == 429 and 'PerDay' in err): raise RuntimeError(err)
            if e.code == 429: time.sleep(65)
        except (http.client.RemoteDisconnected, ConnectionError, TimeoutError, urllib.error.URLError) as e:
            err = 'connection: %s' % e
        time.sleep(20 * (k + 1))
    else: raise RuntimeError(err)
    steps = d.get('steps') or []
    txt = ''.join(c.get('text', '') for s in steps if s.get('type') == 'model_output' for c in s.get('content', []) if c.get('type') == 'text')
    SERVED.append(d.get('model') or model); USAGE.append({'api': 'interactions', 'processing': 'agentic', **(d.get('usage') or {})})
    return txt, {'processing_calls': sum(s.get('type') == 'processing_call' for s in steps), 'status': d.get('status')}


# Owner 2026-11-28: each Gemini model has its own free daily quota (20 requests). When a full Flash model hits it (or stays busy), the
# read continues on the next full Flash model; Flash-Lite never reads dialog values. Names Google serves under another model share that
# model's quota (seen: gemini-3.5-flash -> gemini-3.6-flash, gemini-flash-latest -> gemini-3.8-flash; gemini-3.7-flash is deprecated and
# routes to gemini-3.8-flash).
# Order (owner 2026-11-28): 3.6 / 3.7 Flash with agentic reads first; when their quota ends, the other full Flash models with static reads
# and time-range clipping. gemini-3.7-flash routes to gemini-3.8-flash, so it shares 3.8's quota.
FULL_FLASH = ['gemini-3.6-flash', 'gemini-3.7-flash', 'gemini-3.8-flash', 'gemini-3.5-flash', 'gemini-3-flash-preview']
SERVES = {'gemini-flash-latest': 'gemini-3.8-flash', 'gemini-3.7-flash': 'gemini-3.8-flash', 'gemini-3.5-flash': 'gemini-3.6-flash'}
AGENTIC_MODELS = {'gemini-3.6-flash', 'gemini-3.7-flash'}   # owner 2026-11-28: agentic only on these (asked names); the others read static
EXHAUSTED = set()   # served models whose daily quota is used up in this run
ROTATIONS = []      # [(model tried, reason)] for the record

def rotate(first, fn):
    """fn(model) -> result. Tries `first`, then the other full Flash models, skipping a model whose served model is exhausted."""
    if 'lite' in first: raise ValueError('Flash-Lite (even agentic) is for screening only, never for dialog values (owner 2026-11-28)')
    last = None
    # a job that names a model starts with it (a second-model cross-check names the other model); then the owner's order
    for m in [first] + [x for x in FULL_FLASH if x != first]:
        if SERVES.get(m, m) in EXHAUSTED: continue
        try: return m, fn(m)
        except RuntimeError as e:
            es = str(e); last = e
            if '429' in es and 'PerDay' in es:
                import re
                hit = re.findall(r'model: ([\w.-]+)', es); EXHAUSTED.add(hit[0] if hit else SERVES.get(m, m)); ROTATIONS.append((m, 'daily quota used up'))
            elif '429' in es: ROTATIONS.append((m, 'rate limited (429) after retries'))
            elif '503' in es or 'connection' in es: ROTATIONS.append((m, 'busy (503) / no answer'))
            elif '404' in es or 'not supported' in es or 'INVALID_ARGUMENT' in es: ROTATIONS.append((m, 'refused: ' + es[:120]))
            else: raise
    raise last or RuntimeError('no full Flash model left today')
