#!/usr/bin/env python3
"""Minimal IFC2X3 (STEP physical file) reader for the Tekla exports in tests/steel/tekla/ (own code, no IFC library: the usual
Python IFC toolkits are LGPL and CLAUDE.md forbids GPL/LGPL code). Reads only what Tekla's "surface geometry" export uses:
IfcLocalPlacement chains, IfcFacetedBrep (IfcPolyLoop faces, inner bounds), IfcMappedItem / IfcRepresentationMap with
IfcCartesianTransformationOperator3D, property sets, materials, IfcRelAggregates (assemblies) and
IfcRelConnectsWithRealizingElements (which parts a bolt group or weld joins).

    from ifc_read import Ifc
    f = Ifc(path); f.ents[id] -> (TYPE, args); f.placement(pid) -> 4x4; f.mesh(product_id) -> (V (n,3), T (m,3))
"""
import re
import numpy as np

_tok = re.compile(r"\s*(?:(#\d+)|('(?:[^']|'')*')|(\.[A-Z0-9_]+\.)|([A-Z][A-Z0-9_]*)\s*\(|(\()|(\))|(,)|(\$)|(\*)|([-+0-9.Ee]+))")

def _parse_args(s):
    """the argument list of one entity (text inside the outer parentheses, inclusive) -> nested Python lists"""
    stack = [[]]; pos = 0; n = len(s)
    while pos < n:
        m = _tok.match(s, pos)
        if not m:
            if s[pos].isspace(): pos += 1; continue
            raise ValueError('bad STEP text at %r' % s[pos:pos + 40])
        pos = m.end(); ref, st, en, typ, op, cl, cm, dl, star, num = m.groups()
        if ref: stack[-1].append(('#', int(ref[1:])))
        elif st is not None: stack[-1].append(st[1:-1].replace("''", "'"))
        elif en: stack[-1].append(('.', en[1:-1]))
        elif typ: stack.append(['\x00T', typ])  # typed value, e.g. IFCLABEL('x')
        elif op: stack.append([])
        elif cl:
            v = stack.pop()
            if v and v[0] == '\x00T': v = ('T', v[1], v[2] if len(v) > 2 else None)   # typed value -> ('T', TYPE, value)
            stack[-1].append(v)
        elif cm: pass
        elif dl: stack[-1].append(None)
        elif star: stack[-1].append('*')
        elif num is not None: stack[-1].append(float(num))
    return stack[0][0]

class Ifc:
    def __init__(self, path):
        self.ents = {}
        line_re = re.compile(r'^#(\d+)\s*=\s*([A-Z0-9_]+)\s*(\(.*\))\s*;\s*$')
        buf = ''
        with open(path, encoding='latin-1') as fh:
            data = False
            for raw in fh:
                if not data:
                    if raw.startswith('DATA;'): data = True
                    continue
                if raw.startswith('ENDSEC;'): break
                buf += raw.rstrip('\n')
                if not buf.rstrip().endswith(';'): continue
                m = line_re.match(buf.strip()); buf = ''
                if not m: continue
                self.ents[int(m.group(1))] = (m.group(2), m.group(3))
        self._parsed = {}; self._pl = {}; self._mesh = {}
        self.by_type = {}
        for k, (t, _) in self.ents.items(): self.by_type.setdefault(t, []).append(k)

    def a(self, i):
        """parsed arguments of entity #i"""
        if i not in self._parsed: self._parsed[i] = _parse_args(self.ents[i][1])
        return self._parsed[i]

    def t(self, i): return self.ents[i][0]
    @staticmethod
    def ref(x): return x[1] if isinstance(x, tuple) and x[0] == '#' else None

    def pt(self, i): return np.array(self.a(i)[0], float)
    def dirn(self, i): v = np.array(self.a(i)[0], float); return v / np.linalg.norm(v)

    def axis2(self, i):
        loc, ax, rd = self.a(i)[:3]
        o = self.pt(self.ref(loc)); z = self.dirn(self.ref(ax)) if ax else np.array([0, 0, 1.0])
        x = self.dirn(self.ref(rd)) if rd else (np.array([1.0, 0, 0]) if abs(z[0]) < 0.9 else np.array([0, 1.0, 0]))
        x = x - z * (x @ z); x /= np.linalg.norm(x); y = np.cross(z, x)
        M = np.eye(4); M[:3, 0], M[:3, 1], M[:3, 2], M[:3, 3] = x, y, z, o
        return M

    def placement(self, i):
        """IfcLocalPlacement -> world 4x4"""
        if i is None: return np.eye(4)
        if i in self._pl: return self._pl[i]
        rel, rp = self.a(i)[:2]
        M = self.placement(self.ref(rel)) @ self.axis2(self.ref(rp)) if rel else self.axis2(self.ref(rp))
        self._pl[i] = M; return M

    def cto(self, i):
        """IfcCartesianTransformationOperator3D(axis1, axis2, origin, scale, axis3) -> 4x4"""
        ax1, ax2, org, sc, ax3 = self.a(i)[:5]
        x = self.dirn(self.ref(ax1)) if ax1 else np.array([1.0, 0, 0]); y0 = self.dirn(self.ref(ax2)) if ax2 else np.array([0, 1.0, 0])
        z = self.dirn(self.ref(ax3)) if ax3 else np.cross(x, y0); z /= np.linalg.norm(z)
        x = x - z * (x @ z); x /= np.linalg.norm(x); y = np.cross(z, x)
        s = float(sc) if isinstance(sc, float) else 1.0
        M = np.eye(4); M[:3, 0], M[:3, 1], M[:3, 2] = x * s, y * s, z * s; M[:3, 3] = self.pt(self.ref(org)); return M

    def brep(self, i):
        """IfcFacetedBrep -> (V, T) in its own coordinates; planar faces triangulated (inner bounds = holes)"""
        if i in self._mesh: return self._mesh[i]
        import manifold3d as mf
        shell = self.ref(self.a(i)[0]); faces = [self.ref(f) for f in self.a(shell)[0]]
        V = []; vid = {}; T = []
        def vix(p):
            if p not in vid: vid[p] = len(V); V.append(self.pt(p))
            return vid[p]
        for fi in faces:
            loops = []
            for b in self.a(fi)[0]:
                bi = self.ref(b); lp, ori = self.a(bi)[:2]
                ids = [self.ref(q) for q in self.a(self.ref(lp))[0]]
                if ori == ('.', 'F'): ids = ids[::-1]
                loops.append((self.t(bi) == 'IFCFACEOUTERBOUND', ids))
            loops.sort(key=lambda x: not x[0])
            outer = loops[0][1]; P = np.array([self.pt(q) for q in outer])
            # Newell normal of the outer loop
            nrm = np.zeros(3)
            for k in range(len(P)): a, b = P[k], P[(k + 1) % len(P)]; nrm += np.array([(a[1] - b[1]) * (a[2] + b[2]), (a[2] - b[2]) * (a[0] + b[0]), (a[0] - b[0]) * (a[1] + b[1])])
            if np.linalg.norm(nrm) < 1e-12: continue
            nrm /= np.linalg.norm(nrm); u = np.cross(nrm, [1.0, 0, 0] if abs(nrm[0]) < 0.9 else [0, 1.0, 0]); u /= np.linalg.norm(u); w = np.cross(nrm, u)
            idx = []; polys = []
            for k, (isout, ids) in enumerate(loops):
                pts = [self.pt(q) for q in ids]
                polys.append([(float(p @ u), float(p @ w)) for p in pts]); idx += [vix(q) for q in ids]
            if len(polys) == 1 and len(polys[0]) == 3: tri = [[0, 1, 2]]
            elif len(polys) == 1 and len(polys[0]) == 4 and self._convex(polys[0]): tri = [[0, 1, 2], [0, 2, 3]]
            else: tri = np.asarray(mf.triangulate([np.array(p) for p in polys]), int).tolist()
            for t in tri: T.append([idx[t[0]], idx[t[1]], idx[t[2]]])
        out = (np.array(V, float).reshape(-1, 3), np.array(T, int).reshape(-1, 3)); self._mesh[i] = out; return out

    @staticmethod
    def _convex(p):
        s = 0
        for k in range(4):
            a, b, c = p[k], p[(k + 1) % 4], p[(k + 2) % 4]
            cr = (b[0] - a[0]) * (c[1] - b[1]) - (b[1] - a[1]) * (c[0] - b[0])
            if abs(cr) < 1e-12: continue
            if s == 0: s = 1 if cr > 0 else -1
            elif (cr > 0) != (s > 0): return False
        return True

    def rep_items(self, rep, M=np.eye(4)):
        """a shape representation's items -> list of (kind, entity id, 4x4): kind 'brep' or ('map', ...)"""
        out = []
        for it in self.a(rep)[3]:
            ii = self.ref(it); ty = self.t(ii)
            if ty == 'IFCFACETEDBREP': out.append(('brep', ii, M))
            elif ty == 'IFCMAPPEDITEM':
                src, tgt = [self.ref(x) for x in self.a(ii)[:2]]
                org, mrep = [self.ref(x) for x in self.a(src)[:2]]
                Mo = self.axis2(org) if self.t(org) == 'IFCAXIS2PLACEMENT3D' else np.eye(4)
                out += self.rep_items(mrep, M @ self.cto(tgt) @ Mo)
            else: out.append((ty, ii, M))
        return out

    def product_items(self, pid):
        a = self.a(pid); pl, rep = self.ref(a[5]), self.ref(a[6])
        W = self.placement(pl)
        if rep is None: return W, []
        items = []
        for r in self.a(rep)[2]:
            ri = self.ref(r)
            if self.a(ri)[1] == 'Body': items += self.rep_items(ri)
        return W, items

    def mesh(self, pid):
        """world mesh of a product (all Body breps, mapped items applied)"""
        W, items = self.product_items(pid); Vs = []; Ts = []; n = 0
        for kind, ii, M in items:
            if kind != 'brep': continue
            V, T = self.brep(ii); G = W @ M
            Vw = V @ G[:3, :3].T + G[:3, 3]; Vs.append(Vw); Ts.append(T + n); n += len(V)
        if not Vs: return np.zeros((0, 3)), np.zeros((0, 3), int)
        return np.vstack(Vs), np.vstack(Ts)

    def psets(self, pid):
        """{pset name: {prop: value}} for a product (IfcRelDefinesByProperties)"""
        if not hasattr(self, '_ps'):
            self._ps = {}
            for r in self.by_type.get('IFCRELDEFINESBYPROPERTIES', []):
                a = self.a(r); objs = [self.ref(o) for o in a[4]]; ps = self.ref(a[5])
                pa = self.a(ps); name = pa[2]; props = {}
                for p in pa[4]:
                    q = self.a(self.ref(p)); v = q[2]
                    if isinstance(v, tuple) and v[0] == 'T': v = v[2]
                    props[q[0]] = v
                for o in objs: self._ps.setdefault(o, {})[name] = props
        return self._ps.get(pid, {})

    def materials(self):
        out = {}
        for r in self.by_type.get('IFCRELASSOCIATESMATERIAL', []):
            a = self.a(r); m = self.ref(a[5]); nm = self.a(m)[0] if self.t(m) == 'IFCMATERIAL' else None
            for o in a[4]: out[self.ref(o)] = nm
        return out

def brep_loops(f, i):
    """IfcFacetedBrep -> list of faces, each [outer loop (k,3), inner loops ...] in its own coordinates"""
    shell = f.ref(f.a(i)[0]); out = []
    for fr in f.a(shell)[0]:
        fi = f.ref(fr); loops = []
        for b in f.a(fi)[0]:
            bi = f.ref(b); lp, ori = f.a(bi)[:2]
            P = np.array([f.pt(f.ref(q)) for q in f.a(f.ref(lp))[0]])
            if ori == ('.', 'F'): P = P[::-1]
            loops.append((f.t(bi) == 'IFCFACEOUTERBOUND', P))
        loops.sort(key=lambda x: not x[0]); out.append([P for _, P in loops])
    return out
