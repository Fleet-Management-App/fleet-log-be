"""Generates the user-scenario swimlane diagrams (SVG) for Confluence "01. User Scenarios".

Run: python generate.py   -> writes US-XX.svg next to this file (render.sh turns them into PNG).
Each scenario is described as lanes, nodes placed on a (column, row) grid and edges between them.
"""
import html
import os

OUT = os.path.dirname(os.path.abspath(__file__))

COL_W = 240
NODE_W = 196
DIA_W, DIA_H = 168, 84
ROW_GAP = 40
MARGIN = 24
TITLE_H = 78
LANE_HEAD = 38
FONT = "Segoe UI, Arial, sans-serif"
CHAR_W = 6.9  # rough average glyph width at 13px
LINE_H = 17

LANES = {
    "D": ("Driver", "#2563eb", "#eff6ff", "#dbeafe"),
    "F": ("Fleet Manager", "#d97706", "#fffbeb", "#fde68a"),
    "A": ("Accountant", "#059669", "#ecfdf5", "#a7f3d0"),
    "S": ("FleetLog (system)", "#475569", "#f8fafc", "#e2e8f0"),
    "U": ("Invited user", "#7c3aed", "#f5f3ff", "#ddd6fe"),
}

BR = {
    "BR-01": "Suspended vehicle or expired STK cannot be selected / plan-approved",
    "BR-02": "Only the vignette expiry warns drivers; STK & insurance are Fleet Manager only",
    "BR-03": "A trip cannot be logged until its plan is approved",
    "BR-04": "Odometer end must be >= odometer start",
    "BR-05": "Trip is Completed once odometer start/end are logged; fuel approval never blocks it",
    "BR-06": "Fuel approval is required only when a fuel entry was logged",
    "BR-07": "A plan-rejected trip or rejected fuel entry is editable and resubmittable",
    "BR-08": "A problem report can be filed against any vehicle",
    "BR-09": "Planned service is overdue past its due date or due mileage, whichever comes first",
    "BR-10": "The trip-gap check is odometer-based, not time-based",
    "BR-11": "Up to 10 km odometer gap between trips is tolerated (configurable by Fleet Manager)",
}


def wrap(text, width_px):
    max_chars = max(8, int(width_px / CHAR_W))
    lines = []
    for para in text.split("\n"):
        line = ""
        for word in para.split(" "):
            cand = (line + " " + word).strip()
            if len(cand) > max_chars and line:
                lines.append(line)
                line = word
            else:
                line = cand
        lines.append(line)
    return lines


class Diagram:
    def __init__(self, code, title, spec, lanes):
        self.code, self.title, self.spec = code, title, spec
        # lanes: list of (lane_key, number_of_columns)
        self.lanes = lanes
        self.nodes = {}
        self.edges = []
        self.col_lane = []
        for key, n in lanes:
            self.col_lane += [key] * n

    def node(self, nid, kind, col, row, text="", br=None, align="middle"):
        self.nodes[nid] = dict(kind=kind, col=col, row=row, text=text, br=br, align=align)

    def edge(self, a, b, label=None, out="b", inn="t", via=None):
        self.edges.append(dict(a=a, b=b, label=label, out=out, inn=inn, via=via))

    # ---------- layout ----------
    def _size(self, n):
        k = n["kind"]
        if k == "start":
            return 26, 26
        if k == "end":
            lines = wrap(n["text"], NODE_W - 20) if n["text"] else []
            return 28, 28 + len(lines) * LINE_H
        if k == "decision":
            lines = wrap(n["text"], DIA_W - 44)
            return DIA_W, max(DIA_H, 44 + len(lines) * LINE_H)
        lines = wrap(n["text"], NODE_W - 22)
        return NODE_W, 22 + len(lines) * LINE_H + (6 if n["br"] else 0)

    def layout(self):
        rows = max(n["row"] for n in self.nodes.values()) + 1
        heights = [26] * rows
        for n in self.nodes.values():
            n["w"], n["h"] = self._size(n)
            body_h = 28 if n["kind"] == "end" else n["h"]
            heights[n["row"]] = max(heights[n["row"]], body_h)
        self.row_y = []
        y = MARGIN + TITLE_H + LANE_HEAD + 26
        for h in heights:
            self.row_y.append(y + h / 2)
            y += h + ROW_GAP
        self.body_bottom = y
        for n in self.nodes.values():
            n["cx"] = MARGIN + (n["col"] + 0.5) * COL_W
            n["cy"] = self.row_y[n["row"]]
            if n["kind"] == "end":
                n["bh"] = 28
            else:
                n["bh"] = n["h"]

    def port(self, nid, side):
        n = self.nodes[nid]
        w = n["w"] if n["kind"] not in ("start", "end") else 26
        h = n["bh"] if n["kind"] not in ("start",) else 26
        if n["kind"] == "end":
            w = h = 28
        return {
            "t": (n["cx"], n["cy"] - h / 2),
            "b": (n["cx"], n["cy"] + h / 2),
            "l": (n["cx"] - w / 2, n["cy"]),
            "r": (n["cx"] + w / 2, n["cy"]),
        }[side]

    def route(self, e):
        (x1, y1), (x2, y2) = self.port(e["a"], e["out"]), self.port(e["b"], e["inn"])
        o, i, via = e["out"], e["inn"], e["via"]
        if via is not None:
            axis, v = via
            if axis == "x":  # out sideways to x=v, vertical, then into target
                pts = [(x1, y1), (v, y1), (v, y2), (x2, y2)]
                if i in ("t", "b"):
                    yy = y2 - 18 if i == "t" else y2 + 18
                    pts = [(x1, y1), (v, y1), (v, yy), (x2, yy), (x2, y2)]
            else:  # down/up to y=v, horizontal, then into target
                pts = [(x1, y1), (x1, v), (x2, v), (x2, y2)]
                if i in ("l", "r"):
                    pts = [(x1, y1), (x1, v), (x2 + (-18 if i == "l" else 18), v),
                           (x2 + (-18 if i == "l" else 18), y2), (x2, y2)]
            return pts
        vertical_out = o in ("t", "b")
        vertical_in = i in ("t", "b")
        if vertical_out and vertical_in:
            if abs(x1 - x2) < 1:
                return [(x1, y1), (x2, y2)]
            my = y1 + ROW_GAP / 2 if o == "b" else y1 - ROW_GAP / 2
            return [(x1, y1), (x1, my), (x2, my), (x2, y2)]
        if not vertical_out and vertical_in:
            return [(x1, y1), (x2, y1), (x2, y2)]
        if vertical_out and not vertical_in:
            return [(x1, y1), (x1, y2), (x2, y2)]
        if abs(y1 - y2) < 1:
            return [(x1, y1), (x2, y2)]
        mx = (x1 + x2) / 2
        return [(x1, y1), (mx, y1), (mx, y2), (x2, y2)]

    # ---------- rendering ----------
    def render(self):
        self.layout()
        legend = sorted({n["br"] for n in self.nodes.values() if n["br"]})
        width = MARGIN * 2 + COL_W * len(self.col_lane)
        legend_lines = []
        for code in legend:
            legend_lines += [(code, line) for line in wrap(BR[code], width - 2 * MARGIN - 70)]
        legend_h = (30 + len(legend_lines) * 18) if legend else 0
        height = self.body_bottom + legend_h + 40
        s = []
        a = s.append
        a(f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.0f}" height="{height:.0f}" '
          f'viewBox="0 0 {width:.0f} {height:.0f}" font-family="{FONT}">')
        a('<defs><marker id="arr" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" '
          'orient="auto-start-reverse"><path d="M0,0 L10,5 L0,10 z" fill="#334155"/></marker></defs>')
        a(f'<rect width="{width}" height="{height}" fill="#ffffff"/>')
        a(f'<text x="{MARGIN}" y="{MARGIN + 26}" font-size="22" font-weight="700" fill="#0f172a">'
          f'{html.escape(self.code)}: {html.escape(self.title)}</text>')
        a(f'<text x="{MARGIN}" y="{MARGIN + 50}" font-size="13" fill="#64748b">Specification: '
          f'{html.escape(self.spec)}</text>')

        # lanes
        top = MARGIN + TITLE_H
        col = 0
        for key, n in self.lanes:
            name, strong, bg, _ = LANES[key]
            x = MARGIN + col * COL_W
            w = n * COL_W
            a(f'<rect x="{x}" y="{top}" width="{w}" height="{self.body_bottom - top}" fill="{bg}" '
              f'stroke="#cbd5e1"/>')
            a(f'<rect x="{x}" y="{top}" width="{w}" height="{LANE_HEAD}" fill="{strong}"/>')
            a(f'<text x="{x + w / 2}" y="{top + 25}" font-size="15" font-weight="700" fill="#fff" '
              f'text-anchor="middle">{html.escape(name)}</text>')
            col += n

        # edges first so nodes sit on top of them
        for e in self.edges:
            pts = self.route(e)
            d = "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in pts)
            a(f'<path d="{d}" fill="none" stroke="#334155" stroke-width="1.6" marker-end="url(#arr)"/>')
        for e in self.edges:
            if not e["label"]:
                continue
            pts = self.route(e)
            (xa, ya), (xb, yb) = pts[0], pts[1]
            if abs(xa - xb) + abs(ya - yb) < 40 and len(pts) > 2:
                (xa, ya), (xb, yb) = pts[1], pts[2]
            lx, ly = (xa + xb) / 2, (ya + yb) / 2
            tw = len(e["label"]) * 7 + 12
            a(f'<rect x="{lx - tw / 2:.1f}" y="{ly - 10:.1f}" width="{tw:.1f}" height="20" rx="10" '
              f'fill="#fff" stroke="#94a3b8"/>')
            a(f'<text x="{lx:.1f}" y="{ly + 4.5:.1f}" font-size="12" font-weight="600" fill="#334155" '
              f'text-anchor="middle">{html.escape(e["label"])}</text>')

        for n in self.nodes.values():
            self._node(a, n)

        if legend:
            y = self.body_bottom + 26
            a(f'<text x="{MARGIN}" y="{y}" font-size="13" font-weight="700" fill="#0f172a">'
              f'Business rules (02. Business Rules &amp; Validations)</text>')
            prev = None
            for code, line in legend_lines:
                y += 18
                if code != prev:
                    a(f'<text x="{MARGIN}" y="{y}" font-size="12" font-weight="700" fill="#b91c1c" '
                      f'font-family="Consolas, monospace">{code}</text>')
                a(f'<text x="{MARGIN + 60}" y="{y}" font-size="12" fill="#334155">{html.escape(line)}</text>')
                prev = code
        a("</svg>")
        return "\n".join(s)

    def _text(self, a, lines, cx, cy, bold=False, color="#0f172a", left=None):
        y0 = cy - (len(lines) - 1) * LINE_H / 2 + 4.5
        weight = ' font-weight="700"' if bold else ""
        x, anchor = (cx, "middle") if left is None else (left, "start")
        for k, line in enumerate(lines):
            a(f'<text x="{x:.1f}" y="{y0 + k * LINE_H:.1f}" font-size="13"{weight} fill="{color}" '
              f'text-anchor="{anchor}">{html.escape(line)}</text>')

    def _node(self, a, n):
        lane = self.col_lane[n["col"]]
        _, strong, _, tint = LANES[lane]
        cx, cy, k = n["cx"], n["cy"], n["kind"]
        if k == "start":
            a(f'<circle cx="{cx}" cy="{cy}" r="12" fill="#0f172a"/>')
            if n["text"]:
                a(f'<text x="{cx + 20}" y="{cy + 4.5}" font-size="12" fill="#475569">{html.escape(n["text"])}</text>')
            return
        if k == "end":
            a(f'<circle cx="{cx}" cy="{cy}" r="13" fill="#fff" stroke="#0f172a" stroke-width="2"/>')
            a(f'<circle cx="{cx}" cy="{cy}" r="8" fill="#0f172a"/>')
            if n["text"]:
                lines = wrap(n["text"], NODE_W - 20)
                self._text(a, lines, cx, cy + 22 + (len(lines) - 1) * LINE_H / 2 + 4, color="#475569")
            return
        w, h = n["w"], n["h"]
        x, y = cx - w / 2, cy - h / 2
        if k == "decision":
            a(f'<polygon points="{cx},{y} {cx + w / 2},{cy} {cx},{y + h} {x},{cy}" fill="#fff" '
              f'stroke="{strong}" stroke-width="2"/>')
            self._text(a, wrap(n["text"], w - 44), cx, cy)
        elif k == "state":
            a(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{min(h / 2, 22)}" fill="{tint}" '
              f'stroke="{strong}" stroke-width="2.2"/>')
            self._text(a, wrap(n["text"], w - 22), cx, cy + (3 if n["br"] else 0), bold=True)
        else:
            a(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" fill="#fff" '
              f'stroke="{strong}" stroke-width="1.6"/>')
            left = x + 12 if n["align"] == "left" else None
            self._text(a, wrap(n["text"], w - 22), cx, cy + (3 if n["br"] else 0), left=left)
        if n["br"]:
            bw = 50
            a(f'<rect x="{x + w - bw - 6}" y="{y - 9}" width="{bw}" height="18" rx="4" fill="#b91c1c"/>')
            a(f'<text x="{x + w - bw / 2 - 6}" y="{y + 4}" font-size="11" font-weight="700" fill="#fff" '
              f'text-anchor="middle" font-family="Consolas, monospace">{n["br"]}</text>')

    def save(self):
        path = os.path.join(OUT, f"{self.code}.svg")
        with open(path, "w", encoding="utf-8") as f:
            f.write(self.render())
        return path


SPEC02 = "SPEC-02: Trip & Fuel Log Lifecycle"


def us01():
    d = Diagram("US-01", "Plan a Trip", SPEC02, [("D", 1), ("S", 1), ("F", 1)])
    d.node("s", "start", 0, 0)
    d.node("open", "action", 0, 1, "Open \"Plan a trip\"")
    d.node("pool", "action", 1, 2, "Offer pool vehicles: active, not suspended, valid STK", br="BR-01")
    d.node("pick", "action", 0, 3, "Pick a vehicle (vignette expiry shown as a warning, not a block)", br="BR-02")
    d.node("fill", "action", 0, 4, "Fill in date/time, from/to and purpose")
    d.node("planned", "state", 1, 5, "Trip saved: Planned")
    d.node("review", "action", 2, 6, "Review the plan: vehicle, timing, purpose (overlap flags)")
    d.node("ok", "decision", 2, 7, "Approve the plan?")
    d.node("appr", "state", 1, 7, "Plan Approved", br="BR-01")
    d.node("e", "end", 1, 8, "Driver may carry out the trip (continues in US-02)")
    d.node("why", "action", 2, 8, "Enter the reason for the rejection (required)")
    d.node("rej", "state", 0, 9, "Plan Rejected: driver sees the reason, edits and resubmits", br="BR-07")
    d.edge("s", "open")
    d.edge("open", "pool")
    d.edge("pool", "pick")
    d.edge("pick", "fill")
    d.edge("fill", "planned")
    d.edge("planned", "review")
    d.edge("review", "ok")
    d.edge("ok", "why", "No")
    d.edge("why", "rej", out="b", inn="r")
    d.edge("rej", "pick", out="l", inn="l", via=("x", MARGIN + 12))
    d.edge("ok", "appr", "Yes", out="l", inn="r")
    d.edge("appr", "e")
    return d


def us02():
    d = Diagram("US-02", "Log a Trip (Fuel & Odometer)", SPEC02, [("D", 2), ("S", 1), ("F", 1)])
    d.node("s", "start", 0, 0)
    d.node("open", "action", 0, 1, "After the drive, open the approved trip (from US-01)")
    d.node("odo", "action", 0, 2, "Enter odometer start and end (required)")
    d.node("val", "decision", 2, 3, "End >= start?", br="BR-04")
    d.node("done", "state", 2, 4, "Trip Completed + anomaly checks run (warnings only)", br="BR-05")
    d.node("fuel", "decision", 0, 5, "Add a fuel fill?")
    d.node("e1", "end", 1, 5, "Done: trip stays Completed")
    d.node("fill", "action", 0, 6, "Enter liters, price and receipt photo")
    d.node("pend", "state", 2, 7, "Fuel entry: Pending approval", br="BR-06")
    d.node("fa", "decision", 3, 8, "Approve the fuel entry?")
    d.node("frej", "state", 0, 8, "Fuel Rejected: correct and resubmit the fuel entry", br="BR-07")
    d.node("fok", "state", 2, 9, "Fuel Approved")
    d.node("e2", "end", 2, 10, "Trip is Completed either way")
    d.edge("s", "open")
    d.edge("open", "odo")
    d.edge("odo", "val", out="r", inn="t")
    d.edge("val", "odo", "No", out="l", inn="b")
    d.edge("val", "done", "Yes")
    d.edge("done", "fuel", out="l", inn="t")
    d.edge("fuel", "e1", "No", out="r", inn="l")
    d.edge("fuel", "fill", "Yes")
    d.edge("fill", "pend", out="b", inn="l")
    d.edge("pend", "fa", out="r", inn="t")
    d.edge("fa", "frej", "No", out="l", inn="r")
    d.edge("frej", "fill", out="l", inn="l", via=("x", MARGIN + 12))
    d.edge("fa", "fok", "Yes", out="b", inn="r")
    d.edge("fok", "e2")
    return d


def us03():
    d = Diagram("US-03", "Report a Vehicle Problem", "SPEC-03: Problem Reports", [("D", 1), ("S", 1), ("F", 1)])
    d.node("s", "start", 0, 0)
    d.node("trip", "action", 0, 1, "Choose \"Report a problem\" and pick any company vehicle", br="BR-08")
    d.node("cat", "action", 0, 2, "Pick a category (check-engine light, noise, damage...) and describe it")
    d.node("save", "state", 1, 3, "Report saved: Open (vehicle, reporter, timestamp)")
    d.node("see", "action", 2, 4, "Sees the report in the open problem reports")
    d.node("e", "end", 2, 5, "Reviewed and resolved in US-09")
    d.edge("s", "trip")
    d.edge("trip", "cat")
    d.edge("cat", "save")
    d.edge("save", "see")
    d.edge("see", "e")
    return d


def us04():
    d = Diagram("US-04", "View Trip History", SPEC02, [("D", 1), ("S", 1)])
    d.node("s", "start", 0, 0)
    d.node("open", "action", 0, 1, "Open \"My trips\"")
    d.node("load", "action", 1, 2, "List the driver's own trips, every status (company data only)")
    d.node("pick", "action", 0, 3, "Select a trip")
    d.node("det", "action", 1, 4, "Show detail: vehicle, route, purpose, odometer, fuel, plan and fuel approval status")
    d.node("e", "end", 1, 5)
    d.edge("s", "open")
    d.edge("open", "load")
    d.edge("load", "pick")
    d.edge("pick", "det")
    d.edge("det", "e")
    return d


SPEC08 = "SPEC-08: Account & Tenant Setup"


def us05():
    d = Diagram("US-05", "Register Company & Invite Users", SPEC08, [("F", 1), ("S", 1), ("U", 1)])
    d.node("s", "start", 0, 0)
    d.node("sign", "action", 0, 1, "Sign up: company details and own account")
    d.node("ten", "state", 1, 2, "New company (tenant) created with isolated data")
    d.node("fm", "state", 1, 3, "Registrant becomes the first Fleet Manager")
    d.node("inv", "action", 0, 4, "Invite a user by email with a role: Driver, Accountant or Fleet Manager")
    d.node("send", "action", 1, 5, "Send the invitation")
    d.node("acc", "action", 2, 6, "Accept the invitation and set up the account")
    d.node("act", "state", 1, 7, "User active in the company with the given role")
    d.node("more", "decision", 0, 8, "Invite someone else?")
    d.node("e", "end", 0, 9)
    d.edge("s", "sign")
    d.edge("sign", "ten")
    d.edge("ten", "fm")
    d.edge("fm", "inv")
    d.edge("inv", "send")
    d.edge("send", "acc")
    d.edge("acc", "act")
    d.edge("act", "more")
    d.edge("more", "inv", "Yes", out="l", inn="l", via=("x", MARGIN + 12))
    d.edge("more", "e", "No")
    return d


def us06():
    d = Diagram("US-06", "Manage Users & Roles", SPEC08, [("F", 2), ("S", 2)])
    d.node("s", "start", 0, 0)
    d.node("open", "action", 0, 1, "Open the user list")
    d.node("list", "action", 2, 2, "Show the company's users and their roles")
    d.node("sel", "action", 0, 3, "Select a user")
    d.node("what", "decision", 0, 4, "What to do?")
    d.node("chg", "action", 0, 5, "Choose a new role: Driver, Accountant or Fleet Manager")
    d.node("saved", "state", 2, 6, "Role changed")
    d.node("rev", "state", 3, 5, "Access revoked: user can no longer sign in")
    d.node("e", "end", 2, 7)
    d.node("e2", "end", 3, 6)
    d.edge("s", "open")
    d.edge("open", "list")
    d.edge("list", "sel")
    d.edge("sel", "what")
    d.edge("what", "chg", "Change role")
    d.edge("chg", "saved")
    d.edge("what", "rev", "Revoke access", out="r", inn="t")
    d.edge("saved", "e")
    d.edge("rev", "e2")
    return d


def us07():
    d = Diagram("US-07", "Manage Vehicles", "SPEC-01: Vehicle Management", [("F", 3), ("S", 2)])
    d.node("s", "start", 1, 0)
    d.node("open", "action", 1, 1, "Open the vehicle list")
    d.node("what", "decision", 1, 2, "What to do?")
    d.node("reg", "action", 0, 3, "Register: plate, VIN, make/model, fuel type, current odometer")
    d.node("upd", "action", 1, 3, "Update details or STK / insurance / vignette due dates")
    d.node("sus", "action", 2, 3, "Suspend the vehicle")
    d.node("save", "state", 3, 4, "Vehicle saved as a pool vehicle (no assigned driver)")
    d.node("hid", "state", 4, 5, "Suspended: not selectable for new trips", br="BR-01")
    d.node("exp", "action", 3, 5, "Vignette expiry warns drivers; STK and insurance dates show to the Fleet Manager only", br="BR-02")
    d.node("e", "end", 3, 6)
    d.node("e2", "end", 4, 6)
    d.edge("s", "open")
    d.edge("open", "what")
    d.edge("what", "reg", "Register", out="l", inn="t")
    d.edge("what", "upd", "Update")
    d.edge("what", "sus", "Suspend", out="r", inn="t")
    d.edge("reg", "save", out="b", inn="t")
    d.edge("upd", "save", out="b", inn="t")
    d.edge("sus", "hid", out="r", inn="t")
    d.edge("save", "exp")
    d.edge("exp", "e")
    d.edge("hid", "e2")
    return d


def us08():
    d = Diagram("US-08", "Record or Schedule Service", "SPEC-04: Service & Maintenance", [("F", 2), ("S", 2)])
    d.node("s", "start", 0, 0)
    d.node("open", "action", 0, 1, "Open a vehicle's service section")
    d.node("what", "decision", 0, 2, "Service done or planned?")
    d.node("rec", "action", 0, 3, "Enter what was done, when, odometer, cost and garage")
    d.node("plan", "action", 1, 3, "Set next due date and/or due mileage")
    d.node("saved", "state", 2, 4, "Service record saved (cost feeds statistics and export)")
    d.node("ps", "state", 3, 4, "Planned service saved")
    d.node("due", "decision", 3, 5, "Due date or mileage passed?", br="BR-09")
    d.node("over", "state", 3, 6, "Marked Overdue for the Fleet Manager")
    d.node("e", "end", 2, 5)
    d.node("e2", "end", 3, 7)
    d.edge("s", "open")
    d.edge("open", "what")
    d.edge("what", "rec", "Done")
    d.edge("what", "plan", "Planned", out="r", inn="t")
    d.edge("rec", "saved", out="b", inn="t")
    d.edge("plan", "ps", out="r", inn="t")
    d.edge("saved", "e")
    d.edge("ps", "due")
    d.edge("due", "over", "Yes")
    d.edge("over", "e2")
    return d


def us09():
    d = Diagram("US-09", "Review & Resolve Problem Reports", "SPEC-03: Problem Reports", [("D", 1), ("S", 1), ("F", 1)])
    d.node("s", "start", 0, 0)
    d.node("rep", "action", 0, 1, "Report a vehicle problem (US-03)")
    d.node("open", "state", 1, 2, "Problem report: Open")
    d.node("list", "action", 2, 3, "Open the list of open problem reports")
    d.node("rev", "action", 2, 4, "Review the report (vehicle, category, description)")
    d.node("fix", "decision", 2, 5, "Solved?")
    d.node("pend", "state", 1, 6, "Pending + note (e.g. waiting for the garage)")
    d.node("note", "action", 2, 7, "Write a resolution note")
    d.node("res", "state", 1, 8, "Resolved")
    d.node("e", "end", 1, 9, "A repair may be logged as a service record (US-08)")
    d.edge("s", "rep")
    d.edge("rep", "open")
    d.edge("open", "list")
    d.edge("list", "rev")
    d.edge("rev", "fix")
    d.edge("fix", "pend", "Not yet", out="l", inn="t")
    d.edge("fix", "note", "Yes")
    d.edge("pend", "rev", "Later", out="l", inn="l", via=("x", MARGIN + COL_W + 10))
    d.edge("note", "res", out="b", inn="t")
    d.edge("res", "e")
    return d


def us10():
    d = Diagram("US-10", "Review Flagged Anomalies", "SPEC-05: Anomaly Checks", [("D", 1), ("S", 2), ("F", 1)])
    d.node("s", "start", 0, 0)
    d.node("save", "action", 0, 1, "Save a trip or fuel log (US-02)")
    d.node("stored", "state", 1, 2, "Record saved: a flag never blocks saving")
    d.node("checks", "action", 1, 3,
           "Run checks:\n• odometer goes backwards\n• gap to previous trip > 10 km\n"
           "• trips overlap on a vehicle\n• driver on two trips at once\n"
           "• vehicle in service, suspended or STK expired", br="BR-10", align="left")
    d.node("hit", "decision", 1, 4, "Any check hit?", br="BR-11")
    d.node("ok", "end", 2, 4, "No flag (gap within 10 km is fine)")
    d.node("flag", "state", 1, 5, "Anomaly flag created (warning)")
    d.node("list", "action", 3, 6, "Open the list of open anomalies")
    d.node("rev", "action", 3, 7, "Review the flagged record and follow up")
    d.node("e", "end", 3, 8)
    d.edge("s", "save")
    d.edge("save", "stored")
    d.edge("stored", "checks")
    d.edge("checks", "hit")
    d.edge("hit", "ok", "No", out="r", inn="l")
    d.edge("hit", "flag", "Yes")
    d.edge("flag", "list")
    d.edge("list", "rev")
    d.edge("rev", "e")
    return d


def us11():
    d = Diagram("US-11", "View Statistics", "SPEC-06: Statistics", [("F", 1), ("S", 1)])
    d.node("s", "start", 0, 0)
    d.node("open", "action", 0, 1, "Open Statistics")
    d.node("grp", "action", 0, 2, "Choose the grouping (vehicle, driver or month) and the month")
    d.node("agg", "action", 1, 3, "Sum fuel + service cost and kilometres driven for the company")
    d.node("show", "action", 0, 4, "Read totals per vehicle / driver / month")
    d.node("e", "end", 0, 5)
    d.edge("s", "open")
    d.edge("open", "grp")
    d.edge("grp", "agg")
    d.edge("agg", "show")
    d.edge("show", "e")
    return d


SPEC07 = "SPEC-07: Financial History & Excel Export  ·  Fleet Manager has the same access"


def us12():
    d = Diagram("US-12", "View Financial History", SPEC07, [("A", 1), ("S", 1)])
    d.node("s", "start", 0, 0)
    d.node("open", "action", 0, 1, "Open Financial history")
    d.node("per", "action", 0, 2, "Set the period (e.g. last quarter)")
    d.node("load", "action", 1, 3, "Load fuel fills, service costs and cost-bearing trips for the period (read-only)")
    d.node("read", "action", 0, 4, "Browse the records")
    d.node("e", "end", 0, 5, "Export the same view in US-13")
    d.edge("s", "open")
    d.edge("open", "per")
    d.edge("per", "load")
    d.edge("load", "read")
    d.edge("read", "e")
    return d


def us13():
    d = Diagram("US-13", "Export to Excel", SPEC07, [("A", 1), ("S", 1)])
    d.node("s", "start", 0, 0)
    d.node("open", "action", 0, 1, "Open Financial history")
    d.node("per", "action", 0, 2, "Set the period")
    d.node("exp", "action", 0, 3, "Click \"Export to Excel\"")
    d.node("gen", "action", 1, 4, "Build an .xlsx with trips, fuel fills and service costs for the period")
    d.node("dl", "action", 0, 5, "Download the file")
    d.node("e", "end", 0, 6)
    d.edge("s", "open")
    d.edge("open", "per")
    d.edge("per", "exp")
    d.edge("exp", "gen")
    d.edge("gen", "dl")
    d.edge("dl", "e")
    return d


if __name__ == "__main__":
    for fn in (us01, us02, us03, us04, us05, us06, us07, us08, us09, us10, us11, us12, us13):
        print(fn().save())
