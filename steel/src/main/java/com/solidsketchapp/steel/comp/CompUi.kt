package com.solidsketchapp.steel.comp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solidsketchapp.steel.conn.StConn
import com.solidsketchapp.steel.mode.StBtn
import com.solidsketchapp.steel.mode.SteelMode
import kotlinx.serialization.json.JsonElement

/** Pictures the sheets show: the SVGs (parsed once) and the v58 thumbnails (decoded by the host). */
class CompPics(val ui: TkUi, private val image: (String) -> ImageBitmap?) {
    private val svgs = HashMap<String, Svg>(); private val imgs = HashMap<String, ImageBitmap?>()
    fun svg(key: String, src: String, vb: FloatArray = floatArrayOf(0f, 0f, 400f, 260f)) = svgs.getOrPut(key) { Svg.parse(src, vb) }
    fun img(path: String?): ImageBitmap? = path?.let { imgs.getOrPut(it) { runCatching { image(it) }.getOrNull() } }
    /** v69 stIcon chain: the v58 thumbnail when there is one, else the SS_ICONS schematic. */
    fun thumb(k: String) = img(ui.thumbs[k]?.t)
    fun icon(k: String) = ui.icons[k]?.let { svg("icon:$k", it, floatArrayOf(0f, 0f, 120f, 80f)) }
}

@Composable
fun SvgView(svg: Svg, modifier: Modifier) { val tm = rememberTextMeasurer(); Canvas(modifier) { svg.draw(this, tm) } }

@Composable
private fun Icon(pics: CompPics, k: String, big: Boolean = false) {
    val w = if (big) 160.dp else 72.dp; val h = if (big) 120.dp else 54.dp
    val t = pics.thumb(k)
    if (t != null) Image(t, null, Modifier.size(w, h).background(Color(0xFFF3F5F8), RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
    else pics.icon(k)?.let { SvgView(it, Modifier.size(w, if (big) 107.dp else 48.dp).background(Color(0xFFF3F5F8), RoundedCornerShape(8.dp))) } ?: Box(Modifier.size(w, h).background(Color(0xFFF3F5F8), RoundedCornerShape(8.dp)))
}

/** v69 stCatalog (v38 L14383): search, "Recently used", the 18 groups; tap = pick the parts; Properties = the dialog. */
@Composable
fun CatalogSheet(mode: SteelMode, comps: Components, pics: CompPics, symbolsOn: Boolean, toggleSymbols: () -> Unit, openDialog: (String) -> Unit, app: (String) -> Unit, close: () -> Unit) {
    var filter by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(8.dp).background(Color.White, RoundedCornerShape(16.dp)).border(1.dp, Color(0xFFDDDDDD), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text("Applications & components", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        Box(Modifier.fillMaxWidth().border(1.dp, Color(0xFFCCCCCC), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 8.dp)) {
            if (filter.isEmpty()) Text("Search: name or number, e.g. end plate, 144", color = Color(0x80111111), fontSize = 14.sp)
            BasicTextField(filter, { filter = it }, singleLine = true, textStyle = TextStyle(fontSize = 14.sp), modifier = Modifier.fillMaxWidth())
        }
        val open = remember { mutableStateOf(setOf<String>()) }   // v69 <details open>: closed ones listed here
        LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
            fun row(k: String) = item(key = "${filter}:${k}:${System.identityHashCode(k)}") { CatRow(comps, pics, k, { if (k.startsWith("@")) app(k) else mode.catPick(k) }, { openDialog(k) }) }
            if (filter.isNotBlank()) {
                val keys = comps.search(filter)
                if (keys.isEmpty()) item { Text("Nothing matches.", fontSize = 14.sp, modifier = Modifier.padding(6.dp)) }
                for (k in keys) item(key = "s:$k") { CatRow(comps, pics, k, { if (k.startsWith("@")) app(k) else mode.catPick(k) }, { openDialog(k) }) }
            } else for ((g, items) in comps.groups()) {
                item(key = "g:$g") { Text("${if (g in open.value) "▸" else "▾"} $g (${items.size})", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable { open.value = if (g in open.value) open.value - g else open.value + g }.padding(horizontal = 2.dp, vertical = 6.dp)) }
                if (g !in open.value) items(items, key = { "$g:$it" }) { k -> CatRow(comps, pics, k, { if (k.startsWith("@")) app(k) else mode.catPick(k) }, { openDialog(k) }) }
            }
        }
        val n = comps.countInModel()
        Text("Tap a component, then pick its parts in the model. $n component${if (n == 1) "" else "s"} in the model.", fontSize = 13.sp, modifier = Modifier.padding(vertical = 6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
            StBtn(if (symbolsOn) "Symbols ✓" else "Symbols", symbolsOn) { toggleSymbols() }
            StBtn("Close") { close() }
        }
    }
}

@Composable
private fun CatRow(comps: Components, pics: CompPics, k: String, pick: () -> Unit, props: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { pick() }.padding(horizontal = 6.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(pics, k)
        Column(Modifier.weight(1f)) {
            Text(comps.ui.label(k), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(comps.ui.tekla(k), fontSize = 12.sp, color = Color(0xB3111111), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (k in comps.ui.conn) StBtn("Properties") { props() }
    }
}

// ---------------- the v58 component dialog ----------------

private val PANE = Color(0xFFFFFFFF); private val BG = Color(0xFFEEF0F3); private val INK = Color(0xFF1D2330); private val LINE = Color(0xFFC5CAD1); private val RED = Color(0xFFC0392B)

/** A Tekla dialog button (v69 `.tkdlg button`): square-ish, grey gradient look, one line. */
@Composable
private fun TkBtn(label: String, on: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.heightIn(min = 32.dp).background(if (on) Color.White else Color(0xFFEEF0F3), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFAAB2BD), RoundedCornerShape(4.dp)).clickable { onClick() }.padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
        Text(label, fontSize = 13.sp, color = INK, maxLines = 1, softWrap = false, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun TkCheck(checked: Boolean, onChange: (Boolean) -> Unit) = Checkbox(checked, onChange, Modifier.size(28.dp))

@Composable
private fun TkText(value: String, onChange: (String) -> Unit, placeholder: String?, numeric: Boolean, modifier: Modifier, enabled: Boolean = true) {
    Box(modifier.background(Color(0xFFFFFEF2), RoundedCornerShape(3.dp)).border(1.dp, Color(0xFF9AA3AD), RoundedCornerShape(3.dp)).padding(horizontal = 6.dp, vertical = 6.dp)) {
        if (value.isEmpty() && placeholder != null) Text(placeholder, color = Color(0x80111111), fontSize = 13.sp, maxLines = 1)
        BasicTextField(value, onChange, enabled = enabled, singleLine = true, textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF111111)),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default, modifier = Modifier.fillMaxWidth())
    }
}

/** v69 tkPicker: a dropdown whose options carry Tekla-like pictograms. */
@Composable
private fun TkPicker(pics: CompPics, f: TkField, value: JsonElement, onPick: (JsonElement) -> Unit, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    val cur = f.opts.firstOrNull { Js.str(it.v) == Js.str(value) } ?: f.opts.firstOrNull()
    Box(modifier) {
        Row(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(4.dp)).border(1.dp, Color(0xFFAAB2BD), RoundedCornerShape(4.dp)).clickable { open = true }.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            cur?.ic?.let { ic -> pics.ui.pg[ic]?.let { SvgView(pics.svg("pg:$ic", it, floatArrayOf(0f, 0f, 24f, 24f)), Modifier.size(22.dp)) } }
            Text(cur?.t ?: "", fontSize = 13.sp, color = INK, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); Text("▾", fontSize = 12.sp, color = Color(0x99111111))
        }
        DropdownMenu(open, { open = false }) {
            for (o in f.opts) DropdownMenuItem(text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                o.ic?.let { ic -> pics.ui.pg[ic]?.let { SvgView(pics.svg("pg:$ic", it, floatArrayOf(0f, 0f, 24f, 24f)), Modifier.size(22.dp)) } }; Text(o.t, fontSize = 13.sp) } }, onClick = { open = false; onPick(o.v) })
        }
    }
}

@Composable
private fun InputBox(d: CompDialog, pics: CompPics, i: CompDialog.Input, rev: Int, bump: () -> Unit, modifier: Modifier) {
    @Suppress("UNUSED_EXPRESSION") rev
    when (i.f.type) {
        "chk" -> TkCheck(Js.str(i.value) != "0") { i.value = Js.n(if (it) 1.0 else 0.0); bump() }
        "sel" -> TkPicker(pics, i.f, i.value, { i.value = it; bump() }, modifier)
        else -> TkText(d.text(i), { d.setText(i, it); bump() }, d.placeholder(i.f), i.f.type == "num", modifier)
    }
}

/** v58 stCompDialog (L16304): header with status, presets bar, Tekla tabs (picture with input boxes, parts table, fields with on/off, welds table), OK / Apply / Modify / Get / on/off / Pick parts / Cancel / Delete. */
@Composable
fun CompDialogSheet(mode: SteelMode, comps: Components, pics: CompPics, key: String, conn0: StConn?, status: (StConn) -> Pair<Int, List<String>>, toast: (String) -> Unit, close: () -> Unit) {
    val d = remember(key, conn0?.id) { CompDialog(comps, key, conn0) }
    var rev by remember { mutableIntStateOf(0) }; val bump: () -> Unit = { rev++ }
    var tab by remember(key) { mutableStateOf(d.spec.tabs.first().name) }
    var presetName by remember { mutableStateOf("") }; var preset by remember { mutableStateOf("standard") }
    var allOfType by remember { mutableStateOf(false) }; var ignoreOther by remember { mutableStateOf(true) }
    val uri = LocalUriHandler.current
    @Suppress("UNUSED_EXPRESSION") rev
    Column(Modifier.fillMaxSize().padding(top = 28.dp).background(BG, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))) {
        // header
        val c = d.conn; val st = c?.let(status)
        Row(Modifier.fillMaxWidth().background(Color(0xFFF0F2F5)).padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(64.dp, 48.dp)) { Icon(pics, key) }
            Column(Modifier.weight(1f)) {
                Text(d.def.tekla.ifEmpty { d.def.label }, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = INK)
                Text(if (c != null) "#${c.id} · ${c.parts.joinToString(" · ") { comps.partLabel(it) }}" else "defaults for new components", fontSize = 12.sp, color = Color(0xB31D2330), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (st != null) Text(when (st.first) { 1 -> "OK"; 2 -> "Warning"; else -> "Error" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = if (st.first == 2) Color(0xFF3B2C00) else Color.White, modifier = Modifier.background(when (st.first) { 1 -> Color(0xFF2F9E44); 2 -> Color(0xFFF2B705); else -> Color(0xFFC92A2A) }, RoundedCornerShape(999.dp)).padding(horizontal = 9.dp, vertical = 3.dp))
        }
        if (st != null && st.second.isNotEmpty()) Text(st.second.joinToString(" · "), fontSize = 12.sp, color = if (st.first == 2) Color(0xFF5C4400) else Color(0xFF7A1010), modifier = Modifier.fillMaxWidth().background(if (st.first == 2) Color(0xFFFFF8DB) else Color(0xFFFFE3E3)).padding(horizontal = 10.dp, vertical = 6.dp))
        // top bar
        Row(Modifier.fillMaxWidth().background(Color(0xFFF6F7F9)).horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            TkBtn("Save") { d.save(preset); toast(if (preset == "standard") "Saved as the standard for new components" else "Saved “$preset”") }
            TkBtn("Load") { if (d.load(preset)) { bump(); toast("Loaded “$preset”") } }
            val names = listOf("standard") + comps.presets(key)
            TkPicker(pics, TkField("_preset", "", "sel", names.map { TkOpt(kotlinx.serialization.json.JsonPrimitive(it), it, null) }, null, null, null, null, null, null, null), kotlinx.serialization.json.JsonPrimitive(preset), { preset = Js.str(it) }, Modifier.width(120.dp))
            TkBtn("Save as") { val n = presetName.trim(); if (n.isEmpty()) toast("Type a preset name first") else { comps.savePreset(key, n, d.read(true)); preset = n; toast("Saved “$n”") } }
            TkText(presetName, { presetName = it }, "preset name", false, Modifier.width(110.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { TkCheck(ignoreOther) { ignoreOther = it }; Text("ignore other types", fontSize = 12.sp, color = INK, maxLines = 1) }
            TkBtn("Welds…") { if (d.spec.tabs.any { it.name == "Welds" }) tab = "Welds" }
            TkBtn("Help") { val u = "https://support.tekla.com/doc/tekla-structures/2025/" + d.spec.help; runCatching { uri.openUri(u) }.onFailure { toast(u) } }
        }
        // tabs
        Row(Modifier.fillMaxWidth().background(Color(0xFFDFE3E8)).horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (t in d.spec.tabs) TkBtn(t.name, t.name == tab) { tab = t.name }
        }
        // pane
        val t = d.spec.tabs.first { it.name == tab }
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp).background(PANE, RoundedCornerShape(4.dp)).border(1.dp, LINE, RoundedCornerShape(4.dp)).padding(8.dp)) {
            if (t.pic != null) Picture(d, pics, t, rev, bump)
            t.parts?.let { rows -> PartsTable(d, pics, rows, rev, bump) }
            for ((h, fs) in t.groups) { if (fs.isEmpty()) continue
                Text(h, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2A3A52), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                for (f in fs) FieldRow(d, pics, f, rev, bump) }
            if (t.welds) WeldsTable(d, pics, rev, bump)
        }
        // bottom bar
        Row(Modifier.fillMaxWidth().background(Color(0xFFF6F7F9)).horizontalScroll(rememberScrollState()).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            fun modify() { val locked = d.conn?.let { Js.num(it.q["locked"]).let { v -> v != 0.0 && !v.isNaN() } } == true
                if (!allOfType && d.conn == null || (!allOfType && locked)) { toast(if (locked) "This component is locked (General tab)" else "Select a component of this type in the model, or tick “all of this type”"); return }
                val n = d.modify(allOfType); if (n == 0) { toast("Select a component of this type in the model, or tick “all of this type”"); return }
                toast("$n component${if (n == 1) "" else "s"} rebuilt"); d.conn?.let { c2 -> val r = status(c2); if (r.first != 1) r.second.firstOrNull()?.let(toast) }; bump() }
            TkBtn("OK", true) { if (d.conn != null) modify() else { d.apply(); toast("${d.def.label}: values applied to new components") }; close() }
            TkBtn("Apply") { d.apply(); toast("${d.def.label}: values applied to new components") }
            TkBtn("Modify") { modify() }
            TkBtn("Get") { val c0 = mode.selectionFirst()?.let { comps.connOfBody(it) } ?: d.conn
                if (c0 == null || c0.type != key) toast("Select a ${d.def.label.lowercase()} in the model first") else { d.getFrom(c0); bump(); toast("Got the values of #${c0.id}") } }
            TkBtn("on/off") { d.toggleAll(); bump() }
            Row(verticalAlignment = Alignment.CenterVertically) { TkCheck(allOfType) { allOfType = it }; Text("all of this type", fontSize = 12.sp, color = INK, maxLines = 1) }
            TkBtn("Pick parts") { d.apply(); close(); mode.catPick(key) }
            TkBtn("Cancel") { close() }
            d.conn?.let { c0 -> TkBtn("Delete") { comps.delete(c0); close() } }
        }
    }
}

@Composable
private fun Picture(d: CompDialog, pics: CompPics, t: TkTab, rev: Int, bump: () -> Unit) {
    val th = pics.ui.thumbs[d.key]
    val fields = d.picFields(t); val inputs = fields.mapNotNull { f -> d.inputs.firstOrNull { it.f.k == f.k && (it.f.at != null || f.at != null) }?.let { f to it } }
    // v69: the 3D render above the picture on the Picture tab
    if (t.name == "Picture" && th?.p != null && !(t.pic == "icon" && th.a.isNotEmpty() && t.anchors.isNotEmpty())) pics.img(th.p)?.let { Image(it, null, Modifier.fillMaxWidth().height(200.dp).padding(bottom = 6.dp), contentScale = ContentScale.Fit) }
    val ratio = if (t.pic == "icon" && th?.p != null && th.a.isNotEmpty() && t.anchors.isNotEmpty()) 520f / 360f else 400f / 260f
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(ratio).background(Color.White).border(1.dp, Color(0xFFDDE1E6), RoundedCornerShape(4.dp))) {
        val w = maxWidth; val h = maxHeight
        when {
            t.pic == "bolts" -> SvgView(Svg.parse("<svg viewBox=\"0 0 400 260\">" + BoltPic.svg(d) + "</svg>"), Modifier.fillMaxSize())
            t.pic == "icon" && th?.p != null && th.a.isNotEmpty() && t.anchors.isNotEmpty() -> pics.img(th.p)?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds) }
            t.pic == "icon" -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(pics, d.key, big = true) }
            else -> pics.ui.pics[t.pic]?.let { SvgView(pics.svg("pic:${t.pic}", it), Modifier.fillMaxSize()) }
        }
        val at = if (t.pic == "bolts") BoltPic.at(d) else emptyMap()
        for ((f, i) in inputs) {
            val p = (if (t.pic == "bolts") BoltPic.role(f)?.let { at[it] } else null) ?: f.at ?: continue
            Row(Modifier.offset(w * (p.first.toFloat() / 100f) - 34.dp, h * (p.second.toFloat() / 100f) - 14.dp).background(Color(0xEBFFFFFF), RoundedCornerShape(4.dp)).border(1.dp, RED, RoundedCornerShape(4.dp)).padding(1.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(f.lbl, color = RED, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 2.dp))
                InputBox(d, pics, i, rev, bump, Modifier.width(52.dp))
            }
        }
    }
    if (fields.isNotEmpty()) Text(fields.joinToString("   ") { "${it.lbl} ${it.tip ?: ""}" }, fontSize = 11.5.sp, color = Color(0xCC1D2330), modifier = Modifier.padding(6.dp))
}

@Composable
private fun FieldRow(d: CompDialog, pics: CompPics, f: TkField, rev: Int, bump: () -> Unit) {
    val i = d.inputs.firstOrNull { it.f === f }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TkCheck(i?.on ?: false) { if (i != null) { i.on = it; bump() } }
            Text(f.lbl, fontSize = 13.sp, color = if (f.off != null) Color(0x801D2330) else INK, modifier = Modifier.weight(1.2f))
            if (i != null) InputBox(d, pics, i, rev, bump, Modifier.weight(1f)) else Text("—", modifier = Modifier.weight(1f), color = Color(0x801D2330))
        }
        (f.note ?: f.off)?.let { Text(it, fontSize = 11.sp, color = Color(0x991D2330), modifier = Modifier.padding(start = 34.dp)) }
    }
}

@Composable
private fun PartsTable(d: CompDialog, pics: CompPics, rows: List<TkPartRow>, rev: Int, bump: () -> Unit) {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        val cols = listOf("Part" to 130, "t" to 60, "b" to 60, "h" to 60, "Pos_No prefix" to 70, "start" to 66, "Material" to 70, "Name" to 110, "Finish" to 70)
        Row(Modifier.background(Color(0xFFE9ECF0))) { for ((h, w) in cols) Text(h, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(w.dp).padding(4.dp)) }
        for (row in rows) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(row.lbl, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.width(130.dp).padding(4.dp))
            for (x in listOf("t", "b", "h")) Box(Modifier.width(60.dp).padding(2.dp)) { val k = row.dm[x]; val i = k?.let { kk -> d.inputs.firstOrNull { it.f.k == kk && it.on == null && it.f.lbl == "${row.lbl} $x" } }
                if (i != null) InputBox(d, pics, i, rev, bump, Modifier.fillMaxWidth()) else Text("auto", fontSize = 11.sp, color = Color(0x801D2330)) }
            val r = if (row.role == "*") "ALL" else row.role
            for ((x, w) in listOf("pp" to 70, "ps" to 66, "mat" to 70, "name" to 110, "fin" to 70)) Box(Modifier.width(w.dp).padding(2.dp)) { d.inputs.firstOrNull { it.f.k == "P.$r.$x" }?.let { InputBox(d, pics, it, rev, bump, Modifier.fillMaxWidth()) } }
        }
    }
}

@Composable
private fun WeldsTable(d: CompDialog, pics: CompPics, rev: Int, bump: () -> Unit) {
    Column(Modifier.horizontalScroll(rememberScrollState())) {
        val cols = listOf("Weld" to 200, "Size a" to 70, "Type" to 140, "Shop/Site" to 110, "Around" to 60, "Length" to 60)
        Row(Modifier.background(Color(0xFFE9ECF0))) { for ((h, w) in cols) Text(h, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(w.dp).padding(4.dp)) }
        d.weldNames().forEachIndexed { ix, n -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (n == "*") "All welds" else "${ix + 1} · $n", fontSize = 12.sp, modifier = Modifier.width(200.dp).padding(4.dp))
            for ((x, w) in listOf("a" to 70, "type" to 140, "ws" to 110, "around" to 60)) Box(Modifier.width(w.dp).padding(2.dp)) { d.inputs.firstOrNull { it.f.k == "W.$n.$x" }?.let { InputBox(d, pics, it, rev, bump, Modifier.fillMaxWidth()) } }
            Text(d.weldOf(n)?.let { Js.str(it["len"]) } ?: "", fontSize = 12.sp, modifier = Modifier.width(60.dp).padding(4.dp))
        } }
    }
    Text("Weld size drives the rule check (a ≤ 0.7·t) and the weld lists in drawings and reports.", fontSize = 11.5.sp, color = Color(0xB31D2330), modifier = Modifier.padding(6.dp))
}
