# Writes the sample books in this folder, independently of Folio's converters (the MOBI ones
# were checked with foliate-js). Run: python3 make_samples.py .
import struct, zipfile, base64, io, sys, os
out = sys.argv[1]
# A tiny real PNG (1x1)
PNG = base64.b64decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==")
JPG = b"\xff\xd8\xff\xe0" + b"\x00" * 60 + b"\xff\xd9"

def palmdoc_compress(data):
    out = bytearray(); i = 0; n = len(data)
    while i < n:
        # try back-reference
        best_len = 0; best_dist = 0
        for dist in range(1, min(i, 2047) + 1):
            l = 0
            while l < 10 and i + l < n and data[i + l - dist] == data[i + l]:
                l += 1
            if l > best_len:
                best_len, best_dist = l, dist
        if best_len >= 3:
            v = 0x8000 | (best_dist << 3) | (best_len - 3)
            out += struct.pack(">H", v); i += best_len; continue
        c = data[i]
        if c == 0x20 and i + 1 < n and 0x40 <= data[i + 1] <= 0x7F:
            out.append(data[i + 1] ^ 0x80); i += 2; continue
        if c == 0 or 0x09 <= c <= 0x7F:
            out.append(c); i += 1; continue
        # literal run of high bytes
        j = i
        while j < n and j - i < 8 and not (data[j] == 0 or 0x09 <= data[j] <= 0x7F):
            j += 1
        out.append(j - i); out += data[i:j]; i = j
    return bytes(out)

def backward_varint(v):
    # size encoded so that reading from the end works; first byte has high bit set
    b = []
    while True:
        b.insert(0, v & 0x7F); v >>= 7
        if not v: break
    b[0] |= 0x80
    return bytes(b)

def exth(records):
    body = b"".join(struct.pack(">II", t, 8 + len(d)) + d for t, d in records)
    blob = b"EXTH" + struct.pack(">II", 12 + len(body), len(records)) + body
    pad = (4 - len(blob) % 4) % 4
    return blob + b"\0" * pad

def mobi_header(text_len, n_text, version, encoding, resource_start, exth_blob, name, trailing, fdst=None):
    hlen = 0xE8 if version < 8 else 0x108
    h = bytearray(16 + hlen)
    struct.pack_into(">HHIHHHH", h, 0, 2, 0, text_len, n_text, 4096, 0, 0)
    h[16:20] = b"MOBI"
    struct.pack_into(">I", h, 20, hlen)
    struct.pack_into(">I", h, 24, 2)          # type: book
    struct.pack_into(">I", h, 28, encoding)
    struct.pack_into(">I", h, 36, version)
    struct.pack_into(">I", h, 108, resource_start)
    struct.pack_into(">I", h, 128, 0x40)
    struct.pack_into(">H", h, 242, trailing)
    if fdst is not None: struct.pack_into(">I", h, 192, fdst)
    else: struct.pack_into(">I", h, 192, 0xFFFFFFFF)
    rec = bytes(h) + exth_blob
    struct.pack_into(">I", h, 84, len(rec))
    struct.pack_into(">I", h, 88, len(name))
    rec = bytes(h) + exth_blob + name + b"\0\0"
    return rec

def text_records(text, trailing):
    recs = []
    for i in range(0, len(text), 4096):
        chunk = text[i:i + 4096]
        r = palmdoc_compress(chunk)
        if trailing & 1:   # multibyte (innermost): one overlap byte + count byte 0b01 -> strip 2
            r += b"\xc3\x01"
        if trailing & 2:   # one extra entry of 3 bytes payload + its size
            payload = b"\x01\x02\x03"
            r += payload + backward_varint(len(payload) + 1)
        recs.append(r)
    return recs

def pdb(records, name=b"Test"):
    n = len(records)
    header = bytearray(78)
    header[0:len(name)] = name
    header[60:68] = b"BOOKMOBI"
    struct.pack_into(">H", header, 76, n)
    offset = 78 + n * 8 + 2
    table = b""
    for i, r in enumerate(records):
        table += struct.pack(">I", offset) + bytes([0]) + (i).to_bytes(3, "big")
        offset += len(r)
    return bytes(header) + table + b"\0\0" + b"".join(records)

def write_mobi6():
    body = "<html><head><guide></guide></head><body>" + "<h1>The Long Road</h1>" + \
        "".join(f"<p>Paragraph {i}: café — naïve “quotes” and a repeated phrase repeated phrase repeated.</p>" for i in range(200)) + \
        "<mbp:pagebreak/><p><img recindex=\"00002\"></p><h2>Chapter Two</h2><p>The end.</p></body></html>"
    text = body.encode("cp1252")
    trailing = 3
    recs = text_records(text, trailing)
    n_text = len(recs)
    resource_start = 1 + n_text
    ex = exth([(100, "Ada Writer".encode("cp1252")), (503, "The Long Road".encode("cp1252")), (201, struct.pack(">I", 0))])
    r0 = mobi_header(len(text), n_text, 6, 1252, resource_start, ex, b"Long Road Full", trailing)
    open(os.path.join(out, "sample.mobi"), "wb").write(pdb([r0] + recs + [JPG, PNG]))

def write_kf8():
    flow0 = ("<html><head><title>x</title><link href=\"kindle:flow:0001?mime=text/css\"/></head><body aid=\"0\"><div aid=\"1\"></div></body></html>"
             "<div><h1>Kapitel Eins</h1><p>Grüße aus der Ünterwelt.</p><p><img src=\"kindle:embed:0001?mime=image/jpeg\"/></p></div>"
             "<div><h2>Kapitel Zwei</h2><p>Schluss.</p></div>")
    css = "p { color: red } BODY-CSS-SHOULD-NOT-SHOW"
    text = (flow0 + css).encode("utf-8")
    trailing = 1
    recs = text_records(text, trailing)
    n_text = len(recs)
    fdst_index = 1 + n_text
    fl0 = len(flow0.encode("utf-8"))
    fdst = b"FDST" + struct.pack(">II", 12, 2) + struct.pack(">IIII", 0, fl0, fl0, len(text))
    resource_start = fdst_index + 1
    ex = exth([(100, "Kurt Autor".encode()), (503, "Das Buch".encode()), (201, struct.pack(">I", 0))])
    r0 = mobi_header(len(text), n_text, 8, 65001, resource_start, ex, b"Das Buch", trailing, fdst=fdst_index)
    open(os.path.join(out, "sample.azw3"), "wb").write(pdb([r0] + recs + [fdst, JPG]))

def write_drm():
    text = b"<p>secret</p>"
    recs = text_records(text, 0)
    r0 = bytearray(mobi_header(len(text), 1, 6, 1252, 2, exth([]), b"x", 0))
    struct.pack_into(">H", r0, 12, 2)
    open(os.path.join(out, "drm.mobi"), "wb").write(pdb([bytes(r0)] + recs))

def write_fb2():
    img = base64.b64encode(PNG).decode()
    xml = f'''<?xml version="1.0" encoding="windows-1251"?>
<FictionBook xmlns="http://www.gribuser.ru/xml/fictionbook/2.0" xmlns:l="http://www.w3.org/1999/xlink">
<description><title-info><author><first-name>Лев</first-name><last-name>Толстой</last-name></author>
<book-title>Война и мир</book-title><coverpage><image l:href="#cover.png"/></coverpage></title-info></description>
<body><title><p>Война и мир</p></title>
<section><title><p>Часть первая</p></title><epigraph><p>Эпиграф</p></epigraph>
<p>Ну, князь, <emphasis>Генуя</emphasis> и <strong>Лукка</strong>.</p><empty-line/>
<image l:href="#cover.png"/>
<poem><stanza><v>строка один</v><v>строка два</v></stanza></poem>
<section><title><p>Глава 1</p></title><p>Текст главы.</p></section></section></body>
<body name="notes"><section><title><p>Примечания</p></title><p>Note.</p></section></body>
<binary id="cover.png" content-type="image/png">{img}</binary>
</FictionBook>'''
    open(os.path.join(out, "sample.fb2"), "wb").write(xml.encode("cp1251"))
    with zipfile.ZipFile(os.path.join(out, "sample.fb2.zip"), "w") as z:
        z.writestr("book.fb2", xml.encode("cp1251"))

def write_docx():
    W = 'xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"'
    doc = f'''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document {W}><w:body>
<w:p><w:pPr><w:pStyle w:val="Title"/></w:pPr><w:r><w:t>Report &amp; Notes</w:t></w:r></w:p>
<w:p><w:pPr><w:pStyle w:val="Kop1"/></w:pPr><w:r><w:t>Inleiding</w:t></w:r></w:p>
<w:p><w:r><w:t xml:space="preserve">Plain then </w:t></w:r><w:r><w:rPr><w:b/></w:rPr><w:t>bold</w:t></w:r><w:r><w:t xml:space="preserve"> and </w:t></w:r><w:r><w:rPr><w:i/><w:b w:val="0"/></w:rPr><w:t>italic</w:t></w:r><w:r><w:br/><w:t>new line</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>first item</w:t></w:r></w:p>
<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="1"/></w:numPr></w:pPr><w:r><w:t>second item</w:t></w:r></w:p>
<w:p><w:hyperlink r:id="rId9"><w:r><w:t>a link text</w:t></w:r></w:hyperlink></w:p>
<w:tbl><w:tr><w:tc><w:p><w:r><w:t>A1</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>B1</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
<w:p><w:r><w:drawing><wp:inline><a:graphic><a:graphicData><pic:pic xmlns:pic="x"><pic:blipFill><a:blip r:embed="rId5"/></pic:blipFill></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>
<w:sectPr/></w:body></w:document>'''
    styles = f'''<?xml version="1.0"?><w:styles {W}><w:style w:type="paragraph" w:styleId="Kop1"><w:name w:val="heading 1"/></w:style></w:styles>'''
    rels = '''<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId5" Type="image" Target="media/image1.png"/></Relationships>'''
    core = '''<?xml version="1.0"?><cp:coreProperties xmlns:cp="x" xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:title>Quarterly Report</dc:title><dc:creator>Jo Smith</dc:creator></cp:coreProperties>'''
    with zipfile.ZipFile(os.path.join(out, "sample.docx"), "w") as z:
        z.writestr("[Content_Types].xml", "<Types/>")
        z.writestr("word/document.xml", doc)
        z.writestr("word/styles.xml", styles)
        z.writestr("word/_rels/document.xml.rels", rels)
        z.writestr("word/media/image1.png", PNG)
        z.writestr("docProps/core.xml", core)

def write_odt():
    content = '''<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:style="s" xmlns:fo="f" xmlns:draw="d" xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:table="t">
<office:automatic-styles><style:style style:name="T1" style:family="text"><style:text-properties fo:font-weight="bold"/></style:style>
<style:style style:name="T2" style:family="text"><style:text-properties fo:font-style="italic"/></style:style></office:automatic-styles>
<office:body><office:text>
<text:h text:outline-level="1">Open Heading</text:h>
<text:p>Some <text:span text:style-name="T1">bold</text:span> and <text:span text:style-name="T2">italic</text:span> words<text:s text:c="2"/>spaced<text:line-break/>next.<text:note><text:note-body><text:p>footnote</text:p></text:note-body></text:note></text:p>
<text:list><text:list-item><text:p>one</text:p></text:list-item><text:list-item><text:p>two</text:p></text:list-item></text:list>
<table:table><table:table-row><table:table-cell><text:p>c1</text:p></table:table-cell></table:table-row></table:table>
<text:p><draw:frame><draw:image xlink:href="Pictures/a.png"/></draw:frame></text:p>
</office:text></office:body></office:document-content>'''
    meta = '''<?xml version="1.0"?><office:document-meta xmlns:office="o" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:meta="m"><office:meta><dc:title>Open Doc</dc:title><meta:initial-creator>Olga</meta:initial-creator></office:meta></office:document-meta>'''
    with zipfile.ZipFile(os.path.join(out, "sample.odt"), "w") as z:
        z.writestr(zipfile.ZipInfo("mimetype"), "application/vnd.oasis.opendocument.text")
        z.writestr("content.xml", content)
        z.writestr("meta.xml", meta)
        z.writestr("Pictures/a.png", PNG)

def write_rtf():
    rtf = r'''{\rtf1\ansi\ansicpg1252\deff0{\fonttbl{\f0 Times New Roman;}}{\colortbl;\red255\green0\blue0;}
{\info{\title My RTF Title}{\author Rita Author}}
{\*\generator Msftedit 5.41;}\viewkind4\uc1\pard\f0\fs24 Hello \b bold\b0  and \i italic\i0 .\par
Caf\'e9 na\'efve \u8220?quoted\u8221? \emdash  dash\line next line\par
{\pict\pngblip 89504e47}Picture gone.\par
{\field{\*\fldinst HYPERLINK "http://x"}{\fldrslt link text}}\par
Braces \{ and \} and backslash \\.\par
}'''
    open(os.path.join(out, "sample.rtf"), "wb").write(rtf.encode("latin-1"))

for f in [write_mobi6, write_kf8, write_drm, write_fb2, write_docx, write_odt, write_rtf]:
    f()
print(sorted(os.listdir(out)))
