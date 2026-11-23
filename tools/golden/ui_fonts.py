#!/usr/bin/env python3
"""The owner's phone renders v69 in Roboto (v69's font stack: -apple-system, "Segoe UI", Roboto, …; Android Chrome has
Roboto, and system-ui is Roboto there). Headless Chromium here has no Roboto, so every text width would differ from the
phone. This copies the Roboto files Robolectric's native runtime ships (the font the app's UI tests draw with; Apache-2.0)
to ~/.fonts/android. In those files Regular, Italic, Bold and Bold Italic are named "Roboto21382017" instead of "Roboto",
so Chromium would draw normal text with Roboto Medium; the family name is set to plain "Roboto" here (fontTools).
Test tooling only; nothing goes into the APK or the repo.  pip install fonttools; python3 tools/golden/ui_fonts.py"""
import glob, io, os, subprocess, zipfile
from fontTools.ttLib import TTFont
roots = [os.environ.get('LOCAL_M2', '/home/user/m2local'), os.path.expanduser('~/.gradle/caches'), os.path.expanduser('~/.m2')]
jars = [j for r in roots for j in glob.glob(os.path.join(r, '**', 'nativeruntime-dist-compat-*.jar'), recursive=True)]
if not jars: raise SystemExit('nativeruntime-dist-compat jar not found (run the app UI tests once first)')
out = os.path.expanduser('~/.fonts/android'); os.makedirs(out, exist_ok=True)
STYLE = {'Regular': ('Regular', 400), 'Italic': ('Italic', 400), 'Bold': ('Bold', 700), 'BoldItalic': ('Bold Italic', 700)}
with zipfile.ZipFile(jars[0]) as z:
    for n in z.namelist():
        b = os.path.basename(n)
        if not (b.startswith('Roboto-') and b.endswith('.ttf')): continue
        f = TTFont(io.BytesIO(z.read(n))); st = b[len('Roboto-'):-4]
        if st in STYLE:
            sub, _ = STYLE[st]
            for rec in f['name'].names:
                if rec.nameID in (1, 16): rec.string = 'Roboto'
                elif rec.nameID in (2, 17): rec.string = sub
                elif rec.nameID == 4: rec.string = 'Roboto ' + sub
        f.save(os.path.join(out, b))
subprocess.run(['fc-cache', '-f'], check=True)
print('Roboto installed from', jars[0])
